package com.example.medtap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le fond de toute l'app : le ciel de Laval, à l'heure et à la saison qu'il est.
 *
 * Il reste PÂLE en permanence, y compris la nuit. Ce n'est pas une timidité de couleur :
 * tout le texte de l'app est prune foncé sur crème, et un vrai ciel nocturne rendrait
 * illisible chaque étiquette de chaque écran. Le ciel se lit donc par sa teinte, et les
 * astres sont peints dans des tons PLUS SOMBRES que le fond au lieu du blanc habituel.
 * C'est l'inverse d'un ciel réel, et la seule façon d'en avoir un derrière du texte.
 *
 * Le soleil et la lune traversent de gauche à droite au fil de leur course : à l'aube ils
 * sont au bord gauche et au ras de l'horizon, au milieu de leur passage ils sont hauts et
 * centrés. C'est ce déplacement, plus que la couleur, qui fait sentir que le temps avance.
 */
@Composable
fun Modifier.sky(): Modifier {
    val moment = rememberSky()
    return drawBehind { drawSky(moment) }
}

/**
 * L'instant courant, rafraîchi tant que l'écran est devant les yeux.
 *
 * Quand l'atelier tient l'horloge, on suit son rythme à lui : jusqu'à vingt images par
 * seconde, sinon le défilement d'une année saccaderait. Le reste du temps, une minute
 * suffit largement.
 */
@Composable
fun rememberSky(): Sky.Moment {
    val owner = LocalLifecycleOwner.current
    val labOn by SkyLab.active
    val labAt by SkyLab.instant
    val speed by SkyLab.speed
    val forced by SkyLab.forced

    var real by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                real = System.currentTimeMillis()
                delay(60_000)
            }
        }
    }

    // L'horloge de l'atelier avance toute seule quand une vitesse est choisie.
    //
    // « Jours » avance d'une journée de CALENDRIER par seconde, et non de vingt-quatre
    // heures : c'est la seule façon de traverser mars et novembre sans que l'heure
    // affichée glisse d'une heure à chaque changement d'heure. Les autres vitesses
    // avancent en millisecondes, à vingt images par seconde, pour que le fondu du jour
    // vers la nuit soit fluide — et là, le décalage horaire DOIT se voir, puisqu'il a
    // vraiment lieu ce jour-là.
    LaunchedEffect(speed, labOn) {
        if (!labOn || speed == SkyLab.Speed.STOPPED) return@LaunchedEffect
        if (speed == SkyLab.Speed.DAYS) {
            while (true) {
                delay(1000)
                SkyLab.nudgeDays(1)
            }
        } else {
            while (true) {
                delay(50)
                SkyLab.instant.value += speed.perSecond / 20
            }
        }
    }

    val now = if (labOn) labAt else real
    return remember(now / 1000, forced, labOn) { Sky.moment(now, forced) }
}

// ---- la palette du ciel ----------------------------------------------------

private val NightTop = Color(0xFFC6CBEC)
private val DawnTop = Color(0xFFFBD6C2)
private val DayTop = Color(0xFFCDE7F7)
private val DuskTop = Color(0xFFF7CAD7)

private val SunInk = Color(0xFFF2A93C)
private val MoonInk = Color(0xFFE3C46A)
private val StarInk = Color(0xFF7B84BC)
private val RainInk = Color(0xFF8FA8C6)
private val SnowInk = Color(0xFFA9BDD6)
private val AuroraA = Color(0xFF4FBE9B)
private val AuroraB = Color(0xFF9B7FD4)

private val LeafInks = listOf(
    Color(0xFFD9752F), Color(0xFFC2521F), Color(0xFFE0A03A), Color(0xFFA8452B)
)

/**
 * La lumière de la saison, posée par-dessus celle de l'heure.
 *
 * L'hiver tire le ciel vers un bleu délavé et l'été vers l'or : c'est la même heure de la
 * journée qui n'a pas du tout la même couleur en janvier et en juillet, et c'est ce
 * décalage-là qui fait qu'on reconnaît une saison sans qu'on la nomme.
 */
private fun seasonTint(s: Sky.Season): Pair<Color, Float> = when (s) {
    Sky.Season.WINTER -> Color(0xFFD6E4F2) to 0.42f
    Sky.Season.SPRING -> Color(0xFFDCF0DC) to 0.22f
    Sky.Season.SUMMER -> Color(0xFFFCEBC8) to 0.24f
    Sky.Season.AUTUMN -> Color(0xFFF6DCC0) to 0.34f
}

