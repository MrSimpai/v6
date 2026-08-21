package com.example.medtap.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le ciel de Laval, en vrai, derrière toute l'app.
 *
 * Une première version gardait tout PÂLE en permanence pour ne jamais gêner le texte. Le
 * résultat était un ciel qu'on ne remarquait pas : une nuit lavande, une aurore
 * translucide, un lever de soleil rose bonbon. La bonne réponse n'était pas d'éteindre le
 * ciel mais d'adapter l'encre — [skyInk] rend du blanc quand le ciel est sombre. Le ciel
 * peut donc être franc : une nuit bleu nuit, une aurore verte qui éclaire, un couchant
 * rouge.
 *
 * La page est coupée par un HORIZON, aux deux cinquièmes de la hauteur. Au-dessus, le
 * ciel, ses astres et ses nuages ; l'astre y monte et y redescend en traversant vraiment
 * la ligne. En dessous, le sol de la saison, puis le fond de l'app pour le contenu.
 */
@Composable
fun Modifier.sky(): Modifier {
    val moment = rememberSky()
    val t = rememberSkyAnim()
    // `t.value` est lu DANS le lambda de dessin : seule la phase de dessin se réabonne,
    // donc l'animation ne recompose rien, elle repeint. C'est la différence entre un fond
    // animé et une app qui recompose soixante fois par seconde.
    return drawBehind { drawSky(moment, t.value) }
}

/**
 * L'horloge d'animation : une boucle de vingt secondes, de 0 à 1.
 *
 * Rien ne bougeait. La neige, la pluie et les feuilles étaient semées une fois puis
 * repeintes à l'identique jusqu'à la minute suivante — de la poussière collée sur la
 * vitre. Il fallait une valeur qui change à chaque image.
 *
 * Une boucle plutôt qu'un temps qui monte indéfiniment, et des multiplicateurs ENTIERS
 * pour les vitesses : quand la boucle repasse de 1 à 0, `t * k` repasse de `k` à 0, donc
 * modulo 1 la position d'un flocon est inchangée. Aucun saut au raccord.
 */
@Composable
fun rememberSkyAnim(): State<Float> =
    rememberInfiniteTransition(label = "sky").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t"
    )

/** L'encre du texte posé À MÊME le ciel. Blanche la nuit, prune le jour. */
@Composable
fun skyInk(): Color = lerp(Pal.Ink, Color(0xFFF6F3FA), rememberSky().dark)

/** La même chose pour le texte secondaire. */
@Composable
fun skyMuted(): Color = lerp(Pal.Muted, Color(0xFFB9C0DC), rememberSky().dark)

/**
 * L'horloge du ciel, posée UNE SEULE FOIS tout en haut de l'app.
 *
 * [rememberSky] ne fait que lire : il est appelé une fois par fond d'écran et une fois par
 * couleur de texte, soit une dizaine de fois par page. S'il portait lui-même les boucles,
 * chaque appel ferait avancer l'horloge de l'atelier pour son compte — et le temps
 * défilerait dix fois trop vite, d'autant plus vite que la page est chargée.
 */
@Composable
fun SkyDriver() {
    val owner = LocalLifecycleOwner.current
    val labOn by SkyLab.active
    val speed by SkyLab.speed

    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                SkyClock.real.value = System.currentTimeMillis()
                delay(60_000)
            }
        }
    }

    // « Jours » avance d'une journée de CALENDRIER par seconde, et non de vingt-quatre
    // heures : c'est la seule façon de traverser mars et novembre sans que l'heure
    // affichée glisse d'une heure à chaque changement d'heure.
    LaunchedEffect(speed, labOn) {
        if (!labOn || speed == SkyLab.Speed.STOPPED) return@LaunchedEffect
        if (speed == SkyLab.Speed.DAYS) {
            while (true) { delay(1000); SkyLab.nudgeDays(1) }
        } else {
            while (true) { delay(50); SkyLab.instant.value += speed.perSecond / 20 }
        }
    }
}

/** L'heure vraie, rafraîchie par [SkyDriver] et lue par tout le monde. */
object SkyClock {
    val real = mutableStateOf(System.currentTimeMillis())
}

@Composable
fun rememberSky(): Sky.Moment {
    val labOn by SkyLab.active
    val labAt by SkyLab.instant
    val forced by SkyLab.forced
    val real by SkyClock.real

    val now = if (labOn) labAt else real
    return remember(now / 1000, forced, labOn) { Sky.moment(now, forced) }
}

// ---- les couleurs ----------------------------------------------------------

private val NightHigh = Color(0xFF0B1030)      // le zénith, presque noir
private val NightLow = Color(0xFF2A3A6E)       // vers l'horizon, plus clair
private val DayHigh = Color(0xFF3E9BE0)
private val DayLow = Color(0xFFAFE0F5)
private val FireHigh = Color(0xFF7B2D6B)       // le violet au-dessus d'un couchant
private val FireMid = Color(0xFFE0432F)        // le rouge
private val FireLow = Color(0xFFFF9A3C)        // l'orange au ras de l'horizon

private val SunCore = Color(0xFFFFE9A8)
private val SunRim = Color(0xFFFF7A2F)
private val MoonWhite = Color(0xFFF7F5EE)
private val MoonCrater = Color(0xFFD5D2C6)
private val StarWhite = Color(0xFFFFFFFF)

private val AuroraGreen = Color(0xFF3BFFA8)
private val AuroraCyan = Color(0xFF35D9F0)
private val AuroraViolet = Color(0xFFB06BFF)

private val LeafInks = listOf(
    Color(0xFFE06A1E), Color(0xFFC2371A), Color(0xFFE8A62E), Color(0xFF9C3D1F)
)

/**
 * Le sol de la saison — en pop, pas en documentaire.
 *
 * Les verts forestiers et le brun de terre étaient justes et tristes. Ici le printemps est
 * un vert tendre, l'été un vert vif, l'automne un corail plutôt qu'une boue, et l'hiver
 * une neige bleu-lilas au lieu d'un blanc d'hôpital. La saison doit se lire comme une
 * humeur, pas comme un relevé.
 */
private fun groundColor(s: Sky.Season) = when (s) {
    Sky.Season.WINTER -> Color(0xFFF2EEFF)
    Sky.Season.SPRING -> Color(0xFF9BE08A)
    Sky.Season.SUMMER -> Color(0xFF5FD08A)
    Sky.Season.AUTUMN -> Color(0xFFE8925A)
}

private fun treeColor(s: Sky.Season) = when (s) {
    Sky.Season.WINTER -> Color(0xFF2E6B63)
    Sky.Season.SPRING -> Color(0xFF3FA55C)
    Sky.Season.SUMMER -> Color(0xFF2E8B57)
    Sky.Season.AUTUMN -> Color(0xFFC2512A)
}

/** Le voile de saison posé sur le ciel : c'est lui qui donne l'ambiance. */
private fun seasonTint(s: Sky.Season): Pair<Color, Float> = when (s) {
    Sky.Season.WINTER -> Color(0xFFCFC3FF) to 0.30f      // lilas glacé
    Sky.Season.SPRING -> Color(0xFFFFC7E8) to 0.24f      // rose de floraison
    Sky.Season.SUMMER -> Color(0xFFFFE29A) to 0.22f      // or
    Sky.Season.AUTUMN -> Color(0xFFFFB07A) to 0.28f      // corail
}

/**
 * Les deux couleurs du ciel, du zénith vers l'horizon.
 *
 * Le bas s'embrase près du lever et du coucher : c'est là que la lumière rase l'atmosphère
 * et que le rouge apparaît en vrai. Le haut reste bleu, ce qui donne le dégradé
 * violet-rouge-orange qu'on voit dehors, au lieu d'un aplat orange uniforme.
 */
