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

/** Le sol, qui dit la saison d'un seul coup d'œil. */
private fun groundColor(s: Sky.Season) = when (s) {
    Sky.Season.WINTER -> Color(0xFFEDF3FA)
    Sky.Season.SPRING -> Color(0xFF6FBF63)
    Sky.Season.SUMMER -> Color(0xFF3E9B4F)
    Sky.Season.AUTUMN -> Color(0xFFB5651F)
}

private fun treeColor(s: Sky.Season) = when (s) {
    Sky.Season.WINTER -> Color(0xFF1F5140)
    Sky.Season.SPRING -> Color(0xFF2F7A46)
    Sky.Season.SUMMER -> Color(0xFF215F32)
    Sky.Season.AUTUMN -> Color(0xFF8A3A12)
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
    if (fire <= 0f) return high to low
    return lerp(high, FireHigh, fire * 0.8f) to lerp(low, FireLow, fire)
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
    }

    ground(m, horizon, t)

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

    drawCircle(MoonWhite.copy(alpha = 0.16f), r * 2.6f, p)

    val illum = 1f - abs(m.moon - 0.5f) * 2f      // 0 nouvelle .. 1 pleine
    val shift = r * 2f * (1f - illum)
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
