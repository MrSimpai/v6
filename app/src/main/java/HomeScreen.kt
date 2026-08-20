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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.example.medtap.Her
import com.example.medtap.data.DayState
import com.example.medtap.data.DayWindow
import com.example.medtap.data.DoseLog
import com.example.medtap.data.Medication
import com.example.medtap.data.Slots
import com.example.medtap.data.Week
import com.example.medtap.data.windowOn
import com.example.medtap.reminder.FloMessages
import com.example.medtap.reminder.Tier
import com.example.medtap.data.isManual
import java.text.SimpleDateFormat
import java.util.*

/** Everything the screen needs, computed once by the activity. */
data class HomeState(
    val meds: List<Medication> = emptyList(),
    val logs: List<DoseLog> = emptyList(),
    /**
     * Les couples (médicament, minuit de la journée) déjà enregistrés.
     *
     * La journée et non l'horodatage du créneau : une dose conserve l'heure qu'avait le
     * médicament au moment de la prise, donc changer l'heure du rappel faisait disparaître
     * la coche d'aujourd'hui alors que la dose était bel et bien notée.
     */
    val takenDays: Set<Pair<String, Long>> = emptySet(),
    val justLogged: Medication? = null,
    val dayComplete: Boolean = false,   // toutes les doses du jour sont enregistrées
    val streakOverlay: Int? = null,     // jours à fêter en plein écran, sinon null
    val owned: Set<String> = emptySet(),   // cosmétiques gagnés
    val worn: Set<String> = emptySet(),    // cosmétiques portés
    val chestReward: String? = null,
    val batteryRestricted: Boolean = false,
    val week: List<DayState> = emptyList(),
    val freezeUsed: Boolean = false,
    /**
     * L'essayage : jusqu'à quand tout le casier est ouvert, ou `null` le reste du temps.
     *
     * Rien de ce qui se passe pendant cette fenêtre ne descend jusqu'à la base. Ce n'est
     * pas un raccourci pour tout débloquer — les pièces se gagnent une par journée
     * complète et c'est ce qui leur donne leur valeur — c'est cinq minutes de cabine
     * d'essayage. À l'expiration, elle se retrouve exactement comme avant.
     */
    val previewUntil: Long? = null,
    val previewWorn: Set<String> = emptySet(),
    /** Créneaux passés cette semaine sans rappel posé ni dose notée : une panne, pas un risque. */
    val silentMisses: Int = 0,
    /** La série telle que la lit le widget. Ici, elle sert à décider quand elle a le droit d'être fière. */
    val streak: Int = 0,
    /**
     * Le médicament dont on est en train de lier l'étiquette, ou `null`.
     *
     * C'est un nom et non un booléen parce que la page d'appairage l'affiche : savoir
     * QUELLE bouteille on est en train d'étiqueter est la première chose qu'on se demande
     * en tenant l'étiquette et le téléphone.
     */
    val pairing: String? = null,
    /** Ce que le NFC vient de répondre : étiquette inconnue, déjà prise, ou rien. */
    val nfcNote: String? = null,
    val hasNfc: Boolean = true,
    val nfcOff: Boolean = false
)

/**
 * Ce qu'elle porte à l'écran : la tenue d'essayage tant qu'elle dure, sinon ce qui est
 * réellement mis. Le widget, lui, lit la base et continue donc de montrer la vraie tenue —
 * l'essayage n'existe que dans l'app, là où on peut voir le compte à rebours.
 */
val HomeState.dressed: Set<String>
    get() = if (previewUntil != null) previewWorn else worn

private val clock = SimpleDateFormat("H'h'mm", Locale.CANADA_FRENCH)

/** « 7h00 », à partir de minutes depuis minuit. */
private fun hhmm(minuteOfDay: Int): String =
    String.format(Locale.CANADA_FRENCH, "%dh%02d", minuteOfDay / 60, minuteOfDay % 60)

/** La plage de [med] pour la journée de [now]. */
private fun Medication.windowToday(now: Long): DayWindow = windowOn(
    Week.index(Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK))
)

/**
 * L'heure d'aujourd'hui, avec sa plage quand il y en a une : « 7h00 → 10h00 ».
 *
 * Sur la journée en cours et pas sur `hourOfDay` : depuis que les heures peuvent différer
 * d'un jour à l'autre, ce champ n'est plus l'heure du rappel mais la plus matinale de la
 * semaine, et l'afficher tel quel annoncerait 7h un jeudi réglé à 10h.
 */
