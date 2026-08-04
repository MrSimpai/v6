package com.example.medtap.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.medtap.data.Medication
import java.util.Locale

/**
 * Deux façons d'ajouter un médicament, et la plus simple est la principale.
 *
 * « Ajouter » enregistre tout de suite : le médicament existe, les rappels partent, et
 * la dose se note avec un bouton. Aucune étiquette, aucun matériel, rien à commander.
 * L'étiquette NFC est un raccourci qu'on peut coller plus tard sur la bouteille, quand
 * on en a envie — pas une condition pour que l'app serve à quelque chose.
 *
 * [onConfirm] reçoit le brouillon et un booléen : vrai si on veut enchaîner avec
 * l'appairage d'une étiquette, faux pour enregistrer directement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMedicationSheet(
    onDismiss: () -> Unit,
    onConfirm: (Medication, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("1 comprimé") }
    var hour by remember { mutableStateOf(9) }
    var minute by remember { mutableStateOf(0) }

    fun draft() = Medication(
        tagId = "",                                   // rempli à l'enregistrement
        name = name.ifBlank { "Mon médicament" },
        doseText = dose.ifBlank { "1 dose" },
        hourOfDay = hour, minute = minute
    )

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Text("Nouveau médicament", style = Type.Display, color = Pal.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "Le nom, la dose, et l'heure du rappel. C'est tout ce qu'il faut.",
                style = Type.Body, color = Pal.Muted
            )
            Spacer(Modifier.height(24.dp))

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

            Spacer(Modifier.height(24.dp))
            Text("RAPPEL À", style = Type.Label, color = Pal.Muted)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stepper(String.format(Locale.CANADA_FRENCH, "%02d", hour),
                    onUp = { hour = (hour + 1) % 24 }, onDown = { hour = (hour + 23) % 24 })
                Text("h", style = Type.Display, color = Pal.Ink,
                    modifier = Modifier.padding(horizontal = 8.dp))
                Stepper(String.format(Locale.CANADA_FRENCH, "%02d", minute),
                    onUp = { minute = (minute + 5) % 60 }, onDown = { minute = (minute + 55) % 60 })
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { onConfirm(draft(), false) },
                enabled = name.isNotBlank(),
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = Pal.Iris),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Ajouter", style = Type.Title) }

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

@Composable
private fun Stepper(value: String, onUp: () -> Unit, onDown: () -> Unit) {
    Surface(color = Pal.Mist, shape = Soft) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(onClick = onUp) { Text("+", style = Type.Title, color = Pal.Iris) }
            Text(value, style = Type.Display, color = Pal.Ink)
            TextButton(onClick = onDown) { Text("-", style = Type.Title, color = Pal.Iris) }
        }
    }
}
