package com.example.medtap.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.medtap.Her
import com.example.medtap.data.DayState

/**
 * Les trente premières secondes.
 *
 * Trois pages, pas une de plus, et chacune tient en un regard : qui est le dragon, ce
 * qu'on attend de toi, et ce que tu gagnes. C'est la seule fois où l'app parle d'elle-même,
 * donc elle a le droit de le faire — mais brièvement. Un tutoriel de sept écrans sur une
 * app qui sert à prendre une pilule serait la meilleure façon de ne jamais être ouverte
 * une deuxième fois.
 *
 * Chaque page montre la chose plutôt que de la décrire : le dragon en vrai, les vrais
 * points de la semaine, le vrai casier avec Bernadette assise à côté. Une capture d'écran
 * de ce qui vient serait un dessin de plus à maintenir ; ici c'est le même code que
 * l'app elle-même, donc ça ne peut pas mentir.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var page by remember { mutableStateOf(0) }
    val last = 2

    Column(
        modifier
            .fillMaxSize()
            .background(Pal.Mist)
            .padding(horizontal = 26.dp)
            .padding(top = 40.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "welcome"
            ) { p ->
                when (p) {
                    0 -> PageDragon()
                    1 -> PageStreak()
                    else -> PageLocker()
                }
            }
        }

        Row(
            Modifier.padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(last + 1) { i ->
                Box(
                    Modifier
                        .size(if (i == page) 9.dp else 7.dp)
                        .clip(Pill)
                        .background(if (i == page) Pal.Iris else Pal.IrisSoft)
                )
            }
        }

        Button(
            onClick = { if (page < last) page++ else onDone() },
            shape = Pill,
            colors = ButtonDefaults.buttonColors(containerColor = Pal.Iris),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (page < last) "Suivant" else "Commencer", style = Type.Title)
        }

        // Passer reste possible à tout moment. Une intro qu'on ne peut pas quitter est une
        // porte fermée, et elle n'a pas demandé à lire quoi que ce soit.
        if (page < last) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Passer",
                style = Type.Label, color = Pal.Muted,
                modifier = Modifier
                    .clip(Pill)
                    .clickable { onDone() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

/** Qui elle est, et d'où elle vient. */
@Composable
private fun PageDragon() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(Mood.Love, Modifier.size(190.dp))
        Spacer(Modifier.height(18.dp))
        Text("Voici ${Her.dragon}", style = Type.Display, color = Pal.Ink)
        Spacer(Modifier.height(12.dp))
        Body(
            "Elle vit ici et elle n'a qu'un seul travail : te rappeler ta pilule, " +
                "tous les jours, à la même heure."
        )
        Spacer(Modifier.height(10.dp))
        Body(Her.dedication)
    }
}

/** Ce qu'on attend d'elle : une pilule, une journée, un point de plus. */
@Composable
private fun PageStreak() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = Pal.Card, shape = Soft) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🔥 6", style = Type.Display, color = Pal.Iris)
                Spacer(Modifier.height(14.dp))
                WeekDots(
                    listOf(
                        DayState.DONE, DayState.DONE, DayState.FROZEN,
                        DayState.DONE, DayState.DONE, DayState.TODAY, DayState.FUTURE
                    )
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Une pilule par jour", style = Type.Display, color = Pal.Ink)
        Spacer(Modifier.height(12.dp))
        Body(
            "Chaque journée complète ajoute un jour à ta série. Un oubli par semaine " +
                "est pardonné automatiquement — ${Her.dragon} dépense un gel et la série tient."
        )
    }
}

/** Ce qu'elle y gagne. Bernadette fait la démonstration. */
@Composable
private fun PageLocker() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(
            Mood.Cheering,
            Modifier.size(190.dp),
            worn = setOf("tuque", "ailes_fee", "grenouille")
        )
        Spacer(Modifier.height(18.dp))
        Text("Et le casier", style = Type.Display, color = Pal.Ink)
        Spacer(Modifier.height(12.dp))
        Body(
            "Une pièce par journée complète : chapeaux, ailes, bottes, peluches. " +
                "Cinquante-cinq à trouver, jamais deux fois la même."
        )
        Spacer(Modifier.height(10.dp))
        Body("La grenouille verte, c'est Bernadette. Elle en fait partie.")
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = Type.Body, color = Pal.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
