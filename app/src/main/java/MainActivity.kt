package com.example.medtap

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.example.medtap.data.*
import com.example.medtap.reminder.IconSwitcher
import com.example.medtap.reminder.Reminders
import com.example.medtap.ui.*
import com.example.medtap.ui.Mood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var nfc: NfcAdapter? = null
    private val state = mutableStateOf(HomeState())
    private var pendingMed: Medication? = null      // waiting to be bound to a fresh tag

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
                HomeScreen(
                    state = state.value,
                    onAddMedication = { showAdd = true },
                    onMarkTaken = { med -> logDose(med) },
                    onCancelPairing = {
                        pendingMed = null
                        state.value = state.value.copy(pairing = false)
                    }
                )
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
        val slot = Slots.currentOrPrevious(med)
        if (dao.logForSlot(med.tagId, slot) != null) return@launch   // already logged today
        dao.insert(DoseLog(tagId = med.tagId, scheduledFor = slot, takenAt = System.currentTimeMillis()))
        Reminders.resolve(this@MainActivity, med, slot, streakFor(dao, med))

        withContext(Dispatchers.Main) { state.value = state.value.copy(justLogged = med) }
        delay(4000)
        IconSwitcher.apply(this@MainActivity, Mood.Sleeping)
        withContext(Dispatchers.Main) { state.value = state.value.copy(justLogged = null) }
    }

    // ---- NFC ---------------------------------------------------------------

    override fun onResume() {
        super.onResume()
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
        nfc?.disableReaderMode(this)
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

    /** Consecutive days ending today that have a logged dose. */
    private suspend fun streakFor(dao: MedDao, med: Medication): Int {
        var n = 0
        var slot = Slots.currentOrPrevious(med)
        while (dao.logForSlot(med.tagId, slot) != null) {
            n++
            slot -= 24L * 60 * 60 * 1000
        }
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
        // Android 14 revoked full-screen intents for apps outside the alarm/calling
        // category. Without it the reminder still posts, it just cannot wake the
        // lock screen -- so send the user to the one toggle that restores it.
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(android.net.Uri.parse("package:$packageName"))
                )
            }
        }
    }
}
