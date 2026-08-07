package com.example.medtap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medtap.Her
import com.example.medtap.data.DayWindow
import com.example.medtap.data.Medication
import com.example.medtap.data.Week
import com.example.medtap.data.uniformWeek
import com.example.medtap.data.windows
import com.example.medtap.data.withWindows
import java.util.Locale

/**
 * Deux façons d'ajouter un médicament, et la plus simple est la principale.
 *
 * « Ajouter » enregistre tout de suite : le médicament existe, les rappels partent, et
 * la dose se note avec un bouton. Aucune étiquette, aucun matériel, rien à commander.
 * L'étiquette NFC est un raccourci qu'on peut coller plus tard sur la bouteille, quand
 * on en a envie — pas une condition pour que l'app serve à quelque chose.
 *
 * Plein écran plutôt qu'une feuille : le clavier mange la moitié d'une feuille modale,
 * et il reste alors trois champs coincés dans une bande de deux centimètres.
 *
 * [onConfirm] reçoit le brouillon et un booléen : vrai si on veut enchaîner avec
 * l'appairage d'une étiquette, faux pour enregistrer directement.
 */
@Composable
fun AddMedicationScreen(
    onDismiss: () -> Unit,
    onConfirm: (Medication, Boolean) -> Unit,
    existing: Medication? = null
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dose by remember { mutableStateOf(existing?.doseText ?: "1 comprimé") }

    // Les sept jours, toujours les sept, même quand ils sont identiques. Un seul modèle
    // pour les deux modes d'affichage : le mode « même heure » n'est pas un autre état,
    // c'est le même tableau écrit sept fois, ce qui supprime la question « laquelle des
    // deux valeurs gagne » au moment d'enregistrer.
    val start = existing?.windows() ?: List(Week.DAYS) { DayWindow(9 * 60, 9 * 60) }
    val week = remember { mutableStateListOf<DayWindow>().apply { addAll(start) } }

    // Différent d'un jour à l'autre ? En modification, la réponse vient du médicament
    // lui-même, donc rouvrir un horaire réglé par jour le rouvre tel quel.
    var perDay by remember { mutableStateOf(existing?.uniformWeek == false) }
    var day by remember { mutableStateOf(0) }

    val edited = week[if (perDay) day else 0]

    /** Écrit une plage : sur le jour choisi, ou sur les sept quand ils sont liés. */
    fun set(w: DayWindow) {
        if (perDay) week[day] = w else repeat(Week.DAYS) { week[it] = w }
    }

    /**
     * Relier ou délier les jours.
     *
     * Refermer l'interrupteur APLATIT vraiment la semaine sur le jour affiché. Sans ça,
     * un horaire réglé jour par jour puis rebasculé en « même heure » resterait différent
     * en base tout en n'affichant plus qu'une seule heure : l'écran dirait une chose et le
     * rappel en ferait une autre, ce qui est la seule faute qu'une app de médication n'a
     * pas le droit de commettre.
     */
    fun linkDays(on: Boolean) {
        if (!on) {
            val w = week[day]
            repeat(Week.DAYS) { week[it] = w }
        }
        perDay = on
    }

    // En modification on garde la clé : c'est elle qui relie le médicament à tout son
    // historique. La perdre pour corriger une faute de frappe serait absurde.
    //
    // Et on garde `createdAt` pour la même raison : cet écran sert aussi bien à ajouter
    // qu'à modifier, donc laisser la valeur par défaut ferait passer un médicament de
    // deux ans pour un médicament créé à l'instant à chaque changement d'horaire — et la
    // série cesserait de compter tout ce qui précède la modification.
    fun draft() = Medication(
        tagId = existing?.tagId ?: "",
        name = name.ifBlank { "Mon médicament" },
        doseText = dose.ifBlank { "1 dose" },
        hourOfDay = 0, minute = 0,          // `withWindows` les recalcule sur la semaine
        nagEveryMinutes = existing?.nagEveryMinutes ?: 10,
        createdAt = existing?.createdAt ?: System.currentTimeMillis()
    ).withWindows(week.toList())

    Box(Modifier.fillMaxSize().background(Pal.Mist)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 40.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (existing == null) "NOUVEAU" else "MODIFIER",
                    style = Type.Label, color = Pal.Muted, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("Annuler", style = Type.Label, color = Pal.Muted)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (existing == null) "Un médicament de plus" else existing.name,
                style = Type.Display, color = Pal.Ink
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Le nom, la dose, et l'heure du rappel. C'est tout ce qu'il faut.",
                style = Type.Body, color = Pal.Muted
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nom") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(), shape = Soft
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dose, onValueChange = { dose = it },
                label = { Text("Dose") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(), shape = Soft
            )

            Spacer(Modifier.height(28.dp))
            Text("RAPPEL À", style = Type.Label, color = Pal.Muted)

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Soft)
                    .clickable { linkDays(!perDay) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Selon les jours", style = Type.Body, color = Pal.Ink)
                    Text(
                        if (perDay) "Chaque jour a son heure."
                        else "La même heure les sept jours.",
                        style = Type.Label, color = Pal.Muted
                    )
                }
                Switch(
                    checked = perDay,
                    onCheckedChange = { linkDays(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Pal.Card,
                        checkedTrackColor = Pal.Iris
                    )
                )
            }

            // La semaine entière lisible d'un coup, et le jour qu'on règle en surbrillance.
            // Sept pastilles plutôt que sept lignes dépliées : l'écran doit répondre à
            // « c'est réglé comment ? » sans qu'on ait à faire défiler quoi que ce soit.
            if (perDay) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEachIndexed { i, w ->
                        DayChip(
                            letter = Week.LETTERS[i],
                            time = compact(w.startMinute),
                            selected = i == day,
                            modifier = Modifier.weight(1f),
                            onClick = { day = i }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    Week.NAMES[day].uppercase(Locale.CANADA_FRENCH),
                    style = Type.Label, color = Pal.Iris
                )
            }

            Spacer(Modifier.height(14.dp))
            TimeRow(
                label = "Rappel",
                value = hhmm(edited.startMinute),
                onChange = { delta ->
                    val s = wrap(edited.startMinute + delta)
                    // La fin suit le début tant qu'on n'y a pas touché : sans ça, avancer
                    // le rappel de 9h à 7h laisserait une plage qui se referme avant de
                    // s'ouvrir, donc silencieusement aucune plage du tout.
                    val shift = s - edited.startMinute
                    val e = if (edited.instant) s else (edited.endMinute + shift).coerceIn(s, 1439)
                    set(DayWindow(s, e))
                }
            )

            Spacer(Modifier.height(8.dp))
            TimeRow(
                label = "Sans presser jusqu'à",
                value = if (edited.instant) "—" else hhmm(edited.endMinute),
                onChange = { delta ->
                    val base = if (edited.instant) edited.startMinute else edited.endMinute
                    val e = (base + delta).coerceIn(edited.startMinute, 1439)
                    set(DayWindow(edited.startMinute, e))
                }
            )

            Spacer(Modifier.height(10.dp))
            Text(
                if (edited.instant)
                    "Le rappel part à ${hhmm(edited.startMinute)} et ${Her.dragon} " +
                        "commence à insister tout de suite. Ouvre une plage si tu ne te " +
                        "lèves pas toujours à la même heure."
                else
                    "Le rappel part à ${hhmm(edited.startMinute)}, mais ${Her.dragon} " +
                        "n'insiste qu'à partir de ${hhmm(edited.endMinute)}. Entre les " +
                        "deux, elle attend sans rien dire.",
                style = Type.Label, color = Pal.Muted
            )

            if (perDay) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { repeat(Week.DAYS) { week[it] = edited } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Appliquer ${Week.NAMES[day]} aux sept jours",
                        style = Type.Label, color = Pal.Iris
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
            Button(
                onClick = { onConfirm(draft(), false) },
                enabled = name.isNotBlank(),
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = Pal.Iris),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text(if (existing == null) "Ajouter" else "Enregistrer", style = Type.Title) }

            if (existing == null) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { onConfirm(draft(), true) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ou lier une étiquette NFC maintenant", style = Type.Body, color = Pal.Iris)
                }
                Text(
                    "Une étiquette collée sur la bouteille permet d'enregistrer la dose en " +
                        "approchant le téléphone. Ça se fait très bien plus tard.",
                    style = Type.Label, color = Pal.Muted
                )
            }
        }
    }
}

