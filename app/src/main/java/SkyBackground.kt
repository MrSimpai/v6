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
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
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
    val slow = rememberRareAnim()
    // `t.value` est lu DANS le lambda de dessin : seule la phase de dessin se réabonne,
    // donc l'animation ne recompose rien, elle repeint. C'est la différence entre un fond
    // animé et une app qui recompose soixante fois par seconde.
    return drawBehind { drawSky(moment, t.value, slow.value) }
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

/**
 * La deuxième horloge : deux minutes et demie, pour ce qui doit rester RARE.
 *
 * Le chevreuil vivait sur la boucle de vingt secondes, donc il retraversait l'écran trois
 * fois par minute, toujours dans le même sens. Ce n'était plus un chevreuil qui passe,
 * c'était un tapis roulant — et une chose qu'on voit à tous les coups ne fait plus rien.
 * Pareil pour l'éclair, qui claquait quatre fois par boucle, soit toutes les cinq
 * secondes : un orage n'éclaire pas au métronome.
 *
 * Deux horloges plutôt qu'une seule ralentie, parce que la neige et la pluie ont BESOIN
 * de la boucle courte : leurs vitesses sont des multiples entiers de `t`, et c'est ce qui
 * fait qu'aucun flocon ne saute au raccord. Rallonger cette boucle-là aurait obligé à
 * retoucher chaque vitesse de chaque particule.
 */
@Composable
fun rememberRareAnim(): State<Float> =
    rememberInfiniteTransition(label = "rare").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(150_000, easing = LinearEasing), RepeatMode.Restart),
        label = "slow"
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

private fun DrawScope.drawSky(m: Sky.Moment, t: Float, slow: Float) {
    val h = size.height
    val horizon = h * 0.40f
    val (high, low) = skyColors(m)
    // Le lieu du jour, tiré UNE fois et passé à toutes les couches : le relief, l'eau, la
    // végétation et la maison doivent décrire le même endroit. Chacune le retirant de son
    // côté, on finirait par avoir un chalet de montagne au bord d'un marais.
    val world = worldOf(m)

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
        if (m.aurora > 0f) aurora(m, sky, t, slow)
        moon(m, sky)
        sun(m, sky)
        clouds(m, sky)
        // Les montagnes ferment le ciel par le bas. Elles passent DEVANT les astres :
        // c'est ce qui fait que le soleil se lève de derrière la crête au lieu de sortir
        // d'une ligne droite, et ça règle d'un coup le fond plat de l'horizon.
        mountains(m, world, horizon, t)
        if (m.rainbow) rainbow(sky)
        // Pas sous condition de nuit : l'atelier peut l'imposer, et un bouton qui ne fait
        // rien de visible est un bouton cassé. Naturellement, `Sky.moment` ne la déclenche
        // déjà que la nuit.
        if (m.shootingStar > 0f) shootingStar(m, sky, t)
    }

    ground(m, world, horizon, t)
    // Les visiteurs passent DEVANT les arbres et derrière ce qui tombe : ils appartiennent
    // au paysage, pas au ciel.
    visitors(m, world, horizon, t, slow)

    when (m.falling) {
        Sky.Falling.RAIN -> rain(m, horizon, t, slow)
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
private fun DrawScope.aurora(m: Sky.Moment, sky: Rect, t: Float, slow: Float) {
    // Elle ne bougeait pas d'un pixel. Le pli de chaque rideau ne dépendait que de `x`,
    // donc l'aurore était une image fixe collée en haut du ciel — et une aurore fixe n'est
    // pas une aurore, c'est un papier peint. C'est le mouvement qui la rend vivante :
    // c'est même la SEULE chose que tout le monde en retient.
    //
    // Deux temps superposés. Le souffle court, sur la boucle de vingt secondes, fait
    // onduler les plis et glisser les voiles ; la houle longue, sur celle de deux minutes
    // et demie, fait enfler puis retomber l'ensemble. Une aurore qui n'aurait que le
    // battement rapide vibrerait comme un néon.
    //
    // Tous les multiplicateurs de `t` sont PAIRS : la phase avance alors d'un nombre
    // entier de tours pendant la boucle, et le raccord ne saute pas.
    val tau = 2f * PI.toFloat()
    val breathe = 0.84f + 0.16f * sin(tau * t).toFloat()
    val swell = 0.80f + 0.20f * sin(tau * slow).toFloat()
    val a = m.aurora * breathe * swell

    // Le halo général : c'est lui qui donne l'impression que le ciel est éclairé.
    drawRect(
        Brush.verticalGradient(
            0f to AuroraGreen.copy(alpha = 0.10f * a),
            0.55f to AuroraGreen.copy(alpha = 0.24f * a),
            1f to Color.Transparent,
            startY = 0f, endY = sky.height
        )
    )

    data class Curtain(val color: Color, val top: Float, val height: Float, val drift: Float, val freq: Float)
    listOf(
        Curtain(AuroraGreen, 0.10f, 0.62f, 2f, 1.1f),
        Curtain(AuroraCyan, 0.26f, 0.50f, -2f, 1.6f),
        Curtain(AuroraViolet, 0.02f, 0.34f, 4f, 0.8f),
        Curtain(AuroraGreen, 0.42f, 0.44f, -4f, 2.1f)
    ).forEachIndexed { i, cu ->
        val y0 = sky.height * cu.top
        val hgt = sky.height * cu.height
        // Chaque rideau dérive à sa propre vitesse et dans son propre sens : c'est le
        // décalage entre les voiles qui donne la profondeur. À vitesse commune, les
        // quatre bandes glisseraient en bloc comme un seul décor qui défile.
        fun fold(x: Float, harmonic: Float) =
            sin(tau * (x / sky.width * cu.freq + i * 0.17f + t * cu.drift)).toFloat() * 0.7f +
                sin(tau * (x / sky.width * cu.freq * 2.3f - i * 0.11f + t * cu.drift * harmonic)).toFloat() * 0.3f

        val path = Path()
        path.moveTo(0f, y0)
        var x = 0f
        val stepX = sky.width / 40f
        while (x <= sky.width) {
            path.lineTo(x, y0 + fold(x, 2f) * sky.height * 0.11f)
            x += stepX
        }
        // Le bas ondule AUSSI, sur une autre phase : un rideau dont seul le haut bouge se
        // lit comme une bannière tirée sur un fil.
        x = sky.width
        while (x >= 0f) {
            path.lineTo(x, y0 + hgt + fold(x, 3f) * sky.height * 0.06f)
            x -= stepX
        }
        path.close()

        clipPath(path) {
            drawRect(
                Brush.verticalGradient(
                    0f to cu.color.copy(alpha = 0.78f * a),
                    0.45f to cu.color.copy(alpha = 0.34f * a),
                    1f to Color.Transparent,
                    startY = y0, endY = y0 + hgt
                )
            )
            // Les raies verticales. Une vraie aurore est faite de fils parallèles qui
            // scintillent séparément, et c'est ce grain-là qui la distingue d'un dégradé
            // vert. Ils défilent lentement en travers du rideau.
            val rays = 22
            repeat(rays) { k ->
                val u = (k / rays.toFloat() + t * cu.drift * 0.25f + i * 0.13f) % 1f
                val rx = u * sky.width
                val glow = (0.35f + 0.65f * sin(tau * (k * 0.37f + t * 4f)).toFloat().let { it * it })
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.16f * a * glow),
                        0.5f to cu.color.copy(alpha = 0.20f * a * glow),
                        1f to Color.Transparent,
                        startY = y0, endY = y0 + hgt
                    ),
                    topLeft = Offset(rx, y0),
                    size = Size(sky.width / 90f, hgt)
                )
            }
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

/**
 * L'inclinaison d'un arbre sous le vent, en pixels au sommet.
 *
 * Seize pixels au maximum : un jour de tempête, les arbres frémissaient. C'était le
 * problème — le vent existait dans les données, il pliait la pluie de travers et lançait
 * la poudrerie, mais le paysage, lui, ne réagissait presque pas. Quarante-six pixels au
 * sommet d'un conifère de cent cinquante, ça se voit.
 *
 * Et il souffle par RAFALES. Un balancement d'amplitude constante est un métronome ; une
 * enveloppe lente par-dessus l'oscillation rapide donne la respiration du vent, qui monte,
 * couche tout, et retombe. Chaque arbre reçoit la rafale avec un léger décalage, comme
 * elle traverse vraiment une rangée.
 */
private fun windSway(t: Float, wind: Float, seed: Float): Float {
    val gust = 0.5f + 0.5f * sin((t + seed * 0.11f) * 2f * PI).toFloat()
    return sin((t * 2f + seed) * 2f * PI).toFloat() * (2f + wind * 44f) * (0.45f + gust * 0.55f)
}

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
private fun DrawScope.ground(m: Sky.Moment, world: World, horizon: Float, t: Float) {
    val night = m.dark
    val g = lerp(soilColor(m.season, world.land), Color(0xFF141B33), night * 0.72f)
    val depth = size.height * 0.075f

    // La végétation du jour. Ce n'est plus « la rangée d'arbres » : c'est un champ de
    // maïs, un verger, une érablière, un marais ou une prairie, selon où l'on est ce
    // matin-là. Une forêt tous les jours de l'année, c'est un seul endroit repeint
    // quatre fois — or c'est justement le LIEU qui devait changer.
    vegetation(m, world, horizon, depth, t)

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

    // La rivière passe par-dessus le sol et sous le fondu : elle est le premier plan du
    // paysage, et c'est là que vivent le canard, le huard et le castor.
    river(m, world, horizon, depth, t)

    drawRect(
        Brush.verticalGradient(
            0f to g, 1f to Pal.Mist,
            startY = horizon + depth * 1.04f, endY = horizon + depth * 2.6f
        ),
        Offset(0f, horizon + depth * 1.04f),
        Size(size.width, depth * 1.6f)
    )

    // Les guirlandes de l'hiver, qui respirent doucement.
    if (m.season == Sky.Season.WINTER) {
        val bulbs = listOf(
            Color(0xFFFF5A5A), Color(0xFFFFD24A), Color(0xFF5AE08A), Color(0xFF5AB8FF)
        )
        val r3 = Random(m.day * 149 + 11)
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

/** La terre elle-même. Un champ de maïs coupé n'a pas la couleur d'un pré. */
private fun soilColor(s: Sky.Season, land: Land): Color = when {
    s == Sky.Season.WINTER -> Color(0xFFF2EEFF)
    land == Land.CORN -> Color(0xFFDCB463)
    land == Land.MARSH -> Color(0xFF6FA875)
    land == Land.PUMPKIN -> Color(0xFFC98A4B)
    else -> groundColor(s)
}

/**
 * Ce qui pousse ici aujourd'hui.
 *
 * Chaque lieu a sa propre densité et sa propre façon d'occuper la ligne : la forêt est
 * irrégulière, le verger est aligné, le maïs est serré et uniforme, le marais est bas et
 * troué. C'est cette ORGANISATION, plus que la couleur, qui fait qu'on reconnaît un
 * endroit — un verger dessiné au hasard n'est qu'une forêt de pommiers.
 */
private fun DrawScope.vegetation(
    m: Sky.Moment, world: World, horizon: Float, depth: Float, t: Float
) {
    val night = m.dark
    val base = horizon + 3f
    val rng = Random(m.day * 131 + 7)
    val wood = lerp(Color(0xFF5A3A22), Color(0xFF0C1226), night * 0.8f)
    val winter = m.season == Sky.Season.WINTER

    when (world.land) {
        Land.FOREST -> {
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
                        x, base, h,
                        lerp(treeColor(m.season), Color(0xFF0C1226), night * 0.78f),
                        winter, lean
                    )
                } else {
                    broadleaf(x, base, h, wood, canopyColor(m, rng, night), lean)
                }
                x += depth * (0.9f + rng.nextFloat() * 1.5f)
                i++
            }
        }

        Land.BIRCH -> {
            // Les bouleaux poussent SERRÉS et droits, presque tous de la même hauteur.
            // C'est ce peuplement dense de troncs pâles qui fait la bouleaie, pas la
            // forme des feuilles — qu'on ne verrait de toute façon pas à cette taille.
            var x = -size.width * 0.04f
            var i = 0
            while (x < size.width * 1.04f) {
                val h = depth * (1.7f + rng.nextFloat() * 1.1f)
                birch(x, base, h, night, windSway(t, m.wind, i * 0.5f), if (winter) null else
                    lerp(
                        if (m.season == Sky.Season.AUTUMN) Color(0xFFF2C44A) else Color(0xFF8FD07A),
                        Color(0xFF0C1226), night * 0.78f
                    )
                )
                x += depth * (0.34f + rng.nextFloat() * 0.5f)
                i++
            }
        }

        Land.MAPLE -> {
            var x = -size.width * 0.06f
            var i = 0
            while (x < size.width * 1.06f) {
                val h = depth * (1.6f + rng.nextFloat() * 1.6f)
                maple(
                    x, base, h, wood, night, windSway(t, m.wind, i * 0.8f),
                    if (winter) null else lerp(
                        when (m.season) {
                            Sky.Season.AUTUMN -> LeafInks[rng.nextInt(LeafInks.size)]
                            Sky.Season.SPRING -> Color(0xFF8FD07A)
                            else -> Color(0xFF37944B)
                        },
                        Color(0xFF0C1226), night * 0.78f
                    )
                )
                // L'érablière du printemps est entaillée : chaudière et chalumeau.
                if (m.season == Sky.Season.SPRING && rng.nextFloat() < 0.4f) {
                    val bucket = lerp(Color(0xFFBFC6D2), Color(0xFF0C1226), night * 0.7f)
                    drawRoundRect(
                        bucket, Offset(x - depth * 0.10f, base - depth * 0.44f),
                        Size(depth * 0.20f, depth * 0.26f), CornerRadius(depth * 0.04f)
                    )
                    drawLine(
                        bucket, Offset(x, base - depth * 0.5f), Offset(x + depth * 0.09f, base - depth * 0.5f),
                        strokeWidth = depth * 0.05f
                    )
                }
                x += depth * (1.1f + rng.nextFloat() * 1.2f)
                i++
            }
        }

        Land.CORN -> cornField(m, horizon, depth, t, rng, night)

        Land.MEADOW -> {
            // Une prairie, c'est de l'herbe et DEUX arbres. La tentation serait d'en
            // mettre plus ; c'est le vide qui fait le pré.
            repeat(2 + rng.nextInt(2)) { k ->
                val x = size.width * (0.1f + rng.nextFloat() * 0.8f)
                val h = depth * (1.6f + rng.nextFloat() * 1.4f)
                broadleaf(x, base, h, wood, canopyColor(m, rng, night), windSway(t, m.wind, k * 1.3f))
            }
            grasses(m, horizon, depth, t, rng, night)
        }

        Land.ORCHARD -> {
            // ALIGNÉS, et à intervalle régulier : un verger est une chose plantée par
            // quelqu'un, et c'est la régularité qui le dit.
            val step = depth * 1.5f
            val start = -depth * (0.3f + rng.nextFloat() * 0.9f)
            var x = start
            var i = 0
            while (x < size.width + step) {
                val h = depth * (1.25f + (i % 3) * 0.10f)
                orchardTree(x, base, h, wood, m, night, windSway(t, m.wind, i * 0.6f))
                x += step
                i++
            }
        }

        Land.PUMPKIN -> {
            // Le champ de citrouilles : des vignes basses, et les citrouilles posées
            // dessus en rangs approximatifs.
            grasses(m, horizon, depth, t, rng, night)
            repeat(11) {
                val x = size.width * (0.02f + rng.nextFloat() * 0.96f)
                val r = depth * (0.16f + rng.nextFloat() * 0.16f)
                val y = base - r * 0.5f + rng.nextFloat() * depth * 0.2f
                val skin = lerp(Color(0xFFE07E2A), Color(0xFF0C1226), night * 0.75f)
                drawOval(skin, Offset(x - r * 1.25f, y - r), Size(r * 2.5f, r * 2f))
                drawOval(
                    lerp(skin, Color.Black, 0.18f),
                    Offset(x - r * 0.45f, y - r), Size(r * 0.9f, r * 2f)
                )
                drawLine(
                    lerp(Color(0xFF5F7A34), Color(0xFF0C1226), night * 0.75f),
                    Offset(x, y - r), Offset(x + r * 0.3f, y - r * 1.5f),
                    strokeWidth = r * 0.22f, cap = StrokeCap.Round
                )
            }
            repeat(3) { k ->
                val x = size.width * (0.12f + rng.nextFloat() * 0.76f)
                val h = depth * (1.5f + rng.nextFloat() * 1.2f)
                broadleaf(x, base, h, wood, canopyColor(m, rng, night), windSway(t, m.wind, k * 1.1f))
            }
        }

        Land.MARSH -> {
            // Bas, mou, troué : des quenouilles, quelques joncs, et rien de haut. Le
            // marais est le seul lieu où l'horizon reste presque nu, et c'est ce qui le
            // rend reconnaissable au premier coup d'œil.
            val stalk = lerp(Color(0xFF6E8F4A), Color(0xFF0C1226), night * 0.78f)
            val head = lerp(Color(0xFF7A4A2A), Color(0xFF0C1226), night * 0.78f)
            repeat(46) { k ->
                val x = size.width * rng.nextFloat()
                val h = depth * (0.5f + rng.nextFloat() * 0.85f)
                val lean = windSway(t, m.wind, k * 0.3f) * 0.5f
                drawLine(
                    stalk, Offset(x, base), Offset(x + lean, base - h),
                    strokeWidth = depth * 0.045f, cap = StrokeCap.Round
                )
                // Une quenouille sur trois : le reste sont des joncs nus, sinon la rangée
                // ressemble à une brosse à dents.
                if (k % 3 == 0 && !winter) {
                    drawRoundRect(
                        head, Offset(x + lean - depth * 0.05f, base - h - depth * 0.02f),
                        Size(depth * 0.10f, depth * 0.30f), CornerRadius(depth * 0.05f)
                    )
                }
            }
            repeat(2) { k ->
                val x = size.width * (0.1f + rng.nextFloat() * 0.8f)
                val h = depth * (1.4f + rng.nextFloat() * 1.0f)
                broadleaf(x, base, h, wood, canopyColor(m, rng, night), windSway(t, m.wind, k * 1.7f))
            }
        }
    }

    // La maison du jour, posée dans la végétation plutôt que devant : son pied est
    // couvert par la bande de sol qui vient juste après.
    world.house?.let { house(it, m, horizon, depth, t, night) }
    if (world.hive) beehive(m, horizon, depth, t, night)
}

