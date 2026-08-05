package com.example.medtap.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.medtap.Her
import com.example.medtap.data.DoseLog
import com.example.medtap.data.Medication
import com.example.medtap.data.Slots
import com.example.medtap.data.isManual
import java.text.SimpleDateFormat
import java.util.*

/** Everything the screen needs, computed once by the activity. */
data class HomeState(
    val meds: List<Medication> = emptyList(),
    val logs: List<DoseLog> = emptyList(),
    val takenSlots: Set<Pair<String, Long>> = emptySet(),
    val justLogged: Medication? = null,
    val dayComplete: Boolean = false,   // toutes les doses du jour sont enregistrées
    val streakOverlay: Int? = null,     // jours à fêter en plein écran, sinon null
    val owned: Set<String> = emptySet(),   // cosmétiques gagnés
    val worn: Set<String> = emptySet(),    // cosmétiques portés
    val chestReward: String? = null,
    val pairing: Boolean = false,
    val hasNfc: Boolean = true,
    val nfcOff: Boolean = false
)

private val clock = SimpleDateFormat("H'h'mm", Locale.CANADA_FRENCH)

private fun Medication.timeLabel(): String = clock.format(
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hourOfDay); set(Calendar.MINUTE, minute)
    }.time
)

@Composable
fun HomeScreen(
    state: HomeState,
    onAddMedication: () -> Unit,
    onMarkTaken: (Medication) -> Unit,
    onForget: (Medication) -> Unit,
    onCancelPairing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()

    // Which row is mid-removal. Held here rather than in the row so that opening a second
    // confirmation closes the first -- two armed delete buttons at once is how you tap
    // the wrong one.
    var confirmingRemoval by remember { mutableStateOf<String?>(null) }

    // A dose is owed once its slot has passed and nothing has been logged against it.
    val owed = state.meds.filter { med ->
        val slot = Slots.todayAt(med)
        slot <= now && (med.tagId to slot) !in state.takenSlots
    }

    // How late is the most overdue thing? That drives the dragon's face.
    val worstLateMin = owed.maxOfOrNull { (now - Slots.todayAt(it)) / 60_000L } ?: 0L
    val mood = when {
        state.justLogged != null -> Mood.Cheering
        worstLateMin >= 120 -> Mood.Overdue
        worstLateMin >= 60 -> Mood.Sad
        owed.isNotEmpty() -> Mood.Waiting
        else -> Mood.Sleeping
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Pal.Mist)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 40.dp)
    ) {
        Text(
            SimpleDateFormat("EEEE d MMMM", Locale.CANADA_FRENCH).format(Date())
                .uppercase(Locale.CANADA_FRENCH),
            style = Type.Label, color = Pal.Muted
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                state.dayComplete -> "Journée complète, ${Her.name}"
                state.justLogged != null -> "C'est noté, ${Her.name}"
                mood == Mood.Overdue -> "${Her.realName}, c'est vraiment en retard"
                owed.size == 1 -> "${owed[0].name} t'attend"
                owed.size > 1 -> "${owed.size} doses t'attendent"
                state.meds.isEmpty() -> "${Her.greeting()} — on commence ?"
                else -> "${Her.greeting()}. Tout est à jour."
            },
            style = Type.Display, color = Pal.Ink
        )

        Spacer(Modifier.height(20.dp))

        // ---- hero: the mascot and the prompt ----
        Surface(color = Pal.Card, shape = Soft, tonalElevation = 0.dp) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Mascot(mood, Modifier.size(200.dp), worn = state.worn)
                Spacer(Modifier.height(8.dp))
                AnimatedContent(targetState = mood, label = "prompt") { m ->
                    Text(
                        when {
                            state.pairing && state.nfcOff ->
                                "Active le NFC, puis approche l'étiquette"
                            state.pairing ->
                                "Approche une étiquette vierge du dos du téléphone"
                            m == Mood.Cheering -> "Merci. ${Her.dragon} est contente."
                            m == Mood.Overdue -> "Prends ta dose d'aujourd'hui, s'il te plaît."
                            m == Mood.Sad -> "${Her.dragon} attend depuis un moment."
                            m == Mood.Waiting -> "Une dose t'attend en dessous."
                            else -> "Rien de prévu. ${Her.dragon} fait la sieste."
                        },
                        style = Type.Body, color = Pal.Muted, textAlign = TextAlign.Center
                    )
                }
                if (state.pairing) {
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onCancelPairing) {
                        Text("Annuler l'appairage", style = Type.Label, color = Pal.Iris)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("AUJOURD'HUI", style = Type.Label, color = Pal.Muted)
        Spacer(Modifier.height(10.dp))

        state.meds.forEach { med ->
            val slot = Slots.todayAt(med)
            val taken = (med.tagId to slot) in state.takenSlots
            MedRow(
                med = med,
                taken = taken,
                due = slot <= now,
                loggable = Slots.canLogNow(med),
                confirmingRemoval = confirmingRemoval == med.tagId,
                onAskRemove = { confirmingRemoval = med.tagId },
                onCancelRemove = { confirmingRemoval = null },
                onConfirmRemove = { confirmingRemoval = null; onForget(med) },
                log = state.logs.firstOrNull { it.tagId == med.tagId && it.scheduledFor == slot },
                onMarkTaken = { onMarkTaken(med) }
            )
            Spacer(Modifier.height(10.dp))
        }

        if (state.meds.isEmpty()) {
            Text(
                "Ajoute un médicament, choisis l'heure du rappel, et c'est parti. " +
                    "${Her.dragon} s'occupe du reste : elle te fait signe quand c'est " +
                    "l'heure, et elle n'arrête que quand la dose est notée.",
                style = Type.Body, color = Pal.Muted
            )
        }

        Spacer(Modifier.height(14.dp))
        FilledTonalButton(
            onClick = onAddMedication,
            shape = Pill,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Pal.IrisSoft, contentColor = Pal.Iris
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Ajouter un médicament", style = Type.Title) }

        // ---- history ----
        state.meds.forEach { med ->
            val points = buildDays(med, state.logs)
            Spacer(Modifier.height(32.dp))
            Surface(color = Pal.Card, shape = Soft) {
                Column(Modifier.padding(20.dp)) {
                    Text(med.name.uppercase(), style = Type.Label, color = Pal.Iris)
                    Spacer(Modifier.height(12.dp))
                    StreakStrip(points)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${points.count { it.log != null }} jours sur ${points.size}",
                        style = Type.Label, color = Pal.Muted
                    )
                    Spacer(Modifier.height(24.dp))
                    DriftChart(points, "CIBLE ${med.timeLabel()}")
                }
            }
        }

        Spacer(Modifier.height(36.dp))
        Text(
            Her.dedication,
            style = Type.Label, color = Pal.Muted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * One medication for today. The button is the whole point of it: a dose can always be
 * logged by hand, tag or no tag. Without it, being away from the bottle means the
 * dragon nags about a dose that was actually swallowed an hour ago -- which is exactly
 * how a reminder app teaches you to ignore it.
 */
@Composable
private fun MedRow(
    med: Medication,
    taken: Boolean,
    due: Boolean,
    loggable: Boolean,
    log: DoseLog?,
    confirmingRemoval: Boolean,
    onAskRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    onMarkTaken: () -> Unit
) {
    Surface(
        color = if (taken) Pal.MintSoft else Pal.Card,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(Pill)
                        .background(if (taken) Pal.Mint else Pal.Apricot)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(med.name, style = Type.Title, color = Pal.Ink)
                    Text(
                        "${med.doseText} · ${med.timeLabel()}",
                        style = Type.Body, color = Pal.Muted
                    )
                }
                Text(
                    if (taken && log != null) "Pris à ${clock.format(Date(log.takenAt))}"
                    else if (due) "À prendre" else "Plus tard",
                    style = Type.Label,
                    color = if (taken) Pal.Mint else Pal.Apricot
                )
            }

            // The button only appears once the dose is due, or nearly. Offering it at
            // 4am for a 9am dose invites logging today's pill in the middle of the
            // night, which would then silence the morning reminder.
            if (!taken && loggable) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onMarkTaken,
                    shape = Pill,
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Iris),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("Je l'ai prise", style = Type.Title) }

                if (!med.isManual) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ou approche le téléphone de l'étiquette sur la bouteille.",
                        style = Type.Label, color = Pal.Muted
                    )
                }
            }

            // Removal is two deliberate taps, and the second one is never in the place
            // the first one was -- so a double tap on "Retirer" cannot delete anything.
            Spacer(Modifier.height(8.dp))
            if (!confirmingRemoval) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAskRemove) {
                        Text("Retirer", style = Type.Label, color = Pal.Muted)
                    }
                }
            } else {
                Surface(color = Pal.Mist, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Retirer ${med.name} ?", style = Type.Title, color = Pal.Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Son historique est effacé avec, et ça ne se défait pas.",
                            style = Type.Label, color = Pal.Muted
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onCancelRemove,
                                shape = Pill,
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) { Text("Annuler", style = Type.Label, color = Pal.Ink) }
                            Spacer(Modifier.width(10.dp))
                            Button(
                                onClick = onConfirmRemove,
                                shape = Pill,
                                colors = ButtonDefaults.buttonColors(containerColor = Pal.Danger),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) { Text("Oui, retirer", style = Type.Label) }
                        }
                    }
                }
            }
        }
    }
}