/** « 7h00 », à partir de minutes depuis minuit. */
private fun hhmm(minuteOfDay: Int): String =
    String.format(Locale.CANADA_FRENCH, "%dh%02d", minuteOfDay / 60, minuteOfDay % 60)

/**
 * « 7h », ou « 7h30 quand il le faut ».
 *
 * Sept fois « 10h00 » côte à côte ne tient pas sur la largeur d'un téléphone, et les
 * quatre caractères en trop ne disent rien : une heure ronde se lit aussi bien sans ses
 * deux zéros.
 */
private fun compact(minuteOfDay: Int): String =
    if (minuteOfDay % 60 == 0) "${minuteOfDay / 60}h" else hhmm(minuteOfDay)

/** Reste dans la journée : une heure du rappel ne déborde jamais sur la veille. */
private fun wrap(minuteOfDay: Int): Int = ((minuteOfDay % 1440) + 1440) % 1440

/**
 * Un jour de la semaine, avec son heure écrite dessous.
 *
 * L'heure sous la lettre est ce qui rend la rangée utile : sans elle il faudrait toucher
 * les sept pastilles une à une pour savoir comment la semaine est réglée, et le seul
 * endroit où la question se pose est justement celui-là.
 */
@Composable
private fun DayChip(
    letter: String,
    time: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) Pal.Iris else Pal.Card,
        shape = Soft,
        modifier = modifier.clip(Soft).clickable(onClick = onClick)
    ) {
        Column(
            Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                letter, style = Type.Title,
                color = if (selected) Pal.Card else Pal.Ink
            )
            // Sans l'interlettrage du reste des étiquettes : sept fois « 10h30 » côte à
            // côte sur la largeur d'un téléphone, chaque dixième de millimètre compte.
            Text(
                time,
                style = Type.Label.copy(letterSpacing = 0.sp),
                color = if (selected) Pal.Card else Pal.Muted,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * Une heure, réglée en quatre boutons : l'heure d'un côté, cinq minutes de l'autre.
 *
 * L'ancien sélecteur montait par pas de cinq minutes sur un seul bouton. Passer de 9h à 7h
 * demandait vingt-quatre appuis, ce qui est exactement le genre de détail qui fait qu'on
 * ne règle jamais rien. Deux pas, deux vitesses, et le cas courant tient en deux appuis.
 */
@Composable
private fun TimeRow(label: String, value: String, onChange: (Int) -> Unit) {
    Surface(color = Pal.Card, shape = Soft, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Nudge("−h") { onChange(-60) }
            Nudge("−") { onChange(-5) }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(value, style = Type.Title, color = Pal.Ink)
                Text(label.uppercase(Locale.CANADA_FRENCH), style = Type.Label, color = Pal.Muted)
            }
            Nudge("+") { onChange(5) }
            Nudge("+h") { onChange(60) }
        }
    }
}

@Composable
private fun Nudge(text: String, onClick: () -> Unit) {
    Surface(
        color = Pal.IrisSoft,
        shape = Pill,
        modifier = Modifier.size(width = 40.dp, height = 40.dp).clip(Pill).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, style = Type.Title, color = Pal.Iris)
        }
    }
}

/** Les deux points en bas : la seule indication qu'il existe une page à gauche. */
@Composable
fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Surface(
        color = Pal.Card.copy(alpha = 0.92f),
        shape = Pill,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(if (i == current) 9.dp else 7.dp)
                        .background(if (i == current) Pal.Iris else Pal.Blush, Pill)
                )
            }
        }
    }
}
