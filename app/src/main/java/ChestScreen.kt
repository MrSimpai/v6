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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Une petite vibration, sans planter là où le moteur n'existe pas. */
internal fun buzz(ctx: android.content.Context, ms: Long, amplitude: Int) {
    vibrator(ctx)?.let { v ->
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
        }
    }
}

/**
 * Une SUITE de vibrations, pas une seule.
 *
 * C'est là toute la différence avec le coffre de Duolingo : un choc unique se sent comme
 * une notification, tandis qu'un roulement qui monte puis retombe se sent comme un
 * événement. `createWaveform` laisse le moteur enchaîner les intensités sans que le code
 * ait à dormir entre deux.
 */
internal fun buzzPattern(ctx: android.content.Context, timings: LongArray, amps: IntArray) {
    vibrator(ctx)?.let { v ->
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(timings, amps.map { it.coerceIn(0, 255) }.toIntArray(), -1))
        }
    }
}

private fun vibrator(ctx: android.content.Context): Vibrator? {
    val v: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31)
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    return if (v?.hasVibrator() == true) v else null
}

/**
 * Le coffre.
 *
 * Toute la satisfaction tient dans l'attente, puis dans la décharge. Trois secousses qui
 * montent, un dernier temps d'ARRÊT — le coffre se tasse sur lui-même et ne bouge plus
 * pendant un cinquième de seconde — et c'est ce silence qui fait exploser la suite. Sans
 * cette anticipation, l'ouverture n'était qu'un couvercle qui montait.
 *
 * Ensuite tout part en même temps : éclair blanc, onde de choc, rayons, et des feux
 * d'artifice qui continuent tant qu'on reste là. Les vibrations suivent la même courbe,
 * en roulement plutôt qu'en coups isolés.
 */
