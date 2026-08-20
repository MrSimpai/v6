package com.example.medtap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * L'atelier du ciel.
 *
 * Le fond de cette page EST le ciel qu'on règle : il n'y a pas d'aperçu séparé, parce
 * qu'un aperçu dans un cadre ne dit rien de ce que ça fait en vrai derrière du texte. On
 * touche un bouton, tout l'écran change, et c'est exactement ce qu'on verra demain matin.
 *
 * Le contenu est volontairement dense et sans fioritures : c'est un banc d'essai, pas une
 * page de l'app. Il ne se voit qu'après avoir dit le mot, et il ne touche jamais aux
 * médicaments — seulement à l'horloge du décor.
 */
@Composable
fun SkyLabScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val instant by SkyLab.instant
    val speed by SkyLab.speed
    val forced by SkyLab.forced
    val moment = rememberSky()

    val stamp = remember(instant) {
        SimpleDateFormat("EEEE d MMMM yyyy — H'h'mm", Locale.CANADA_FRENCH).format(instant)
    }

    Column(
        modifier
            .fillMaxSize()
            .sky()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 26.dp, bottom = 40.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ATELIER DU CIEL", style = Type.Label, color = skyMuted(),
                modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text("Fermer", style = Type.Label, color = Pal.Muted)
            }
        }

        Spacer(Modifier.height(10.dp))
        Card {
            Text(stamp.replaceFirstChar { it.uppercase() }, style = Type.Title, color = Pal.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "${phaseLabel(moment.phase)} · ${seasonLabel(moment.season)} · " +
                    "${fallingLabel(moment.falling)} · lune ${(moment.moon * 100).toInt()}%",
                style = Type.Label, color = Pal.Muted
            )
        }

        // ---- le temps qui passe ----
        Spacer(Modifier.height(22.dp))
        Section("VITESSE")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkyLab.Speed.entries.forEach { s ->
                Chip(s.label, speed == s, Modifier.weight(1f)) {
                    SkyLab.active.value = true
                    SkyLab.speed.value = s
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "« Heures » fait une journée en une minute — la nuit devient aube, puis midi, " +
                "puis couchant. « Jours » fait un jour par seconde : une année tourne en " +
                "six minutes, saisons comprises.",
            style = Type.Label, color = Pal.Muted
        )

        // ---- l'heure ----
        Spacer(Modifier.height(20.dp))
        Section("HEURE DU JOUR — ${Sky.minuteOfDay(instant) / 60}h" +
            String.format(Locale.CANADA_FRENCH, "%02d", Sky.minuteOfDay(instant) % 60))
        Slider(
            value = Sky.minuteOfDay(instant).toFloat(),
            onValueChange = { SkyLab.setMinuteOfDay(it.toInt()) },
            valueRange = 0f..1439f,
            colors = SliderDefaults.colors(thumbColor = Pal.Iris, activeTrackColor = Pal.Iris)
        )

        // ---- la date ----
        Spacer(Modifier.height(8.dp))
        Section("SAUTER À UNE SAISON")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sky.Season.entries.forEach { s ->
                Chip(seasonLabel(s), moment.season == s, Modifier.weight(1f)) {
                    SkyLab.jumpTo(s)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("− 1 mois", false, Modifier.weight(1f)) { SkyLab.nudgeDays(-30) }
            Chip("− 1 jour", false, Modifier.weight(1f)) { SkyLab.nudgeDays(-1) }
            Chip("+ 1 jour", false, Modifier.weight(1f)) { SkyLab.nudgeDays(1) }
            Chip("+ 1 mois", false, Modifier.weight(1f)) { SkyLab.nudgeDays(30) }
        }

        // ---- la lune ----
        Spacer(Modifier.height(20.dp))
        Section("LUNE — ${moonLabel(moment.moon)}")
        Slider(
            value = forced.moon ?: moment.moon,
            onValueChange = { SkyLab.forced.value = forced.copy(moon = it) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Pal.Iris, activeTrackColor = Pal.Iris)
        )
        TextButton(onClick = { SkyLab.forced.value = forced.copy(moon = null) }) {
            Text("Rendre la vraie lune", style = Type.Label, color = Pal.Iris)
        }

        // ---- ce qui tombe ----
        Spacer(Modifier.height(12.dp))
        Section("CE QUI TOMBE")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sky.Falling.entries.forEach { f ->
                Chip(fallingLabel(f), forced.falling == f, Modifier.weight(1f)) {
                    SkyLab.active.value = true
                    SkyLab.forced.value =
                        forced.copy(falling = if (forced.falling == f) null else f)
                }
            }
        }

        // ---- les rares ----
        Spacer(Modifier.height(20.dp))
        Section("LES RARES")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Aurore", forced.aurora != null, Modifier.weight(1f)) {
                SkyLab.active.value = true
                SkyLab.forced.value =
                    forced.copy(aurora = if (forced.aurora != null) null else 1f)
            }
            Chip("Arc-en-ciel", forced.rainbow == true, Modifier.weight(1f)) {
                SkyLab.active.value = true
                SkyLab.forced.value =
                    forced.copy(rainbow = if (forced.rainbow == true) null else true)
            }
            Chip("Étoile filante", forced.shootingStar != null, Modifier.weight(1f)) {
                SkyLab.active.value = true
                SkyLab.forced.value =
                    forced.copy(shootingStar = if (forced.shootingStar != null) null else 1f)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Un bouton allumé IMPOSE l'effet ; éteint, il rend la main au calcul. " +
                "L'aurore et l'étoile filante ne se voient que de nuit — mets l'heure " +
                "à minuit pour les regarder.",
            style = Type.Label, color = Pal.Muted
        )

        Spacer(Modifier.height(26.dp))
        Surface(
            color = Pal.IrisSoft, shape = Pill,
            modifier = Modifier.fillMaxWidth().clip(Pill).clickable { SkyLab.reset() }
        ) {
            Text(
                "Revenir à maintenant",
                style = Type.Title, color = Pal.Iris, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text, style = Type.Label, color = Pal.Iris)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Pal.Card, shape = Soft) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun Chip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = if (on) Pal.Iris else Pal.Card,
        shape = Pill,
        modifier = modifier.clip(Pill).clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = Type.Label, color = if (on) Pal.Card else Pal.Ink,
            textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 4.dp)
        )
    }
}

private fun phaseLabel(p: Sky.Phase) = when (p) {
    Sky.Phase.NIGHT -> "nuit"
    Sky.Phase.DAWN -> "aube"
    Sky.Phase.DAY -> "jour"
    Sky.Phase.DUSK -> "couchant"
}

private fun seasonLabel(s: Sky.Season) = when (s) {
    Sky.Season.WINTER -> "Hiver"
    Sky.Season.SPRING -> "Printemps"
    Sky.Season.SUMMER -> "Été"
    Sky.Season.AUTUMN -> "Automne"
}

private fun fallingLabel(f: Sky.Falling) = when (f) {
    Sky.Falling.NONE -> "Rien"
    Sky.Falling.RAIN -> "Pluie"
    Sky.Falling.SNOW -> "Neige"
    Sky.Falling.LEAVES -> "Feuilles"
}

private fun moonLabel(p: Float) = when {
    p < 0.03f || p > 0.97f -> "nouvelle"
    p < 0.22f -> "premier croissant"
    p < 0.28f -> "premier quartier"
    p < 0.47f -> "gibbeuse croissante"
    p < 0.53f -> "pleine"
    p < 0.72f -> "gibbeuse décroissante"
    p < 0.78f -> "dernier quartier"
    else -> "dernier croissant"
}
