package com.example.medtap

import android.Manifest
import android.content.Intent
import android.app.KeyguardManager
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.medtap.data.*
import com.example.medtap.reminder.Reminders
import com.example.medtap.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var nfc: NfcAdapter? = null
    private val state = mutableStateOf(HomeState())
    private var pendingMed: Medication? = null      // waiting to be bound to a fresh tag

    @Volatile private var resumed = false

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        state.value = state.value.copy(hasNfc = nfc != null)
        Reminders.ensureChannels(this)
        requestNotificationAccess()
        observeDatabase()

        setContent {
            MedTapTheme {
                var showAdd by remember { mutableStateOf(false) }
                // Explicit Box: the celebration has to sit ON TOP of the home screen,
                // not be laid out beside it.
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize()
                ) {
                HomeScreen(
                    state = state.value,
                    onAddMedication = { showAdd = true },
                    onMarkTaken = { med -> logDose(med) },
                    onForget = { med -> forget(med) },
                    onCancelPairing = {
                        pendingMed = null
                        state.value = state.value.copy(pairing = false)
                    }
                )
                state.value.streakOverlay?.let { days ->
                    StreakCelebration(
                        days = days,
                        onDismiss = { state.value = state.value.copy(streakOverlay = null) }
                    )
                }
                if (showAdd) AddMedicationSheet(
                    onDismiss = { showAdd = false },
                    onConfirm = { draft, pairWithTag ->
                        showAdd = false
                        if (pairWithTag) {
                            // Hold it aside; the next tag we see becomes its key.
                            pendingMed = draft
                            state.value = state.value.copy(pairing = true)
                        } else {
                            saveMedication(draft.copy(tagId = Medication.manualId()))
                        }
                    }
                )
                }
            }
        }
        handleTagIntent(intent)
    }

    // ---- writing ------------------------------------------------------------

    private fun saveMedication(med: Medication) = lifecycleScope.launch(Dispatchers.IO) {
        Db.get(this@MainActivity).dao().upsert(med)
        Reminders.scheduleNext(this@MainActivity, med)
    }

    /**
     * The one place a dose gets written, whether it came from a tag scan or from the
     * button on the home screen. Both paths have to behave identically -- same slot
     * matching, same streak, same celebration -- or the history chart starts lying.
     */
    private fun logDose(med: Medication) = lifecycleScope.launch(Dispatchers.IO) {
        val dao = Db.get(this@MainActivity).dao()
        if (!Slots.canLogNow(med)) return@launch      // too early to count for today
        val slot = Slots.todayAt(med)
        if (dao.logForSlot(med.tagId, slot) != null) return@launch   // already logged today
        dao.insert(DoseLog(tagId = med.tagId, scheduledFor = slot, takenAt = System.currentTimeMillis()))
        Reminders.resolve(this@MainActivity, med, slot)

        // Is this the last dose of the day? Counting every medication scheduled today,
        // not only the ones already due -- a 9pm pill still outstanding means the day is
        // not finished, however early it is.
        val meds = dao.activeMedsOnce()
        val complete = meds.all { dao.logForSlot(it.tagId, Slots.todayAt(it)) != null }
        val days = if (complete) dao.perfectDayStreak(meds) else 0

        val inApp = withContext(Dispatchers.Main) { watchingNow() }

        if (inApp) {
            withContext(Dispatchers.Main) {
                state.value = state.value.copy(
                    justLogged = med,
                    dayComplete = complete,
                    streakOverlay = if (complete && days > 0) days else null
                )
            }
            delay(4000)
            withContext(Dispatchers.Main) {
                state.value = state.value.copy(justLogged = null, dayComplete = false)
            }
        } else {
            // Phone asleep on the counter, tag tapped against the bottle. The news has to
            // survive until she picks it up, so it goes out as a notification instead.
            Reminders.celebrate(
                this@MainActivity, med, slot, streakFor(dao, med), if (complete) days else 0
            )
        }
    }

    /** Remove a medication and its history. Alarms first, so nothing fires into a void. */
    private fun forget(med: Medication) = lifecycleScope.launch(Dispatchers.IO) {
        Reminders.cancelAll(this@MainActivity, med)
        val dao = Db.get(this@MainActivity).dao()
        dao.deleteLogs(med.tagId)
        dao.deleteMed(med.tagId)
    }

    // ---- NFC ---------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        resumed = true
        state.value = state.value.copy(nfcOff = nfc != null && !nfc!!.isEnabled)
        // Reader mode beats foreground dispatch: no intent round-trip, and it suppresses
        // the system's own tag-discovered sound so the app owns the feedback.
        nfc?.enableReaderMode(
            this,
            { tag -> onTagScanned(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        nfc?.disableReaderMode(this)
    }

    /**
     * Is she actually watching, right now?
     *
     * `resumed` alone isn't enough: a tag held against a sleeping phone launches this
     * activity, which resumes behind the lock screen. So the screen has to be on and the
     * keyguard down as well -- that combination is what "elle regarde l'app" really means,
     * and it decides between the full-screen celebration and a notification.
     */
    private fun watchingNow(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        return resumed && pm.isInteractive && !km.isKeyguardLocked
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTagIntent(intent)
    }

    /** Covers the case where the tag itself launched the app while it was closed. */
    private fun handleTagIntent(intent: Intent?) {
        val tag: Tag? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        else @Suppress("DEPRECATION") intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let { onTagScanned(it) }
    }

    private fun onTagScanned(tag: Tag) {
        val uid = tag.id.joinToString("") { "%02X".format(it) }
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = Db.get(this@MainActivity).dao()

            // Pairing a brand-new tag to a medication the user just described.
            pendingMed?.let { draft ->
                val med = draft.copy(tagId = uid)
                dao.upsert(med)
                Reminders.scheduleNext(this@MainActivity, med)
                pendingMed = null
                withContext(Dispatchers.Main) {
                    state.value = state.value.copy(pairing = false)
                }
                return@launch
            }

            val med = dao.med(uid) ?: return@launch     // unknown tag, ignore quietly
            logDose(med)
        }
    }

    /** Consecutive days ending today where this one medication was logged. */
    private suspend fun streakFor(dao: MedDao, med: Medication): Int {
        var n = 0
        while (dao.logForSlot(med.tagId, Slots.slotDaysAgo(med, n)) != null) n++
        return n
    }


    // ---- data --------------------------------------------------------------

    private fun observeDatabase() {
        val dao = Db.get(this).dao()
        val since = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000  // 60 days
        lifecycleScope.launch {
            combine(dao.activeMeds(), dao.logsSince(since)) { meds, logs -> meds to logs }
                .collect { (meds, logs) ->
                    meds.forEach { Reminders.scheduleNext(this@MainActivity, it) }
                    Reminders.scheduleLastCall(this@MainActivity)
                    state.value = state.value.copy(
                        meds = meds,
                        logs = logs,
                        takenSlots = logs.map { it.tagId to it.scheduledFor }.toSet()
                    )
                }
        }
    }

    // ---- permissions -------------------------------------------------------

    private fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= 33) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Nothing else. An earlier version also asked for the full-screen-intent
        // permission and pushed the user into a Settings screen on first launch, to let
        // the reminder wake the lock screen. A high-importance notification that bypasses
        // Do Not Disturb and re-posts every ten minutes is already impossible to miss;
        // taking over the screen on top of that was hostile.
    }
}
