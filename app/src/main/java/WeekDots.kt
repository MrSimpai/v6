package com.example.medtap.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.example.medtap.data.DayState
import kotlinx.coroutines.delay

/** Lundi tout à gauche, dimanche tout à droite. Toujours. */
private val LETTERS = listOf("L", "M", "M", "J", "V", "S", "D")

fun dayColor(s: DayState): Color = when (s) {
    DayState.DONE   -> Pal.Mint
    DayState.FROZEN -> Pal.Teal
    DayState.TODAY  -> Pal.Iris
    DayState.MISSED -> Pal.Blush
    DayState.FUTURE -> Pal.IrisSoft
}

/**
 * La semaine en sept points.
 *
 * L'ordre est figé du lundi au dimanche plutôt que glissant sur les sept derniers jours :
 * des points qui se décalent chaque matin obligeraient à les relire à chaque fois, alors
 * qu'une semaine fixe se reconnaît d'un coup d'œil, comme un calendrier.
 *
 * Les points apparaissent en cascade de gauche à droite, et celui d'aujourd'hui respire
 * tant que la dose n'est pas prise — c'est le seul qui demande quelque chose, donc le
 * seul qui bouge.
 */
@Composable
fun WeekDots(
    week: List<DayState>,
    modifier: Modifier = Modifier,
    showLetters: Boolean = true
) {
    if (week.size != 7) return

    // Une cascade, pas sept animations indépendantes : le retard croissant fait lire la
    // rangée dans le sens de la semaine.
    val shown = remember { mutableStateListOf(*Array(7) { false }) }
    LaunchedEffect(Unit) {
        repeat(7) { i ->
            delay(55L)
            shown[i] = true
        }
    }

    val breathe = rememberInfiniteTransition(label = "today")
    val pulse by breathe.animateFloat(
        initialValue = 1f, targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        week.forEachIndexed { i, state ->
            val target = dayColor(state)
            val color by animateColorAsState(target, tween(400), label = "c$i")
            val pop by animateFloatAsState(
                if (shown[i]) 1f else 0f,
                spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
                label = "p$i"
            )
            val extra = if (state == DayState.TODAY) pulse else 1f

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(if (state == DayState.FUTURE) 9.dp else 13.dp)
                        .scale(pop * extra)
                        .clip(Pill)
                        .background(color)
                        .then(
                            if (state == DayState.FROZEN)
                                Modifier.border(2.dp, Pal.Card, Pill) else Modifier
                        )
                )
                if (showLetters) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        LETTERS[i],
                        style = Type.Label,
                        color = if (state == DayState.TODAY) Pal.Iris else Pal.Muted
                    )
                }
            }
        }
    }
}