@Composable
fun ChestScreen(cosmetic: Cosmetic, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var opened by remember { mutableStateOf(false) }

    val shake = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }     // l'anticipation : il se tasse
    val lid = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val shock = remember { Animatable(0f) }      // l'onde de choc

    LaunchedEffect(cosmetic.id) {
        // Trois secousses, chacune plus courte et plus forte que la précédente.
        repeat(3) { i ->
            buzzPattern(
                ctx,
                longArrayOf(0, 30L + i * 15L, 40L, 30L + i * 15L),
                intArrayOf(0, 70 + i * 50, 0, 70 + i * 50)
            )
            val d = 105 - i * 22
            shake.animateTo(1f, tween(d, easing = FastOutLinearInEasing))
            shake.animateTo(-1f, tween(d, easing = FastOutLinearInEasing))
            shake.animateTo(0f, tween(d, easing = FastOutSlowInEasing))
            delay(170L - i * 55L)
        }

        // L'anticipation. Il se tasse, et surtout il s'immobilise.
        squash.animateTo(0.82f, tween(180, easing = FastOutSlowInEasing))
        buzzPattern(ctx, longArrayOf(0, 220), intArrayOf(0, 40))
        delay(200)

        // La décharge.
        opened = true
        buzzPattern(
            ctx,
            longArrayOf(0, 60, 30, 110, 40, 60, 60, 40),
            intArrayOf(0, 255, 0, 210, 0, 150, 0, 90)
        )
        launch { flash.animateTo(1f, tween(60)); flash.animateTo(0f, tween(420)) }
        launch { shock.animateTo(1f, tween(620, easing = LinearOutSlowInEasing)) }
        launch { squash.animateTo(1.08f, spring(0.32f, Spring.StiffnessMedium)) }
        lid.animateTo(1f, spring(dampingRatio = 0.46f, stiffness = Spring.StiffnessVeryLow))
    }

    // Les petits coups qui accompagnent chaque bouquet, tant qu'on reste sur l'écran.
    LaunchedEffect(opened) {
        if (!opened) return@LaunchedEffect
        delay(500)
        while (true) {
            buzzPattern(ctx, longArrayOf(0, 22, 50, 16), intArrayOf(0, 120, 0, 70))
            delay(640)
        }
    }

    LaunchedEffect(opened) {
        if (opened) reveal.animateTo(1f, spring(0.5f, Spring.StiffnessLow))
    }

    val loop = rememberInfiniteTransition(label = "chest")
    val rays by loop.animateFloat(
        0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "rays"
    )
    val fw by loop.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "fw"
    )
    val bob by loop.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pal.IrisSoft, Pal.Card, Pal.Card))),
        contentAlignment = Alignment.Center
    ) {
        if (opened) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2f, size.height * 0.42f)

                // Les rayons, sous tout le reste.
                rotate(rays, c) {
                    repeat(18) { i ->
                        val a0 = (i * 360.0 / 18 * PI / 180).toFloat()
                        val a1 = a0 + 0.10f
                        val r = size.maxDimension
                        drawPath(
                            Path().apply {
                                moveTo(c.x, c.y)
                                lineTo(c.x + cos(a0) * r, c.y + sin(a0) * r)
                                lineTo(c.x + cos(a1) * r, c.y + sin(a1) * r)
                                close()
                            },
                            Pal.Butter.copy(alpha = 0.34f * reveal.value)
                        )
                    }
                }

                // L'onde de choc : un anneau qui s'ouvre et s'affine.
                if (shock.value in 0.001f..0.999f) {
                    val e = shock.value
                    drawCircle(
                        Pal.Iris.copy(alpha = (1f - e) * 0.55f),
                        size.minDimension * 0.12f + e * size.maxDimension * 0.62f,
                        c,
                        style = Stroke(width = (1f - e) * 26f + 2f)
                    )
                }

                fireworks(fw, reveal.value)
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
                    shake = shake.value,
                    squash = squash.value,
                    lid = lid.value,
                    modifier = Modifier.fillMaxSize()
                )
                if (opened) {
                    // L'objet sort du coffre : le dragon le porte déjà, c'est plus clair
                    // qu'une icône flottante — on voit tout de suite ce qu'on a gagné. Et
                    // il flotte doucement, pour que la scène ne se fige pas.
                    Mascot(
                        Mood.Cheering,
                        Modifier
                            .size(150.dp)
                            .offset(y = (-56 - bob * 6).dp)
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
                    modifier = Modifier.alpha(reveal.value).scale(0.85f + reveal.value * 0.15f)
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

        // L'éclair, tout en haut de la pile : c'est lui qui masque l'instant où le
        // couvercle saute, et qui fait qu'on ne voit pas la couture.
        if (flash.value > 0.001f) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.White.copy(alpha = flash.value * 0.85f))
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

// ---- les feux d'artifice ---------------------------------------------------

private val FwColors = listOf(
    Color(0xFFFF5A9E), Color(0xFFFF8FC6), Color(0xFFFFD166),
    Color(0xFF7BD8E8), Color(0xFFC08BFF), Color(0xFFFF6B6B)
)

/**
 * Un point du contour d'un cœur, pour `u` de 0 à 2π.
 *
 * La courbe classique : `x = 16 sin³u`. Un cercle aplati ne se lirait pas comme un cœur —
 * il faut la pointe en bas et le creux en haut, et c'est exactement ce que donne cette
 * paramétrique. Le signe de `y` est inversé parce que l'écran descend.
 */
private fun heartPoint(u: Float): Pair<Float, Float> {
    val x = 16f * sin(u).pow(3)
    val y = 13f * cos(u) - 5f * cos(2 * u) - 2f * cos(3 * u) - cos(4 * u)
    return x / 17f to -y / 17f
}

/**
 * Six bouquets qui se relaient en boucle, dont la moitié en cœurs roses.
 *
 * Chacun a son propre décalage de phase, donc il y en a toujours un ou deux en l'air : un
 * seul bouquet à la fois laisserait des trous, et six simultanés seraient une bouillie.
 */
private fun DrawScope.fireworks(t: Float, alpha: Float) {
    val rng = Random(7)
    repeat(6) { i ->
        val phase = (t + i / 6f) % 1f
        if (phase > 0.82f) return@repeat

        val e = phase / 0.82f
        val cx = size.width * (0.14f + rng.nextFloat() * 0.72f)
        val cy = size.height * (0.12f + rng.nextFloat() * 0.44f)
        val heart = i % 2 == 0
        val color = if (heart) FwColors[rng.nextInt(2)] else FwColors[2 + rng.nextInt(4)]

        // Il part vite et ralentit : une expansion linéaire fait « rond qui grandit »
        // plutôt que « explosion ».
        val spread = size.minDimension * (if (heart) 0.30f else 0.26f) *
            (1f - (1f - e).pow(2.6f))
        val fade = ((1f - e).pow(1.5f) * alpha).coerceIn(0f, 1f)
        val gravity = size.height * 0.10f * e * e
        val n = if (heart) 30 else 22

        repeat(n) { k ->
            val u = k / n.toFloat() * 2f * PI.toFloat()
            val (dx, dy) = if (heart) heartPoint(u) else cos(u) to sin(u)
            val px = cx + dx * spread
            val py = cy + dy * spread + gravity
            val r = (4.5f - e * 2.6f).coerceAtLeast(1.2f)

            // La traînée, tracée vers le centre : c'est elle qui donne la vitesse.
            drawLine(
                color.copy(alpha = fade * 0.35f),
                Offset(px, py),
                Offset(cx + dx * spread * 0.72f, cy + dy * spread * 0.72f + gravity * 0.7f),
                strokeWidth = r * 0.9f
            )
            drawCircle(color.copy(alpha = fade), r, Offset(px, py))
        }
        // Le cœur du bouquet, qui s'éteint le premier.
        drawCircle(
            Color.White.copy(alpha = (fade * (1f - e) * 1.6f).coerceIn(0f, 1f)),
            size.minDimension * 0.03f * (1f - e), Offset(cx, cy)
        )
    }
}

// ---- le coffre -------------------------------------------------------------

/** Le coffre lui-même : caisse, couvercle, ferrures. Dessiné, pas une image. */
@Composable
private fun Chest(shake: Float, squash: Float, lid: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val s = w / 220f
        val cx = size.width / 2f
        val baseY = size.height * 0.68f

        rotate(shake * 7f, Offset(cx, baseY)) {
            // Le tassement : plus large quand il s'écrase, plus étroit quand il rebondit.
            // Un objet qui se déforme a du poids ; un objet rigide qui saute n'en a pas.
            val sx = 2f - squash
            withTransform({ scale(sx, squash, Offset(cx, baseY)) }) {
                drawRoundRect(
                    Pal.Butter,
                    Offset(cx - 74 * s, baseY - 54 * s), Size(148 * s, 78 * s),
                    CornerRadius(10 * s)
                )
                drawRect(Pal.Iris, Offset(cx - 74 * s, baseY - 10 * s), Size(148 * s, 16 * s))
                drawRoundRect(
                    Pal.Iris,
                    Offset(cx - 13 * s, baseY - 22 * s), Size(26 * s, 30 * s),
                    CornerRadius(5 * s)
                )
                // L'intérieur, qui se découvre quand le couvercle part.
                if (lid > 0.05f) {
                    drawRoundRect(
                        Color(0xFF6B2A45).copy(alpha = (lid * 2f).coerceAtMost(1f)),
                        Offset(cx - 66 * s, baseY - 58 * s), Size(132 * s, 18 * s),
                        CornerRadius(6 * s)
                    )
                    drawOval(
                        Color(0xFFFFF3C4).copy(alpha = 0.55f * lid),
                        Offset(cx - 58 * s, baseY - 72 * s), Size(116 * s, 30 * s)
                    )
                }
            }

            // Le couvercle : il monte, bascule, et part légèrement de côté — un couvercle
            // qui monte tout droit ressemble à un ascenseur.
            val liftY = -lid * 74 * s
            val slideX = lid * 16 * s
            rotate(-lid * 34f, Offset(cx - 74 * s + slideX, baseY - 54 * s + liftY)) {
                drawRoundRect(
                    Pal.Butter,
                    Offset(cx - 78 * s + slideX, baseY - 84 * s + liftY), Size(156 * s, 34 * s),
                    CornerRadius(12 * s)
                )
                drawRect(
                    Pal.Iris,
                    Offset(cx - 78 * s + slideX, baseY - 60 * s + liftY), Size(156 * s, 10 * s)
                )
            }
        }
    }
}
