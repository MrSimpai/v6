package com.example.medtap.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.medtap.Her

/**
 * L'appairage d'une étiquette, sur toute la page.
 *
 * Avant, cette étape était une ligne de sous-titre sur l'écran d'accueil : on touchait
 * « lier une étiquette », l'écran d'ajout se fermait, et on se retrouvait devant le dragon
 * avec une phrase de plus. Le médicament n'était nulle part — ni dans la liste, ni
 * enregistré — et rien ne disait quoi faire, combien de temps ça durait, ni comment en
 * sortir. C'est le genre d'étape dont on conclut que « le NFC ne marche pas ».
 *
 * Trois choses qu'une page entière permet et qu'une ligne ne permettait pas : dire OÙ
 * approcher l'étiquette (le NFC est vers le haut du dos sur la plupart des téléphones, ce
 * que personne ne sait), montrer que l'app attend vraiment, et offrir une sortie qui ne
 * jette pas le travail déjà fait.
 */
@Composable
fun PairTagScreen(
    medName: String,
    nfcOff: Boolean,
    hasNfc: Boolean,
    error: String?,
    onOpenNfcSettings: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = rememberInfiniteTransition(label = "pair")
    val pulse by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )

    Column(
        modifier
            .fillMaxSize()
            .sky()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
            .padding(top = 36.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ÉTIQUETTE", style = Type.Label, color = skyMuted())
        Spacer(Modifier.height(6.dp))
        Text(medName, style = Type.Display, color = skyInk(), textAlign = TextAlign.Center)

        Spacer(Modifier.height(26.dp))

        // Deux ondes qui partent du dragon : sans mouvement, une page qui attend
        // ressemble à une page bloquée.
        Box(contentAlignment = Alignment.Center) {
            listOf(0f, 0.5f).forEach { offset ->
                val p = (pulse + offset) % 1f
                Box(
                    Modifier
                        .size(200.dp)
                        .scale(0.6f + p * 0.6f)
                        .alpha((1f - p) * 0.35f)
                        .background(Pal.IrisSoft, Pill)
                )
            }
            Mascot(Mood.Waiting, Modifier.size(160.dp))
        }

        Spacer(Modifier.height(26.dp))

        when {
            !hasNfc -> Note(
                "Ce téléphone n'a pas de NFC.",
                "Aucun problème : la dose se note avec le bouton de l'écran d'accueil, " +
                    "et tout le reste marche pareil."
            )

            nfcOff -> Note(
                "Le NFC est éteint",
                "Active-le dans les réglages, puis reviens ici. ${Her.dragon} attend."
            )

            else -> {
                Text(
                    "Approche l'étiquette",
                    style = Type.Title, color = Pal.Ink, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Step(1, "Colle l'étiquette sur la bouteille.")
                Step(2, "Touche le dos du téléphone avec, vers le haut — c'est là qu'est " +
                    "l'antenne sur presque tous les téléphones.")
                Step(3, "Garde-la immobile une seconde.")
            }
        }

        if (error != null) {
            Spacer(Modifier.height(18.dp))
            Surface(color = Pal.Card, shape = Soft) {
                Text(
                    error,
                    style = Type.Body, color = Pal.Apricot,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        if (nfcOff) {
            Button(
                onClick = onOpenNfcSettings,
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = Pal.Iris),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Ouvrir les réglages NFC", style = Type.Title) }
            Spacer(Modifier.height(8.dp))
        }

        // La sortie n'annule rien. Le médicament est déjà enregistré et ses rappels sont
        // déjà armés — l'étiquette n'était qu'un raccourci. Renvoyer quelqu'un à l'écran
        // d'accueil en ayant silencieusement jeté ce qu'il venait de saisir serait la
        // pire façon de terminer cette page.
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (hasNfc) "Plus tard — c'est déjà enregistré" else "Continuer",
                style = Type.Body, color = Pal.Iris
            )
        }
        Text(
            "Le médicament est enregistré et les rappels sont réglés. L'étiquette se " +
                "colle et se lie quand tu veux, depuis la fiche du médicament.",
            style = Type.Label, color = Pal.Muted, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Surface(color = Pal.IrisSoft, shape = Pill) {
            Text(
                "$n",
                style = Type.Label, color = Pal.Iris,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = Type.Body, color = Pal.Muted, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Note(title: String, body: String) {
    Surface(color = Pal.Card, shape = Soft) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = Type.Title, color = Pal.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(body, style = Type.Label, color = Pal.Muted, textAlign = TextAlign.Center)
        }
    }
}