private fun topColor(m: Sky.Moment): Color {
    val base = when (m.phase) {
        Sky.Phase.NIGHT -> NightTop
        Sky.Phase.DAY -> DayTop
        Sky.Phase.DAWN ->
            if (m.blend < 0.5f) lerp(NightTop, DawnTop, m.blend * 2f)
            else lerp(DawnTop, DayTop, (m.blend - 0.5f) * 2f)
        Sky.Phase.DUSK ->
            if (m.blend < 0.5f) lerp(DayTop, DuskTop, m.blend * 2f)
            else lerp(DuskTop, NightTop, (m.blend - 0.5f) * 2f)
    }
    val (tint, amount) = seasonTint(m.season)
    // La nuit garde sa couleur : une saison ne change pas la teinte du noir, et l'hiver
    // délavé sur un ciel nocturne le rendrait simplement gris.
    return lerp(base, tint, if (m.night) amount * 0.35f else amount)
}

// ---- le dessin -------------------------------------------------------------

private fun DrawScope.drawSky(m: Sky.Moment) {
    val h = size.height
    val band = h * 0.55f

    drawRect(
        Brush.verticalGradient(
            0f to topColor(m), 0.55f to Pal.Mist, 1f to Pal.Mist,
            startY = 0f, endY = h
        )
    )

    val sky = Rect(0f, 0f, size.width, band * 0.62f)

    if (m.aurora > 0f) aurora(m, sky)

    if (m.night) {
        stars(m, sky)
        moon(m, sky)
        if (m.shootingStar > 0f) shootingStar(m, sky)
    } else {
        sun(m, sky)
    }

    if (m.rainbow) rainbow(sky)

    when (m.falling) {
        Sky.Falling.RAIN -> rain(m, band)
        Sky.Falling.SNOW -> snow(m, band)
        Sky.Falling.LEAVES -> leaves(m, band)
        Sky.Falling.NONE -> Unit
    }
}

/** De gauche à droite, et d'autant plus haut qu'on est au milieu de la course. */
private fun DrawScope.arcPos(sky: Rect, m: Sky.Moment): Offset = Offset(
    sky.width * (0.12f + 0.76f * m.traverse),
    sky.bottom - sky.height * (0.12f + 0.72f * m.arc)
)

private fun DrawScope.sun(m: Sky.Moment, sky: Rect) {
    val p = arcPos(sky, m)
    val r = sky.height * 0.16f
    drawCircle(SunInk.copy(alpha = 0.16f), r * 2.1f, p)
    drawCircle(SunInk.copy(alpha = 0.28f), r * 1.4f, p)
    drawCircle(SunInk.copy(alpha = 0.85f), r, p)
}

/**
 * Le croissant, découpé et non repeint.
 *
 * Le fond est un dégradé : masquer une partie du disque avec une couleur unie laisserait
 * une tache visible. La morsure est donc retirée du chemin lui-même.
 */
private fun DrawScope.moon(m: Sky.Moment, sky: Rect) {
    val p = arcPos(sky, m)
    val r = sky.height * 0.15f
    drawCircle(MoonInk.copy(alpha = 0.14f), r * 2f, p)

    val illum = 1f - abs(m.moon - 0.5f) * 2f      // 0 nouvelle .. 1 pleine
    val shift = r * 2f * (1f - illum)
    val side = if (m.moon < 0.5f) 1f else -1f

    val disc = Path().apply { addOval(Rect(p.x - r, p.y - r, p.x + r, p.y + r)) }
    val bite = Path().apply {
        addOval(
            Rect(p.x - r + side * shift, p.y - r * 1.04f, p.x + r + side * shift, p.y + r * 1.04f)
        )
    }
    val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
    drawPath(if (illum > 0.97f) disc else crescent, MoonInk.copy(alpha = 0.9f))
}

private fun DrawScope.stars(m: Sky.Moment, sky: Rect) {
    val rng = Random(31)
    repeat(34) {
        val x = rng.nextFloat() * sky.width
        val y = rng.nextFloat() * sky.height
        val r = 1.2f + rng.nextFloat() * 2.2f
        val a = (0.5f - y / sky.height * 0.35f) * (1f - m.aurora * 0.25f)
        drawCircle(StarInk.copy(alpha = a.coerceIn(0f, 1f)), r, Offset(x, y))
    }
}

