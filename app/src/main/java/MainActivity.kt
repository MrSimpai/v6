package com.example.medtap

import android.Manifest
import android.content.Intent
import android.app.KeyguardManager
import android.nfc.NfcAdapter
import android.os.PowerManager
import android.provider.Settings
import com.example.medtap.reminder.DragonShortcut
import com.example.medtap.reminder.DragonWidget
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
    private var editing = mutableStateOf<Medication?>(null)

    private val createBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) { Backup.export(this@MainActivity, uri) }
        }

    private val openBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch(Dispatchers.IO) {
                Backup.import(this@MainActivity, uri)
                Reminders.rescheduleAll(this@MainActivity)
            }
        }

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfc = NfcAdapter.getDefaultAdapter(this)
        state.value = state.value.copy(hasNfc = nfc != null)
        Reminders.ensureChannels(this)
        // Le bilan hebdomadaire est une alarme inexacte : elle ne survit ni au
        // redémarrage ni à une mise à jour, donc on la réarme à chaque ouverture. Poser
        // deux fois la même alarme la remplace, ça ne l'empile pas.
        Reminders.scheduleRecap(this)
        requestNotificationAccess()
        observeDatabase()

        setContent {
            MedTapTheme {
                // Les trente premières secondes, une seule fois dans la vie de l'app.
                // Le drapeau vit dans les préférences et non dans la base : ce n'est pas
                // une donnée d'elle, c'est un détail d'affichage, et une restauration de
                // sauvegarde ne doit pas lui faire relire l'introduction.
                var welcomed by remember { mutableStateOf(seenWelcome()) }
                if (!welcomed) {
                    WelcomeScreen(onDone = { markWelcomeSeen(); welcomed = true })
                    return@MedTapTheme
                }

                var showAdd by remember { mutableStateOf(false) }
                // Explicit Box: the celebration has to sit ON TOP of the home screen,
                // not be laid out beside it.
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize()
                ) {
                // Deux pages, l'accueil au départ : le casier se trouve en glissant vers
                // la droite, comme le casier de Clash Royale. Il n'a pas d'onglet parce
                // qu'il ne doit jamais concurrencer la seule chose qui compte, la dose.
                // Trois pages : le casier à gauche, l'accueil au centre, les réglages à
                // droite. L'accueil ne répond qu'à une question — ai-je pris ma pilule —
                // et tout ce qui n'y répond pas a été déplacé de part et d'autre.
                // La cabine d'essayage se referme d'elle-même. Une attente unique jusqu'à
                // l'échéance plutôt qu'un sondage : l'effet est relancé quand la date
                // change, et annulé si elle disparaît.
                val previewUntil = state.value.previewUntil
                LaunchedEffect(previewUntil) {
                    if (previewUntil == null) return@LaunchedEffect
                    delay((previewUntil - System.currentTimeMillis()).coerceAtLeast(0L))
                    state.value = state.value.copy(previewUntil = null, previewWorn = emptySet())
                }

                val pager = rememberPagerState(initialPage = 1) { 3 }
                HorizontalPager(state = pager, modifier = androidx.compose.ui.Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> LockerScreen(
                            owned = state.value.owned,
                            worn = state.value.dressed,
                            previewUntil = state.value.previewUntil,
                            onToggle = { id -> toggleCosmetic(id) },
                            onCode = { startPreview(it) }
                        )
                        2 -> SettingsScreen(
                            batteryRestricted = state.value.batteryRestricted,
                            onFixBattery = { requestBatteryExemption() },
                            onBackup = { createBackup.launch(Backup.suggestedFileName()) },
                            onRestore = { openBackup.launch(arrayOf("application/json")) }
                        )
                        else ->
                HomeScreen(
                    state = state.value,
                    onAddMedication = { showAdd = true },
                    onMarkTaken = { med -> logDose(med) },
                    onForget = { med -> forget(med) },
                    onSkip = { med -> skipDose(med) },
                    onEdit = { med -> editing.value = med },
                    onCancelPairing = {
                        pendingMed = null
                        state.value = state.value.copy(pairing = false)
                    }
                )
                    }
                }
                state.value.streakOverlay?.let { days ->
                    StreakCelebration(
                        days = days,
                        week = state.value.week,
                        onDismiss = {
                            state.value = state.value.copy(streakOverlay = null)
                            grantDailyCosmetic()
                        }
                    )
                }
                state.value.chestReward?.let { id ->
                    Cosmetics.byId(id)?.let { item ->
                        ChestScreen(
                            cosmetic = item,
                            onDone = { state.value = state.value.copy(chestReward = null) }
                        )
                    }
                }
                // Les points restent sous les deux pages, mais disparaissent dès qu'un
                // écran plein passe par-dessus : ils indiqueraient une navigation qui
                // n'existe plus à ce moment-là.
                if (!showAdd && state.value.streakOverlay == null && state.value.chestReward == null) {
                    PageDots(
                        current = pager.currentPage,
                        count = 3,
                        modifier = androidx.compose.ui.Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                    )
                }
                editing.value?.let { med ->
                    AddMedicationScreen(
                        onDismiss = { editing.value = null },
                        onConfirm = { draft, _ ->
                            editing.value = null
                            saveMedication(draft)
                        },
                        existing = med
                    )
                }
                if (showAdd) AddMedicationScreen(
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
        // Le créneau que cette prise remplit : celui d'aujourd'hui d'habitude, celui
        // d'hier soir si on est dans les heures qui suivent minuit. Null s'il n'y a rien
        // à noter — trop tôt, ou déjà fait.
        val slot = dao.slotToLog(med) ?: return@launch
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

    /**
     * Une pièce par journée complète, jamais deux le même jour, jamais deux fois la même.
     *
     * La pièce est écrite en base AVANT que le coffre s'affiche : si l'app est tuée
     * pendant l'animation, le cadeau est déjà acquis. L'inverse -- accorder à la fermeture
     * -- ferait perdre la récompense exactement au moment le plus frustrant.
     */
    private fun grantDailyCosmetic() = lifecycleScope.launch(Dispatchers.IO) {
        val dao = Db.get(this@MainActivity).dao()
        val already = dao.cosmeticsOnce()
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (already.any { it.unlockedAt >= startOfDay }) return@launch      // déjà servi

        val next = Cosmetics.nextLocked(already.map { it.id }.toSet()) ?: return@launch
        dao.grant(OwnedCosmetic(next.id, System.currentTimeMillis()))
        withContext(Dispatchers.Main) {
            state.value = state.value.copy(chestReward = next.id)
        }
    }

    /**
     * Un seul objet porté par emplacement. Équiper une pièce retire silencieusement celle
     * qui occupait la place -- c'est ce qu'on attend d'une garde-robe, et ça évite un
     * message d'erreur pour un problème que l'app peut régler toute seule.
     */
    // ---- la première ouverture ---------------------------------------------

    private fun prefs() = getSharedPreferences("medtap", MODE_PRIVATE)

    private fun seenWelcome(): Boolean = prefs().getBoolean(KEY_WELCOMED, false)

    private fun markWelcomeSeen() = prefs().edit().putBoolean(KEY_WELCOMED, true).apply()

    /**
     * Ouvre la cabine d'essayage si le mot est le bon.
     *
     * La tenue de départ est celle qu'elle porte vraiment, pour que l'essayage commence
     * là où elle en est plutôt que sur un dragon nu.
     */
    private fun startPreview(code: String): Boolean {
        if (!Cosmetics.isPreviewCode(code)) return false
        state.value = state.value.copy(
            previewUntil = System.currentTimeMillis() + Cosmetics.PREVIEW_MINUTES * 60_000L,
            previewWorn = state.value.worn
        )
        return true
    }

    private fun toggleCosmetic(id: String) {
        val slot = Cosmetics.byId(id)?.slot ?: return

        // Pendant l'essayage, rien ne descend jusqu'à la base — ni ce qu'on enfile, ni ce
        // qu'on retire. C'est ce qui permet d'ouvrir tout le casier sans rien offrir : à
        // l'expiration, la vraie tenue est encore exactement là où elle était.
        if (state.value.previewUntil != null) {
            val cur = state.value.previewWorn
            state.value = state.value.copy(
                previewWorn =
                    if (id in cur) cur - id
                    else cur.filterNot { Cosmetics.byId(it)?.slot == slot }.toSet() + id
            )
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = Db.get(this@MainActivity).dao()
            val owned = dao.cosmeticsOnce()
            val current = owned.firstOrNull { it.id == id } ?: return@launch

            if (current.equipped) {
                dao.setEquipped(id, false)
                return@launch
            }
            owned.filter { it.equipped && Cosmetics.byId(it.id)?.slot == slot }
                .forEach { dao.setEquipped(it.id, false) }
            dao.setEquipped(id, true)
        }
    }

    /**
     * Sauter volontairement. La dose est écrite comme n'importe quelle autre, avec un
     * drapeau : les rappels s'arrêtent, la série tient, et le graphique de dérive ne
     * trace rien, parce qu'il n'y a rien à tracer.
     */
    private fun skipDose(med: Medication) = lifecycleScope.launch(Dispatchers.IO) {
        val dao = Db.get(this@MainActivity).dao()
        val slot = Slots.todayAt(med)
        if (dao.logForSlot(med.tagId, slot) != null) return@launch
        dao.insert(
            DoseLog(
                tagId = med.tagId, scheduledFor = slot,
                takenAt = System.currentTimeMillis(), skipped = true
            )
        )
        Reminders.resolve(this@MainActivity, med, slot)
    }

    private fun requestBatteryExemption() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:$packageName"))
            )
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
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
        // Hier a-t-il sauté ? Si oui et qu'une série était en cours, le gel part tout seul.
        lifecycleScope.launch(Dispatchers.IO) {
            val used = Db.get(this@MainActivity).dao().useFreezeIfNeeded()
            if (used) withContext(Dispatchers.Main) {
                state.value = state.value.copy(freezeUsed = true)
            }
        }
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
            combine(
                dao.activeMeds(), dao.logsSince(since), dao.cosmetics()
            ) { meds, logs, cosmetics -> Triple(meds, logs, cosmetics) }
                .collect { (meds, logs, cosmetics) ->
                    meds.forEach { Reminders.scheduleNext(this@MainActivity, it) }
                    state.value = state.value.copy(
                        meds = meds,
                        logs = logs,
                        takenDays = logs.map { it.tagId to Slots.dayOf(it.scheduledFor) }.toSet(),
                        week = dao.weekStatus(meds),
                        owned = cosmetics.map { it.id }.toSet(),
                        worn = cosmetics.filter { it.equipped }.map { it.id }.toSet(),
                        batteryRestricted = !ReminderHealth.batteryUnrestricted(this@MainActivity),
                        // Combien de fois, cette semaine, un rappel n'est pas parti du
                        // tout. Recalculé à chaque changement de données, donc noter la
                        // dose manquante fait disparaître l'avertissement tout seul.
                        silentMisses = dao.silentMisses(meds),
                        streak = dao.currentStreak(meds)
                    )
                    DragonWidget.refresh(this@MainActivity)
                    // Le raccourci du lanceur porte la tenue du moment. L'icône, elle,
                    // ne le peut pas — voir DragonShortcut pour pourquoi.
                    DragonShortcut.refresh(
                        this@MainActivity,
                        cosmetics.filter { it.equipped }.map { it.id }.toSet()
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

    private companion object {
        const val KEY_WELCOMED = "welcomed"
    }
}
