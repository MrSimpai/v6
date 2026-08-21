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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val glow = remember { Animatable(0f) }       // la lumière qui filtre par la fente
    val hover = remember { Animatable(0f) }      // il décolle avant de céder
    val lid = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val shock = remember { Animatable(0f) }      // l'onde de choc

    LaunchedEffect(cosmetic.id) {
        // Le coffre s'ouvrait en une seconde et demie, dont une bonne moitié en secousses
        // identiques. C'est trop court pour qu'il se passe quoi que ce soit dans la tête :
        // on voyait un couvercle sauter avant d'avoir eu le temps de vouloir qu'il saute.
        //
        // Ce qui rend un coffre satisfaisant, ce n'est pas l'ouverture, c'est le REFUS de
        // s'ouvrir. Alors il résiste maintenant pendant trois secondes et demie, en quatre
        // temps qui montent : trois sursauts de plus en plus francs, un roulement, et un
        // silence. La récompense n'a pas changé — c'est l'attente qui la fabrique.

        // Un temps mort d'abord : sans lui, l'animation a déjà commencé quand l'œil
        // arrive dessus, et on rate le premier tiers.
        delay(280)

        // Premier temps. Trois sursauts, chacun plus ample que le précédent, séparés par
        // des silences de plus en plus COURTS. C'est le raccourcissement des silences qui
        // fait monter la tension, pas l'amplitude des coups.
        listOf(0.34f, 0.62f, 1f).forEachIndexed { i, amp ->
            buzzPattern(
                ctx,
                longArrayOf(0, 26L + i * 16L, 44L, 26L + i * 16L),
                intArrayOf(0, 60 + i * 55, 0, 60 + i * 55)
            )
            val d = 132 - i * 20
            shake.animateTo(amp, tween(d, easing = FastOutLinearInEasing))
            shake.animateTo(-amp * 0.92f, tween(d, easing = FastOutLinearInEasing))
            shake.animateTo(amp * 0.42f, tween(d, easing = FastOutLinearInEasing))
            shake.animateTo(0f, tween(d + 46, easing = FastOutSlowInEasing))
            // À chaque sursaut, un peu plus de lumière sort de la fente : quelque chose
            // là-dedans est en train de se réveiller.
            launch { glow.animateTo(0.22f + i * 0.26f, tween(320)) }
            delay(430L - i * 125L)
        }

        // Deuxième temps : le roulement. Des secousses courtes et serrées qui accélèrent,
        // pendant que le coffre décolle et que la lumière monte au maximum.
        launch { glow.animateTo(1f, tween(700)) }
        launch { hover.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        buzzPattern(
            ctx,
            longArrayOf(0, 46, 26, 46, 22, 52, 18, 58, 14, 64, 12, 80),
            intArrayOf(0, 55, 0, 80, 0, 110, 0, 145, 0, 185, 0, 225)
        )
        repeat(9) { k ->
            val a = 0.14f + k * 0.052f
            shake.animateTo(if (k % 2 == 0) a else -a, tween(48 - k * 2, easing = LinearEasing))
        }
        shake.animateTo(0f, tween(70))

        // Troisième temps : LE SILENCE. Il retombe, se tasse, la lumière faiblit et plus
        // rien ne bouge pendant un tiers de seconde. C'est le temps le plus important de
        // toute la séquence, et c'est le seul où il ne se passe rien.
        launch { glow.animateTo(0.30f, tween(200)) }
        launch { hover.animateTo(0f, tween(210)) }
        squash.animateTo(0.78f, tween(230, easing = FastOutSlowInEasing))
        buzzPattern(ctx, longArrayOf(0, 270), intArrayOf(0, 28))
        delay(320)

        // La décharge.
        opened = true
        buzzPattern(
            ctx,
            longArrayOf(0, 60, 30, 110, 40, 60, 60, 40),
            intArrayOf(0, 255, 0, 210, 0, 150, 0, 90)
        )
        launch { glow.animateTo(1f, tween(90)) }
        launch { flash.animateTo(1f, tween(60)); flash.animateTo(0f, tween(460)) }
        launch { shock.animateTo(1f, tween(680, easing = LinearOutSlowInEasing)) }
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
                            ShellHi.copy(alpha = 0.40f * reveal.value)
                        )
                    }
                }

                // L'onde de choc : un anneau qui s'ouvre et s'affine.
                if (shock.value in 0.001f..0.999f) {
                    val e = shock.value
                    drawCircle(
                        Shell.copy(alpha = (1f - e) * 0.6f),
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
                    glow = glow.value,
                    hover = hover.value,
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
                // Blanc légèrement rosé plutôt que blanc pur : un flash neutre a l'air
                // d'un écran qui saute, un flash teinté a l'air de faire partie de la fête.
                drawRect(Color(0xFFFFF4F8).copy(alpha = flash.value * 0.85f))
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

// La palette du coffre : framboise et or. Il était crème et violet, c'est-à-dire les
// couleurs de l'app et de rien d'autre — un meuble. Un coffre doit avoir l'air d'un objet
// PRÉCIEUX, et le rose bonbon bordé d'or est exactement ce vocabulaire-là.
private val ShellHi = Color(0xFFFFD3E4)
private val Shell = Color(0xFFFF9EC8)
private val ShellLo = Color(0xFFE8639C)
private val Band = Color(0xFFD8447F)
private val Gold = Color(0xFFFFDE9B)
private val GoldLo = Color(0xFFF0B44E)
private val Inside = Color(0xFF7A2247)
private val Light = Color(0xFFFFF6DC)

/**
 * Le coffre lui-même : caisse, couvercle, ferrures, ruban. Dessiné, pas une image.
 *
 * `glow` est la lumière qui filtre par la fente du couvercle pendant que le coffre
 * résiste. C'est la seule chose qui dit qu'il y a QUELQUE CHOSE dedans : sans elle, les
 * secousses ne sont qu'une boîte qui bouge, et rien ne justifie d'attendre trois secondes.
 * `hover` le décolle du sol juste avant qu'il cède.
 */
@Composable
private fun Chest(
    shake: Float,
    squash: Float,
    lid: Float,
    glow: Float,
    hover: Float,
    modifier: Modifier
) {
    Canvas(modifier) {
        val w = size.minDimension
        val s = w / 220f
        val cx = size.width / 2f
        val floorY = size.height * 0.68f
        val baseY = floorY - hover * 12f * s

        // Le halo : il grossit avec la lumière intérieure et déborde du coffre.
        if (glow > 0.01f) {
            drawCircle(
                Brush.radialGradient(
                    listOf(
                        Color(0xFFFFC7E0).copy(alpha = 0.55f * glow),
                        Color(0x00FFC7E0)
                    ),
                    center = Offset(cx, baseY - 30 * s),
                    radius = (70f + glow * 90f) * s
                ),
                (70f + glow * 90f) * s,
                Offset(cx, baseY - 30 * s)
            )
        }

        // L'ombre portée : elle rétrécit et pâlit quand le coffre décolle. C'est elle qui
        // rend le décollage lisible — sans ombre, un objet qui monte a juste changé de
        // place.
        drawOval(
            Color(0xFF6B2A45).copy(alpha = 0.20f * (1f - hover * 0.55f)),
            Offset(cx - (72f - hover * 12f) * s, floorY + 18 * s),
            Size((144f - hover * 24f) * s, 16 * s)
        )

        rotate(shake * 7f, Offset(cx, baseY)) {
            // Le tassement : plus large quand il s'écrase, plus étroit quand il rebondit.
            // Un objet qui se déforme a du poids ; un objet rigide qui saute n'en a pas.
            val sx = 2f - squash
            withTransform({ scale(sx, squash, Offset(cx, baseY)) }) {
                // La caisse, en dégradé : clair en haut, framboise en bas. Un aplat unique
                // se lit comme un rectangle de couleur ; le dégradé lui donne un volume.
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(Shell, ShellLo),
                        startY = baseY - 54 * s, endY = baseY + 24 * s
                    ),
                    Offset(cx - 74 * s, baseY - 54 * s), Size(148 * s, 78 * s),
                    CornerRadius(14 * s)
                )
                // Le reflet du haut, sur le bord gauche : la lumière vient toujours du
                // même coin dans toute l'app.
                drawRoundRect(
                    ShellHi.copy(alpha = 0.6f),
                    Offset(cx - 66 * s, baseY - 47 * s), Size(52 * s, 12 * s),
                    CornerRadius(6 * s)
                )

                // Les deux sangles verticales et la ceinture, en framboise foncée.
                listOf(-46f, 46f).forEach { dx ->
                    drawRect(
                        Band.copy(alpha = 0.85f),
                        Offset(cx + (dx - 7) * s, baseY - 54 * s), Size(14 * s, 78 * s)
                    )
                }
                drawRect(Band, Offset(cx - 74 * s, baseY - 12 * s), Size(148 * s, 18 * s))
                drawRect(
                    GoldLo.copy(alpha = 0.5f),
                    Offset(cx - 74 * s, baseY - 13 * s), Size(148 * s, 2.5f * s)
                )

                // Trois petits cœurs sur le panneau : c'est un cadeau, pas un colis.
                listOf(-24f to -34f, 0f to -28f, 24f to -34f).forEach { (dx, dy) ->
                    heart(Offset(cx + dx * s, baseY + dy * s), 6.5f * s, ShellHi.copy(alpha = 0.55f))
                }

                // La serrure : un cœur d'or. Un rectangle faisait cadenas de valise.
                heart(Offset(cx, baseY - 3 * s), 15 * s, GoldLo)
                heart(Offset(cx, baseY - 4 * s), 13 * s, Gold)
                drawCircle(Inside.copy(alpha = 0.55f), 3.2f * s, Offset(cx, baseY - 2 * s))

                // L'intérieur, qui se découvre quand le couvercle part.
                if (lid > 0.05f) {
                    drawRoundRect(
                        Inside.copy(alpha = (lid * 2f).coerceAtMost(1f)),
                        Offset(cx - 66 * s, baseY - 58 * s), Size(132 * s, 18 * s),
                        CornerRadius(7 * s)
                    )
                    drawOval(
                        Light.copy(alpha = 0.6f * lid),
                        Offset(cx - 58 * s, baseY - 74 * s), Size(116 * s, 32 * s)
                    )
                }
            }

            // La FENTE. Tant que le couvercle tient, une ligne de lumière court le long du
            // joint et déborde un peu de chaque côté. C'est tout ce qu'il faut pour qu'on
            // veuille savoir ce qu'il y a dessous.
            if (lid < 0.2f && glow > 0.01f) {
                val g = glow * (1f - lid * 5f).coerceIn(0f, 1f)
                val seamY = baseY - 55 * s
                drawRoundRect(
                    Light.copy(alpha = 0.9f * g),
                    Offset(cx - 70 * s, seamY - 2.5f * s * g), Size(140 * s, 5f * s * g + 1f),
                    CornerRadius(3 * s)
                )
                drawRoundRect(
                    Color(0xFFFFB8D8).copy(alpha = 0.5f * g),
                    Offset(cx - 78 * s, seamY - 9 * s * g), Size(156 * s, 18 * s * g + 1f),
                    CornerRadius(9 * s)
                )
            }

            // Le couvercle : il monte, bascule, et part légèrement de côté — un couvercle
            // qui monte tout droit ressemble à un ascenseur.
            val liftY = -lid * 74 * s
            val slideX = lid * 16 * s
            rotate(-lid * 34f, Offset(cx - 74 * s + slideX, baseY - 54 * s + liftY)) {
                val top = baseY - 84 * s + liftY
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(ShellHi, Shell), startY = top, endY = top + 34 * s
                    ),
                    Offset(cx - 78 * s + slideX, top), Size(156 * s, 34 * s),
                    CornerRadius(15 * s)
                )
                drawRect(
                    Band,
                    Offset(cx - 78 * s + slideX, top + 24 * s), Size(156 * s, 12 * s)
                )
                listOf(-46f, 46f).forEach { dx ->
                    drawRect(
                        Band.copy(alpha = 0.85f),
                        Offset(cx + (dx - 7) * s + slideX, top), Size(14 * s, 34 * s)
                    )
                }
                // Le nœud sur le dessus : deux boucles et un centre doré.
                val bx = cx + slideX
                listOf(-1f, 1f).forEach { d ->
                    drawPath(
                        Path().apply {
                            moveTo(bx, top + 4 * s)
                            quadraticBezierTo(bx + d * 30 * s, top - 14 * s, bx + d * 26 * s, top + 2 * s)
                            quadraticBezierTo(bx + d * 22 * s, top + 11 * s, bx, top + 5 * s)
                            close()
                        },
                        Gold
                    )
                }
                drawCircle(GoldLo, 6 * s, Offset(bx, top + 4 * s))
                drawCircle(Light.copy(alpha = 0.7f), 2.4f * s, Offset(bx - 1.6f * s, top + 2.4f * s))
            }
        }
    }
}

/** Un cœur plein, centré, de rayon `r`. Le même tracé que les feux d'artifice. */
private fun DrawScope.heart(center: Offset, r: Float, color: Color) {
    drawPath(
        Path().apply {
            for (k in 0..28) {
                val (dx, dy) = heartPoint(k / 28f * 2f * PI.toFloat())
                val px = center.x + dx * r
                val py = center.y + dy * r
                if (k == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        },
        color
    )
}