private fun skyColors(m: Sky.Moment): Pair<Color, Color> {
    val day = 1f - m.dark
    val high = lerp(NightHigh, DayHigh, day)
    val low = lerp(NightLow, DayLow, day)

    // L'embrasement est maximal juste au moment où le soleil touche l'horizon, et retombe
    // vite de part et d'autre.
    val fire = (1f - abs(m.dark - 0.62f) / 0.34f).coerceIn(0f, 1f)
    var h = high
    var l = low
    if (fire > 0f) {
        h = lerp(h, FireHigh, fire * 0.8f)
        l = lerp(l, FireLow, fire)
    }
    // Le voile de saison, appliqué en dernier et à moitié la nuit : une teinte de saison
    // sur du bleu nuit ne ferait que le délaver en gris.
    val (tint, amount) = seasonTint(m.season)
    val k = amount * (1f - m.dark * 0.6f)
    return lerp(h, tint, k * 0.55f) to lerp(l, tint, k)
}

// ---- le dessin -------------------------------------------------------------

private fun DrawScope.drawSky(m: Sky.Moment, t: Float) {
    val h = size.height
    val horizon = h * 0.40f
    val (high, low) = skyColors(m)

    // Le ciel jusqu'à l'horizon, puis un fondu court vers le fond de l'app.
    drawRect(
        Brush.verticalGradient(
            0f to high,
            (horizon / h) * 0.72f to lerp(high, low, 0.55f),
            horizon / h to low,
            (horizon / h) + 0.16f to Pal.Mist,
            1f to Pal.Mist,
            startY = 0f, endY = h
        )
    )

    val sky = Rect(0f, 0f, size.width, horizon)

    // Tout ce qui est au-dessus de l'horizon y est découpé : c'est ce qui permet au
    // soleil d'en SORTIR au lieu d'être posé dessus tout fait.
    clipRect(0f, 0f, size.width, horizon) {
        if (m.dark > 0.45f) stars(m, sky)
        if (m.aurora > 0f) aurora(m, sky)
        moon(m, sky)
        sun(m, sky)
        clouds(m, sky)
        if (m.rainbow) rainbow(sky)
        // Pas sous condition de nuit : l'atelier peut l'imposer, et un bouton qui ne fait
        // rien de visible est un bouton cassé. Naturellement, `Sky.moment` ne la déclenche
        // déjà que la nuit.
        if (m.shootingStar > 0f) shootingStar(m, sky, t)
    }

    ground(m, horizon, t)
    // Les visiteurs passent DEVANT les arbres et derrière ce qui tombe : ils appartiennent
    // au paysage, pas au ciel.
    visitors(m, horizon, t)

    when (m.falling) {
        Sky.Falling.RAIN -> rain(m, horizon, t)
        Sky.Falling.SNOW -> snow(m, horizon, t)
        Sky.Falling.LEAVES -> leaves(m, horizon, t)
        Sky.Falling.NONE -> Unit
    }
}

/** La course : de gauche à droite, et la hauteur suit un vrai arc de cercle. */
private fun arcPos(sky: Rect, t: Float): Offset = Offset(
    sky.width * (0.08f + 0.84f * t),
    sky.bottom - sin(t * PI).toFloat() * sky.height * 0.82f
)

private fun DrawScope.sun(m: Sky.Moment, sky: Rect) {
    if (m.sunT < -0.14f || m.sunT > 1.14f) return
    val p = arcPos(sky, m.sunT)
    val r = sky.height * 0.11f

    // La halo grossit et rougit quand le soleil rase l'horizon.
    val low = (1f - sin(m.sunT * PI).toFloat()).coerceIn(0f, 1f)
    val glow = lerp(SunCore, SunRim, low)
    drawCircle(glow.copy(alpha = 0.30f * (0.5f + low)), r * (3.4f + low * 2.5f), p)
    drawCircle(glow.copy(alpha = 0.42f), r * 1.9f, p)
    drawCircle(lerp(SunCore, SunRim, low * 0.8f), r, p)
}

/**
 * La lune : blanche, et pas un deuxième soleil.
 *
 * Elle était dorée et de la même taille que le soleil, ce qui les rendait presque
 * indistinguables. Blanc froid, plus petite, avec trois mers grises — le peu de détail
 * suffit à ce qu'on la reconnaisse au premier regard.
 */
private fun DrawScope.moon(m: Sky.Moment, sky: Rect) {
    if (m.moonT < -0.14f || m.moonT > 1.14f) return
    val p = arcPos(sky, m.moonT)
    val r = sky.height * 0.085f

    val illum = 1f - abs(m.moon - 0.5f) * 2f      // 0 nouvelle .. 1 pleine

    // Une nouvelle lune ne se voit pas. Pas de disque, pas de halo, rien — c'est la
    // définition même d'une nouvelle lune.
    if (illum < 0.04f) return

    drawCircle(MoonWhite.copy(alpha = 0.16f * illum), r * 2.6f, p)

    // Le décalage de la morsure suit la part ÉCLAIRÉE, et non son complément.
    //
    // C'était inversé, et l'erreur ne se voyait qu'aux extrêmes : à la nouvelle lune,
    // `1 - illum` valait 1, la morsure partait à deux rayons de là — donc elle ne mordait
    // rien du tout et le disque entier restait, une pleine lune le soir précisément où il
    // ne devait rien y avoir. Au premier quartier, la moitié enlevée était la mauvaise.
    val shift = r * 2f * illum
    val side = if (m.moon < 0.5f) 1f else -1f

    val disc = Path().apply { addOval(Rect(p.x - r, p.y - r, p.x + r, p.y + r)) }
    val bite = Path().apply {
        addOval(
            Rect(p.x - r + side * shift, p.y - r * 1.04f, p.x + r + side * shift, p.y + r * 1.04f)
        )
    }
    val shape = if (illum > 0.97f) disc else Path().apply {
        op(disc, bite, PathOperation.Difference)
    }
    drawPath(shape, MoonWhite)

    // Les mers, découpées dans la même forme : sur un croissant, seules celles qui
    // tombent sur la partie éclairée doivent se voir.
    clipPath(shape) {
        listOf(
            Triple(-0.30f, -0.24f, 0.30f), Triple(0.20f, 0.06f, 0.24f),
            Triple(-0.12f, 0.36f, 0.18f)
        ).forEach { (dx, dy, rr) ->
            drawCircle(
                MoonCrater.copy(alpha = 0.75f), r * rr,
                Offset(p.x + r * dx, p.y + r * dy)
            )
        }
    }
}

/** Des points nets et blancs, avec quelques-uns plus gros. Pas des taches pâles. */
private fun DrawScope.stars(m: Sky.Moment, sky: Rect) {
    val rng = Random(31)
    val strength = ((m.dark - 0.45f) / 0.4f).coerceIn(0f, 1f)
    repeat(90) {
        val x = rng.nextFloat() * sky.width
        val y = rng.nextFloat() * sky.height * 0.94f
        val big = rng.nextFloat() < 0.14f
        val r = if (big) 1.8f + rng.nextFloat() * 1.4f else 0.7f + rng.nextFloat() * 1.1f
        // Elles s'effacent près de l'horizon, comme sous la lueur de la ville.
        val a = strength * (0.95f - (y / sky.height) * 0.55f) * (0.55f + rng.nextFloat() * 0.45f)
        if (big) drawCircle(StarWhite.copy(alpha = a * 0.35f), r * 2.6f, Offset(x, y))
        drawCircle(StarWhite.copy(alpha = a.coerceIn(0f, 1f)), r, Offset(x, y))
    }
}

/**
 * L'aurore : verte et cyan, franche, et qui ÉCLAIRE.
 *
 * Elle était à seize pour cent d'opacité, ce qui donnait un voile qu'on ne distinguait
 * pas du ciel. Une vraie aurore est la chose la plus lumineuse de la nuit : des rideaux
 * verts saturés, un halo qui déteint sur le haut du ciel, du violet sur les franges.
 */