private fun Medication.timeLabel(now: Long = System.currentTimeMillis()): String {
    val w = windowToday(now)
    return if (w.instant) hhmm(w.startMinute)
    else "${hhmm(w.startMinute)} → ${hhmm(w.endMinute)}"
}

@Composable
fun HomeScreen(
    state: HomeState,
    onAddMedication: () -> Unit,
    onMarkTaken: (Medication) -> Unit,
    onForget: (Medication) -> Unit,
    onSkip: (Medication) -> Unit,
    onEdit: (Medication) -> Unit,
    modifier: Modifier = Modifier
) {
    // L'heure, qui avance pendant qu'on regarde l'écran.
    //
    // `System.currentTimeMillis()` lu une seule fois n'est pas un état observable : la
    // valeur restait figée à l'instant de la composition, et comme rien ne recompose tant
    // que la base ne change pas, l'humeur du dragon ne bougeait plus. Une dose devenait
    // due, le retard passait l'heure, et le dragon gardait la tête qu'il avait à
    // l'ouverture — jusqu'à ce qu'on ferme l'app et qu'on la rouvre, ce qui refait une
    // composition depuis zéro. C'est exactement le symptôme qu'on voyait.
    //
    // `repeatOnLifecycle(RESUMED)` fait deux choses d'un coup : rien ne tourne quand
    // l'écran n'est pas devant les yeux, et la valeur est rafraîchie à l'instant même du
    // retour — donc pas de dragon périmé pendant les vingt premières secondes.
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(20_000)
            }
        }
    }

    // Which row is mid-removal. Held here rather than in the row so that opening a second
    // confirmation closes the first -- two armed delete buttons at once is how you tap
    // the wrong one.
    var confirmingRemoval by remember { mutableStateOf<String?>(null) }

    // Sauter une dose est irréversible pour la journée : le créneau est consommé, et il
    // n'y a pas de « annuler ». Donc deux gestes, comme pour le retrait.
    var confirmingSkip by remember { mutableStateOf<String?>(null) }

    // A dose is owed once its slot has passed and nothing has been logged against it.
    val owed = state.meds.filter { med ->
        val slot = Slots.todayAt(med, now)
        slot <= now && (med.tagId to Slots.dayOf(slot)) !in state.takenDays
    }

    // How late is the most overdue thing? That drives the dragon's face. Le retard se
    // compte à partir de la FIN de la plage horaire : entre 7h et 10h, une dose réglée sur
    // cette plage n'est pas en retard, elle est dans son créneau.
    val worstLateMin =
        owed.maxOfOrNull { Slots.pressureMinutes(it, Slots.todayAt(it, now), now) } ?: 0L
    val mood = when {
        // Le mot du casier : elle le porte partout, pas seulement sur la page où il a
        // été dit. C'est ce qui en fait une réponse plutôt qu'un déverrouillage.
        state.previewUntil != null -> Mood.Love
        state.justLogged != null -> Mood.Cheering
        owed.isEmpty() && state.streak >= 7 -> Mood.Proud
        // Les mêmes paliers que le rappel, décidés au même endroit.
        owed.isNotEmpty() -> Tier.forLateness(worstLateMin).mood
        else -> Mood.Sleeping
    }

    Column(
        modifier
            .fillMaxSize()
            .sky()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 40.dp)
    ) {
        Text(
            // Sur la même horloge que le reste de l'écran : sinon l'app laissée ouverte
            // toute la nuit affiche encore la date d'hier au matin.
            SimpleDateFormat("EEEE d MMMM", Locale.CANADA_FRENCH).format(Date(now))
                .uppercase(Locale.CANADA_FRENCH),
            style = Type.Label, color = skyMuted()
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
            style = Type.Display, color = skyInk()
        )

        // Une panne constatée, pas un risque. Elle a sa place sur l'écran d'accueil alors
        // que l'avertissement de batterie n'y est plus : celui-là est permanent et devient
        // du décor, celui-ci n'apparaît que le jour où un rappel n'est réellement pas
        // parti. Ce jour-là, c'est la chose la plus importante de l'écran.
        if (state.silentMisses > 0) {
            Spacer(Modifier.height(14.dp))
            Surface(color = Pal.Card, shape = Soft) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Je n'ai pas réussi à te prévenir 😔", style = Type.Title, color = Pal.Apricot)
                    Text(
                        if (state.silentMisses == 1)
                            "Un rappel n'est pas parti cette semaine. Le téléphone a mis " +
                                "l'app en veille."
                        else
                            "${state.silentMisses} rappels ne sont pas partis cette semaine. " +
                                "Le téléphone met l'app en veille.",
                        style = Type.Label, color = Pal.Muted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Va dans les réglages, à droite, pour que ça n'arrive plus.",
                        style = Type.Label, color = Pal.Muted
                    )
                }
            }
        }

        if (state.freezeUsed) {
            Spacer(Modifier.height(14.dp))
            Surface(color = Pal.MintSoft, shape = Soft) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Série protégée ❄️", style = Type.Title, color = Pal.Mint)
                    Text(
                        "Hier a sauté, mais ${Her.dragon} a utilisé ton gel de la semaine. " +
                            "Ta série continue.",
                        style = Type.Label, color = Pal.Muted
                    )
                }
            }
        }


        Spacer(Modifier.height(20.dp))

        // ---- hero: the mascot and the prompt ----
        //
        // Le carton est resserré autour du dragon : marges réduites, mascotte à 150 dp,
        // et une bande de chaque côté. Il occupait presque la moitié de la hauteur de
        // l'écran en aplat blanc, ce qui recouvrait justement la partie du ciel où il se
        // passe quelque chose — l'horizon, les nuages, l'astre. Le dragon n'y a rien
        // perdu ; c'est le vide autour de lui qui est parti.
        Surface(
            color = Pal.Card, shape = Soft, tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Mascot(mood, Modifier.size(150.dp), worn = state.dressed)
                Spacer(Modifier.height(4.dp))
                // L'appairage a sa propre page maintenant : il n'a plus rien à dire ici.
                AnimatedContent(targetState = mood, label = "prompt") { m ->
                    Text(
                        FloMessages.moodLine(m),
                        style = Type.Body, color = Pal.Muted, textAlign = TextAlign.Center
                    )
                }
                // Le compteur, à l'endroit où on le cherche.
                //
                // Il n'était affiché que sur la tuile de l'écran d'accueil du téléphone,
                // dans la célébration de quatre secondes, et dans le bilan du dimanche.
                // Autrement dit : nulle part, pour qui n'a pas posé la tuile. On note sa
                // première dose, la série vaut un, et l'app n'en dit rien — donc la seule
                // chose qu'elle demande de faire tous les jours n'a aucune trace visible
                // le reste du temps. C'est le chiffre qui fait revenir ; il ne peut pas
                // vivre uniquement dans un écran qui passe.
                //
                // Zéro ne s'affiche pas : « 0 journée complète » est un reproche, et les
                // sept points disent déjà où on en est.
                if (state.streak > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.streak}",
                        style = Type.Display, color = Pal.Mint
                    )
                    Text(
                        // Le même mot que la célébration plein écran, exactement : deux
                        // formulations pour un seul chiffre feraient douter que ce soit
                        // le même.
                        if (state.streak == 1) "JOURNÉE COMPLÈTE" else "JOURNÉES COMPLÈTES",
                        style = Type.Label, color = Pal.Mint
                    )
                }
                if (state.week.size == 7) {
                    Spacer(Modifier.height(10.dp))
                    WeekDots(state.week)
                }
                // Ce que le NFC vient de répondre, quand ce n'était pas une dose : une
                // étiquette inconnue ne faisait RIEN du tout, silencieusement, ce qui se
                // lit exactement comme un NFC en panne.
                state.nfcNote?.let { note ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        note,
                        style = Type.Label, color = Pal.Apricot,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("AUJOURD'HUI", style = Type.Label, color = Pal.Muted)
        Spacer(Modifier.height(10.dp))

        state.meds.forEach { med ->
            val slot = Slots.todayAt(med, now)
            val taken = (med.tagId to Slots.dayOf(slot)) in state.takenDays

            // Le créneau que le bouton remplirait. Même règle que celle qui écrit en base
            // — `Slots.loggableSlots` — mais vérifiée contre ce que l'écran a sous la
            // main. Deux copies de la règle, c'est deux occasions de ne pas être d'accord
            // sur ce que le bouton vient de faire.
            val target = Slots.loggableSlots(med, now)
                .firstOrNull { (med.tagId to Slots.dayOf(it)) !in state.takenDays }

            MedRow(
                med = med,
                taken = taken,
                due = slot <= now,
                // En retard veut dire « la plage est passée », pas « l'heure est passée ».
                // Sans ça une dose réglée de 7h à 10h s'affiche en retard à 7h01, ce qui
                // est précisément le reproche qu'on cherche à ne plus faire.
                late = Slots.pressureMinutes(med, slot, now) > 0,
                loggable = target != null,
                // Passé minuit, le bouton note la dose d'HIER. Il doit le dire : un
                // bouton qui enregistre autre chose que ce qu'on croit est pire qu'un
                // bouton grisé.
                forYesterday = target != null && Slots.dayOf(target) != Slots.dayOf(now),
                confirmingRemoval = confirmingRemoval == med.tagId,
                onAskRemove = { confirmingRemoval = med.tagId },
                onCancelRemove = { confirmingRemoval = null },
                onConfirmRemove = { confirmingRemoval = null; onForget(med) },
                log = state.logs.firstOrNull {
                    it.tagId == med.tagId && Slots.dayOf(it.scheduledFor) == Slots.dayOf(slot)
                },
                onMarkTaken = { onMarkTaken(med) },
                confirmingSkip = confirmingSkip == med.tagId,
                onAskSkip = { confirmingSkip = med.tagId },
                onCancelSkip = { confirmingSkip = null },
                onConfirmSkip = { confirmingSkip = null; onSkip(med) },
                onEdit = { onEdit(med) }
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
                    // La plage est écrite par le graphique lui-même, à partir des jours
                    // qu'il affiche : la lui passer d'ici voudrait dire choisir un jour de
                    // référence, et se tromper dès que la semaine n'est pas uniforme.
                    DoseChart(points)
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
    late: Boolean,
    loggable: Boolean,
    forYesterday: Boolean,
    log: DoseLog?,
    confirmingRemoval: Boolean,
    onAskRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    onMarkTaken: () -> Unit,
    confirmingSkip: Boolean,
    onAskSkip: () -> Unit,
    onCancelSkip: () -> Unit,
    onConfirmSkip: () -> Unit,
    onEdit: () -> Unit
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
                    when {
                        log?.skipped == true -> "Sautée"
                        taken && log != null -> "Pris à ${clock.format(Date(log.takenAt))}"
                        late -> "En retard"
                        due -> "À prendre"
                        else -> "Plus tard"
                    },
                    style = Type.Label,
                    color = if (taken) Pal.Mint else Pal.Apricot
                )
            }

            // The button only appears once the dose is due, or nearly. Offering it at
            // 4am for a 9am dose invites logging today's pill in the middle of the
            // night, which would then silence the morning reminder.
            if ((!taken || forYesterday) && loggable) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onMarkTaken,
                    shape = Pill,
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Iris),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text(
                        if (forYesterday) "Je l'ai prise (hier soir)" else "Je l'ai prise",
                        style = Type.Title
                    )
                }

                if (!med.isManual) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ou approche le téléphone de l'étiquette sur la bouteille.",
                        style = Type.Label, color = Pal.Muted
                    )
                }

                // Sauter volontairement, sans mentir à l'historique. Sans ce bouton, une
                // bouteille vide ou une pause prescrite ne laisse qu'un choix : noter une
                // dose jamais prise. C'est le graphique qu'on montre à un médecin.
                Spacer(Modifier.height(4.dp))
                if (!confirmingSkip) {
                    TextButton(onClick = onAskSkip, modifier = Modifier.fillMaxWidth()) {
                        Text("Pas aujourd'hui", style = Type.Label, color = Pal.Muted)
                    }
                } else {
                    Surface(color = Pal.Mist, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("Sauter la dose d'aujourd'hui ?", style = Type.Title, color = Pal.Ink)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Elle sera notée comme sautée, pas comme prise. Les rappels " +
                                    "s'arrêtent et ta série tient, mais le créneau est " +
                                    "consommé pour la journée.",
                                style = Type.Label, color = Pal.Muted
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = onCancelSkip,
                                    shape = Pill,
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) { Text("Annuler", style = Type.Label, color = Pal.Ink) }
                                Spacer(Modifier.width(10.dp))
                                Button(
                                    onClick = onConfirmSkip,
                                    shape = Pill,
                                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Apricot),
                                    modifier = Modifier.weight(1f).height(46.dp)
                                ) { Text("Oui, sauter", style = Type.Label) }
                            }
                        }
                    }
                }
            }

            // Removal is two deliberate taps, and the second one is never in the place
            // the first one was -- so a double tap on "Retirer" cannot delete anything.
            Spacer(Modifier.height(8.dp))
            if (!confirmingRemoval) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit) {
                        Text("Modifier", style = Type.Label, color = Pal.Iris)
                    }
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
