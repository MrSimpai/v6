package com.example.medtap.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Une petite vibration, sans planter là où le moteur n'existe pas. */
internal fun buzz(ctx: android.content.Context, ms: Long, amplitude: Int) {
    val v: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    if (v?.hasVibrator() != true) return
    runCatching { v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255))) }
}

/**
 * Le coffre : trois secousses, puis il s'ouvre.
 *
 * Toute la satisfaction tient dans l'attente. Un coffre qui s'ouvre immédiatement, c'est
 * une boîte de dialogue ; un coffre qui tremble deux secondes pendant qu'on ne peut rien
 * faire, c'est un cadeau. Les vibrations montent avec les secousses pour la même raison.
 */
@Composable
fun ChestScreen(cosmetic: Cosmetic, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var opened by remember { mutableStateOf(false) }

    val shake = remember { Animatable(0f) }
    val lidPop = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }

    LaunchedEffect(cosmetic.id) {
        // trois secousses, de plus en plus fortes
        repeat(3) { i ->
            buzz(ctx, 40L + i * 25L, 90 + i * 55)
            shake.animateTo(1f, tween(110, easing = FastOutLinearInEasing))
            shake.animateTo(-1f, tween(110, easing = FastOutLinearInEasing))
            shake.animateTo(0f, tween(90))
            delay(180L - i * 50L)
        }
        buzz(ctx, 160L, 255)
        opened = true
        lidPop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow))
        reveal.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }

    val spin = rememberInfiniteTransition(label = "chest")
    val angle by spin.animateFloat(
        0f, 360f, infiniteRepeatable(tween(22_000, easing = LinearEasing)), label = "rays"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pal.IrisSoft, Pal.Card, Pal.Card))),
        contentAlignment = Alignment.Center
    ) {
        if (opened) {
            Canvas(Modifier.fillMaxSize().alpha(0.35f * reveal.value)) {
                val c = Offset(size.width / 2f, size.height * 0.42f)
                val r = size.maxDimension
                rotate(angle, c) {
                    repeat(16) { i ->
                        val a0 = Math.toRadians(i * 360.0 / 16).toFloat()
                        val a1 = a0 + 0.11f
                        drawPath(
                            Path().apply {
                                moveTo(c.x, c.y)
                                lineTo(c.x + cos(a0) * r, c.y + sin(a0) * r)
                                lineTo(c.x + cos(a1) * r, c.y + sin(a1) * r)
                                close()
                            },
                            Pal.Butter
                        )
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (opened) "Tu as trouvé" else "Un coffre pour toi",
                style = Type.Label, color = Pal.Muted
            )
            Spacer(Modifier.height(20.dp))

            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Chest(
                    open = opened,
                    shake = shake.value,
                    lid = lidPop.value,
                    modifier = Modifier.fillMaxSize()
                )
                if (opened) {
                    // L'objet sort du coffre : le dragon le porte déjà, c'est plus clair
                    // qu'une icône flottante -- on voit tout de suite ce qu'on a gagné.
                    Mascot(
                        Mood.Cheering,
                        Modifier
                            .size(150.dp)
                            .offset(y = (-56).dp)
                            .scale(reveal.value),
                        worn = setOf(cosmetic.id)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (opened) {
                Text(
                    cosmetic.name,
                    style = Type.Display.copy(fontSize = 30.sp, lineHeight = 36.sp),
                    color = Pal.Iris, textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(reveal.value)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    cosmetic.blurb,
                    style = Type.Body, color = Pal.Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(reveal.value)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Déjà rangée dans ton casier.",
                    style = Type.Label, color = Pal.Muted,
                    modifier = Modifier.alpha(reveal.value)
                )
            }
        }

        if (opened) {
            Button(
                onClick = onDone,
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = Pal.Iris),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 32.dp, end = 32.dp, bottom = 44.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .alpha(reveal.value)
            ) { Text("Super!", style = Type.Title) }
        }
    }
}

/** Le coffre lui-même : caisse, couvercle, ferrures. Dessiné, pas une image. */
@Composable
private fun Chest(open: Boolean, shake: Float, lid: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val s = w / 220f
        run {
            val cx = size.width / 2f
            val baseY = size.height * 0.68f

            rotate(shake * 7f, Offset(cx, baseY)) {
                // caisse
                drawRoundRect(
                    color = Pal.Butter,
                    topLeft = Offset(cx - 74 * s, baseY - 54 * s),
                    size = androidx.compose.ui.geometry.Size(148 * s, 78 * s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10 * s)
                )
                drawRect(
                    color = Pal.Iris,
                    topLeft = Offset(cx - 74 * s, baseY - 10 * s),
                    size = androidx.compose.ui.geometry.Size(148 * s, 16 * s)
                )
                // serrure
                drawRoundRect(
                    color = Pal.Iris,
                    topLeft = Offset(cx - 13 * s, baseY - 22 * s),
                    size = androidx.compose.ui.geometry.Size(26 * s, 30 * s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5 * s)
                )

                // couvercle : il se soulève et bascule vers l'arrière
                val liftY = -lid * 62 * s
                val tilt = -lid * 26f
                rotate(tilt, Offset(cx - 74 * s, baseY - 54 * s + liftY)) {
                    drawRoundRect(
                        color = Pal.Butter,
                        topLeft = Offset(cx - 78 * s, baseY - 84 * s + liftY),
                        size = androidx.compose.ui.geometry.Size(156 * s, 34 * s),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12 * s)
                    )
                    drawRect(
                        color = Pal.Iris,
                        topLeft = Offset(cx - 78 * s, baseY - 60 * s + liftY),
                        size = androidx.compose.ui.geometry.Size(156 * s, 10 * s)
                    )
                }
            }
        }
    }
}