private fun DrawScope.aurora(m: Sky.Moment, sky: Rect) {
    val a = m.aurora
    // Le halo général : c'est lui qui donne l'impression que le ciel est éclairé.
    drawRect(
        Brush.verticalGradient(
            0f to AuroraGreen.copy(alpha = 0.10f * a),
            0.55f to AuroraGreen.copy(alpha = 0.24f * a),
            1f to Color.Transparent,
            startY = 0f, endY = sky.height
        )
    )

    listOf(
        Triple(AuroraGreen, 0.10f, 0.62f),
        Triple(AuroraCyan, 0.26f, 0.50f),
        Triple(AuroraViolet, 0.02f, 0.34f),
        Triple(AuroraGreen, 0.42f, 0.44f)
    ).forEachIndexed { i, (color, top, height) ->
        // Chaque rideau est une bande verticale ondulée qui s'éteint vers le bas.
        val path = Path()
        val y0 = sky.height * top
        path.moveTo(0f, y0)
        var x = 0f
        while (x <= sky.width) {
            val w = sin((x / sky.width * 2.2f + i * 0.9f) * PI).toFloat()
            path.lineTo(x, y0 + w * sky.height * 0.10f)
            x += sky.width / 28f
        }
        path.lineTo(sky.width, y0 + sky.height * height)
        path.lineTo(0f, y0 + sky.height * height)
        path.close()

        clipPath(path) {
            drawRect(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.78f * a),
                    0.45f to color.copy(alpha = 0.34f * a),
                    1f to Color.Transparent,
                    startY = y0, endY = y0 + sky.height * height
                )
            )
        }
    }
}

/**
 * Les nuages, et leur couleur au lever et au coucher.
 *
 * C'est le détail qui fait le ciel du Québec en octobre : les nuages prennent le rose, le
 * rouge et le violet par en dessous pendant que le haut du ciel est encore bleu. Ils sont
 * donc peints à partir des couleurs de l'heure, pas en gris.
 */
private fun DrawScope.clouds(m: Sky.Moment, sky: Rect) {
    val fire = (1f - abs(m.dark - 0.62f) / 0.34f).coerceIn(0f, 1f)
    val day = 1f - m.dark

    val body = when {
        fire > 0.15f -> lerp(Color(0xFFFF9EC4), FireMid, fire * 0.55f)
        else -> lerp(Color(0xFF2B3765), Color.White, day)
    }
    val under = when {
        fire > 0.15f -> lerp(Color(0xFF8E4BA8), FireMid, fire * 0.5f)
        else -> lerp(Color(0xFF1B2447), Color(0xFFD8E6F2), day)
    }

    // Semés sur la journée : le ciel n'a pas les mêmes nuages tous les jours, mais il
    // garde les siens toute la journée.
    val rng = Random(m.day * 53 + 7)
    val count = 3 + rng.nextInt(3)
    repeat(count) {
        val cx = rng.nextFloat() * sky.width
        val cy = sky.height * (0.20f + rng.nextFloat() * 0.62f)
        val w = sky.width * (0.20f + rng.nextFloat() * 0.26f)
        val hh = w * 0.30f
        val alpha = 0.55f + rng.nextFloat() * 0.35f

        // Le dessous d'abord, décalé : c'est lui qui donne le relief au couchant.
        listOf(-0.30f to 0.55f, 0f to 0.75f, 0.32f to 0.5f).forEach { (dx, s) ->
            drawOval(
                under.copy(alpha = alpha * 0.85f),
                Offset(cx + w * dx - w * s / 2f, cy - hh * s / 2f + hh * 0.34f),
                Size(w * s, hh * s)
            )
        }
        listOf(-0.28f to 0.6f, 0.02f to 0.85f, 0.30f to 0.55f).forEach { (dx, s) ->
            drawOval(
                body.copy(alpha = alpha),
                Offset(cx + w * dx - w * s / 2f, cy - hh * s / 2f),
                Size(w * s, hh * s)
            )
        }
    }
}

/**
 * L'étoile filante, qui file vraiment.
 *
 * Elle avait disparu du dessin en même temps que le reste du bas du fichier, ce qui
 * explique qu'aucun bouton ne la montrait. Et même avant, elle ne bougeait pas : une
 * traînée figée pendant vingt minutes n'est pas une étoile filante, c'est une rayure.
 *
 * Elle traverse maintenant en une seconde et demie, puis laisse cinq secondes de ciel
 * vide avant la suivante. C'est le silence entre deux passages qui fait qu'on la guette.
 */
private fun DrawScope.shootingStar(m: Sky.Moment, sky: Rect, t: Float) {
    val rng = Random(m.day * 7 + 11)

    // Trois passages par boucle de vingt secondes, et la traînée n'occupe que le premier
    // quart de chacun.
    val cycle = (t * 3f + rng.nextFloat()) % 1f
    if (cycle > 0.26f) return
    val p = cycle / 0.26f

    val fromX = sky.width * (rng.nextFloat() * 0.5f - 0.05f)
    val fromY = sky.height * (0.05f + rng.nextFloat() * 0.22f)
    val travel = sky.width * 0.55f

    val head = Offset(fromX + travel * p, fromY + travel * 0.42f * p)
    val tail = Offset(head.x - travel * 0.26f, head.y - travel * 0.11f)

    // Elle s'allume vite et s'éteint doucement, comme une vraie.
    val a = (if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f).coerceIn(0f, 1f) *
        m.shootingStar

    drawLine(
        StarWhite.copy(alpha = 0.75f * a), head, tail,
        strokeWidth = 3f, cap = StrokeCap.Round
    )
    drawCircle(StarWhite.copy(alpha = 0.35f * a), 7f, head)
    drawCircle(StarWhite.copy(alpha = a), 2.6f, head)
}

private fun DrawScope.rainbow(sky: Rect) {
    val colors = listOf(
        Color(0xFFE8402F), Color(0xFFF08A2E), Color(0xFFF0D93E),
        Color(0xFF4FBF6A), Color(0xFF3B87D9), Color(0xFF8B5FD0)
    )
    val cx = sky.width * 0.5f
    val cy = sky.bottom + sky.height * 0.5f
    colors.forEachIndexed { i, c ->
        val r = sky.height * (0.72f + i * 0.06f)
        drawArc(
            color = c.copy(alpha = 0.42f),
            startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
            style = Stroke(width = sky.height * 0.05f)
        )
    }
}

// ---- le sol, les arbres, et ce qui tombe -----------------------------------

/** L'inclinaison d'un arbre sous le vent, en pixels au sommet. */
private fun windSway(t: Float, wind: Float, seed: Float): Float =
    sin((t * 2f + seed) * 2f * PI).toFloat() * (2f + wind * 16f)

/**
 * Un conifère : trois étages, coiffés de neige l'hiver.
 *
 * Le triangle unique d'avant se lisait comme un cône de signalisation. Trois étages
 * décalés, un tronc, et la silhouette devient reconnaissable même en ombre chinoise.
 */
private fun DrawScope.conifer(
    x: Float, base: Float, h: Float, body: Color, snowy: Boolean, lean: Float
) {
    val w = h * 0.56f
    drawRect(
        lerp(body, Color.Black, 0.25f),
        Offset(x - w * 0.06f, base - h * 0.18f), Size(w * 0.12f, h * 0.19f)
    )
    for (i in 0..2) {
        val top = base - h * (0.42f + i * 0.27f)
        val half = w * (0.5f - i * 0.11f)
        val slide = lean * (i + 1) / 3f
        drawPath(
            Path().apply {
                moveTo(x + slide, top)
                lineTo(x + half, top + h * 0.34f)
                lineTo(x - half, top + h * 0.34f)
                close()
            },
            body
        )
        if (snowy) {
            drawPath(
                Path().apply {
                    moveTo(x + slide, top)
                    lineTo(x + half * 0.52f, top + h * 0.17f)
                    lineTo(x - half * 0.52f, top + h * 0.17f)
                    close()
                },
                Color(0xFFF4F9FF).copy(alpha = 0.9f)
            )
        }
    }
}