/** La couleur d'une couronne selon la saison. L'hiver rend null : les branches sont nues. */
private fun canopyColor(m: Sky.Moment, rng: Random, night: Float): Color? = when (m.season) {
    Sky.Season.WINTER -> null
    Sky.Season.SPRING -> lerp(Color(0xFF7ACB6B), Color(0xFF0C1226), night * 0.78f)
    Sky.Season.SUMMER -> lerp(Color(0xFF2F8F45), Color(0xFF0C1226), night * 0.78f)
    Sky.Season.AUTUMN ->
        lerp(LeafInks[rng.nextInt(LeafInks.size)], Color(0xFF0C1226), night * 0.78f)
}

/** Des touffes d'herbe et quelques fleurs, qui se couchent sous la rafale. */
private fun DrawScope.grasses(
    m: Sky.Moment, horizon: Float, depth: Float, t: Float, rng: Random, night: Float
) {
    if (m.season == Sky.Season.WINTER) return
    val blade = lerp(Color(0xFF4E9A46), Color(0xFF0C1226), night * 0.78f)
    val base = horizon + 3f
    repeat(60) { k ->
        val x = size.width * rng.nextFloat()
        val h = depth * (0.22f + rng.nextFloat() * 0.4f)
        val lean = windSway(t, m.wind, k * 0.21f) * 0.6f
        drawPath(
            Path().apply {
                moveTo(x, base)
                quadraticBezierTo(x + lean * 0.4f, base - h * 0.6f, x + lean, base - h)
            },
            blade, style = Stroke(width = depth * 0.035f, cap = StrokeCap.Round)
        )
    }
    if (m.season == Sky.Season.SPRING || m.season == Sky.Season.SUMMER) {
        val petals = listOf(
            Color(0xFFFFD166), Color(0xFFFF8FC6), Color(0xFFFFFFFF), Color(0xFFB98FFF)
        )
        repeat(18) { k ->
            val x = size.width * rng.nextFloat()
            val h = depth * (0.24f + rng.nextFloat() * 0.32f)
            val lean = windSway(t, m.wind, k * 0.33f) * 0.6f
            drawLine(blade, Offset(x, base), Offset(x + lean, base - h), strokeWidth = depth * 0.03f)
            drawCircle(
                lerp(petals[rng.nextInt(petals.size)], Color(0xFF0C1226), night * 0.7f),
                depth * 0.055f, Offset(x + lean, base - h)
            )
        }
    }
}

/**
 * Un champ de maïs.
 *
 * Serré, régulier, et exactement de la même hauteur d'un bout à l'autre : c'est une
 * culture, pas une forêt. La houle est ce qui le rend vivant — le vent traverse un champ
 * de maïs en VAGUES, et le décalage de phase d'un rang à l'autre suffit à le montrer.
 */
