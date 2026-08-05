package com.example.medtap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.medtap.Her
import com.example.medtap.data.ReminderHealth

/**
 * La page de droite : les réglages, et rien d'autre.
 *
 * Tout ce qui est ici a été retiré de l'accueil volontairement. L'avertissement sur la
 * batterie et les boutons de sauvegarde sont importants deux fois par an et encombrants
 * les 363 autres jours — or l'accueil ne doit répondre qu'à une seule question : est-ce
 * que j'ai pris ma pilule. Chaque bloc ajouté à cet écran-là rend la réponse plus lente
 * à trouver.
 */
@Composable
fun SettingsScreen(
    batteryRestricted: Boolean,
    onFixBattery: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Pal.Mist)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 60.dp)
    ) {
        Text("RÉGLAGES", style = Type.Label, color = Pal.Muted)
        Spacer(Modifier.height(6.dp))
        Text("Les coulisses", style = Type.Display, color = Pal.Ink)

        Spacer(Modifier.height(24.dp))

        // ---- santé des rappels ----
        Text("RAPPELS", style = Type.Label, color = Pal.Iris)
        Spacer(Modifier.height(10.dp))
        Surface(color = Pal.Card, shape = Soft) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(
                    if (batteryRestricted) "À corriger" else "Tout est en ordre",
                    style = Type.Title,
                    color = if (batteryRestricted) Pal.Apricot else Pal.Mint
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (batteryRestricted)
                        "Android peut mettre l'app en veille et supprimer les rappels sans " +
                            "prévenir. Une app de médication qui échoue en silence est pire " +
                            "que pas d'app du tout, parce qu'on arrête de vérifier soi-même."
                    else
                        "L'app est exemptée des restrictions de batterie. Les rappels " +
                            "partiront même après plusieurs jours sans y toucher.",
                    style = Type.Body, color = Pal.Muted
                )
                if (batteryRestricted) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onFixBattery,
                        shape = Pill,
                        colors = ButtonDefaults.buttonColors(containerColor = Pal.Apricot),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Autoriser", style = Type.Title) }
                    Spacer(Modifier.height(10.dp))
                    Text(ReminderHealth.SAMSUNG_STEPS, style = Type.Label, color = Pal.Muted)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ---- sauvegarde ----
        Text("SAUVEGARDE", style = Type.Label, color = Pal.Iris)
        Spacer(Modifier.height(10.dp))
        Surface(color = Pal.Card, shape = Soft) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Un fichier, chez toi", style = Type.Title, color = Pal.Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Médicaments, historique complet, cosmétiques et gels, dans un seul " +
                        "fichier que tu choisis. Aucun compte, aucun nuage. À refaire de " +
                        "temps en temps, et avant de changer de téléphone.",
                    style = Type.Body, color = Pal.Muted
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onBackup,
                    shape = Pill,
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Iris),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Sauvegarder", style = Type.Title) }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onRestore,
                    shape = Pill,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Restaurer", style = Type.Label, color = Pal.Ink) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Restaurer n'efface rien : le contenu du fichier est fusionné avec ce " +
                        "qui est déjà là.",
                    style = Type.Label, color = Pal.Muted
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text("SÉRIE", style = Type.Label, color = Pal.Iris)
        Spacer(Modifier.height(10.dp))
        Surface(color = Pal.Card, shape = Soft) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Le gel hebdomadaire ❄️", style = Type.Title, color = Pal.Ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Si une journée saute, ${Her.dragon} dépense automatiquement un gel et " +
                        "la série continue. Un seul par semaine, et tu n'as rien à activer.",
                    style = Type.Body, color = Pal.Muted
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Aide-mémoire, pas dispositif médical. Si une dose est vraiment critique, " +
                "garde un deuxième rappel qui ne dépend pas de la batterie d'un téléphone.",
            style = Type.Label, color = Pal.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