/** Un feuillu : tronc, deux branches, et une couronne en trois touffes. */
private fun DrawScope.broadleaf(
    x: Float, base: Float, h: Float, wood: Color, leaf: Color?, lean: Float
) {
    val trunk = h * 0.10f
    drawPath(
        Path().apply {
            moveTo(x - trunk / 2f, base)
            lineTo(x - trunk * 0.28f + lean, base - h * 0.62f)
            lineTo(x + trunk * 0.28f + lean, base - h * 0.62f)
            lineTo(x + trunk / 2f, base)
            close()
        },
        wood
    )
    val fork = h * 0.05f
    drawLine(
        wood, Offset(x + lean * 0.6f, base - h * 0.5f),
        Offset(x - h * 0.2f + lean, base - h * 0.68f), fork
    )
    drawLine(
        wood, Offset(x + lean * 0.6f, base - h * 0.46f),
        Offset(x + h * 0.2f + lean, base - h * 0.66f), fork
    )

    if (leaf == null) return                        // l'hiver les laisse nus
    val cy = base - h * 0.78f
    listOf(-0.24f to 0.44f, 0.02f to 0.58f, 0.26f to 0.42f).forEach { (dx, s) ->
        drawCircle(leaf, h * s * 0.5f, Offset(x + h * dx + lean, cy + h * (0.5f - s) * 0.2f))
    }
}

/**
 * Le sol et sa rangée d'arbres, semés sur la journée.
 *
 * La rangée change tous les jours : ni les mêmes essences, ni les mêmes hauteurs, ni les
 * mêmes écarts. C'est ce qui fait qu'un matin ne ressemble pas au précédent alors que
 * rien d'autre n'a bougé — et ça ne coûte qu'une graine.
 */
private fun DrawScope.ground(m: Sky.Moment, horizon: Float, t: Float) {
    val night = m.dark
    val g = lerp(groundColor(m.season), Color(0xFF141B33), night * 0.72f)
    val wood = lerp(Color(0xFF5A3A22), Color(0xFF0C1226), night * 0.8f)
    val depth = size.height * 0.075f

    val rng = Random(m.day * 131 + 7)
    var x = -size.width * 0.05f
    var i = 0
    while (x < size.width * 1.05f) {
        val h = depth * (1.1f + rng.nextFloat() * 2.1f)
        val lean = windSway(t, m.wind, i * 0.7f)
        val conifers = when (m.season) {
            Sky.Season.WINTER -> 0.85f
            Sky.Season.SUMMER -> 0.35f
            else -> 0.45f
        }
        if (rng.nextFloat() < conifers) {
            conifer(
                x, horizon + 3f, h,
                lerp(treeColor(m.season), Color(0xFF0C1226), night * 0.78f),
                m.season == Sky.Season.WINTER, lean
            )
        } else {
            val canopy = when (m.season) {
                Sky.Season.WINTER -> null
                Sky.Season.SPRING -> Color(0xFF7ACB6B)
                Sky.Season.SUMMER -> Color(0xFF2F8F45)
                Sky.Season.AUTUMN -> LeafInks[rng.nextInt(LeafInks.size)]
            }
            broadleaf(
                x, horizon + 3f, h, wood,
                canopy?.let { lerp(it, Color(0xFF0C1226), night * 0.78f) }, lean
            )
        }
        x += depth * (0.9f + rng.nextFloat() * 1.5f)
        i++
    }

    drawRect(g, Offset(0f, horizon), Size(size.width, depth))

    // L'hiver accumule : des congères irrégulières sur la ligne d'horizon. Sans elles, la
    // neige tombait sur un sol qui n'en gardait aucune trace.
    if (m.season == Sky.Season.WINTER) {
        val r2 = Random(m.day * 17 + 3)
        repeat(5) {
            val cx = r2.nextFloat() * size.width
            val w = size.width * (0.16f + r2.nextFloat() * 0.22f)
            drawOval(
                Color(0xFFF7FBFF).copy(alpha = 0.95f),
                Offset(cx - w / 2f, horizon - depth * 0.22f),
                Size(w, depth * 0.7f)
            )
        }
    }

    drawRect(
        Brush.verticalGradient(
            0f to g, 1f to Pal.Mist,
            startY = horizon + depth * 0.55f, endY = horizon + depth * 2.4f
        ),
        Offset(0f, horizon + depth * 0.55f),
        Size(size.width, depth * 1.9f)
    )

    // Les guirlandes de l'hiver, qui respirent doucement.
    if (m.season == Sky.Season.WINTER) {
        val bulbs = listOf(
            Color(0xFFFF5A5A), Color(0xFFFFD24A), Color(0xFF5AE08A), Color(0xFF5AB8FF)
        )
        val r3 = Random(404)
        repeat(26) { k ->
            val bx = r3.nextFloat() * size.width
            val by = horizon - depth * r3.nextFloat() * 1.5f
            val c = bulbs[r3.nextInt(bulbs.size)]
            val pulse = 0.55f + 0.45f * sin((t * 3f + k * 0.4f) * 2f * PI).toFloat()
            drawCircle(c.copy(alpha = 0.30f * pulse), 5.5f, Offset(bx, by))
            drawCircle(c.copy(alpha = (0.6f + 0.4f * pulse).coerceAtMost(1f)), 1.9f, Offset(bx, by))
        }
    }

    if (m.halloween) pumpkins(m, horizon, depth, t)
}

/** Trois citrouilles allumées, la dernière semaine d'octobre. */
private fun DrawScope.pumpkins(m: Sky.Moment, horizon: Float, depth: Float, t: Float) {
    val rng = Random(m.day * 71 + 13)
    repeat(3) { k ->
        val cx = size.width * (0.12f + rng.nextFloat() * 0.76f)
        val r = depth * (0.30f + rng.nextFloat() * 0.16f)
        val cy = horizon + depth * 0.42f
        // La bougie vacille : c'est ce tremblement qui fait la citrouille allumée plutôt
        // qu'un dessin de citrouille.
        val flicker = 0.7f + 0.3f * sin((t * 7f + k) * 2f * PI).toFloat()

        drawCircle(Color(0xFFFF9A2E).copy(alpha = 0.28f * flicker), r * 2.6f, Offset(cx, cy))
        listOf(-0.55f to 0.72f, 0f to 1f, 0.55f to 0.72f).forEach { (dx, s) ->
            drawOval(
                Color(0xFFE0761C),
                Offset(cx + r * dx - r * s * 0.62f, cy - r * s),
                Size(r * s * 1.24f, r * s * 2f)
            )
        }
        drawRect(
            Color(0xFF4E7A3A),
            Offset(cx - r * 0.10f, cy - r * 1.32f), Size(r * 0.20f, r * 0.34f)
        )
        val glow = Color(0xFFFFD24A).copy(alpha = flicker.coerceIn(0f, 1f))
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.42f, cy - r * 0.10f); lineTo(cx - r * 0.16f, cy - r * 0.10f)
                lineTo(cx - r * 0.29f, cy - r * 0.50f); close()
            },
            glow
        )
        drawPath(
            Path().apply {
                moveTo(cx + r * 0.42f, cy - r * 0.10f); lineTo(cx + r * 0.16f, cy - r * 0.10f)
                lineTo(cx + r * 0.29f, cy - r * 0.50f); close()
            },
            glow
        )
        drawPath(
            Path().apply {
                moveTo(cx - r * 0.44f, cy + r * 0.24f); lineTo(cx + r * 0.44f, cy + r * 0.24f)
                lineTo(cx + r * 0.20f, cy + r * 0.66f); lineTo(cx, cy + r * 0.34f)
                lineTo(cx - r * 0.20f, cy + r * 0.66f); close()
            },
            glow
        )
    }
}

