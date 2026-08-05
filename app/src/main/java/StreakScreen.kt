package com.example.medtap.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medtap.Her
import com.example.medtap.reminder.FloMessages
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The full-page streak celebration, shown only when the LAST dose of the day is logged
 * while she's actually looking at the screen.
 *
 * The reason this is worth a whole screen: it's the one moment the app has something to
 * give back. Every other interaction is the app asking her for something. A card tucked
 * under the dragon reads as a status line; taking over the display for four seconds reads
 * as the app stopping to make a fuss over her, which is the entire trick Duolingo pulls.
 *
 * It is deliberately dismissed by hand rather than on a timer. An animation that vanishes
 * on its own is something that happened at you; one you close is something you finished.
 */
@Composable
fun StreakCelebration(days: Int, onDismiss: () -> Unit) {

    // Everything hangs off this single spring so the whole screen arrives as one gesture
    // rather than a sequence of unrelated tweens.
    val enter = remember { Animatable(0f) }
    val fall = remember { Animatable(0f) }
    var counted by remember { mutableStateOf(0) }

    LaunchedEffect(days) {
        enter.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(days) {
        fall.animateTo(1f, tween(3200, easing = LinearEasing))
    }
    LaunchedEffect(days) {
        // Roll the number up rather than snapping it. Short streaks tick one by one;
        // long ones would take all day, so the step scales with the total.
        val step = (days / 24).coerceAtLeast(1)
        var n = 0
        while (n < days) {
            n = (n + step).coerceAtMost(days)
            counted = n
            kotlinx.coroutines.delay(if (days < 24) 55L else 30L)
        }
        counted = days
    }

    val spin = rememberInfiniteTransition(label = "rays")
    val angle by spin.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "angle"
    )

    val confetti = remember(days) { makeConfetti(days) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Pal.MintSoft, Pal.Card, Pal.Card)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // ---- rotating rays behind everything ----
        Canvas(Modifier.fillMaxSize().alpha(0.30f * enter.value)) {
            val c = Offset(size.width / 2f, size.height * 0.40f)
            val r = size.maxDimension
            rotate(angle, c) {
                repeat(14) { i ->
                    val a0 = Math.toRadians((i * 360.0 / 14)).toFloat()
                    val a1 = a0 + 0.13f
                    val p = Path().apply {
                        moveTo(c.x, c.y)
                        lineTo(c.x + cos(a0) * r, c.y + sin(a0) * r)
                        lineTo(c.x + cos(a1) * r, c.y + sin(a1) * r)
                        close()
                    }
                    drawPath(p, Pal.Mint)
                }
            }
        }

        // ---- confetti ----
        Canvas(Modifier.fillMaxSize()) {
            confetti.forEach { c ->
                val t = ((fall.value - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
                if (t <= 0f) return@forEach
                val x = c.x * size.width + c.drift * size.width * t
                val y = t * (size.height + 80f) - 40f
                rotate(c.spin * t, Offset(x, y)) {
                    drawRect(
                        color = c.color.copy(alpha = (1f - t * t).coerceIn(0f, 1f)),
                        topLeft = Offset(x - c.size / 2f, y - c.size / 2f),
                        size = Size(c.size, c.size * 0.62f)
                    )
                }
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Mascot(
                Mood.Cheering,
                Modifier
                    .size(168.dp)
                    .scale(0.6f + 0.4f * enter.value)
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "$counted",
                style = Type.Display.copy(fontSize = 96.sp, lineHeight = 100.sp),
                color = Pal.Mint,
                modifier = Modifier.scale(0.5f + 0.5f * enter.value)
            )
            Text(
                if (days == 1) "JOURNÉE COMPLÈTE" else "JOURNÉES COMPLÈTES",
                style = Type.Label, color = Pal.Mint
            )

            Spacer(Modifier.height(22.dp))

            // Seven dots: the current week, filled up to where she is.
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                repeat(7) { i ->
                    val on = i < (days.coerceAtMost(7))
                    Box(
                        Modifier
                            .size(if (on) 13.dp else 10.dp)
                            .clip(Pill)
                            .background(if (on) Pal.Mint else Pal.MintSoft)
                            .alpha(enter.value)
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            // The hand-written lines vary wildly in length -- "UNE SEMAINE! ça passe
            // vite" next to a full verse of Life Is A Highway. Step the size down for the
            // long ones so a good joke never gets clipped off the bottom of the screen.
            // La ligne écrite à la main est le vrai contenu de l'écran, donc elle est
            // dimensionnée comme tel. Trois paliers : les courtes en gros, les longues
            // seulement un peu plus petites, jamais en dessous du corps de texte.
            val message = FloMessages.dayStreakLine(days)
            val size = when {
                message.length <= 34 -> 30.sp
                message.length <= 70 -> 24.sp
                else -> 20.sp
            }
            Text(
                message,
                style = Type.Title.copy(
                    fontSize = size,
                    lineHeight = size * 1.34f
                ),
                color = Pal.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(enter.value)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Plus rien à prendre aujourd'hui, ${Her.name}.",
                style = Type.Label, color = Pal.Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(enter.value)
            )
        }

        // Quatre appuis, pas un.
        //
        // Le bouton se remplit et vibre un peu plus fort à chaque fois. Ça n'a aucune
        // utilité fonctionnelle et c'est exactement le but : le coffre attend derrière,
        // et trois secondes d'effort inutile transforment une transition d'écran en
        // impatience. Un seul appui rendrait la récompense gratuite.
        val ctx = LocalContext.current
        var taps by remember { mutableStateOf(0) }
        val fill by animateFloatAsState(taps / 4f, tween(220), label = "fill")
        val nudge = remember { Animatable(1f) }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp, end = 32.dp, bottom = 44.dp)
                .fillMaxWidth()
                .height(56.dp)
                .alpha(enter.value)
                .scale(nudge.value)
                .clip(Pill)
                .background(Pal.MintSoft)
                .clickable(enabled = taps < 4) { taps++ },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(Pal.Mint)
            )
            Text(
                when (taps) {
                    0 -> "Continuer"
                    1, 2 -> "Encore…"
                    3 -> "Presque!"
                    else -> "Ouvre!"
                },
                style = Type.Title,
                color = if (taps >= 2) Pal.Card else Pal.Mint
            )
        }

        LaunchedEffect(taps) {
            if (taps == 0) return@LaunchedEffect
            buzz(ctx, 22L + taps * 16L, 70 + taps * 45)
            nudge.animateTo(0.94f, tween(70))
            nudge.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessHigh))
            if (taps >= 4) onDismiss()
        }
    }
}

private class Confetto(
    val x: Float, val delay: Float, val drift: Float,
    val spin: Float, val size: Float, val color: Color
)

private fun makeConfetti(seed: Int): List<Confetto> {
    val r = Random(seed * 31 + 7)
    val palette = listOf(Pal.Mint, Pal.Iris, Pal.Butter, Pal.Blush, Pal.Teal)
    return List(52) {
        Confetto(
            x = r.nextFloat(),
            delay = r.nextFloat() * 0.4f,
            drift = (r.nextFloat() - 0.5f) * 0.3f,
            spin = (r.nextFloat() - 0.5f) * 1000f,
            size = 7f + r.nextFloat() * 9f,
            color = palette[r.nextInt(palette.size)]
        )
    }
}