private fun DrawScope.aurora(m: Sky.Moment, sky: Rect) {
    listOf(AuroraA to 0f, AuroraB to 0.28f, AuroraA to 0.55f)
        .forEachIndexed { i, (color, offset) ->
            val path = Path()
            val top = sky.height * (0.06f + offset)
            path.moveTo(0f, top + sky.height * 0.1f)
            var x = 0f
            while (x <= sky.width) {
                val wave = sin((x / sky.width * 2.6f + i * 0.7f) * PI).toFloat()
                path.lineTo(x, top + wave * sky.height * 0.11f)
                x += sky.width / 24f
            }
            path.lineTo(sky.width, sky.bottom)
            path.lineTo(0f, sky.bottom)
            path.close()
            drawPath(path, color.copy(alpha = 0.16f * m.aurora))
        }
}

private fun DrawScope.shootingStar(m: Sky.Moment, sky: Rect) {
    val rng = Random(11)
    val x0 = sky.width * (0.1f + rng.nextFloat() * 0.5f)
    val y0 = sky.height * (0.08f + rng.nextFloat() * 0.3f)
    val len = sky.width * 0.22f
    val t = m.shootingStar
    val head = Offset(x0 + len * (1f - t), y0 + len * 0.45f * (1f - t))
    drawLine(
        StarInk.copy(alpha = 0.55f * t), head,
        Offset(head.x - len * 0.5f, head.y - len * 0.22f),
        strokeWidth = 2.5f, cap = StrokeCap.Round
    )
    drawCircle(StarInk.copy(alpha = 0.8f * t), 3f, head)
}

private fun DrawScope.rainbow(sky: Rect) {
    val colors = listOf(
        Color(0xFFE0685E), Color(0xFFE9A45E), Color(0xFFE7CE63),
        Color(0xFF6BB98A), Color(0xFF5C93C9), Color(0xFF8E7BC0)
    )
    val cx = sky.width * 0.5f
    val cy = sky.bottom + sky.height * 0.55f
    colors.forEachIndexed { i, c ->
        val r = sky.height * (0.78f + i * 0.055f)
        drawArc(
            color = c.copy(alpha = 0.17f),
            startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
            style = Stroke(width = sky.height * 0.05f)
        )
    }
}

// ---- ce qui tombe ----------------------------------------------------------
//
// Tout s'efface en descendant : rien de ce qui tombe ne doit jamais croiser le texte.

private fun DrawScope.rain(m: Sky.Moment, band: Float) {
    val rng = Random(4242)
    repeat((14 + m.fall * 34f).toInt()) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * band
        val len = 10f + rng.nextFloat() * 14f
        drawLine(
            RainInk.copy(alpha = (0.3f * (1f - y / band)).coerceAtLeast(0f)),
            Offset(x, y), Offset(x - len * 0.32f, y + len),
            strokeWidth = 1.8f, cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.snow(m: Sky.Moment, band: Float) {
    val rng = Random(777)
    repeat((18 + m.fall * 40f).toInt()) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * band
        val r = 1.8f + rng.nextFloat() * 3.4f
        drawCircle(
            SnowInk.copy(alpha = (0.42f * (1f - y / band)).coerceAtLeast(0f)),
            r, Offset(x, y)
        )
    }
}

/**
 * Les feuilles d'automne : de petites amandes penchées, jamais deux du même angle.
 *
 * Des ronds oranges se liraient comme des bulles, et des losanges comme des confettis.
 * C'est l'inclinaison irrégulière qui donne la chute.
 */
private fun DrawScope.leaves(m: Sky.Moment, band: Float) {
    val rng = Random(1010)
    repeat((10 + m.fall * 22f).toInt()) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * band
        val s = 4f + rng.nextFloat() * 5f
        val color = LeafInks[rng.nextInt(LeafInks.size)]
        val alpha = (0.55f * (1f - y / band)).coerceAtLeast(0f)
        rotate(rng.nextFloat() * 360f, Offset(x, y)) {
            val leaf = Path().apply {
                moveTo(x, y - s)
                quadraticBezierTo(x + s * 0.9f, y, x, y + s)
                quadraticBezierTo(x - s * 0.9f, y, x, y - s)
                close()
            }
            drawPath(leaf, color.copy(alpha = alpha))
        }
    }
}