/**
 * La position d'une particule qui tombe pour de bon.
 *
 * `t * speed` monte, le modulo la fait réapparaître en haut. Les vitesses sont ENTIÈRES
 * pour que le raccord de la boucle soit invisible : quand `t` repasse de 1 à 0, `t*k`
 * repasse d'un multiple de 1 à 0, et modulo 1 la particule n'a pas bougé.
 */
private fun fall(t: Float, speed: Int, seed: Float): Float = (t * speed + seed) % 1f

private fun DrawScope.rain(m: Sky.Moment, horizon: Float, t: Float) {
    val rng = Random(4242)
    val band = horizon * 1.7f
    val drift = 0.12f + m.wind * 0.9f
    val n = (34 + m.fall * 60f + if (m.storm) 40f else 0f).toInt()

    repeat(n) {
        val lane = rng.nextFloat()
        val speed = 3 + rng.nextInt(3)
        val p = fall(t, speed, rng.nextFloat())
        val y = p * band
        val x = ((lane + p * drift) % 1f) * size.width
        val len = (14f + rng.nextFloat() * 20f) * (1f + m.wind)
        drawLine(
            Color(0xFFBFD8EE).copy(alpha = (0.6f * (1f - y / band)).coerceAtLeast(0f)),
            Offset(x, y), Offset(x - len * drift, y + len),
            strokeWidth = 2f, cap = StrokeCap.Round
        )
    }

    // L'éclair : quatre battements très courts par boucle, jamais aux mêmes instants d'un
    // jour à l'autre. Un flash permanent serait une lampe ; c'est sa brièveté qui frappe.
    if (m.storm) {
        val seed = Random(m.day * 31 + 9).nextFloat()
        val f = (t * 4f + seed) % 1f
        if (f < 0.045f) {
            val k = 1f - f / 0.045f
            drawRect(Color.White.copy(alpha = 0.5f * k))
            val bx = size.width * (0.2f + seed * 0.6f)
            drawPath(
                Path().apply {
                    moveTo(bx, 0f)
                    lineTo(bx - horizon * 0.09f, horizon * 0.34f)
                    lineTo(bx + horizon * 0.04f, horizon * 0.36f)
                    lineTo(bx - horizon * 0.07f, horizon * 0.82f)
                },
                Color(0xFFFFF6C4).copy(alpha = k),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }
    }
}

private fun DrawScope.snow(m: Sky.Moment, horizon: Float, t: Float) {
    val rng = Random(777)
    val band = horizon * 1.8f
    val blizzard = m.storm
    val drift = 0.05f + m.wind * 0.8f + if (blizzard) 0.8f else 0f
    val n = (36 + m.fall * 60f + if (blizzard) 90f else 0f).toInt()

    repeat(n) {
        val lane = rng.nextFloat()
        val speed = 1 + rng.nextInt(3)
        val p = fall(t, speed, rng.nextFloat())
        val y = p * band
        // Le flocon oscille en descendant : c'est ce balancement qui distingue la neige de
        // la pluie, bien plus que la forme du grain.
        val wobble = sin((p * 3f + lane * 6f) * 2f * PI).toFloat() * (10f + m.wind * 26f)
        val x = ((lane + p * drift) % 1f) * size.width + wobble
        val r = (2f + rng.nextFloat() * 3.4f) * if (blizzard) 0.8f else 1f
        val a = (0.9f * (1f - y / band)).coerceAtLeast(0f)
        drawCircle(Color.White.copy(alpha = a * 0.3f), r * 2.2f, Offset(x, y))
        drawCircle(Color.White.copy(alpha = a), r, Offset(x, y))
    }

    // La poudrerie : des voiles horizontaux qui balaient l'écran.
    if (blizzard) {
        repeat(7) { k ->
            val p = fall(t, 2, k * 0.13f)
            val y = (k / 7f) * band
            drawOval(
                Color.White.copy(alpha = 0.14f),
                Offset(-size.width * 0.3f + p * size.width * 1.6f, y),
                Size(size.width * 0.55f, horizon * 0.05f)
            )
        }
    }
}

private fun DrawScope.leaves(m: Sky.Moment, horizon: Float, t: Float) {
    val rng = Random(1010)
    val band = horizon * 1.9f
    // Le vent d'automne emporte les feuilles DE CÔTÉ bien plus qu'il ne les fait tomber.
    val drift = 0.3f + m.wind * 1.6f
    val n = (18 + m.fall * 30f).toInt()

    repeat(n) {
        val lane = rng.nextFloat()
        val speed = 1 + rng.nextInt(2)
        val p = fall(t, speed, rng.nextFloat())
        val y = p * band
        val swirl = sin((p * 2f + lane * 5f) * 2f * PI).toFloat() * (18f + m.wind * 44f)
        val x = ((lane + p * drift) % 1f) * size.width + swirl
        val s = 5f + rng.nextFloat() * 7f
        val color = LeafInks[rng.nextInt(LeafInks.size)]
        val alpha = (0.95f * (1f - y / band)).coerceAtLeast(0f)
        // Elle tourne en tombant, d'autant plus vite qu'il vente.
        rotate(p * 360f * (1f + m.wind * 3f) + lane * 180f, Offset(x, y)) {
            drawPath(
                Path().apply {
                    moveTo(x, y - s)
                    quadraticBezierTo(x + s * 0.9f, y, x, y + s)
                    quadraticBezierTo(x - s * 0.9f, y, x, y - s)
                    close()
                },
                color.copy(alpha = alpha)
            )
            drawLine(
                color.copy(alpha = alpha * 0.5f),
                Offset(x, y - s), Offset(x, y + s), strokeWidth = 1f
            )
        }
    }
}

// ---- les visiteurs ---------------------------------------------------------
//
// Quinze petites choses qui passent, une ou deux par jour, tirées au sort sur la date et
// filtrées par la saison et l'heure. C'est ce qui fait qu'un mardi de mars n'a pas la même
// tête qu'un autre mardi de mars : le ciel, lui, ne change presque pas d'un jour à l'autre,
// alors qu'un chevreuil qui traverse, ça se remarque.
//
// Elles vivent sur la ligne d'horizon ou juste au-dessus, jamais dans la zone du contenu.

private enum class Visitor {
    CABIN, DEER, BUTTERFLY, FROG, GEESE, RABBIT, FIREFLIES, SNOWMAN,
    BLOSSOMS, FOX, KITE, BALLOON, BATS, SQUIRREL, OWL
}

/**
 * Qui passe aujourd'hui.
 *
 * Le chalet et le bonhomme de neige sont des DÉCORS : ils restent toute la saison, parce
 * qu'une cabane qui disparaît un jour sur deux ne serait pas une cabane. Le reste sont des
 * passages, un ou deux par jour.
 */
private fun visitorsFor(m: Sky.Moment): List<Visitor> {
    val out = mutableListOf<Visitor>()
    val night = m.dark > 0.6f

    if (m.season == Sky.Season.WINTER) {
        out += Visitor.CABIN
        if (Random(m.day * 53 + 1).nextFloat() < 0.55f) out += Visitor.SNOWMAN
    }
    if (m.season == Sky.Season.SPRING) out += Visitor.BLOSSOMS

    val pool = buildList {
        when (m.season) {
            Sky.Season.WINTER -> { add(Visitor.DEER); add(Visitor.FOX); add(Visitor.OWL) }
            Sky.Season.SPRING -> {
                add(Visitor.BUTTERFLY); add(Visitor.FROG); add(Visitor.RABBIT)
                if (m.wind > 0.35f) add(Visitor.KITE)
            }
            Sky.Season.SUMMER -> {
                add(Visitor.BUTTERFLY); add(Visitor.BALLOON); add(Visitor.FROG)
                if (night) add(Visitor.FIREFLIES)
            }
            Sky.Season.AUTUMN -> {
                add(Visitor.GEESE); add(Visitor.SQUIRREL); add(Visitor.DEER)
                if (m.halloween && night) add(Visitor.BATS)
            }
        }
        if (night) { add(Visitor.OWL); remove(Visitor.BUTTERFLY) }
    }
    if (pool.isNotEmpty()) {
        val rng = Random(m.day * 787 + 5)
        out += pool[rng.nextInt(pool.size)]
        // Une seconde visite un jour sur trois : deux tous les jours, ce serait un zoo.
        if (rng.nextFloat() < 0.34f) out += pool[rng.nextInt(pool.size)]
    }
    return out.distinct()
}

private fun DrawScope.visitors(m: Sky.Moment, horizon: Float, t: Float) {
    val night = m.dark
    visitorsFor(m).forEachIndexed { i, v ->
        val seed = Random(m.day * 97 + i * 31)
        when (v) {
            Visitor.CABIN -> cabin(m, horizon, t, night)
            Visitor.SNOWMAN -> snowman(horizon, seed, night)
            Visitor.BLOSSOMS -> blossoms(horizon, m, t)
            Visitor.DEER -> deer(horizon, t, seed, night)
            Visitor.FOX -> fox(horizon, t, seed, night)
            Visitor.RABBIT -> rabbit(horizon, t, seed, night)
            Visitor.SQUIRREL -> squirrel(horizon, t, seed, night)
            Visitor.FROG -> frog(horizon, t, seed, night)
            Visitor.BUTTERFLY -> butterfly(horizon, t, seed)
            Visitor.GEESE -> geese(horizon, t, seed, night)
            Visitor.BATS -> bats(horizon, t, seed)
            Visitor.OWL -> owl(horizon, seed, night, t)
            Visitor.KITE -> kite(horizon, t, seed, m.wind)
            Visitor.BALLOON -> balloon(horizon, t, seed)
            Visitor.FIREFLIES -> fireflies(horizon, t, seed)
        }
    }
}

private fun dim(c: Color, night: Float) = lerp(c, Color(0xFF141B33), night * 0.7f)

/** Le chalet : toit enneigé, fenêtre allumée, fumée qui monte. Tout l'hiver. */
private fun DrawScope.cabin(m: Sky.Moment, horizon: Float, t: Float, night: Float) {
    val rng = Random(m.day / 90 + 7)                 // il ne déménage pas chaque matin
    val w = size.width * 0.17f
    val x = size.width * (0.06f + rng.nextFloat() * 0.16f)
    val h = w * 0.62f
    val base = horizon + 2f

    drawRect(dim(Color(0xFF8A5A3C), night), Offset(x, base - h), Size(w, h))
    // Le toit dépasse des deux côtés, sinon la maison lit comme une boîte.
    drawPath(
        Path().apply {
            moveTo(x - w * 0.14f, base - h)
            lineTo(x + w / 2f, base - h * 1.62f)
            lineTo(x + w * 1.14f, base - h)
            close()
        },
        dim(Color(0xFF6B3F2A), night)
    )
    drawPath(
        Path().apply {
            moveTo(x - w * 0.14f, base - h)
            lineTo(x + w / 2f, base - h * 1.62f)
            lineTo(x + w * 1.14f, base - h)
            lineTo(x + w * 1.14f, base - h * 1.06f)
            lineTo(x - w * 0.14f, base - h * 1.06f)
            close()
        },
        Color(0xFFFAFCFF)
    )
    // La fenêtre chaude : c'est elle, et rien d'autre, qui rend une cabane accueillante.
    val glow = 0.75f + 0.25f * sin(t * 6f * 2f * PI).toFloat()
    drawRect(
        Color(0xFFFFC65A).copy(alpha = 0.30f * glow),
        Offset(x + w * 0.18f, base - h * 0.86f), Size(w * 0.46f, h * 0.56f)
    )
    drawRect(
        Color(0xFFFFD98A).copy(alpha = (0.75f + 0.25f * glow).coerceAtMost(1f)),
        Offset(x + w * 0.26f, base - h * 0.78f), Size(w * 0.30f, h * 0.40f)
    )

    // La cheminée et sa fumée, qui monte en s'élargissant.
    val cx = x + w * 0.82f
    drawRect(dim(Color(0xFF6B3F2A), night), Offset(cx, base - h * 1.46f), Size(w * 0.12f, h * 0.42f))
    repeat(4) { k ->
        val p = (t * 2f + k * 0.25f) % 1f
        val py = base - h * 1.5f - p * h * 1.5f
        drawCircle(
            Color(0xFFEFEFF6).copy(alpha = 0.36f * (1f - p)),
            w * (0.05f + p * 0.10f),
            Offset(cx + w * 0.06f + sin(p * 3f * PI).toFloat() * w * 0.08f, py)
        )
    }
}

/** Un bonhomme de neige, deux boules, un nez de carotte. */
private fun DrawScope.snowman(horizon: Float, rng: Random, night: Float) {
    val x = size.width * (0.55f + rng.nextFloat() * 0.35f)
    val r = size.height * 0.018f
    val base = horizon + 2f
    val snow = lerp(Color.White, Color(0xFF2A3560), night * 0.55f)
    drawCircle(snow, r * 1.5f, Offset(x, base - r * 1.5f))
    drawCircle(snow, r, Offset(x, base - r * 3.6f))
    drawCircle(Color(0xFF2B2B33), r * 0.16f, Offset(x - r * 0.34f, base - r * 3.8f))
    drawCircle(Color(0xFF2B2B33), r * 0.16f, Offset(x + r * 0.34f, base - r * 3.8f))
    drawPath(
        Path().apply {
            moveTo(x, base - r * 3.5f)
            lineTo(x + r * 0.9f, base - r * 3.3f)
            lineTo(x, base - r * 3.1f)
            close()
        },
        Color(0xFFE8873A)
    )
    // L'écharpe, la seule tache de couleur.
    drawRect(Color(0xFFE0435E), Offset(x - r * 0.9f, base - r * 2.9f), Size(r * 1.8f, r * 0.4f))
}

/** Un tapis de fleurs qui respirent, tout le printemps. */
private fun DrawScope.blossoms(horizon: Float, m: Sky.Moment, t: Float) {
    val rng = Random(m.day * 29 + 3)
    val petals = listOf(Color(0xFFFF9EC4), Color(0xFFFFD1E8), Color(0xFFFFF3A8), Color(0xFFD6B0FF))
    repeat(14) { k ->
        val x = rng.nextFloat() * size.width
        val y = horizon + size.height * (0.008f + rng.nextFloat() * 0.05f)
        val r = size.height * 0.0055f
        val sway = sin((t * 2f + k * 0.6f) * 2f * PI).toFloat() * r * 0.6f
        val c = petals[rng.nextInt(petals.size)]
        repeat(5) { i ->
            val a = i * 2.0 * PI / 5.0
            drawCircle(
                c, r * 0.62f,
                Offset(x + sway + (cos(a) * r).toFloat(), y + (sin(a) * r).toFloat())
            )
        }
        drawCircle(Color(0xFFFFE066), r * 0.5f, Offset(x + sway, y))
    }
}

/** Un chevreuil qui traverse : corps, cou, tête, et des bois. */
private fun DrawScope.deer(horizon: Float, t: Float, rng: Random, night: Float) {
    val p = (t + rng.nextFloat()) % 1f
    val dir = if (rng.nextBoolean()) 1f else -1f
    val x = if (dir > 0) p * size.width * 1.2f - size.width * 0.1f
    else size.width * 1.1f - p * size.width * 1.2f
    val h = size.height * 0.030f
    val base = horizon + 3f
    val body = dim(Color(0xFF9A6A42), night)
    // Les pattes alternent : sans ça, il glisse au lieu de marcher.
    val step = sin(p * 26f * 2f * PI).toFloat()

    drawOval(body, Offset(x - h * 0.9f, base - h * 1.5f), Size(h * 1.8f, h * 0.85f))
    listOf(-0.6f to step, -0.3f to -step, 0.4f to -step, 0.7f to step).forEach { (dx, s) ->
        drawLine(
            body, Offset(x + h * dx, base - h * 0.8f),
            Offset(x + h * dx + s * h * 0.22f, base), strokeWidth = h * 0.13f
        )
    }
    drawLine(
        body, Offset(x + dir * h * 0.75f, base - h * 1.3f),
        Offset(x + dir * h * 1.2f, base - h * 2.2f), strokeWidth = h * 0.24f
    )
    drawOval(
        body,
        Offset(x + dir * h * 1.05f - h * 0.28f, base - h * 2.5f), Size(h * 0.56f, h * 0.4f)
    )
    val antler = dim(Color(0xFFC9A57A), night)
    listOf(-0.12f, 0.12f).forEach { o ->
        val ax = x + dir * h * 1.15f + o * h
        drawLine(antler, Offset(ax, base - h * 2.45f), Offset(ax + dir * h * 0.1f, base - h * 3.1f), strokeWidth = h * 0.08f)
        drawLine(antler, Offset(ax + dir * h * 0.05f, base - h * 2.8f), Offset(ax - dir * h * 0.2f, base - h * 3.0f), strokeWidth = h * 0.07f)
    }
}

/** Un renard : plus bas, plus long, et une queue épaisse à bout blanc. */
private fun DrawScope.fox(horizon: Float, t: Float, rng: Random, night: Float) {
    val p = (t * 2f + rng.nextFloat()) % 1f
    val dir = if (rng.nextBoolean()) 1f else -1f
    val x = if (dir > 0) p * size.width * 1.2f - size.width * 0.1f
    else size.width * 1.1f - p * size.width * 1.2f
    val h = size.height * 0.020f
    val base = horizon + 3f
    val body = dim(Color(0xFFE07A32), night)
    val step = sin(p * 34f * 2f * PI).toFloat()

    drawOval(body, Offset(x - h, base - h * 1.2f), Size(h * 2f, h * 0.7f))
    listOf(-0.7f to step, 0.6f to -step).forEach { (dx, s) ->
        drawLine(body, Offset(x + h * dx, base - h * 0.6f), Offset(x + h * dx + s * h * 0.2f, base), strokeWidth = h * 0.14f)
    }
    // La queue, presque aussi longue que lui.
    drawOval(body, Offset(x - dir * h * 1.9f, base - h * 1.5f), Size(h * 1.1f, h * 0.5f))
    drawOval(Color.White.copy(alpha = 0.9f), Offset(x - dir * h * 2.1f, base - h * 1.45f), Size(h * 0.35f, h * 0.4f))
    drawOval(body, Offset(x + dir * h * 0.75f, base - h * 1.9f), Size(h * 0.7f, h * 0.55f))
    drawPath(
        Path().apply {
            moveTo(x + dir * h * 0.9f, base - h * 1.85f)
            lineTo(x + dir * h * 1.0f, base - h * 2.3f)
            lineTo(x + dir * h * 1.2f, base - h * 1.8f)
            close()
        },
        body
    )
}

/** Un lapin qui fait des bonds : c'est la trajectoire en arcs qui le désigne. */
private fun DrawScope.rabbit(horizon: Float, t: Float, rng: Random, night: Float) {
    val p = (t * 2f + rng.nextFloat()) % 1f
    val x = p * size.width * 1.15f - size.width * 0.08f
    val h = size.height * 0.016f
    val hop = abs(sin(p * 9f * PI).toFloat()) * h * 1.6f
    val base = horizon + 3f - hop
    val body = dim(Color(0xFFD8CFC4), night)

    drawOval(body, Offset(x - h * 0.8f, base - h), Size(h * 1.6f, h * 0.9f))
    drawOval(body, Offset(x + h * 0.4f, base - h * 1.6f), Size(h * 0.75f, h * 0.65f))
    listOf(-0.05f, 0.25f).forEach { o ->
        drawOval(body, Offset(x + h * (0.55f + o), base - h * 2.5f), Size(h * 0.22f, h * 1f))
    }
    drawCircle(Color.White.copy(alpha = 0.95f), h * 0.28f, Offset(x - h * 0.85f, base - h * 0.7f))
}

/** Un écureuil, arrêté, la queue en point d'interrogation. */
private fun DrawScope.squirrel(horizon: Float, t: Float, rng: Random, night: Float) {
    val x = size.width * (0.12f + rng.nextFloat() * 0.76f)
    val h = size.height * 0.015f
    val base = horizon + 3f
    val body = dim(Color(0xFF9A6B4A), night)
    // Il sursaute de temps en temps, sinon il n'a pas l'air vivant.
    val twitch = if (((t * 5f + rng.nextFloat()) % 1f) < 0.08f) h * 0.2f else 0f

    drawOval(body, Offset(x - h * 0.5f, base - h * 1.3f - twitch), Size(h, h * 1.3f))
    drawOval(body, Offset(x - h * 0.35f, base - h * 2.1f - twitch), Size(h * 0.7f, h * 0.6f))
    drawPath(
        Path().apply {
            moveTo(x - h * 0.5f, base - h * 0.3f)
            quadraticBezierTo(x - h * 1.9f, base - h * 1.2f, x - h * 0.9f, base - h * 2.4f)
            quadraticBezierTo(x - h * 1.5f, base - h * 1.3f, x - h * 0.3f, base - h * 0.6f)
            close()
        },
        body
    )
}

/** Une grenouille qui saute d'un bord à l'autre du bas de l'écran. */
private fun DrawScope.frog(horizon: Float, t: Float, rng: Random, night: Float) {
    val p = (t * 1.5f + rng.nextFloat()) % 1f
    val x = p * size.width * 1.1f - size.width * 0.05f
    val h = size.height * 0.013f
    val hop = abs(sin(p * 7f * PI).toFloat()) * h * 2.2f
    val base = horizon + size.height * 0.035f - hop
    val body = dim(Color(0xFF6FC65A), night)

    drawOval(body, Offset(x - h, base - h * 0.9f), Size(h * 2f, h * 1.1f))
    listOf(-0.5f, 0.5f).forEach { o ->
        drawCircle(body, h * 0.32f, Offset(x + h * o, base - h * 1.25f))
        drawCircle(Color.White, h * 0.16f, Offset(x + h * o, base - h * 1.3f))
        drawCircle(Color(0xFF1E2A18), h * 0.09f, Offset(x + h * o, base - h * 1.3f))
    }
    // Les pattes arrière repliées, tendues au sommet du saut.
    val kick = hop / (h * 2.2f)
    listOf(-1f, 1f).forEach { d ->
        drawLine(
            body, Offset(x + d * h * 0.7f, base - h * 0.3f),
            Offset(x + d * h * (1.1f + kick * 0.6f), base + h * 0.2f), strokeWidth = h * 0.22f
        )
    }
}

/** Un papillon, qui flotte en huit et bat des ailes. */
private fun DrawScope.butterfly(horizon: Float, t: Float, rng: Random) {
    val p = (t * 1.2f + rng.nextFloat()) % 1f
    val x = p * size.width * 1.1f - size.width * 0.05f
    val y = horizon * (0.55f + 0.22f * sin(p * 5f * 2f * PI).toFloat())
    val s = size.height * 0.010f
    val flap = 0.35f + 0.65f * abs(sin(t * 40f * PI).toFloat())
    val wings = listOf(Color(0xFFFF8FC6), Color(0xFFFFC46B), Color(0xFF9BD8FF))
    val c = wings[rng.nextInt(wings.size)]

    listOf(-1f, 1f).forEach { d ->
        drawOval(
            c.copy(alpha = 0.92f),
            Offset(x + d * s * 0.15f - if (d < 0) s * flap else 0f, y - s * 0.7f),
            Size(s * flap, s * 1.1f)
        )
        drawOval(
            c.copy(alpha = 0.75f),
            Offset(x + d * s * 0.1f - if (d < 0) s * flap * 0.7f else 0f, y),
            Size(s * flap * 0.7f, s * 0.75f)
        )
    }
    drawLine(Color(0xFF3A2A34), Offset(x, y - s * 0.6f), Offset(x, y + s * 0.5f), strokeWidth = s * 0.22f)
}

/** Des outardes en V, l'automne. Le son en moins. */
private fun DrawScope.geese(horizon: Float, t: Float, rng: Random, night: Float) {
    val p = (t * 0.6f + rng.nextFloat()) % 1f
    val lead = Offset(p * size.width * 1.3f - size.width * 0.15f, horizon * (0.16f + rng.nextFloat() * 0.2f))
    val s = size.height * 0.008f
    val ink = dim(Color(0xFF3A3F55), night * 0.4f)
    repeat(7) { k ->
        val row = (k + 1) / 2
        val side = if (k % 2 == 0) -1 else 1
        val gx = lead.x - row * s * 2.2f
        val gy = lead.y + row * s * 1.3f * side
        // Chaque oiseau bat un peu décalé : un vol synchrone au battement près est faux.
        val beat = sin((t * 26f + k * 0.5f) * 2f * PI).toFloat() * s * 0.55f
        drawLine(ink, Offset(gx - s, gy + beat), Offset(gx, gy - beat * 0.3f), strokeWidth = s * 0.34f)
        drawLine(ink, Offset(gx, gy - beat * 0.3f), Offset(gx + s, gy + beat), strokeWidth = s * 0.34f)
    }
}

/** Des chauves-souris, la semaine de l'Halloween seulement. */
private fun DrawScope.bats(horizon: Float, t: Float, rng: Random) {
    repeat(5) { k ->
        val p = (t * 1.4f + k * 0.19f + rng.nextFloat()) % 1f
        val x = p * size.width * 1.2f - size.width * 0.1f
        val y = horizon * (0.2f + 0.3f * sin((p * 3f + k) * 2f * PI).toFloat() + k * 0.05f)
        val s = size.height * 0.007f
        val flap = sin((t * 30f + k) * 2f * PI).toFloat() * s * 0.7f
        val ink = Color(0xFF241B33)
        drawPath(
            Path().apply {
                moveTo(x, y)
                quadraticBezierTo(x - s, y - s * 0.9f - flap, x - s * 2f, y + flap * 0.4f)
                quadraticBezierTo(x - s, y + s * 0.4f, x, y)
                quadraticBezierTo(x + s, y + s * 0.4f, x + s * 2f, y + flap * 0.4f)
                quadraticBezierTo(x + s, y - s * 0.9f - flap, x, y)
                close()
            },
            ink
        )
    }
}

/** Un hibou posé, qui cligne des yeux. La nuit uniquement. */
private fun DrawScope.owl(horizon: Float, rng: Random, night: Float, t: Float) {
    val x = size.width * (0.1f + rng.nextFloat() * 0.8f)
    val h = size.height * 0.020f
    val base = horizon - h * 0.2f
    val body = dim(Color(0xFF8A7A6A), night * 0.5f)

    drawOval(body, Offset(x - h * 0.6f, base - h * 1.6f), Size(h * 1.2f, h * 1.6f))
    listOf(-0.35f, 0.35f).forEach { o ->
        drawPath(
            Path().apply {
                moveTo(x + h * o - h * 0.2f, base - h * 1.45f)
                lineTo(x + h * o + h * 0.05f, base - h * 1.95f)
                lineTo(x + h * o + h * 0.28f, base - h * 1.45f)
                close()
            },
            body
        )
    }
    // Le clignement : les yeux se ferment un bref instant, à intervalles irréguliers.
    val open = ((t * 4f + rng.nextFloat()) % 1f) > 0.06f
    listOf(-0.28f, 0.28f).forEach { o ->
        drawCircle(Color(0xFFF2E2B8), h * 0.28f, Offset(x + h * o, base - h * 1.25f))
        if (open) drawCircle(Color(0xFF2B2118), h * 0.14f, Offset(x + h * o, base - h * 1.25f))
    }
    drawPath(
        Path().apply {
            moveTo(x, base - h * 1.18f); lineTo(x - h * 0.09f, base - h * 1.02f)
            lineTo(x + h * 0.09f, base - h * 1.02f); close()
        },
        Color(0xFFE0A044)
    )
}

/** Un cerf-volant, les jours de vent au printemps. */
private fun DrawScope.kite(horizon: Float, t: Float, rng: Random, wind: Float) {
    val drift = sin((t * 1.4f + rng.nextFloat()) * 2f * PI).toFloat()
    val x = size.width * (0.5f + drift * 0.32f)
    val y = horizon * (0.24f + 0.12f * sin((t * 2.3f) * 2f * PI).toFloat())
    val s = size.height * 0.020f
    val tilt = drift * (8f + wind * 22f)

    rotate(tilt, Offset(x, y)) {
        drawPath(
            Path().apply {
                moveTo(x, y - s); lineTo(x + s * 0.7f, y)
                lineTo(x, y + s * 1.3f); lineTo(x - s * 0.7f, y); close()
            },
            Color(0xFFFF6FA8)
        )
        drawPath(
            Path().apply {
                moveTo(x, y - s); lineTo(x + s * 0.7f, y); lineTo(x, y + s * 1.3f); close()
            },
            Color(0xFFFFC46B)
        )
        // La queue à nœuds, qui ondule.
        repeat(4) { k ->
            val ky = y + s * (1.5f + k * 0.55f)
            val kx = x + sin((t * 6f + k) * 2f * PI).toFloat() * s * 0.35f
            drawCircle(Color(0xFF9BD8FF), s * 0.14f, Offset(kx, ky))
        }
    }
}

/** Une montgolfière qui traverse doucement, l'été. */
private fun DrawScope.balloon(horizon: Float, t: Float, rng: Random) {
    val p = (t * 0.35f + rng.nextFloat()) % 1f
    val x = p * size.width * 1.25f - size.width * 0.12f
    val y = horizon * (0.2f + 0.1f * sin(p * 4f * 2f * PI).toFloat())
    val r = size.height * 0.026f

    drawOval(Color(0xFFFF7BA8), Offset(x - r, y - r), Size(r * 2f, r * 2.3f))
    drawOval(Color(0xFFFFD166), Offset(x - r * 0.34f, y - r), Size(r * 0.68f, r * 2.3f))
    drawOval(Color(0xFF6FD3E0).copy(alpha = 0.85f), Offset(x - r, y - r), Size(r * 0.4f, r * 2.3f))
    drawLine(Color(0xFF6B4A32), Offset(x - r * 0.4f, y + r * 1.25f), Offset(x - r * 0.25f, y + r * 1.75f), strokeWidth = r * 0.08f)
    drawLine(Color(0xFF6B4A32), Offset(x + r * 0.4f, y + r * 1.25f), Offset(x + r * 0.25f, y + r * 1.75f), strokeWidth = r * 0.08f)
    drawRect(Color(0xFF9A6B3F), Offset(x - r * 0.3f, y + r * 1.7f), Size(r * 0.6f, r * 0.45f))
}

/** Des lucioles, les nuits d'été : elles s'allument et s'éteignent chacune à son rythme. */
private fun DrawScope.fireflies(horizon: Float, t: Float, rng: Random) {
    repeat(16) { k ->
        val bx = rng.nextFloat()
        val by = rng.nextFloat()
        val x = (bx + sin((t * 1.2f + k) * 2f * PI).toFloat() * 0.03f) * size.width
        val y = horizon * (0.55f + by * 0.5f) + sin((t * 1.7f + k * 2f) * 2f * PI).toFloat() * horizon * 0.03f
        val on = (0.5f + 0.5f * sin((t * 6f + k * 1.3f) * 2f * PI).toFloat())
        val glow = Color(0xFFFFF07A)
        drawCircle(glow.copy(alpha = 0.28f * on), 7f, Offset(x, y))
        drawCircle(glow.copy(alpha = on), 2.2f, Offset(x, y))
    }
}