private fun DrawScope.cornField(
    m: Sky.Moment, horizon: Float, depth: Float, t: Float, rng: Random, night: Float
) {
    val base = horizon + 3f
    val dry = m.season == Sky.Season.AUTUMN
    val stalk = lerp(
        if (dry) Color(0xFFC9A24E) else Color(0xFF5FA046), Color(0xFF0C1226), night * 0.78f
    )
    val leafC = lerp(
        if (dry) Color(0xFFB88C3C) else Color(0xFF74B95A), Color(0xFF0C1226), night * 0.78f
    )
    val cob = lerp(Color(0xFFE8C24A), Color(0xFF0C1226), night * 0.75f)
    val n = 54

    repeat(n) { k ->
        val x = size.width * (k + 0.5f + (rng.nextFloat() - 0.5f) * 0.4f) / n
        val h = depth * (1.5f + rng.nextFloat() * 0.35f)
        // La vague : la phase dépend de la POSITION, donc la rafale traverse le champ.
        val lean = windSway(t, m.wind, x / size.width * 3f)
        drawLine(
            stalk, Offset(x, base), Offset(x + lean, base - h),
            strokeWidth = depth * 0.05f, cap = StrokeCap.Round
        )
        // Deux feuilles retombantes de chaque côté : c'est la silhouette du plant.
        listOf(-1f, 1f).forEach { d ->
            drawPath(
                Path().apply {
                    moveTo(x + lean * 0.55f, base - h * 0.6f)
                    quadraticBezierTo(
                        x + lean * 0.8f + d * depth * 0.34f, base - h * 0.66f,
                        x + lean * 0.9f + d * depth * 0.42f, base - h * 0.3f
                    )
                    quadraticBezierTo(
                        x + lean * 0.7f + d * depth * 0.2f, base - h * 0.52f,
                        x + lean * 0.55f, base - h * 0.6f
                    )
                    close()
                },
                leafC
            )
        }
        // Un épi sur trois plants, jamais du même côté.
        if (k % 3 == 0) {
            drawRoundRect(
                cob,
                Offset(x + lean * 0.7f + depth * 0.02f, base - h * 0.72f),
                Size(depth * 0.09f, depth * 0.26f), CornerRadius(depth * 0.045f)
            )
        }
        // Le plumet au sommet.
        drawLine(
            leafC, Offset(x + lean, base - h),
            Offset(x + lean * 1.2f, base - h * 1.16f), strokeWidth = depth * 0.03f
        )
    }
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

private fun DrawScope.rain(m: Sky.Moment, horizon: Float, t: Float, slow: Float) {
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

    // L'éclair, sur l'horloge LENTE : deux coups par cycle de deux minutes et demie, soit
    // un peu plus d'un par minute. Il claquait quatre fois par boucle de vingt secondes,
    // c'est-à-dire toutes les cinq secondes — au métronome, et donc plus du tout comme un
    // orage. Ce qui fait peur dans un éclair, c'est de ne pas savoir quand il revient.
    //
    // Et il ne frappe pas d'un seul bloc : un vrai éclair papillote. Deux battements
    // séparés d'un souffle valent bien mieux qu'un flash propre.
    if (m.storm) {
        val seed = Random(m.day * 31 + 9).nextFloat()
        // Sauf à l'atelier. Un bouton « Tempête » qui oblige à fixer l'écran pendant une
        // minute avant de montrer quoi que ce soit est un bouton qu'on croit cassé — on
        // vient de le vivre avec l'étoile filante. Sous la manette, l'éclair reprend
        // l'horloge courte et claque tout de suite.
        val clock = if (SkyLab.forced.value.storm == true) t * 3f else slow * 2f
        val f = (clock + seed) % 1f
        val k = when {
            f < 0.030f -> 1f - f / 0.030f                  // le premier coup
            f in 0.052f..0.086f -> (1f - (f - 0.052f) / 0.034f) * 0.72f   // la réplique
            else -> 0f
        }
        if (k > 0f) {
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

// ---- le lieu du jour -------------------------------------------------------
//
// La rangée d'arbres changeait tous les matins, mais tout le reste était FIGÉ : les mêmes
// trois crêtes semées sur une constante, la même rivière à la même place, le même chalet
// tout l'hiver. Une seule couche bougeait, et les autres la contredisaient — on finissait
// par lire le décor comme un fond fixe avec des arbres qui gigotent.
//
// Maintenant l'endroit ENTIER se retire au sort chaque jour. Certains matins il n'y a pas
// de montagnes du tout, d'autres la rivière est à sec, souvent il n'y a personne pour
// habiter le paysage. C'est la rareté qui donne sa valeur à ce qui est là : un chalet
// qu'on voit tous les jours n'est plus qu'un motif du fond d'écran.

/** Ce qu'on traverse aujourd'hui. Une forêt, oui — mais pas seulement. */
internal enum class Land { FOREST, BIRCH, MAPLE, CORN, MEADOW, ORCHARD, PUMPKIN, MARSH }

/** Qui habite là, quand quelqu'un habite là. */
internal enum class House { CABIN, COTTAGE, HALLOWEEN }

internal class World(
    val land: Land,
    /** De zéro à trois crêtes. Zéro, c'est la plaine, et ça arrive. */
    val ridges: Int,
    /** La largeur de la rivière, en parts de la bande de sol. Zéro : elle est à sec. */
    val river: Float,
    val house: House?,
    val hive: Boolean
)

internal fun landsFor(season: Sky.Season): List<Land> = when (season) {
    Sky.Season.WINTER -> listOf(Land.FOREST, Land.BIRCH, Land.MEADOW, Land.MAPLE)
    Sky.Season.SPRING -> listOf(Land.FOREST, Land.MEADOW, Land.ORCHARD, Land.BIRCH, Land.MARSH)
    Sky.Season.SUMMER ->
        listOf(Land.FOREST, Land.CORN, Land.MEADOW, Land.ORCHARD, Land.MARSH, Land.BIRCH)
    Sky.Season.AUTUMN ->
        listOf(Land.FOREST, Land.CORN, Land.PUMPKIN, Land.MAPLE, Land.BIRCH, Land.MEADOW)
}

/**
 * Le tirage du jour.
 *
 * Tous les nombres sont pris d'un coup, dans l'ordre, avant d'être interprétés : si on
 * tirait au fil des `when`, chaque branche consommerait un nombre différent et le choix
 * du relief déplacerait la rivière. Ce genre de couplage est invisible jusqu'au jour où
 * on ajoute une condition et où tout le décor change sans raison.
 */
internal fun worldOf(m: Sky.Moment): World {
    val rng = Random(m.day * 2_246_827L + 17)
    val a = rng.nextFloat()
    val b = rng.nextFloat()
    val c = rng.nextFloat()
    val d = rng.nextFloat()
    val e = rng.nextFloat()

    val lands = landsFor(m.season)
    return World(
        land = lands[(a * lands.size).toInt().coerceAtMost(lands.size - 1)],
        ridges = when {
            b < 0.20f -> 0
            b < 0.42f -> 1
            b < 0.72f -> 2
            else -> 3
        },
        // Un tiers des jours à sec. Les bêtes d'eau le savent : voir un huard sur un pré
        // serait la seule chose de tout ce décor qui aurait vraiment l'air cassé.
        river = if (c < 0.32f) 0f else 0.55f + c * 0.85f,
        house = if (d < 0.45f) null else when (m.season) {
            Sky.Season.WINTER -> House.CABIN
            Sky.Season.AUTUMN -> House.HALLOWEEN
            else -> House.COTTAGE
        },
        hive = m.season != Sky.Season.WINTER && e < 0.34f
    )
}

// ---- le relief -------------------------------------------------------------
//
// L'horizon était une ligne droite : le ciel s'arrêtait net, le sol commençait, et la
// seule chose qui dépassait était une rangée d'arbres tous à la même distance. Aucun
// LOIN, donc aucune profondeur, et un décor sans profondeur reste un fond d'écran quoi
// qu'on y ajoute.
//
// Trois crêtes en retrait les unes des autres et une rivière au premier plan suffisent à
// creuser la scène. Elles ne changent pas d'un jour à l'autre — c'est le même endroit
// tous les matins — mais leur couleur, elle, suit l'heure et la saison.

private class Ridge(val seed: Int, val peaks: Int, val rise: Float, val haze: Float)

/**
 * Le fond de vallée : de zéro à trois lignes de sommets.
 *
 * Elles étaient semées sur les constantes 5, 11 et 23 — toujours les mêmes montagnes,
 * toujours au même endroit, alors que la rangée d'arbres devant, elle, changeait chaque
 * matin. Deux couches du même paysage qui ne suivent pas la même horloge, et c'est la
 * fixe qui gagne : l'œil s'accroche à ce qui ne bouge pas.
 *
 * Le nombre de crêtes fait partie du tirage. Zéro, c'est la plaine — et une plaine de
 * temps en temps est ce qui redonne du poids aux montagnes le lendemain.
 */
private fun DrawScope.mountains(m: Sky.Moment, world: World, horizon: Float, t: Float) {
    if (world.ridges == 0) return
    val (_, low) = skyColors(m)
    val stone = lerp(Color(0xFF6C6A96), Color(0xFF141A31), m.dark)
    // L'hiver descend la limite des neiges : c'est le signal de saison le plus lisible
    // qu'on puisse mettre sur une montagne.
    val snowLine = if (m.season == Sky.Season.WINTER) 0.44f else 0.76f
    val pick = Random(m.day * 5_407L + 3)
    // La dernière crête est la plus proche et la plus haute : on garde donc la FIN de la
    // liste, pas le début, sinon deux crêtes donneraient deux collines lointaines et un
    // premier plan vide.
    val all = listOf(
        Ridge(pick.nextInt(9_000), 7 + pick.nextInt(5), 0.22f + pick.nextFloat() * 0.10f, 0.62f),
        Ridge(pick.nextInt(9_000), 5 + pick.nextInt(4), 0.32f + pick.nextFloat() * 0.14f, 0.36f),
        Ridge(pick.nextInt(9_000), 3 + pick.nextInt(4), 0.44f + pick.nextFloat() * 0.20f, 0.15f)
    )

    all.takeLast(world.ridges).forEach { r ->
        val rng = Random(r.seed)
        // La brume aérienne : plus c'est loin, plus ça se confond avec le ciel. C'est le
        // seul truc qui fabrique vraiment la distance sur une image plate.
        val rock = lerp(stone, low, r.haze)
        val n = r.peaks * 2
        val pts = ArrayList<Offset>(n + 1)
        val high = ArrayList<Float>(n + 1)
        for (k in 0..n) {
            val x = -size.width * 0.06f + size.width * 1.12f * k / n
            val f = if (k % 2 == 1) 0.58f + rng.nextFloat() * 0.42f
            else 0.10f + rng.nextFloat() * 0.24f
            high += f
            pts += Offset(x, horizon - horizon * r.rise * f)
        }

        drawPath(
            Path().apply {
                moveTo(pts.first().x, horizon + 6f)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, horizon + 6f)
                close()
            },
            rock
        )

        // Le versant éclairé : la même crête décalée vers la droite, en plus clair. Une
        // montagne d'une seule teinte est une découpe de carton.
        drawPath(
            Path().apply {
                moveTo(pts.first().x, horizon + 6f)
                pts.forEach { lineTo(it.x + horizon * r.rise * 0.14f, it.y + horizon * r.rise * 0.05f) }
                lineTo(pts.last().x, horizon + 6f)
                close()
            },
            lerp(rock, low, 0.28f).copy(alpha = 0.55f)
        )

        // Les neiges éternelles, en calotte sous le sommet.
        for (k in 1 until n step 2) {
            if (high[k] < snowLine) continue
            val peak = pts[k]
            val drop = (peak.y - pts[k - 1].y) * 0.26f
            drawPath(
                Path().apply {
                    moveTo(peak.x, peak.y)
                    lineTo(peak.x - (peak.x - pts[k - 1].x) * 0.24f, peak.y - drop)
                    lineTo(peak.x - (peak.x - pts[k - 1].x) * 0.10f, peak.y - drop * 0.55f)
                    lineTo(peak.x + (pts[k + 1].x - peak.x) * 0.16f, peak.y - drop * 1.1f)
                    lineTo(peak.x + (pts[k + 1].x - peak.x) * 0.26f, peak.y - drop * 0.5f)
                    close()
                },
                lerp(Color(0xFFFBFCFF), low, r.haze * 0.7f).copy(alpha = 0.92f)
            )
        }
    }

    // La brume de fond de vallée, qui rampe et qui respire. C'est elle qui pose les
    // montagnes DERRIÈRE les arbres au lieu de les poser à côté.
    val drift = sin(2f * PI.toFloat() * t).toFloat()
    repeat(3) { k ->
        val y = horizon - horizon * (0.05f + k * 0.035f)
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to lerp(Color.White, low, 0.35f).copy(alpha = (0.16f - k * 0.04f) * (1f - m.dark * 0.5f))
            ),
            topLeft = Offset(drift * size.width * 0.02f * (k + 1), y),
            size = Size(size.width * 1.1f, horizon * 0.09f)
        )
    }
}

/**
 * La rivière, dans la bande de sol.
 *
 * Elle sert deux fois. D'abord elle RÉFLÉCHIT : la colonne tremblante sous le soleil ou
 * sous la lune est le seul endroit du décor où deux couches se répondent, et c'est ce qui
 * fait qu'on la lit comme de l'eau et pas comme une bande bleue. Ensuite elle donne un
 * territoire à des bêtes qui n'avaient nulle part où aller — le canard, le huard, le
 * castor et la libellule vivent tous ici.
 */
private fun DrawScope.river(m: Sky.Moment, world: World, horizon: Float, depth: Float, t: Float) {
    // Certains jours elle n'est pas là. Une rivière permanente finit par se lire comme
    // une bande décorative en bas de l'écran ; celle qui manque un matin sur trois se
    // remarque le matin où elle revient.
    if (world.river <= 0f) return
    val (_, low) = skyColors(m)
    // La largeur du jour, et la berge qui remonte avec elle.
    val span = world.river.coerceIn(0.5f, 1.4f)
    val top = horizon + depth * (0.94f - 0.44f * span)
    val bottom = horizon + depth * 0.88f
    val hgt = bottom - top
    val tau = 2f * PI.toFloat()
    val frozen = m.season == Sky.Season.WINTER

    // L'eau prend la couleur du ciel, assombrie : une rivière est un miroir avant d'être
    // bleue. Écrite en dur, elle serait bleue à minuit comme à midi.
    val water = lerp(lerp(low, Color(0xFF1B3A5C), 0.55f), Color(0xFF0A1226), m.dark * 0.7f)

    // La berge du fond ondule doucement.
    val bank = Path().apply {
        moveTo(0f, bottom)
        var x = 0f
        while (x <= size.width) {
            lineTo(x, top + sin(tau * (x / size.width * 1.3f + t)).toFloat() * hgt * 0.10f)
            x += size.width / 30f
        }
        lineTo(size.width, bottom)
        close()
    }

    clipPath(bank) {
        drawRect(
            Brush.verticalGradient(
                listOf(lerp(water, low, 0.32f), water), startY = top, endY = bottom
            ),
            topLeft = Offset(0f, top - hgt), size = Size(size.width, hgt * 2.2f)
        )

        // Le reflet de l'astre : une colonne qui tremble, faite de traits horizontaux
        // décalés. Un simple dégradé vertical ferait un projecteur, pas un reflet.
        val skyRect = Rect(0f, 0f, size.width, horizon)
        val sunUp = m.sunT in 0f..1f
        val ax = if (sunUp) arcPos(skyRect, m.sunT).x else arcPos(skyRect, m.moonT).x
        val glint = if (sunUp) Color(0xFFFFE9A8) else Color(0xFFEAF2FF)
        val power = if (sunUp) 1f else (0.35f + m.moon * 1.1f).coerceAtMost(1f)
        repeat(14) { k ->
            val u = k / 14f
            val y = top + hgt * (0.06f + u * 0.9f)
            // Chaque trait glisse à sa vitesse : c'est le décalage qui fait le tremblement.
            val wob = sin(tau * (t * (2f + k % 3) + k * 0.31f)).toFloat()
            val w = hgt * (2.6f - u * 1.4f) * (0.7f + 0.3f * wob)
            drawRoundRect(
                glint.copy(alpha = 0.30f * power * (1f - u * 0.6f)),
                Offset(ax - w / 2f + wob * hgt * 0.5f, y),
                Size(w, hgt * 0.07f),
                CornerRadius(hgt * 0.04f)
            )
        }

        // Les rides : des traits fins qui descendent lentement, comme le courant.
        val rng = Random(404)
        repeat(26) {
            val lane = rng.nextFloat()
            val speed = 1 + rng.nextInt(2)
            val u = (t * speed + lane) % 1f
            val y = top + hgt * u
            val w = size.width * (0.04f + rng.nextFloat() * 0.12f)
            drawLine(
                Color.White.copy(alpha = 0.13f * (1f - u) * (1f - m.dark * 0.5f)),
                Offset(lane * size.width, y),
                Offset(lane * size.width + w, y),
                strokeWidth = hgt * 0.045f, cap = StrokeCap.Round
            )
        }

        // L'hiver la prend en glace, sauf un chenal au milieu : une rivière entièrement
        // gelée n'est plus une rivière, c'est un trottoir.
        if (frozen) {
            val ice = lerp(Color(0xFFDCEBF7), Color(0xFF6E86B8), m.dark * 0.8f)
            listOf(0f to 0.34f, 0.68f to 1f).forEach { (x0, x1) ->
                drawRect(
                    ice.copy(alpha = 0.92f),
                    Offset(size.width * x0, top - hgt * 0.1f),
                    Size(size.width * (x1 - x0), hgt * 1.3f)
                )
            }
            val r2 = Random(88)
            repeat(7) {
                val cx = size.width * r2.nextFloat()
                drawLine(
                    ice.copy(alpha = 0.5f),
                    Offset(cx, top), Offset(cx + hgt * (r2.nextFloat() - 0.5f), bottom),
                    strokeWidth = 1.2f
                )
            }
        }
    }

    // La rive proche, un liseré de terre ou de neige.
    drawRect(
        lerp(groundColor(m.season), Color(0xFF141B33), m.dark * 0.72f),
        Offset(0f, bottom - 1f), Size(size.width, depth * 0.16f)
    )
}

// ---- les visiteurs ---------------------------------------------------------
//
// Quinze petites choses qui passent, une ou deux par jour, tirées au sort sur la date et
// filtrées par la saison et l'heure. C'est ce qui fait qu'un mardi de mars n'a pas la même
// tête qu'un autre mardi de mars : le ciel, lui, ne change presque pas d'un jour à l'autre,
// alors qu'un chevreuil qui traverse, ça se remarque.
//
// Elles vivent sur la ligne d'horizon ou juste au-dessus, jamais dans la zone du contenu.

internal enum class Visitor {
    DEER, BUTTERFLY, FROG, GEESE, RABBIT, FIREFLIES, SNOWMAN,
    BLOSSOMS, FOX, KITE, BALLOON, BATS, SQUIRREL, OWL,
    // La deuxième fournée. La rivière en a rendu la moitié possible : un canard, un
    // huard et un castor n'avaient tout simplement nulle part où exister avant.
    HEDGEHOG, RACCOON, DUCKS, LOON, CARDINAL, DRAGONFLY, SNAIL, BEAVER, CAT, HUMMINGBIRD,
    BEAR,
    // Les ÉVÉNEMENTS. Quelques jours par an chacun, et jamais deux le même soir.
    COMET, PLANE, LANTERNS
}

/**
 * La fenêtre de passage d'un visiteur.
 *
 * Une bête ne traverse pas en boucle : elle passe, et puis l'écran lui appartient de
 * nouveau. `share` est la part du demi-cycle pendant laquelle elle est là — le reste du
 * temps cette fonction rend `null`, et il n'y a rien à dessiner.
 *
 * Deux passages par cycle, en SENS OPPOSÉS. Le sens était tiré sur la date : le chevreuil
 * d'un mardi allait donc de droite à gauche toute la journée, à chacun de ses trois
 * passages par minute, et on finissait par conclure qu'il ne savait aller que dans ce
 * sens-là. En alternant à l'intérieur du cycle, celui qu'on voit revenir revient par
 * l'autre bord — ce qui est aussi, tout simplement, ce que font les vraies bêtes.
 */
private fun pass(slow: Float, share: Float, offset: Float): Pair<Float, Float>? {
    // À l'atelier, tout le monde sort. On y vient pour REGARDER les bêtes — vérifier
    // qu'elles savent se faire attendre, ça se fait sur l'app, pas sur le banc d'essai.
    val window = if (SkyLab.active.value) 0.94f else share
    val p = (slow + offset) % 1f
    val back = p >= 0.5f
    val q = (if (back) p - 0.5f else p) / 0.5f
    if (q >= window) return null
    val e = q / window
    // La progression est rendue DÉJÀ retournée : `e` va toujours du bord d'où la bête
    // arrive vers celui où elle sort. Sans ça, le miroir de [facing] lui met la tête à
    // gauche pendant qu'elle continue d'avancer vers la droite — elle marche à reculons.
    return (if (back) 1f - e else e) to (if (back) -1f else 1f)
}

/**
 * Dessine tourné vers la droite, puis retourne tout le bloc si la bête va vers la gauche.
 *
 * Chaque animal repositionnait à la main les quelques traits qui dépendent du sens — la
 * tête, la queue — en multipliant leurs décalages par `dir`. Tout le reste, lui, ne
 * bougeait pas : les pattes gardaient leur écartement d'origine, le poitrail restait du
 * même côté, et un chevreuil qui marchait vers la gauche avait la tête à gauche et le
 * corps encore tourné à droite. C'est exactement ce qui donnait l'impression que la
 * géométrie était fausse : elle ne l'était que dans un sens sur deux.
 *
 * Un miroir autour du point d'appui règle le problème une fois pour toutes, et surtout il
 * ne peut plus se désynchroniser quand on retouche le dessin.
 */
private fun DrawScope.facing(pivot: Offset, dir: Float, body: DrawScope.() -> Unit) {
    if (dir > 0f) body() else withTransform({ scale(-1f, 1f, pivot) }) { body() }
}

/**
 * Qui passe aujourd'hui.
 *
 * Le bonhomme de neige et les pétales sont des DÉCORS : posés le matin, ils restent la
 * journée. Le reste sont des passages, un ou deux par jour.
 *
 * La maison, elle, ne passe plus par ici : elle appartient au LIEU, pas aux visiteurs, et
 * c'est [worldOf] qui décide s'il y en a une aujourd'hui et laquelle.
 */
internal fun visitorsFor(m: Sky.Moment, world: World): List<Visitor> {
    val out = mutableListOf<Visitor>()
    val night = m.dark > 0.6f
    // Rien sur l'eau les jours où il n'y a pas d'eau. Un huard posé sur un pré serait la
    // seule chose de tout ce décor qui aurait vraiment l'air cassée.
    val wet = world.river > 0f

    if (m.season == Sky.Season.WINTER && Random(m.day * 53 + 1).nextFloat() < 0.55f) {
        out += Visitor.SNOWMAN
    }
    if (m.season == Sky.Season.SPRING) out += Visitor.BLOSSOMS

    // Des réserves LARGES. À trois candidats par saison, le chevreuil sortait un jour sur
    // trois en automne et un jour sur trois en hiver — plus de la moitié de l'année. Une
    // bête qu'on croise à ce rythme-là cesse d'être une rencontre et devient un décor.
    val pool = buildList {
        when (m.season) {
            Sky.Season.WINTER -> {
                add(Visitor.DEER); add(Visitor.FOX); add(Visitor.OWL)
                add(Visitor.RABBIT); add(Visitor.SQUIRREL)
                add(Visitor.CARDINAL); add(Visitor.CAT); add(Visitor.BEAR)
            }
            Sky.Season.SPRING -> {
                add(Visitor.BUTTERFLY); add(Visitor.FROG); add(Visitor.RABBIT)
                add(Visitor.SQUIRREL); add(Visitor.FOX)
                add(Visitor.HEDGEHOG); add(Visitor.CAT); add(Visitor.BEAR)
                if (wet) add(Visitor.DUCKS)
                if (m.wind > 0.35f) add(Visitor.KITE)
                if (m.falling == Sky.Falling.RAIN) add(Visitor.SNAIL)
            }
            Sky.Season.SUMMER -> {
                add(Visitor.BUTTERFLY); add(Visitor.BALLOON); add(Visitor.FROG)
                add(Visitor.RABBIT); add(Visitor.SQUIRREL)
                add(Visitor.HUMMINGBIRD); add(Visitor.CAT)
                if (wet) { add(Visitor.DRAGONFLY); add(Visitor.LOON); add(Visitor.BEAVER) }
                if (night) add(Visitor.FIREFLIES)
                if (m.falling == Sky.Falling.RAIN) add(Visitor.SNAIL)
            }
            Sky.Season.AUTUMN -> {
                add(Visitor.GEESE); add(Visitor.SQUIRREL); add(Visitor.DEER)
                add(Visitor.FOX); add(Visitor.RABBIT)
                add(Visitor.HEDGEHOG); add(Visitor.BEAR)
                if (wet) { add(Visitor.DUCKS); add(Visitor.BEAVER) }
                if (m.halloween && night) add(Visitor.BATS)
            }
        }
        if (night) {
            add(Visitor.OWL); add(Visitor.RACCOON)
            remove(Visitor.BUTTERFLY); remove(Visitor.HUMMINGBIRD); remove(Visitor.DRAGONFLY)
            remove(Visitor.BEAR)
        }
    }
    if (pool.isNotEmpty()) {
        val rng = Random(m.day * 787 + 5)
        out += pool[rng.nextInt(pool.size)]
        // Une seconde visite un jour sur trois : deux tous les jours, ce serait un zoo.
        if (rng.nextFloat() < 0.34f) out += pool[rng.nextInt(pool.size)]
    }

    // Les événements, tirés à part et VOLONTAIREMENT rares : quelques jours par an
    // chacun. Ils ne remplacent pas la visite du jour, ils s'y ajoutent — c'est ce qui
    // fait qu'un soir de comète reste un soir de comète et pas un soir sans chevreuil.
    //
    // Un seul à la fois : deux prodiges le même soir, et aucun des deux n'en est un.
    val ev = Random(m.day * 911 + 13).nextFloat()
    out += when {
        night && ev < 0.020f -> Visitor.COMET          // une vingtaine de soirs par siècle
        night && ev < 0.055f -> Visitor.PLANE
        ev < 0.085f && m.dark > 0.35f -> Visitor.LANTERNS
        else -> return out.distinct()
    }
    return out.distinct()
}

private fun DrawScope.visitors(
    m: Sky.Moment, world: World, horizon: Float, t: Float, slow: Float
) {
    val night = m.dark
    visitorsFor(m, world).forEachIndexed { i, v ->
        val seed = Random(m.day * 97 + i * 31)
        // Chaque visiteur a sa fenêtre à lui, décalée des autres : deux bêtes qui
        // traversent ensemble se lisent comme un défilé, pas comme une rencontre.
        val off = (0.41f * i + seed.nextFloat() * 0.18f) % 1f

        when (v) {
            // Les DÉCORS restent : un chalet qui s'absente n'est plus un chalet, un hibou
            // posé sur sa branche est là toute la nuit, et des lucioles qui s'éteignent
            // toutes ensemble ne seraient qu'une panne.
            Visitor.SNOWMAN -> snowman(horizon, seed, night)
            Visitor.BLOSSOMS -> blossoms(horizon, m, t)
            Visitor.SQUIRREL -> squirrel(horizon, t, seed, night)
            Visitor.OWL -> owl(horizon, seed, night, t)
            Visitor.KITE -> kite(horizon, t, seed, m.wind)
            Visitor.BALLOON -> balloon(horizon, t, seed)
            Visitor.FIREFLIES -> fireflies(horizon, t, seed)
            Visitor.BATS -> bats(horizon, t, seed)

            // Les PASSAGES. Chacun reste le temps qu'il lui faut pour traverser, puis
            // s'en va : le chevreuil prend son temps, le renard file.
            Visitor.DEER -> pass(slow, 0.26f, off)?.let { (p, d) -> deer(horizon, p, d, seed, night) }
            Visitor.FOX -> pass(slow, 0.16f, off)?.let { (p, d) -> fox(horizon, p, d, seed, night) }
            Visitor.RABBIT -> pass(slow, 0.15f, off)?.let { (p, d) -> rabbit(horizon, p, d, night) }
            Visitor.FROG -> pass(slow, 0.20f, off)?.let { (p, d) -> frog(horizon, p, d, night) }
            Visitor.GEESE -> pass(slow, 0.24f, off)?.let { (p, d) -> geese(horizon, p, d, t, seed, night) }
            // Le papillon est une ambiance plus qu'un événement : il a droit à une
            // fenêtre bien plus large, sinon les après-midis d'été sont vides.
            Visitor.BUTTERFLY -> pass(slow, 0.40f, off)?.let { (p, _) -> butterfly(horizon, p, t, seed) }

            // La deuxième fournée.
            Visitor.HEDGEHOG -> pass(slow, 0.18f, off)?.let { (p, d) -> hedgehog(horizon, p, d, night) }
            Visitor.RACCOON -> pass(slow, 0.20f, off)?.let { (p, d) -> raccoon(horizon, p, d, night) }
            Visitor.CAT -> pass(slow, 0.20f, off)?.let { (p, d) -> cat(horizon, p, d, seed, night) }
            Visitor.SNAIL -> pass(slow, 0.34f, off)?.let { (p, d) -> snail(horizon, p, d, night) }
            // Les bêtes d'eau dérivent : leurs fenêtres sont longues parce qu'elles ne
            // traversent pas, elles flânent.
            Visitor.DUCKS -> pass(slow, 0.36f, off)?.let { (p, d) -> ducks(world, horizon, p, d, t, seed, night) }
            Visitor.BEAVER -> pass(slow, 0.28f, off)?.let { (p, d) -> beaver(world, horizon, p, d, night) }
            Visitor.LOON -> pass(slow, 0.34f, off)?.let { (p, d) -> loon(world, horizon, p, d, t, night) }
            Visitor.DRAGONFLY -> pass(slow, 0.32f, off)?.let { (p, _) -> dragonfly(world, horizon, p, t, seed) }
            Visitor.BEAR -> pass(slow, 0.24f, off)?.let { (p, d) -> bear(horizon, p, d, seed, night) }
            // Le cardinal et le colibri ne traversent pas : ils se posent, puis repartent.
            Visitor.CARDINAL -> pass(slow, 0.30f, off)?.let { (p, d) -> cardinal(horizon, p, d, t, seed, night) }
            Visitor.HUMMINGBIRD -> pass(slow, 0.26f, off)?.let { (p, d) -> hummingbird(horizon, p, d, t, seed) }

            // Les événements. Fenêtres serrées : ça passe, et c'est fini.
            Visitor.COMET -> pass(slow, 0.30f, off)?.let { (p, d) -> comet(horizon, p, d) }
            Visitor.PLANE -> pass(slow, 0.34f, off)?.let { (p, d) -> plane(horizon, p, d, t, seed) }
            Visitor.LANTERNS -> pass(slow, 0.55f, off)?.let { (p, _) -> lanterns(horizon, p, t, seed) }
        }
    }
}

private fun dim(c: Color, night: Float) = lerp(c, Color(0xFF141B33), night * 0.7f)

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
/**
 * Une biche.
 *
 * Pas de bois. À cette taille — le garrot fait une trentaine de pixels — un bois se
 * résume à trois traits de huit pixels qui se croisent, et trois traits qui se croisent
 * au-dessus d'une tête ne ressemblent pas à un bois : ça ressemble à une erreur. C'était
 * ça, la géométrie bizarre. Une biche a une silhouette que la petite taille ne détruit
 * pas : longues pattes, croupe haute, encolure tendue, et la tache blanche de la queue.
 *
 * Les pattes ont un GENOU. Un trait droit qui pivote balaie comme un essuie-glace ; deux
 * segments articulés donnent la démarche. Les deux pattes du fond sont posées avant le
 * corps et assombries, les deux de devant par-dessus : c'est tout ce qu'il faut pour que
 * la bête ait une épaisseur.
 */
private fun DrawScope.deer(horizon: Float, p: Float, dir: Float, rng: Random, night: Float) {
    // Toutes les biches n'ont pas la même taille, et celle-là garde la sienne toute la
    // journée : c'est la même bête qui repasse, pas une nouvelle à chaque traversée.
    val h = size.height * 0.036f * (0.86f + rng.nextFloat() * 0.3f)
    val base = horizon + 3f
    val x = -size.width * 0.14f + p * size.width * 1.28f
    val coat = dim(Color(0xFFB57C4E), night)
    val far = dim(Color(0xFF8E5F3A), night)
    val cream = dim(Color(0xFFEBD6BA), night)
    val dark = dim(Color(0xFF4A3222), night)
    // Un pas complet tous les huitièmes de traversée : plus vite, il trottine ; plus
    // lentement, il patine.
    val step = sin(p * 22f * 2f * PI).toFloat()

    fun leg(dx: Float, swing: Float, c: Color, w: Float) {
        val top = Offset(x + h * dx, base - h * 0.98f)
        val knee = Offset(x + h * (dx + swing * 0.16f), base - h * 0.46f)
        val foot = Offset(x + h * (dx + swing * 0.32f), base)
        drawLine(c, top, knee, strokeWidth = h * w, cap = StrokeCap.Round)
        drawLine(c, knee, foot, strokeWidth = h * w * 0.72f, cap = StrokeCap.Round)
        drawCircle(dark, h * 0.055f, foot)
    }

    facing(Offset(x, base), dir) {
        // Les deux pattes du fond, en retrait de phase.
        leg(-0.52f, -step, far, 0.11f)
        leg(0.62f, step, far, 0.11f)

        // Le corps : dos qui creuse un peu, croupe ronde, poitrail plein, ventre rentré.
        drawPath(
            Path().apply {
                moveTo(x - h * 0.92f, base - h * 1.06f)
                quadraticBezierTo(x - h * 0.1f, base - h * 1.30f, x + h * 0.76f, base - h * 1.08f)
                quadraticBezierTo(x + h * 1.06f, base - h * 0.92f, x + h * 0.84f, base - h * 0.56f)
                quadraticBezierTo(x, base - h * 0.38f, x - h * 0.84f, base - h * 0.60f)
                quadraticBezierTo(x - h * 1.10f, base - h * 0.82f, x - h * 0.92f, base - h * 1.06f)
                close()
            },
            coat
        )
        // Le ventre clair : c'est lui qui décolle la bête du sol sombre.
        drawOval(cream.copy(alpha = 0.75f), Offset(x - h * 0.66f, base - h * 0.72f), Size(h * 1.4f, h * 0.3f))

        // La queue, levée, tache blanche : la signature du chevreuil de loin.
        drawOval(Color(0xFFFDF6EE).copy(alpha = 0.92f), Offset(x - h * 1.06f, base - h * 1.16f), Size(h * 0.26f, h * 0.36f))

        // L'encolure, en coin : large à l'épaule, fine à la nuque.
        drawPath(
            Path().apply {
                moveTo(x + h * 0.48f, base - h * 1.16f)
                lineTo(x + h * 1.02f, base - h * 2.04f)
                lineTo(x + h * 1.34f, base - h * 1.90f)
                quadraticBezierTo(x + h * 1.0f, base - h * 1.34f, x + h * 0.9f, base - h * 1.02f)
                close()
            },
            coat
        )

        // La tête et le museau, deux ovales qui se recouvrent — l'angle entre les deux
        // suffit à faire un profil.
        drawOval(coat, Offset(x + h * 1.04f, base - h * 2.26f), Size(h * 0.6f, h * 0.44f))
        drawOval(coat, Offset(x + h * 1.44f, base - h * 2.14f), Size(h * 0.38f, h * 0.28f))
        drawOval(cream.copy(alpha = 0.8f), Offset(x + h * 1.52f, base - h * 2.02f), Size(h * 0.26f, h * 0.13f))
        drawCircle(dark, h * 0.05f, Offset(x + h * 1.78f, base - h * 1.98f))
        drawCircle(Color(0xFF2A1B12), h * 0.052f, Offset(x + h * 1.28f, base - h * 2.08f))

        // Les oreilles, en amande et tournées vers l'arrière.
        listOf(-0.06f to -0.34f, 0.10f to -0.24f).forEach { (dx, dy) ->
            drawOval(coat, Offset(x + h * (1.0f + dx), base - h * (2.42f + dy * 0.4f)), Size(h * 0.3f, h * 0.19f))
        }

        // Les deux pattes de devant, par-dessus le corps.
        leg(-0.62f, step, coat, 0.125f)
        leg(0.52f, -step, coat, 0.125f)
    }
}

/**
 * Un renard : bas sur pattes, long, et cette queue énorme qui fait la moitié de la bête.
 *
 * La queue partait droit à l'horizontale et se terminait par une pastille blanche posée à
 * côté, sans contact — ça donnait un renard qui traîne un objet. Ici c'est une courbe
 * fermée qui part de la croupe et s'épaissit vers le bout, avec le blanc DANS la pointe.
 */
private fun DrawScope.fox(horizon: Float, p: Float, dir: Float, rng: Random, night: Float) {
    val h = size.height * 0.024f * (0.88f + rng.nextFloat() * 0.26f)
    val base = horizon + 3f
    val x = -size.width * 0.12f + p * size.width * 1.24f
    val coat = dim(Color(0xFFE07A32), night)
    val far = dim(Color(0xFFB65B22), night)
    val cream = dim(Color(0xFFFBEEDC), night)
    val sock = dim(Color(0xFF3A2418), night)
    val trot = sin(p * 34f * 2f * PI).toFloat()

    fun leg(dx: Float, swing: Float, c: Color) {
        val top = Offset(x + h * dx, base - h * 0.82f)
        val knee = Offset(x + h * (dx + swing * 0.14f), base - h * 0.38f)
        val foot = Offset(x + h * (dx + swing * 0.26f), base)
        drawLine(c, top, knee, strokeWidth = h * 0.15f, cap = StrokeCap.Round)
        drawLine(sock, knee, foot, strokeWidth = h * 0.12f, cap = StrokeCap.Round)
    }

    facing(Offset(x, base), dir) {
        leg(-0.62f, -trot, far)
        leg(0.58f, trot, far)

        // La queue, dessinée AVANT le corps pour qu'elle passe derrière la croupe.
        drawPath(
            Path().apply {
                moveTo(x - h * 0.9f, base - h * 1.02f)
                quadraticBezierTo(x - h * 2.1f, base - h * 1.48f, x - h * 2.5f, base - h * 0.72f)
                quadraticBezierTo(x - h * 1.9f, base - h * 0.5f, x - h * 0.86f, base - h * 0.66f)
                close()
            },
            coat
        )
        drawOval(cream, Offset(x - h * 2.62f, base - h * 1.02f), Size(h * 0.44f, h * 0.46f))

        // Le corps : une gouttière basse et longue, pas un œuf.
        drawPath(
            Path().apply {
                moveTo(x - h * 0.94f, base - h * 1.04f)
                quadraticBezierTo(x - h * 0.1f, base - h * 1.22f, x + h * 0.86f, base - h * 1.10f)
                quadraticBezierTo(x + h * 1.12f, base - h * 0.94f, x + h * 0.9f, base - h * 0.62f)
                quadraticBezierTo(x, base - h * 0.46f, x - h * 0.88f, base - h * 0.64f)
                close()
            },
            coat
        )
        drawOval(cream.copy(alpha = 0.85f), Offset(x - h * 0.5f, base - h * 0.74f), Size(h * 1.3f, h * 0.26f))

        // La tête : un museau pointu, deux oreilles triangulaires, et la joue claire.
        drawPath(
            Path().apply {
                moveTo(x + h * 0.74f, base - h * 1.6f)
                quadraticBezierTo(x + h * 1.34f, base - h * 1.7f, x + h * 1.72f, base - h * 1.24f)
                quadraticBezierTo(x + h * 1.3f, base - h * 1.02f, x + h * 0.8f, base - h * 1.12f)
                close()
            },
            coat
        )
        drawOval(cream, Offset(x + h * 1.16f, base - h * 1.3f), Size(h * 0.48f, h * 0.24f))
        drawCircle(sock, h * 0.07f, Offset(x + h * 1.72f, base - h * 1.24f))
        drawCircle(Color(0xFF2B1A10), h * 0.055f, Offset(x + h * 1.06f, base - h * 1.42f))
        listOf(0.66f to 1.86f, 0.98f to 1.8f).forEach { (ex, ey) ->
            drawPath(
                Path().apply {
                    moveTo(x + h * ex, base - h * (ey - 0.34f))
                    lineTo(x + h * (ex + 0.1f), base - h * ey)
                    lineTo(x + h * (ex + 0.34f), base - h * (ey - 0.42f))
                    close()
                },
                if (ex < 0.8f) far else coat
            )
        }

        leg(-0.5f, trot, coat)
        leg(0.7f, -trot, coat)
    }
}

/**
 * Un lapin, en bonds.
 *
 * Le corps s'ÉTIRE en l'air et se tasse à l'atterrissage. Un ovale rigide qui monte et
 * descend se lit comme une balle ; c'est la déformation qui fait le vivant, et elle ne
 * coûte que deux multiplications.
 */
private fun DrawScope.rabbit(horizon: Float, p: Float, dir: Float, night: Float) {
    val h = size.height * 0.019f
    val x = -size.width * 0.1f + p * size.width * 1.2f
    val air = abs(sin(p * 11f * PI).toFloat())
    val hop = air * h * 1.9f
    val base = horizon + 3f - hop
    val body = dim(Color(0xFFE4DAD0), night)
    val shade = dim(Color(0xFFC3B4A6), night)
    val pink = dim(Color(0xFFF3A9BC), night)
    val stretch = 1f + air * 0.22f            // long en l'air, ramassé au sol

    facing(Offset(x, horizon + 3f), dir) {
        drawOval(body, Offset(x - h * 0.85f * stretch, base - h * 0.98f), Size(h * 1.7f * stretch, h * 0.92f))
        drawOval(shade.copy(alpha = 0.55f), Offset(x - h * 0.6f, base - h * 0.42f), Size(h * 1.2f, h * 0.22f))
        drawOval(body, Offset(x + h * 0.42f, base - h * 1.62f), Size(h * 0.8f, h * 0.7f))

        // Les oreilles se couchent vers l'arrière au sommet du bond.
        listOf(-0.02f, 0.24f).forEach { o ->
            val lay = air * h * 0.5f
            drawOval(
                body,
                Offset(x + h * (0.56f + o) - lay, base - h * (2.48f - air * 0.3f)),
                Size(h * 0.24f, h * 1.0f)
            )
            drawOval(
                pink.copy(alpha = 0.7f),
                Offset(x + h * (0.6f + o) - lay, base - h * (2.36f - air * 0.3f)),
                Size(h * 0.12f, h * 0.62f)
            )
        }
        drawCircle(Color(0xFF3A2C24), h * 0.06f, Offset(x + h * 0.92f, base - h * 1.3f))
        // La queue en pompon, blanche : la seule chose qu'on voit vraiment de dos.
        drawCircle(Color(0xFFFFFBF6).copy(alpha = 0.96f), h * 0.3f, Offset(x - h * 0.92f, base - h * 0.72f))
        // Les pattes arrière, allongées en vol.
        drawOval(shade, Offset(x - h * 0.5f, base - h * 0.34f), Size(h * (0.6f + air * 0.4f), h * 0.24f))
    }
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

/**
 * Bernadette, la grenouille.
 *
 * Elle était vert pomme — `#6FC65A` — et elle sautait sur une pelouse de printemps
 * `#9BE08A` puis d'été `#5FD08A`. Autrement dit : un vert sur le même vert, aux deux
 * seules saisons où elle sort. On ne la voyait littéralement pas. C'est le piège de
 * choisir la couleur d'une bête sans regarder ce qu'il y a derrière.
 *
 * Elle est maintenant TURQUOISE, avec le ventre crème et un liseré sombre. Le turquoise
 * est la seule famille de verts qui tranche sur une herbe jaune-vert, et le liseré la
 * détache même quand elle passe devant un buisson. Un nénuphar l'accompagne à chaque
 * atterrissage : une petite flaque sombre sous elle, et le contraste est réglé pour de
 * bon.
 */
private fun DrawScope.frog(horizon: Float, p: Float, dir: Float, night: Float) {
    val h = size.height * 0.016f
    val x = -size.width * 0.08f + p * size.width * 1.16f
    val air = abs(sin(p * 9f * PI).toFloat())
    val hop = air * h * 2.4f
    val ground = horizon + size.height * 0.035f
    val base = ground - hop
    val skin = dim(Color(0xFF34B39A), night)
    val deep = dim(Color(0xFF15756A), night)
    val cream = dim(Color(0xFFF7EFC8), night)
    val blush = dim(Color(0xFFFF9BC0), night)

    facing(Offset(x, ground), dir) {
        // Le nénuphar, seulement quand elle touche terre : elle se pose DESSUS, ce qui
        // explique où elle va et lui donne un fond sombre.
        if (air < 0.25f) {
            val k = 1f - air / 0.25f
            drawOval(
                deep.copy(alpha = 0.55f * k),
                Offset(x - h * 1.5f, ground - h * 0.18f), Size(h * 3f, h * 0.6f)
            )
            drawOval(
                dim(Color(0xFF2E8B6E), night).copy(alpha = 0.9f * k),
                Offset(x - h * 1.35f, ground - h * 0.3f), Size(h * 2.7f, h * 0.62f)
            )
        }

        // Les pattes arrière : repliées au sol, tendues vers l'arrière en vol.
        listOf(-1f, 1f).forEach { d ->
            drawPath(
                Path().apply {
                    moveTo(x + d * h * 0.55f, base - h * 0.5f)
                    quadraticBezierTo(
                        x + d * h * (1.2f + air * 0.5f), base - h * (0.7f + air * 0.5f),
                        x - h * (0.9f + air * 1.1f), base - h * (0.1f + air * 0.5f)
                    )
                    quadraticBezierTo(
                        x + d * h * 0.9f, base - h * 0.25f,
                        x + d * h * 0.55f, base - h * 0.5f
                    )
                    close()
                },
                deep
            )
        }

        // Le corps, plus large que haut, et le ventre crème bien visible.
        drawOval(deep, Offset(x - h * 1.06f, base - h * 1.0f), Size(h * 2.12f, h * 1.2f))
        drawOval(skin, Offset(x - h * 0.98f, base - h * 0.96f), Size(h * 1.96f, h * 1.06f))
        drawOval(cream, Offset(x - h * 0.66f, base - h * 0.5f), Size(h * 1.32f, h * 0.44f))

        // Les yeux, bombés au-dessus de la tête : c'est la marque de la grenouille.
        listOf(-0.46f, 0.46f).forEach { o ->
            drawCircle(deep, h * 0.4f, Offset(x + h * o, base - h * 1.2f))
            drawCircle(skin, h * 0.33f, Offset(x + h * o, base - h * 1.24f))
            drawCircle(Color(0xFFFFFDF4), h * 0.2f, Offset(x + h * o, base - h * 1.26f))
            drawCircle(Color(0xFF16281F), h * 0.11f, Offset(x + h * o, base - h * 1.27f))
            drawCircle(Color.White, h * 0.045f, Offset(x + h * (o + 0.07f), base - h * 1.32f))
        }
        drawCircle(blush.copy(alpha = 0.6f), h * 0.2f, Offset(x + h * 0.82f, base - h * 0.82f))
        drawCircle(blush.copy(alpha = 0.6f), h * 0.2f, Offset(x - h * 0.82f, base - h * 0.82f))
        // Le sourire.
        drawArc(
            deep, 8f, 164f, false,
            Offset(x - h * 0.52f, base - h * 1.02f), Size(h * 1.04f, h * 0.78f),
            style = Stroke(width = h * 0.11f, cap = StrokeCap.Round)
        )
    }
}

/** Un papillon, qui flotte en huit et bat des ailes. */
private fun DrawScope.butterfly(horizon: Float, p: Float, t: Float, rng: Random) {
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
private fun DrawScope.geese(horizon: Float, p: Float, dir: Float, t: Float, rng: Random, night: Float) {
    // Plus large que l'écran des deux côtés : la formation traîne sept oiseaux derrière la
    // meneuse, et il faut que le DERNIER ait quitté le cadre avant que la fenêtre se ferme.
    val lead = Offset(p * size.width * 1.7f - size.width * 0.35f, horizon * (0.16f + rng.nextFloat() * 0.2f))
    val s = size.height * 0.008f
    val ink = dim(Color(0xFF3A3F55), night * 0.4f)
    repeat(7) { k ->
        val row = (k + 1) / 2
        val side = if (k % 2 == 0) -1 else 1
        // La formation traîne DERRIÈRE la meneuse, donc du côté d'où le vol arrive.
        val gx = lead.x - dir * row * s * 2.2f
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

// ---- la deuxième fournée ---------------------------------------------------
//
// Dix bêtes de plus, et trois choses qui n'arrivent presque jamais. La moitié vit sur la
// rivière : avant qu'elle existe, un canard n'avait littéralement pas d'endroit où être.

/** La ligne d'eau, là où flotte ce qui flotte. Calée sur [river]. */
private fun DrawScope.waterline(world: World, horizon: Float): Float {
    val depth = size.height * 0.075f
    val span = world.river.coerceIn(0.5f, 1.4f)
    val top = horizon + depth * (0.94f - 0.44f * span)
    return top + (horizon + depth * 0.88f - top) * 0.42f
}

/** Un hérisson : une brosse de piquants sur deux pattes, et un museau qui dépasse. */
private fun DrawScope.hedgehog(horizon: Float, p: Float, dir: Float, night: Float) {
    val h = size.height * 0.014f
    val base = horizon + 3f
    val x = -size.width * 0.08f + p * size.width * 1.16f
    val quill = dim(Color(0xFF7A5C42), night)
    val skin = dim(Color(0xFFE8C9A8), night)
    val trot = sin(p * 40f * 2f * PI).toFloat()

    facing(Offset(x, base), dir) {
        // Le dos : un dôme, puis les piquants plantés dessus en éventail.
        drawOval(quill, Offset(x - h * 1.1f, base - h * 1.25f), Size(h * 2.1f, h * 1.3f))
        repeat(13) { k ->
            val a = PI.toFloat() * (0.08f + 0.84f * k / 12f)
            val sx = x - h * 0.05f - cos(a) * h * 1.0f
            val sy = base - h * 0.6f - sin(a) * h * 0.68f
            drawLine(
                quill, Offset(sx, sy),
                Offset(sx - cos(a) * h * 0.42f, sy - sin(a) * h * 0.42f),
                strokeWidth = h * 0.13f, cap = StrokeCap.Round
            )
        }
        // Le museau, clair, pointu, en avant du dôme.
        drawOval(skin, Offset(x + h * 0.62f, base - h * 0.86f), Size(h * 0.9f, h * 0.62f))
        drawCircle(Color(0xFF2E2018), h * 0.09f, Offset(x + h * 1.48f, base - h * 0.58f))
        drawCircle(Color(0xFF2E2018), h * 0.07f, Offset(x + h * 0.98f, base - h * 0.72f))
        listOf(-0.3f to trot, 0.44f to -trot).forEach { (dx, s) ->
            drawLine(
                skin, Offset(x + h * dx, base - h * 0.3f),
                Offset(x + h * (dx + s * 0.16f), base),
                strokeWidth = h * 0.14f, cap = StrokeCap.Round
            )
        }
    }
}

/** Un raton laveur : le masque et la queue annelée, rien d'autre n'est nécessaire. */
private fun DrawScope.raccoon(horizon: Float, p: Float, dir: Float, night: Float) {
    val h = size.height * 0.019f
    val base = horizon + 3f
    val x = -size.width * 0.1f + p * size.width * 1.2f
    val fur = dim(Color(0xFF9AA0AC), night)
    val dark = dim(Color(0xFF3A3F4C), night)
    val pale = dim(Color(0xFFE6E9EE), night)
    val amble = sin(p * 26f * 2f * PI).toFloat()

    facing(Offset(x, base), dir) {
        // La queue annelée, derrière, relevée.
        repeat(5) { k ->
            drawCircle(
                if (k % 2 == 0) dark else fur, h * (0.34f - k * 0.02f),
                Offset(x - h * (0.95f + k * 0.42f), base - h * (0.7f + k * 0.17f))
            )
        }
        listOf(-0.42f to amble, 0.5f to -amble).forEach { (dx, s) ->
            drawLine(
                dark, Offset(x + h * dx, base - h * 0.55f),
                Offset(x + h * (dx + s * 0.16f), base),
                strokeWidth = h * 0.17f, cap = StrokeCap.Round
            )
        }
        drawOval(fur, Offset(x - h * 0.95f, base - h * 1.15f), Size(h * 1.9f, h * 0.95f))
        // La tête et le masque.
        drawOval(fur, Offset(x + h * 0.5f, base - h * 1.72f), Size(h * 1.15f, h * 0.95f))
        drawOval(pale, Offset(x + h * 0.72f, base - h * 1.42f), Size(h * 0.85f, h * 0.42f))
        drawRoundRect(
            dark, Offset(x + h * 0.62f, base - h * 1.5f), Size(h * 0.92f, h * 0.34f),
            CornerRadius(h * 0.17f)
        )
        drawCircle(Color(0xFF15181F), h * 0.075f, Offset(x + h * 1.32f, base - h * 1.34f))
        listOf(0.6f, 0.98f).forEach { o ->
            drawCircle(fur, h * 0.2f, Offset(x + h * o, base - h * 1.78f))
            drawCircle(dark, h * 0.11f, Offset(x + h * o, base - h * 1.78f))
        }
    }
}

/** Un chat de ruelle, la queue en l'air. Sa couleur ne change pas de la journée. */
private fun DrawScope.cat(horizon: Float, p: Float, dir: Float, rng: Random, night: Float) {
    val coats = listOf(
        Color(0xFFE49A4E), Color(0xFF9BA3AF), Color(0xFF3E3A44), Color(0xFFF3EBE0)
    )
    val c0 = coats[rng.nextInt(coats.size)]
    val h = size.height * 0.018f
    val base = horizon + 3f
    val x = -size.width * 0.1f + p * size.width * 1.2f
    val fur = dim(c0, night)
    val dark = dim(lerp(c0, Color.Black, 0.35f), night)
    val step = sin(p * 30f * 2f * PI).toFloat()

    facing(Offset(x, base), dir) {
        // La queue, dressée avec un crochet au bout : la posture du chat content.
        drawPath(
            Path().apply {
                moveTo(x - h * 0.85f, base - h * 0.9f)
                quadraticBezierTo(x - h * 1.7f, base - h * 1.6f, x - h * 1.35f, base - h * 2.35f)
            },
            fur, style = Stroke(width = h * 0.24f, cap = StrokeCap.Round)
        )
        listOf(-0.55f to step, 0.55f to -step).forEach { (dx, s) ->
            drawLine(
                dark, Offset(x + h * dx, base - h * 0.62f),
                Offset(x + h * (dx + s * 0.2f), base),
                strokeWidth = h * 0.14f, cap = StrokeCap.Round
            )
        }
        drawOval(fur, Offset(x - h * 0.92f, base - h * 1.2f), Size(h * 1.84f, h * 0.8f))
        drawOval(fur, Offset(x + h * 0.6f, base - h * 1.85f), Size(h * 0.95f, h * 0.85f))
        listOf(0.72f to -1f, 1.24f to 1f).forEach { (ex, d) ->
            drawPath(
                Path().apply {
                    moveTo(x + h * ex, base - h * 1.72f)
                    lineTo(x + h * (ex + 0.12f * d), base - h * 2.28f)
                    lineTo(x + h * (ex + 0.34f), base - h * 1.72f)
                    close()
                },
                fur
            )
        }
        drawCircle(Color(0xFFF7E27A), h * 0.09f, Offset(x + h * 1.3f, base - h * 1.5f))
        drawCircle(Color(0xFF1B1A20), h * 0.04f, Offset(x + h * 1.32f, base - h * 1.5f))
    }
}

/** Un escargot, les jours de pluie. Il traverse en une éternité, et c'est tout le gag. */
private fun DrawScope.snail(horizon: Float, p: Float, dir: Float, night: Float) {
    val h = size.height * 0.011f
    val base = horizon + size.height * 0.02f
    val x = -size.width * 0.06f + p * size.width * 1.12f
    val foot = dim(Color(0xFFE3C9B4), night)
    val shell = dim(Color(0xFFC98A4B), night)
    val shellD = dim(Color(0xFF9A6432), night)
    // Il ondule au lieu de marcher : le pied d'un escargot avance par vagues.
    val wave = sin(p * 60f * 2f * PI).toFloat()

    facing(Offset(x, base), dir) {
        // La bave, derrière lui, qui brille faiblement.
        drawLine(
            Color.White.copy(alpha = 0.22f),
            Offset(x - h * 5f, base), Offset(x - h * 0.6f, base),
            strokeWidth = h * 0.28f, cap = StrokeCap.Round
        )
        drawOval(foot, Offset(x - h * 1.3f, base - h * 0.42f), Size(h * 2.8f, h * 0.5f))
        // La coquille : une spirale, dessinée en arcs de rayon décroissant.
        drawCircle(shell, h * 0.85f, Offset(x - h * 0.15f, base - h * 0.95f))
        repeat(3) { k ->
            drawArc(
                shellD, 20f + k * 100f, 250f, false,
                Offset(x - h * (0.72f - k * 0.17f), base - h * (1.52f - k * 0.17f)),
                Size(h * (1.44f - k * 0.4f), h * (1.44f - k * 0.4f)),
                style = Stroke(width = h * 0.14f)
            )
        }
        // La tête et les deux yeux au bout de leurs tentacules.
        drawOval(
            foot, Offset(x + h * 0.85f, base - h * 0.72f + wave * h * 0.05f),
            Size(h * 1.1f, h * 0.62f)
        )
        listOf(-0.1f, 0.22f).forEach { o ->
            val ex = x + h * (1.6f + o)
            val ey = base - h * (1.5f + o * 0.6f)
            drawLine(foot, Offset(x + h * 1.35f, base - h * 0.6f), Offset(ex, ey), strokeWidth = h * 0.13f)
            drawCircle(Color(0xFF2E2018), h * 0.12f, Offset(ex, ey))
        }
    }
}

/** Trois canards à la file sur la rivière, chacun avec son sillage. */
private fun DrawScope.ducks(world: World, horizon: Float, p: Float, dir: Float, t: Float, rng: Random, night: Float) {
    val w = waterline(world, horizon)
    val h = size.height * 0.011f
    val x0 = -size.width * 0.12f + p * size.width * 1.24f
    val body = dim(Color(0xFFB0A08E), night)
    val head = dim(Color(0xFF2F6B4F), night)
    val bill = dim(Color(0xFFE0A83E), night)
    val n = 2 + rng.nextInt(2)

    repeat(n) { k ->
        // Ils ne sont pas alignés au cordeau : chacun traîne un peu.
        val x = x0 - k * h * 3.4f * (if (dir > 0f) 1f else -1f)
        val bobY = sin(2f * PI.toFloat() * (t * 2f + k * 0.3f)).toFloat() * h * 0.14f
        val y = w + bobY

        facing(Offset(x, y), dir) {
            // Le sillage en V, derrière : c'est lui qui dit qu'ils AVANCENT.
            listOf(1f, -1f).forEach { s ->
                drawLine(
                    Color.White.copy(alpha = 0.22f),
                    Offset(x - h * 0.6f, y + h * 0.1f),
                    Offset(x - h * 4.5f, y + h * 0.1f + s * h * 0.9f),
                    strokeWidth = h * 0.1f
                )
            }
            drawOval(body, Offset(x - h * 1.15f, y - h * 0.62f), Size(h * 2.3f, h * 0.95f))
            // La queue relevée en pointe.
            drawPath(
                Path().apply {
                    moveTo(x - h * 0.95f, y - h * 0.4f)
                    lineTo(x - h * 1.9f, y - h * 0.95f)
                    lineTo(x - h * 0.85f, y - h * 0.02f)
                    close()
                },
                body
            )
            drawLine(
                head, Offset(x + h * 0.7f, y - h * 0.45f),
                Offset(x + h * 0.95f, y - h * 1.25f), strokeWidth = h * 0.42f
            )
            drawOval(head, Offset(x + h * 0.6f, y - h * 1.85f), Size(h * 0.85f, h * 0.75f))
            drawOval(bill, Offset(x + h * 1.3f, y - h * 1.48f), Size(h * 0.62f, h * 0.26f))
            drawCircle(Color(0xFF16211A), h * 0.08f, Offset(x + h * 1.18f, y - h * 1.6f))
        }
    }
}

/** Un castor qui nage : une tête, un dos, et le V du sillage. Il plonge parfois. */
private fun DrawScope.beaver(world: World, horizon: Float, p: Float, dir: Float, night: Float) {
    // Il passe la moitié du temps sous l'eau. Un castor qui reste en surface d'un bout à
    // l'autre n'est pas un castor, c'est un tronc.
    val dive = sin(p * 5f * PI).toFloat()
    if (dive < 0.05f) return
    val w = waterline(world, horizon)
    val h = size.height * 0.012f
    val x = -size.width * 0.1f + p * size.width * 1.2f
    val fur = dim(Color(0xFF6B4A32), night)
    val furD = dim(Color(0xFF4A3222), night)
    val sink = (1f - dive) * h * 0.8f

    facing(Offset(x, w), dir) {
        listOf(1f, -1f).forEach { s ->
            drawLine(
                Color.White.copy(alpha = 0.25f * dive),
                Offset(x - h * 0.4f, w),
                Offset(x - h * 5.5f, w + s * h * 1.3f),
                strokeWidth = h * 0.12f
            )
        }
        drawOval(fur, Offset(x - h * 1.7f, w - h * 0.42f + sink), Size(h * 2.4f, h * 0.6f))
        drawOval(fur, Offset(x + h * 0.28f, w - h * 0.85f + sink), Size(h * 1.25f, h * 0.95f))
        drawOval(furD, Offset(x + h * 1.1f, w - h * 0.52f + sink), Size(h * 0.5f, h * 0.34f))
        drawCircle(Color(0xFF1B140E), h * 0.1f, Offset(x + h * 1.05f, w - h * 0.62f + sink))
        listOf(0.4f, 0.9f).forEach { o ->
            drawCircle(furD, h * 0.16f, Offset(x + h * o, w - h * 1.02f + sink))
        }
        // La branche qu'il rapporte : le détail qui raconte toute une histoire.
        drawLine(
            furD, Offset(x + h * 0.9f, w - h * 0.5f + sink),
            Offset(x + h * 3.2f, w - h * 0.9f + sink),
            strokeWidth = h * 0.16f, cap = StrokeCap.Round
        )
    }
}

/** Un huard : le collier blanc, l'œil rouge, et des plongeons qui l'effacent. */
private fun DrawScope.loon(world: World, horizon: Float, p: Float, dir: Float, t: Float, night: Float) {
    // Le plongeon : il disparaît complètement pendant un quart du passage, puis
    // ressurgit AILLEURS. C'est exactement ce que fait un huard, et c'est la seule bête
    // du décor qui se téléporte sans que ça ait l'air d'un bogue.
    val cycle = (p * 3f) % 1f
    if (cycle > 0.72f) return
    val w = waterline(world, horizon)
    val h = size.height * 0.013f
    val x = -size.width * 0.1f + p * size.width * 1.2f
    val ink = dim(Color(0xFF1D2230), night * 0.6f)
    val pale = dim(Color(0xFFF4F7FB), night * 0.5f)
    // Il s'enfonce en entrant et en sortant du plongeon, il ne clignote pas.
    val emerge = minOf(cycle / 0.10f, (0.72f - cycle) / 0.10f).coerceIn(0f, 1f)
    val sink = (1f - emerge) * h * 1.4f
    val bob = sin(2f * PI.toFloat() * t * 2f).toFloat() * h * 0.1f
    val dy = sink + bob

    facing(Offset(x, w), dir) {
        drawOval(ink, Offset(x - h * 1.6f, w - h * 0.5f + dy), Size(h * 3f, h * 0.8f))
        // Le damier du dos, en pointillé clair.
        repeat(6) { k ->
            drawCircle(
                pale.copy(alpha = 0.8f), h * 0.11f,
                Offset(x - h * (1.2f - k * 0.42f), w - h * (0.62f - (k % 2) * 0.16f) + dy)
            )
        }
        drawLine(
            ink, Offset(x + h * 1.05f, w - h * 0.55f + dy),
            Offset(x + h * 1.2f, w - h * 1.75f + dy), strokeWidth = h * 0.4f
        )
        // Le collier : trois traits pâles sur le cou. La signature du huard.
        repeat(3) { k ->
            drawLine(
                pale, Offset(x + h * (1.02f + k * 0.03f), w - h * (1.0f + k * 0.16f) + dy),
                Offset(x + h * (1.36f + k * 0.03f), w - h * (1.02f + k * 0.16f) + dy),
                strokeWidth = h * 0.08f
            )
        }
        drawOval(ink, Offset(x + h * 0.86f, w - h * 2.15f + dy), Size(h * 0.8f, h * 0.62f))
        drawPath(
            Path().apply {
                moveTo(x + h * 1.6f, w - h * 1.92f + dy)
                lineTo(x + h * 2.5f, w - h * 1.82f + dy)
                lineTo(x + h * 1.6f, w - h * 1.7f + dy)
                close()
            },
            ink
        )
        drawCircle(Color(0xFFC42B2B), h * 0.1f, Offset(x + h * 1.45f, w - h * 1.98f + dy))
    }
}

/** Une libellule : elle file, s'arrête net, repart. Les ailes ne sont qu'un flou. */
private fun DrawScope.dragonfly(world: World, horizon: Float, p: Float, t: Float, rng: Random) {
    val w = waterline(world, horizon)
    val h = size.height * 0.009f
    // Le vol saccadé : quatre bonds pendant la traversée. Une libellule ne plane jamais.
    val seg = p * 4f
    val k = seg.toInt()
    val u = (seg - k).coerceIn(0f, 1f)
    val ease = 1f - (1f - u).pow(3f)
    val lane = rng.nextFloat()
    val x = size.width * (-0.05f + 1.1f * (k + ease) / 4f)
    val y = w - horizon * (0.06f + 0.10f * lane) -
        sin(2f * PI.toFloat() * (t * 3f + k)).toFloat() * h * 1.2f
    val body = Color(0xFF3FBFC4)
    val glass = Color(0xFFDFF6FF)

    // Les ailes battent trop vite pour être vues : deux ovales pâles superposés.
    val flap = 0.5f + 0.5f * abs(sin(2f * PI.toFloat() * t * 40f).toFloat())
    listOf(-1f, 1f).forEach { d ->
        drawOval(
            glass.copy(alpha = 0.5f),
            Offset(x + d * h * 0.2f - if (d < 0) h * 2.2f else 0f, y - h * 0.75f),
            Size(h * 2.2f, h * 0.55f * flap + h * 0.2f)
        )
        drawOval(
            glass.copy(alpha = 0.38f),
            Offset(x + d * h * 0.15f - if (d < 0) h * 1.9f else 0f, y - h * 0.15f),
            Size(h * 1.9f, h * 0.45f * flap + h * 0.18f)
        )
    }
    drawRoundRect(body, Offset(x - h * 0.35f, y - h * 0.4f), Size(h * 3.2f, h * 0.34f), CornerRadius(h * 0.17f))
    drawCircle(Color(0xFF1E8A93), h * 0.42f, Offset(x - h * 0.42f, y - h * 0.25f))
    drawCircle(Color(0xFF9BE8EC), h * 0.14f, Offset(x - h * 0.6f, y - h * 0.4f))
}

/** Un cardinal : rouge vif sur la neige. Il se pose, sautille, et repart. */
private fun DrawScope.cardinal(horizon: Float, p: Float, dir: Float, t: Float, rng: Random, night: Float) {
    val h = size.height * 0.013f
    val perch = horizon - h * 0.4f
    // Deux tiers du passage sont un vol, le tiers du milieu est une PAUSE posée. Un
    // oiseau qui traverse sans jamais s'arrêter ne se regarde pas.
    val sit = p in 0.34f..0.66f
    val x = if (sit) size.width * (0.18f + rng.nextFloat() * 0.64f)
    else -size.width * 0.08f + p * size.width * 1.16f
    val y = if (sit) perch - abs(sin(2f * PI.toFloat() * t * 4f).toFloat()) * h * 0.5f
    else perch - horizon * 0.22f * sin(p * PI).toFloat()
    val red = dim(Color(0xFFD32B3A), night)
    val redD = dim(Color(0xFF9C1C2A), night)

    facing(Offset(x, y), dir) {
        drawOval(red, Offset(x - h * 0.85f, y - h * 0.8f), Size(h * 1.7f, h * 1.15f))
        // La queue, longue et droite.
        drawPath(
            Path().apply {
                moveTo(x - h * 0.7f, y - h * 0.5f)
                lineTo(x - h * 2.3f, y - h * 0.9f)
                lineTo(x - h * 0.66f, y - h * 0.05f)
                close()
            },
            redD
        )
        drawOval(red, Offset(x + h * 0.3f, y - h * 1.55f), Size(h * 0.95f, h * 0.88f))
        // La huppe : la pointe sur le crâne, sans quoi c'est un moineau peint en rouge.
        drawPath(
            Path().apply {
                moveTo(x + h * 0.42f, y - h * 1.5f)
                lineTo(x + h * 0.62f, y - h * 2.25f)
                lineTo(x + h * 1.06f, y - h * 1.42f)
                close()
            },
            red
        )
        // Le masque noir autour du bec, et le bec orange.
        drawOval(Color(0xFF1E1418), Offset(x + h * 0.9f, y - h * 1.28f), Size(h * 0.42f, h * 0.34f))
        drawPath(
            Path().apply {
                moveTo(x + h * 1.16f, y - h * 1.24f)
                lineTo(x + h * 1.7f, y - h * 1.1f)
                lineTo(x + h * 1.14f, y - h * 0.96f)
                close()
            },
            Color(0xFFE8913A)
        )
        drawCircle(Color(0xFF120C10), h * 0.09f, Offset(x + h * 0.9f, y - h * 1.32f))
        if (!sit) {
            // En vol, une aile ouverte qui bat.
            val beat = sin(2f * PI.toFloat() * t * 16f).toFloat()
            drawOval(redD, Offset(x - h * 0.5f, y - h * 1.0f + beat * h * 0.3f), Size(h * 1.2f, h * 0.5f))
        }
    }
}

/** Un colibri en vol stationnaire devant une fleur, l'été. */
private fun DrawScope.hummingbird(horizon: Float, p: Float, dir: Float, t: Float, rng: Random) {
    val h = size.height * 0.008f
    val fx = size.width * (0.12f + rng.nextFloat() * 0.76f)
    val fy = horizon - horizon * 0.06f
    // Il arrive, reste devant la fleur, puis repart : le point d'arrivée est la fleur.
    val stop = fx - h * 3.4f
    val fromX = if (dir > 0f) -size.width * 0.1f else size.width * 1.1f
    val toX = if (dir > 0f) size.width * 1.1f else -size.width * 0.1f
    val x = if (p < 0.5f) fromX + (stop - fromX) * (1f - (1f - (p / 0.5f)).pow(3f))
    else stop + (toX - stop) * ((p - 0.5f) / 0.5f).pow(3f)
    val y = fy - h * 0.6f + sin(2f * PI.toFloat() * t * 6f).toFloat() * h * 0.25f

    // La fleur qu'il vient visiter, plantée là pour la journée.
    drawLine(Color(0xFF4E8A46), Offset(fx, fy + h * 3f), Offset(fx, fy), strokeWidth = h * 0.25f)
    repeat(5) { k ->
        val a = 2f * PI.toFloat() * k / 5f
        drawCircle(Color(0xFFFF7FB0), h * 0.5f, Offset(fx + cos(a) * h * 0.62f, fy + sin(a) * h * 0.62f))
    }
    drawCircle(Color(0xFFFFD86B), h * 0.34f, Offset(fx, fy))

    facing(Offset(x, y), dir) {
        // Les ailes : un flou, jamais une forme nette.
        val blur = 0.4f + 0.6f * abs(sin(2f * PI.toFloat() * t * 60f).toFloat())
        listOf(-1f, 1f).forEach { d ->
            drawOval(
                Color(0xFFCBE7F5).copy(alpha = 0.45f),
                Offset(x - h * 0.2f, y - h * 1.1f + d * h * 0.5f),
                Size(h * 2.4f, h * 0.9f * blur + h * 0.2f)
            )
        }
        drawOval(Color(0xFF2FA86F), Offset(x - h * 1.1f, y - h * 0.5f), Size(h * 2.2f, h * 0.9f))
        drawOval(Color(0xFFD8425F), Offset(x + h * 0.5f, y - h * 0.55f), Size(h * 0.8f, h * 0.7f))
        drawLine(
            Color(0xFF2A2620), Offset(x + h * 1.2f, y - h * 0.2f),
            Offset(x + h * 2.9f, y - h * 0.05f), strokeWidth = h * 0.14f
        )
        drawPath(
            Path().apply {
                moveTo(x - h * 0.95f, y - h * 0.25f)
                lineTo(x - h * 2.1f, y - h * 0.7f)
                lineTo(x - h * 0.95f, y + h * 0.2f)
                close()
            },
            Color(0xFF23795A)
        )
    }
}

// ---- les événements --------------------------------------------------------

/**
 * Une comète. Une vingtaine de soirs par siècle.
 *
 * Rien à voir avec l'étoile filante : celle-là met une minute à traverser au lieu d'une
 * demi-seconde, elle a une tête nette, une queue fourchue, et elle est ÉNORME. C'est la
 * chose la plus rare du décor, et il faut que ça se voie au premier coup d'œil.
 */
private fun DrawScope.comet(horizon: Float, p: Float, dir: Float) {
    val x = -size.width * 0.2f + p * size.width * 1.4f
    val y = horizon * (0.44f - p * 0.2f)
    val r = size.height * 0.008f
    // La queue s'allonge à l'approche puis se rétracte : elle est poussée par le vent
    // solaire, elle ne suit pas la comète comme une écharpe.
    val tail = size.width * 0.30f * (0.5f + sin(p * PI).toFloat() * 0.5f)
    val inks = listOf(Color(0xFF9FE8FF), Color(0xFFDFF6FF), Color(0xFFBFA8FF))

    facing(Offset(x, y), dir) {
        listOf(-0.22f, 0f, 0.2f).forEachIndexed { i, spread ->
            drawPath(
                Path().apply {
                    moveTo(x, y)
                    quadraticBezierTo(
                        x - tail * 0.5f, y - tail * (0.18f + spread),
                        x - tail, y - tail * (0.34f + spread * 1.6f)
                    )
                    quadraticBezierTo(x - tail * 0.5f, y - tail * (0.10f + spread), x, y + r)
                    close()
                },
                inks[i].copy(alpha = 0.20f + 0.10f * sin(p * PI).toFloat())
            )
        }
        drawCircle(Color(0xFFBFEEFF).copy(alpha = 0.35f), r * 5.5f, Offset(x, y))
        drawCircle(Color(0xFFEAFBFF), r * 1.8f, Offset(x, y))
        drawCircle(Color.White, r * 0.9f, Offset(x, y))
    }
}

/** Un avion de nuit : une silhouette minuscule et deux feux qui clignotent. */
private fun DrawScope.plane(horizon: Float, p: Float, dir: Float, t: Float, rng: Random) {
    val x = -size.width * 0.12f + p * size.width * 1.24f
    val y = horizon * (0.16f + rng.nextFloat() * 0.22f)
    val s = size.height * 0.005f
    val ink = Color(0xFF2A3050)

    facing(Offset(x, y), dir) {
        // La traînée de condensation, qui s'efface derrière lui.
        repeat(12) { k ->
            drawCircle(
                Color.White.copy(alpha = 0.10f * (1f - k / 12f)),
                s * (0.5f + k * 0.11f), Offset(x - s * (2.5f + k * 3.4f), y)
            )
        }
        drawOval(ink, Offset(x - s * 2.2f, y - s * 0.34f), Size(s * 4.4f, s * 0.68f))
        drawPath(
            Path().apply {
                moveTo(x - s * 0.2f, y)
                lineTo(x - s * 1.9f, y - s * 1.5f)
                lineTo(x - s * 1.1f, y)
                lineTo(x - s * 1.9f, y + s * 1.5f)
                close()
            },
            ink
        )
        drawPath(
            Path().apply {
                moveTo(x - s * 1.9f, y - s * 0.2f)
                lineTo(x - s * 2.6f, y - s * 0.95f)
                lineTo(x - s * 1.9f, y + s * 0.1f)
                close()
            },
            ink
        )
        // Les feux réglementaires : rouge à bâbord, vert à tribord, un flash blanc.
        val blink = if ((t * 8f) % 1f < 0.16f) 1f else 0.15f
        drawCircle(Color(0xFFFF4A4A).copy(alpha = blink), s * 0.5f, Offset(x - s * 1.9f, y - s * 1.4f))
        drawCircle(Color(0xFF4AFF7A).copy(alpha = blink), s * 0.5f, Offset(x - s * 1.9f, y + s * 1.4f))
        drawCircle(
            Color.White.copy(alpha = if ((t * 8f + 0.5f) % 1f < 0.1f) 1f else 0f),
            s * 0.6f, Offset(x + s * 2.2f, y)
        )
    }
}

/**
 * Des lanternes de papier qui montent. Le plus doux des trois événements.
 *
 * Elles ne traversent pas : elles s'élèvent, dérivent au vent et sortent par le haut.
 * Chacune a son retard, sinon c'est une grappe de ballons.
 */
private fun DrawScope.lanterns(horizon: Float, p: Float, t: Float, rng: Random) {
    repeat(9) { k ->
        val lag = rng.nextFloat() * 0.35f
        val lane = rng.nextFloat()
        val s = size.height * (0.006f + rng.nextFloat() * 0.005f)
        val u = ((p - lag) / (1f - lag)).coerceIn(0f, 1f)
        if (u <= 0f) return@repeat
        val y = horizon * (1.02f - u * 1.15f)
        val sway = sin(2f * PI.toFloat() * (t * 2f + k * 0.4f)).toFloat()
        val x = size.width * (0.08f + lane * 0.84f) + sway * s * 2.5f + u * size.width * 0.06f
        // Elles s'éteignent en montant : une lanterne qui sort du cadre à pleine
        // intensité donne l'impression que l'animation a été coupée au montage.
        val a = (1f - u).coerceIn(0f, 1f).pow(0.6f)

        drawCircle(Color(0xFFFFC46B).copy(alpha = 0.20f * a), s * 3.4f, Offset(x, y))
        drawPath(
            Path().apply {
                moveTo(x - s, y - s * 1.2f)
                quadraticBezierTo(x - s * 1.25f, y + s * 0.3f, x - s * 0.62f, y + s * 1.1f)
                lineTo(x + s * 0.62f, y + s * 1.1f)
                quadraticBezierTo(x + s * 1.25f, y + s * 0.3f, x + s, y - s * 1.2f)
                quadraticBezierTo(x, y - s * 1.7f, x - s, y - s * 1.2f)
                close()
            },
            Color(0xFFFFD98A).copy(alpha = 0.92f * a)
        )
        drawCircle(Color(0xFFFFF3C4).copy(alpha = a), s * 0.42f, Offset(x, y + s * 0.55f))
        drawLine(
            Color(0xFF8A6B3A).copy(alpha = 0.7f * a),
            Offset(x - s * 0.62f, y + s * 1.1f), Offset(x + s * 0.62f, y + s * 1.1f),
            strokeWidth = s * 0.16f
        )
    }
}

// ---- les essences ----------------------------------------------------------

/** Un bouleau : tronc crème, chevrons noirs, couronne légère et haut perchée. */
private fun DrawScope.birch(
    x: Float, base: Float, h: Float, night: Float, lean: Float, leaf: Color?
) {
    val bark = lerp(Color(0xFFF2EADC), Color(0xFF1A2036), night * 0.72f)
    val mark = lerp(Color(0xFF3E3A38), Color(0xFF0C1226), night * 0.6f)
    val w = h * 0.045f

    drawPath(
        Path().apply {
            moveTo(x - w, base)
            lineTo(x - w * 0.6f + lean, base - h)
            lineTo(x + w * 0.6f + lean, base - h)
            lineTo(x + w, base)
            close()
        },
        bark
    )
    // Les chevrons : c'est la SEULE chose qui distingue un bouleau d'un poteau pâle.
    repeat(5) { k ->
        val u = 0.15f + k * 0.16f
        val y = base - h * u
        val dx = lean * u
        drawLine(
            mark, Offset(x - w * 0.8f + dx, y), Offset(x - w * 0.1f + dx, y - h * 0.014f),
            strokeWidth = w * 0.45f, cap = StrokeCap.Round
        )
        if (k % 2 == 0) {
            drawLine(
                mark, Offset(x + w * 0.2f + dx, y + h * 0.03f),
                Offset(x + w * 0.75f + dx, y + h * 0.022f),
                strokeWidth = w * 0.35f, cap = StrokeCap.Round
            )
        }
    }
    // Les branches fines, tout en haut : un bouleau n'a rien sur les deux tiers du bas.
    val twig = lerp(Color(0xFF6E5F4E), Color(0xFF0C1226), night * 0.7f)
    listOf(-1f, 1f).forEach { d ->
        drawLine(
            twig, Offset(x + lean * 0.82f, base - h * 0.82f),
            Offset(x + lean + d * h * 0.16f, base - h * 0.98f), strokeWidth = w * 0.3f
        )
    }
    if (leaf == null) return
    listOf(-0.13f to 0.2f, 0.02f to 0.26f, 0.14f to 0.19f).forEach { (dx, s) ->
        drawCircle(
            leaf.copy(alpha = 0.88f), h * s,
            Offset(x + lean + h * dx, base - h * (0.92f + s * 0.1f))
        )
    }
}

/** Un érable : couronne large et basse, et une vraie fourche visible dedans. */
private fun DrawScope.maple(
    x: Float, base: Float, h: Float, wood: Color, night: Float, lean: Float, leaf: Color?
) {
    val w = h * 0.09f
    drawPath(
        Path().apply {
            moveTo(x - w, base)
            // Le pied s'évase : un tronc à bords parallèles est un tuyau.
            quadraticBezierTo(x - w * 0.42f, base - h * 0.24f, x - w * 0.3f + lean * 0.5f, base - h * 0.5f)
            lineTo(x + w * 0.3f + lean * 0.5f, base - h * 0.5f)
            quadraticBezierTo(x + w * 0.42f, base - h * 0.24f, x + w, base)
            close()
        },
        wood
    )
    // Trois charpentières, qui se divisent chacune une fois.
    listOf(-1f, -0.25f, 1f).forEach { d ->
        val ex = x + lean * 0.85f + d * h * 0.24f
        val ey = base - h * 0.74f
        drawLine(
            wood, Offset(x + lean * 0.5f, base - h * 0.48f), Offset(ex, ey),
            strokeWidth = w * 0.42f, cap = StrokeCap.Round
        )
        drawLine(
            wood, Offset(ex, ey), Offset(ex + d * h * 0.09f, ey - h * 0.12f),
            strokeWidth = w * 0.24f, cap = StrokeCap.Round
        )
    }
    if (leaf == null) return
    // La couronne : sept touffes qui se recouvrent, plus claires en haut à gauche. Trois
    // cercles alignés donnaient un nuage de bande dessinée ; c'est le recouvrement
    // irrégulier qui fait le feuillage.
    val lit = lerp(leaf, Color.White, 0.16f)
    listOf(
        Triple(-0.30f, 0.80f, 0.24f), Triple(-0.10f, 0.94f, 0.28f),
        Triple(0.16f, 0.88f, 0.26f), Triple(0.32f, 0.74f, 0.21f),
        Triple(-0.22f, 0.66f, 0.21f), Triple(0.06f, 0.70f, 0.24f),
        Triple(0.00f, 1.02f, 0.18f)
    ).forEach { (dx, uy, r) ->
        drawCircle(leaf, h * r, Offset(x + lean + h * dx, base - h * uy))
    }
    listOf(Triple(-0.20f, 0.92f, 0.15f), Triple(0.04f, 0.98f, 0.12f)).forEach { (dx, uy, r) ->
        drawCircle(lit, h * r, Offset(x + lean + h * dx, base - h * uy))
    }
}

/** Un arbre de verger : petit, rond, taillé court — et chargé quand c'est la saison. */
private fun DrawScope.orchardTree(
    x: Float, base: Float, h: Float, wood: Color, m: Sky.Moment, night: Float, lean: Float
) {
    val w = h * 0.09f
    drawRect(wood, Offset(x - w / 2f, base - h * 0.46f), Size(w, h * 0.47f))
    listOf(-1f, 1f).forEach { d ->
        drawLine(
            wood, Offset(x, base - h * 0.42f), Offset(x + lean * 0.6f + d * h * 0.15f, base - h * 0.6f),
            strokeWidth = w * 0.5f, cap = StrokeCap.Round
        )
    }
    when (m.season) {
        Sky.Season.WINTER -> return
        Sky.Season.SPRING -> {
            // En fleurs : blanc rosé, et quelques pétales plus clairs par-dessus.
            val bloom = lerp(Color(0xFFFBE3EE), Color(0xFF0C1226), night * 0.72f)
            drawCircle(bloom, h * 0.36f, Offset(x + lean, base - h * 0.78f))
            drawCircle(
                lerp(Color(0xFFFFF6FA), Color(0xFF0C1226), night * 0.72f),
                h * 0.16f, Offset(x + lean - h * 0.12f, base - h * 0.88f)
            )
        }
        else -> {
            val leaf = lerp(
                if (m.season == Sky.Season.AUTUMN) Color(0xFF5F8F3E) else Color(0xFF3C9A4E),
                Color(0xFF0C1226), night * 0.78f
            )
            drawCircle(leaf, h * 0.36f, Offset(x + lean, base - h * 0.78f))
            drawCircle(lerp(leaf, Color.White, 0.14f), h * 0.15f, Offset(x + lean - h * 0.12f, base - h * 0.88f))
            // Les pommes : rouges, petites, et seulement l'automne. Un verger chargé au
            // mois de juin serait joli et faux.
            if (m.season == Sky.Season.AUTUMN) {
                val fruit = lerp(Color(0xFFD8343E), Color(0xFF0C1226), night * 0.72f)
                listOf(-0.2f to 0.72f, 0.16f to 0.82f, 0.0f to 0.64f).forEach { (dx, uy) ->
                    drawCircle(fruit, h * 0.055f, Offset(x + lean + h * dx, base - h * uy))
                }
            }
        }
    }
}

// ---- les maisons -----------------------------------------------------------

/**
 * La maison du jour, une par saison, et jamais tous les jours.
 *
 * Le chalet d'hiver restait planté là du premier flocon au dégel. À force, on ne le
 * voyait plus : ce n'était plus un chalet, c'était une forme dans le coin gauche. Il
 * revient maintenant un peu plus d'un jour sur deux, et il change de place, de taille et
 * de couleur — de sorte que le matin où il est là, on le remarque.
 */
private fun DrawScope.house(
    kind: House, m: Sky.Moment,
    horizon: Float, depth: Float, t: Float, night: Float
) {
    val rng = Random(m.day * 733 + 19)
    val w = size.width * (0.15f + rng.nextFloat() * 0.06f)
    // Elle déménage : parfois à gauche, parfois à droite, jamais au milieu — le centre
    // est réservé au dragon.
    val x = if (rng.nextBoolean()) size.width * (0.03f + rng.nextFloat() * 0.13f)
    else size.width * (0.66f + rng.nextFloat() * 0.14f)
    val h = w * 0.62f
    val base = horizon + 2f

    val wall = when (kind) {
        House.CABIN -> Color(0xFF8A5A3C)
        House.COTTAGE -> Color(0xFFF4EADC)
        House.HALLOWEEN -> Color(0xFFE07E2A)
    }
    val trim = when (kind) {
        House.CABIN -> Color(0xFF6B3F2A)
        House.COTTAGE -> Color(0xFF7FA8B8)
        House.HALLOWEEN -> Color(0xFF7A3F62)
    }

    drawRect(dim(wall, night), Offset(x, base - h), Size(w, h))
    if (kind == House.CABIN) {
        // Les rondins : quatre lignes horizontales. Sans elles, c'est une boîte brune.
        repeat(4) { k ->
            drawLine(
                dim(trim, night).copy(alpha = 0.5f),
                Offset(x, base - h * (0.18f + k * 0.2f)),
                Offset(x + w, base - h * (0.18f + k * 0.2f)),
                strokeWidth = h * 0.03f
            )
        }
    }

    // Le toit dépasse des deux côtés, sinon la maison lit comme une boîte.
    val roof = Path().apply {
        moveTo(x - w * 0.14f, base - h)
        lineTo(x + w / 2f, base - h * 1.62f)
        lineTo(x + w * 1.14f, base - h)
        close()
    }
    drawPath(roof, dim(trim, night))
    if (m.season == Sky.Season.WINTER) {
        // La neige sur le toit, avec des glaçons au bord : c'est la moitié de l'effet
        // « cosy », parce qu'elle dit qu'il fait froid DEHORS.
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
        repeat(6) { k ->
            val ix = x - w * 0.1f + w * 0.22f * k
            val il = h * (0.08f + (k % 3) * 0.05f)
            drawPath(
                Path().apply {
                    moveTo(ix, base - h * 1.02f)
                    lineTo(ix + w * 0.035f, base - h * 1.02f)
                    lineTo(ix + w * 0.017f, base - h * 1.02f + il)
                    close()
                },
                Color(0xFFDCEEFB).copy(alpha = 0.9f)
            )
        }
    }

    // La fenêtre chaude : c'est elle, et rien d'autre, qui rend une maison accueillante.
    val glow = 0.75f + 0.25f * sin(t * 6f * 2f * PI).toFloat()
    drawRect(
        Color(0xFFFFC65A).copy(alpha = 0.30f * glow),
        Offset(x + w * 0.14f, base - h * 0.88f), Size(w * 0.52f, h * 0.6f)
    )
    drawRect(
        Color(0xFFFFD98A).copy(alpha = (0.75f + 0.25f * glow).coerceAtMost(1f)),
        Offset(x + w * 0.22f, base - h * 0.8f), Size(w * 0.34f, h * 0.44f)
    )
    // Les croisillons : une fenêtre sans montants est un rectangle jaune.
    val mullion = dim(trim, night)
    drawLine(
        mullion, Offset(x + w * 0.39f, base - h * 0.8f), Offset(x + w * 0.39f, base - h * 0.36f),
        strokeWidth = w * 0.018f
    )
    drawLine(
        mullion, Offset(x + w * 0.22f, base - h * 0.58f), Offset(x + w * 0.56f, base - h * 0.58f),
        strokeWidth = w * 0.018f
    )

    // La porte, et sa couronne : le détail qui dit que quelqu'un tient à cette maison.
    drawRoundRect(
        mullion, Offset(x + w * 0.7f, base - h * 0.52f), Size(w * 0.2f, h * 0.52f),
        CornerRadius(w * 0.03f)
    )
    val wreath = when (kind) {
        House.CABIN -> Color(0xFF2F7A46)
        House.HALLOWEEN -> Color(0xFF7A3F62)
        House.COTTAGE -> Color(0xFFE8739F)
    }
    drawCircle(
        dim(wreath, night), w * 0.05f, Offset(x + w * 0.8f, base - h * 0.36f),
        style = Stroke(width = w * 0.026f)
    )

    when (kind) {
        House.CABIN -> {
            // La corde de bois, empilée contre le mur : trois rangées de rondelles.
            val log = dim(Color(0xFFC79A6A), night)
            repeat(3) { row ->
                repeat(4) { col ->
                    drawCircle(
                        log, w * 0.028f,
                        Offset(x - w * 0.11f + col * w * 0.058f, base - h * (0.08f + row * 0.12f))
                    )
                }
            }
            // Un fanal accroché près de la porte, qui vacille tout seul.
            val flick = 0.6f + 0.4f * sin(t * 11f * 2f * PI).toFloat()
            drawCircle(
                Color(0xFFFFD98A).copy(alpha = 0.35f * flick), w * 0.06f,
                Offset(x + w * 0.98f, base - h * 0.6f)
            )
            drawCircle(Color(0xFFFFF0C4).copy(alpha = flick), w * 0.022f, Offset(x + w * 0.98f, base - h * 0.6f))
        }

        House.COTTAGE -> {
            // La jardinière sous la fenêtre, et une corde à linge qui pend.
            drawRect(dim(trim, night), Offset(x + w * 0.14f, base - h * 0.34f), Size(w * 0.52f, h * 0.1f))
            val petals = listOf(Color(0xFFFF8FC6), Color(0xFFFFD166), Color(0xFFFFFFFF))
            repeat(6) { k ->
                drawCircle(
                    dim(petals[k % petals.size], night), w * 0.028f,
                    Offset(x + w * (0.18f + k * 0.085f), base - h * 0.38f)
                )
            }
            // Les volets ouverts, de part et d'autre.
            listOf(0.08f, 0.66f).forEach { o ->
                drawRect(dim(trim, night), Offset(x + w * o, base - h * 0.86f), Size(w * 0.06f, h * 0.5f))
            }
        }

        House.HALLOWEEN -> {
            // Deux citrouilles allumées sur le pas de la porte, et une guirlande orange
            // le long du toit. L'automne a droit à sa maison, pas seulement à ses feuilles.
            repeat(2) { k ->
                val px = x + w * (0.62f + k * 0.28f)
                val pr = w * 0.055f
                val flick = 0.55f + 0.45f * sin((t * 9f + k * 0.5f) * 2f * PI).toFloat()
                drawOval(dim(Color(0xFFE07E2A), night), Offset(px - pr, base - pr * 1.6f), Size(pr * 2f, pr * 1.7f))
                drawCircle(Color(0xFFFFD98A).copy(alpha = 0.7f * flick), pr * 0.45f, Offset(px, base - pr * 0.8f))
            }
            repeat(9) { k ->
                val u = k / 8f
                val lx = x - w * 0.1f + w * 1.2f * u
                val ly = base - h * (1.02f + 0.56f * (1f - abs(u - 0.5f) * 2f))
                val pulse = 0.5f + 0.5f * sin((t * 4f + k * 0.5f) * 2f * PI).toFloat()
                drawCircle(Color(0xFFFF9A3C).copy(alpha = 0.9f * pulse), w * 0.016f, Offset(lx, ly))
            }
        }
    }

    // La cheminée et sa fumée, qui monte en s'élargissant. Elle ne fume que quand ça a du
    // sens : personne ne chauffe le poêle un après-midi de juillet.
    val cx = x + w * 0.82f
    drawRect(dim(trim, night), Offset(cx, base - h * 1.46f), Size(w * 0.12f, h * 0.42f))
    val smoking = m.season == Sky.Season.WINTER || m.season == Sky.Season.AUTUMN || night > 0.5f
    if (smoking) {
        repeat(5) { k ->
            val p = (t * 2f + k * 0.2f) % 1f
            val py = base - h * 1.5f - p * h * 1.6f
            drawCircle(
                Color(0xFFEFEFF6).copy(alpha = 0.36f * (1f - p)),
                w * (0.05f + p * 0.11f),
                Offset(cx + w * 0.06f + sin(p * 3f * PI).toFloat() * w * (0.06f + m.wind * 0.3f), py)
            )
        }
    }
}

/**
 * Une ruche et ses abeilles.
 *
 * Le vieux panier de paille plutôt que la boîte carrée : la boîte est ce qu'on utilise
 * vraiment, mais à quarante pixels de haut elle se lit comme une commode. Les anneaux du
 * panier se lisent, eux, tout de suite.
 *
 * Les abeilles tournent AUTOUR, chacune sur son orbite et à sa vitesse. Une abeille qui
 * traverse en ligne droite est une mouche.
 */
private fun DrawScope.beehive(m: Sky.Moment, horizon: Float, depth: Float, t: Float, night: Float) {
    val rng = Random(m.day * 617 + 41)
    val base = horizon + 3f
    val x = size.width * (0.24f + rng.nextFloat() * 0.52f)
    val r = depth * 0.36f
    val straw = dim(Color(0xFFD9A94E), night)
    val strawD = dim(Color(0xFFB0842F), night)

    // Le panier : quatre anneaux de largeur décroissante.
    repeat(4) { k ->
        val rw = r * (1f - k * 0.16f)
        val ry = base - r * 0.42f * k
        drawRoundRect(
            if (k % 2 == 0) straw else strawD,
            Offset(x - rw, ry - r * 0.44f), Size(rw * 2f, r * 0.48f),
            CornerRadius(r * 0.24f)
        )
    }
    // Le trou d'envol, en bas.
    drawOval(Color(0xFF3A2A14), Offset(x - r * 0.2f, base - r * 0.36f), Size(r * 0.4f, r * 0.26f))

    // Les abeilles. Le corps fait trois pixels : ce sont les RAYURES et le vol nerveux
    // qui font l'abeille, pas la silhouette.
    val gold = Color(0xFFF2C230)
    repeat(7) { k ->
        val orbit = r * (1.2f + k * 0.24f)
        val speed = 2f + k % 3
        val a = 2f * PI.toFloat() * ((t * speed + k * 0.37f) % 1f)
        val bx = x + cos(a) * orbit
        val by = base - r * 1.1f + sin(a) * orbit * 0.42f +
            sin(2f * PI.toFloat() * (t * 8f + k)).toFloat() * r * 0.1f
        val s = depth * 0.05f
        drawOval(Color(0xFFDDE9F2).copy(alpha = 0.55f), Offset(bx - s, by - s * 0.9f), Size(s * 2f, s * 0.8f))
        drawOval(gold, Offset(bx - s * 0.7f, by - s * 0.42f), Size(s * 1.4f, s * 0.84f))
        drawLine(
            Color(0xFF2A2114), Offset(bx - s * 0.12f, by - s * 0.42f),
            Offset(bx - s * 0.12f, by + s * 0.42f), strokeWidth = s * 0.26f
        )
        drawLine(
            Color(0xFF2A2114), Offset(bx + s * 0.34f, by - s * 0.36f),
            Offset(bx + s * 0.34f, by + s * 0.36f), strokeWidth = s * 0.24f
        )
    }
}

/**
 * Un ours noir, celui des fins d'automne.
 *
 * Massif et BAS : ce qui distingue un ours d'un gros chien, c'est le garrot plus bas que
 * la croupe, la tête portée en avant du poitrail, et une démarche où tout le corps roule.
 * Il avance lentement, s'arrête pour renifler, et repart.
 */
private fun DrawScope.bear(horizon: Float, p: Float, dir: Float, rng: Random, night: Float) {
    val h = size.height * 0.030f * (0.9f + rng.nextFloat() * 0.22f)
    val base = horizon + 3f
    val x = -size.width * 0.12f + p * size.width * 1.24f
    val fur = dim(Color(0xFF3A3038), night)
    val far = dim(Color(0xFF2A2229), night)
    val muzzle = dim(Color(0xFF8A6B4E), night)
    val amble = sin(p * 18f * 2f * PI).toFloat()
    // Il s'arrête deux fois pour renifler : la tête descend, le corps se tasse.
    val sniff = ((sin(p * 4f * PI).toFloat() - 0.7f) / 0.3f).coerceIn(0f, 1f)

    fun paw(dx: Float, swing: Float, c: Color) {
        val top = Offset(x + h * dx, base - h * 0.78f)
        val foot = Offset(x + h * (dx + swing * 0.22f), base)
        drawLine(c, top, foot, strokeWidth = h * 0.24f, cap = StrokeCap.Round)
        drawOval(c, Offset(foot.x - h * 0.22f, base - h * 0.1f), Size(h * 0.44f, h * 0.16f))
    }

    facing(Offset(x, base), dir) {
        paw(-0.5f, -amble, far)
        paw(0.6f, amble, far)

        // Le corps : croupe haute à l'arrière, épaule qui plonge vers l'avant.
        drawPath(
            Path().apply {
                moveTo(x - h * 1.05f, base - h * 1.02f)
                quadraticBezierTo(x - h * 0.5f, base - h * 1.24f, x + h * 0.2f, base - h * 1.06f)
                quadraticBezierTo(x + h * 0.82f, base - h * 0.94f, x + h * 0.95f, base - h * 0.6f)
                quadraticBezierTo(x, base - h * 0.42f, x - h * 0.98f, base - h * 0.62f)
                quadraticBezierTo(x - h * 1.2f, base - h * 0.82f, x - h * 1.05f, base - h * 1.02f)
                close()
            },
            fur
        )

        // La tête, portée bas et en avant. Elle descend encore quand il renifle.
        val hy = base - h * (1.14f - sniff * 0.32f)
        drawOval(fur, Offset(x + h * 0.62f, hy - h * 0.42f), Size(h * 0.92f, h * 0.78f))
        drawOval(muzzle, Offset(x + h * 1.24f, hy - h * 0.1f), Size(h * 0.44f, h * 0.3f))
        drawCircle(Color(0xFF141014), h * 0.09f, Offset(x + h * 1.6f, hy + h * 0.02f))
        drawCircle(Color(0xFF141014), h * 0.055f, Offset(x + h * 1.16f, hy - h * 0.2f))
        // Les deux oreilles rondes, bien écartées : la signature de l'ours noir.
        listOf(0.62f, 0.98f).forEach { o ->
            drawCircle(fur, h * 0.17f, Offset(x + h * o, hy - h * 0.48f))
            drawCircle(far, h * 0.09f, Offset(x + h * o, hy - h * 0.48f))
        }

        paw(-0.62f, amble, fur)
        paw(0.48f, -amble, fur)
    }
}
