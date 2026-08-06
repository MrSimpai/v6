package com.example.medtap.reminder

import android.graphics.*
import com.example.medtap.ui.Dragon
import com.example.medtap.ui.Mood
import java.util.Calendar
import kotlin.random.Random

/**
 * Tout ce qui est peint pour l'extérieur de l'app : la bannière des notifications, le
 * décor du widget, la pastille de série et la semaine.
 *
 * La bannière dessinée plutôt qu'écrite est la raison pour laquelle les rappels de
 * Duolingo se lisent comme un personnage qui parle et pas comme un message système.
 * Le décor change avec l'humeur — nuit étoilée quand il n'y a rien à faire, coucher de
 * soleil quand c'est l'heure, braises quand ça traîne — donc le rappel a une couleur
 * avant même d'avoir été lu.
 */
object NotifArt {

    /**
     * L'échelle de couleurs du décor, dans l'ordre où on la monte.
     *
     * C'est le mécanisme de Duolingo : la tuile ne change pas seulement de texte quand
     * ça traîne, elle change de température. Turquoise, bleu, indigo, magenta, rouge —
     * on voit qu'on est en retard depuis l'autre bout de la pièce, sans avoir rien lu.
     *
     * Le repos a deux versions parce qu'une lune en plein après-midi est le genre de
     * détail qui fait comprendre d'un coup que le dessin ne regarde pas l'heure.
     */
    enum class Vibe { REST_DAY, REST_NIGHT, WIN, DUE, NUDGE, SULK, DRAMA, ANGRY }

    private class Skin(
        val sky: Int,
        val ground: Int,
        /** Ce qui se pose PAR-DESSUS le ciel : lune, soleil, braises. */
        val glow: Int
    )

    private fun skin(v: Vibe): Skin = when (v) {
        // Rien à prendre, en plein jour : ciel clair, soleil, deux nuages.
        Vibe.REST_DAY   -> Skin(0xFF4FB4E8.toInt(), 0xFF4EBE8A.toInt(), 0xFFFFF6D2.toInt())
        // Rien à prendre, la nuit : la seule ambiance qui n'appelle à rien.
        Vibe.REST_NIGHT -> Skin(0xFF262A63.toInt(), 0xFF5B4E96.toInt(), 0xFFFFF3C4.toInt())
        // C'est noté : menthe, confettis.
        Vibe.WIN        -> Skin(0xFF2FA98D.toInt(), 0xFF1E7F76.toInt(), 0xFFFFFFFF.toInt())
        // L'heure vient d'arriver. Encore neutre : il n'y a rien à se reprocher.
        Vibe.DUE        -> Skin(0xFF2F9FD8.toInt(), 0xFF2C6CC4.toInt(), 0xFFDCF0FF.toInt())
        // Dix minutes. Le premier cran de couleur, à peine.
        Vibe.NUDGE      -> Skin(0xFF6350C6.toInt(), 0xFF8E3FBF.toInt(), 0xFFE7D3F5.toInt())
        // Une demi-heure. Ça vire au magenta.
        Vibe.SULK       -> Skin(0xFF9B3AAE.toInt(), 0xFFC92F79.toInt(), 0xFFF3D3E8.toInt())
        // Une heure. Rouge, et il pleut.
        Vibe.DRAMA      -> Skin(0xFFC42E5C.toInt(), 0xFFD9452F.toInt(), 0xFFFFD9C4.toInt())
        // Deux heures : braises sur fond bordeaux. Le dernier cran, et ça se voit.
        Vibe.ANGRY      -> Skin(0xFF6B0C1C.toInt(), 0xFFB82A1B.toInt(), 0xFFFFB65C.toInt())
    }

    /** Nuit de 20h à 6h : c'est là que la lune remplace le soleil. */
    fun isNight(now: Long = System.currentTimeMillis()): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return hour >= 20 || hour < 6
    }

    /**
     * L'ambiance du moment. [lateMin] n'est lu que si une dose est effectivement due —
     * les paliers viennent de [Tier] plutôt que d'une seconde liste de seuils, sinon le
     * décor et le texte du rappel finiraient par escalader à des minutes différentes.
     */
    fun vibeFor(mood: Mood, lateMin: Long, now: Long = System.currentTimeMillis()): Vibe =
        when (mood) {
            Mood.Cheering -> Vibe.WIN
            Mood.Sleeping -> if (isNight(now)) Vibe.REST_NIGHT else Vibe.REST_DAY
            else -> vibeFor(Tier.forLateness(lateMin))
        }

    /** Le palier du rappel, traduit en décor : la bannière connaît son [Tier], pas l'heure. */
    fun vibeFor(tier: Tier): Vibe = when (tier) {
        Tier.PONCTUEL -> Vibe.DUE
        Tier.RELANCE  -> Vibe.NUDGE
        Tier.BOUDERIE -> Vibe.SULK
        Tier.DRAME    -> Vibe.DRAMA
        Tier.SERIEUX  -> Vibe.ANGRY
    }

    // ---- le décor ---------------------------------------------------------

    /**
     * Le paysage, peint sur toute la surface qu'on lui donne.
     *
     * Une forme XML ne sait faire qu'un dégradé : posée sur un fond d'écran ça reste un
     * rectangle inerte. Ici il y a une lune, des braises, de la pluie — et ça vaut la
     * peine parce que c'est ce qu'on regarde toute la journée sur l'écran d'accueil.
     */
    private fun scene(c: Canvas, w: Float, h: Float, v: Vibe, night: Boolean, radius: Float) {
        val sk = skin(v)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
        val box = RectF(0f, 0f, w, h)

        p.shader = LinearGradient(0f, 0f, w * 0.3f, h, sk.sky, sk.ground, Shader.TileMode.CLAMP)
        if (radius > 0f) c.drawRoundRect(box, radius, radius, p) else c.drawRect(box, p)
        p.shader = null

        c.save()
        if (radius > 0f) {
            c.clipPath(Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) })
        }

        val u = minOf(w, h) / 400f          // l'unité : le décor suit la plus petite dimension
        val rng = Random(v.ordinal * 31 + 7)

        when (v) {
            Vibe.REST_DAY   -> { sun(c, p, w, h, u, sk.glow); clouds(c, p, w, h) }
            Vibe.REST_NIGHT -> { moon(c, p, w, h, u, sk.glow); stars(c, p, w, h, u, rng) }
            Vibe.WIN        -> {
                halo(c, p, w * 0.5f, h * 0.42f, minOf(w, h) * 0.52f, 0x33FFFFFF)
                repeat(14) {
                    p.color = 0x99FFFFFF.toInt()
                    sparkle(
                        c, p, rng.nextFloat() * w, rng.nextFloat() * h * 0.82f,
                        (3f + rng.nextFloat() * 5f) * u
                    )
                }
            }
            // Les paliers de retard n'ont ni soleil ni lune : ils empruntent au ciel du
            // moment ce qu'il a de discret, et gardent la couleur pour dire le retard.
            Vibe.DUE, Vibe.NUDGE -> if (night) stars(c, p, w, h, u, rng) else clouds(c, p, w, h)
            Vibe.SULK -> {
                if (night) stars(c, p, w, h, u, rng) else clouds(c, p, w, h)
                rain(c, p, w, h, u, rng, 10)
            }
            Vibe.DRAMA -> rain(c, p, w, h, u, rng, 20)
            Vibe.ANGRY -> {
                halo(c, p, w * 0.5f, h * 1.02f, h * 0.66f, 0x4DFFB65C)
                repeat(16) {                                      // les braises qui montent
                    val r = (2f + rng.nextFloat() * 4f) * u
                    p.color = if (rng.nextBoolean()) 0xB3FFC46B.toInt() else 0x73FFFFFF
                    c.drawCircle(rng.nextFloat() * w, h * 0.24f + rng.nextFloat() * h * 0.66f, r, p)
                }
            }
        }

        // écailles, très discrètes : la texture se sent plus qu'elle ne se voit
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * u
        p.color = 0x14FFFFFF
        var row = 0
        while (row * 52f * u < h) {
            var col = 0
            while (col * 58f * u < w + 58f * u) {
                val ox = col * 58f * u + (row % 2) * 29f * u
                c.drawArc(
                    RectF(ox, row * 52f * u, ox + 50f * u, row * 52f * u + 50f * u),
                    180f, 180f, false, p
                )
                col++
            }
            row++
        }
        p.style = Paint.Style.FILL

        // le sol : une bosse claire sur laquelle le dragon est posé, sinon il flotte
        p.color = 0x26FFFFFF
        c.drawOval(RectF(-w * 0.25f, h * 0.80f, w * 1.25f, h * 1.6f), p)

        // Un voile sombre sur toute la hauteur, plus dense en haut. Le texte du widget est
        // écrit à même le décor, et le décor va du bleu nuit au ciel de midi : sans ce
        // voile, aucune couleur d'encre ne tiendrait sur les huit ambiances. Avec, le
        // blanc marche partout, et c'est le message qui reste lisible quand la tuile change.
        p.shader = LinearGradient(
            0f, 0f, 0f, h, 0x5E000000, 0x1F000000, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        c.restore()
    }

    // ---- les éléments du ciel ---------------------------------------------

    private fun sun(c: Canvas, p: Paint, w: Float, h: Float, u: Float, glow: Int) {
        halo(c, p, w * 0.82f, h * 0.20f, 84f * u, 0x47FFFFFF)
        p.color = glow
        c.drawCircle(w * 0.82f, h * 0.20f, 32f * u, p)
    }

    private fun moon(c: Canvas, p: Paint, w: Float, h: Float, u: Float, glow: Int) {
        halo(c, p, w * 0.80f, h * 0.20f, 62f * u, 0x33FFFFFF)
        // Le croissant est une différence de deux disques et non un disque repeint
        // par-dessus : le ciel est un dégradé, donc aucune couleur unie ne saurait
        // refermer la morsure sans laisser une tache.
        p.color = glow
        val disc = Path().apply { addCircle(w * 0.80f, h * 0.20f, 30f * u, Path.Direction.CW) }
        val bite = Path().apply { addCircle(w * 0.71f, h * 0.155f, 27f * u, Path.Direction.CW) }
        disc.op(bite, Path.Op.DIFFERENCE)
        c.drawPath(disc, p)
    }

    private fun stars(c: Canvas, p: Paint, w: Float, h: Float, u: Float, rng: Random) {
        p.color = 0xCCFFFFFF.toInt()
        repeat(16) {
            sparkle(
                c, p, rng.nextFloat() * w, rng.nextFloat() * h * 0.72f,
                (2.5f + rng.nextFloat() * 4.5f) * u
            )
        }
    }

    private fun clouds(c: Canvas, p: Paint, w: Float, h: Float) {
        p.color = 0x33FFFFFF
        c.drawRoundRect(RectF(w * 0.04f, h * 0.30f, w * 0.40f, h * 0.36f), h, h, p)
        c.drawRoundRect(RectF(w * 0.54f, h * 0.44f, w * 0.93f, h * 0.49f), h, h, p)
    }

    private fun rain(c: Canvas, p: Paint, w: Float, h: Float, u: Float, rng: Random, drops: Int) {
        p.strokeWidth = 2.4f * u
        p.strokeCap = Paint.Cap.ROUND
        p.style = Paint.Style.STROKE
        p.color = 0x66FFFFFF
        repeat(drops) {
            val x = rng.nextFloat() * w * 1.1f - w * 0.05f
            val y = h * 0.16f + rng.nextFloat() * h * 0.64f
            c.drawLine(x, y, x - 5f * u, y + 16f * u, p)
        }
        p.style = Paint.Style.FILL
    }

    /** Une lueur ronde qui s'éteint vers le bord : lune, soleil, chaleur des braises. */
    private fun halo(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float, tint: Int) {
        p.shader = RadialGradient(
            cx, cy, r, tint, tint and 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, p)
        p.shader = null
    }

    /** Le fond du widget : carré, coins arrondis découpés dans le bitmap lui-même. */
    fun widgetBg(px: Int, vibe: Vibe, night: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        scene(Canvas(bmp), px.toFloat(), px.toFloat(), vibe, night, px * 0.14f)
        return bmp
    }

    // ---- la pastille de série ---------------------------------------------

    /**
     * La flamme et le chiffre, comme sur le widget de Duolingo : une gélule sombre
     * translucide en haut à gauche, qui se lit sur n'importe quel décor.
     *
     * Peinte plutôt que composée en vues parce que le chiffre doit rester collé à la
     * flamme quelle que soit sa longueur, ce qu'un `TextView` avec un `drawableStart`
     * ne garantit pas d'un lanceur à l'autre.
     */
    fun streakPill(h: Int, streak: Int): Bitmap {
        val hh = h.toFloat()
        val label = streak.toString()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textSize = hh * 0.56f
            color = if (streak > 0) 0xFFFFFFFF.toInt() else 0xB3FFFFFF.toInt()
        }
        val flameW = hh * 0.50f
        val padX = hh * 0.24f
        val gap = hh * 0.11f
        val w = (padX * 2 + flameW + gap + text.measureText(label)).toInt().coerceAtLeast(h)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        val box = RectF(0f, 0f, w.toFloat(), hh)
        p.color = 0x59000000
        c.drawRoundRect(box, hh / 2f, hh / 2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = hh * 0.05f
        p.color = 0x40FFFFFF
        c.drawRoundRect(box.apply { inset(hh * 0.025f, hh * 0.025f) }, hh / 2f, hh / 2f, p)
        p.style = Paint.Style.FILL

        flame(c, p, padX, hh * 0.20f, flameW, hh * 0.62f, streak > 0)

        val fm = text.fontMetrics
        c.drawText(
            label, padX + flameW + gap,
            hh / 2f - (fm.ascent + fm.descent) / 2f, text
        )
        return bmp
    }

    private fun flame(c: Canvas, p: Paint, x: Float, y: Float, w: Float, h: Float, lit: Boolean) {
        val cx = x + w / 2f
        p.color = if (lit) 0xFFFF9600.toInt() else 0xFF8E93A0.toInt()
        Path().apply {
            moveTo(cx, y)
            cubicTo(cx + w * 0.56f, y + h * 0.32f, cx + w * 0.52f, y + h * 0.64f, cx + w * 0.30f, y + h * 0.86f)
            cubicTo(cx + w * 0.10f, y + h * 1.02f, cx - w * 0.10f, y + h * 1.02f, cx - w * 0.30f, y + h * 0.86f)
            cubicTo(cx - w * 0.52f, y + h * 0.66f, cx - w * 0.44f, y + h * 0.30f, cx - w * 0.08f, y + h * 0.08f)
            cubicTo(cx - w * 0.16f, y + h * 0.38f, cx - w * 0.02f, y + h * 0.44f, cx, y)
            close()
        }.also { c.drawPath(it, p) }

        p.color = if (lit) 0xFFFFD34E.toInt() else 0xFFB9BEC8.toInt()
        Path().apply {
            moveTo(cx, y + h * 0.40f)
            cubicTo(cx + w * 0.30f, y + h * 0.60f, cx + w * 0.26f, y + h * 0.82f, cx, y + h * 0.94f)
            cubicTo(cx - w * 0.26f, y + h * 0.82f, cx - w * 0.30f, y + h * 0.60f, cx, y + h * 0.40f)
            close()
        }.also { c.drawPath(it, p) }
    }

    // ---- la semaine -------------------------------------------------------

    /**
     * Les sept points de la semaine, lundi à gauche. En blanc et en transparence plutôt
     * qu'en couleurs : le décor derrière change cinq fois par jour, une palette fixe
     * finirait par se poser sur un fond de la même teinte et disparaître.
     */
    fun weekStrip(w: Int, h: Int, week: List<Int>): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (week.size != 7) return bmp

        val gap = w / 7f
        val cy = h / 2f
        week.forEachIndexed { i, state ->
            val cx = gap * i + gap / 2f
            // 0 DONE, 1 FROZEN, 2 TODAY, 3 MISSED, 4 FUTURE
            when (state) {
                0 -> { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(cx, cy, h * 0.30f, p) }
                1 -> {
                    p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.11f
                    p.color = 0xCCFFFFFF.toInt(); c.drawCircle(cx, cy, h * 0.27f, p)
                    p.style = Paint.Style.FILL
                }
                2 -> {
                    p.color = 0x40FFFFFF; c.drawCircle(cx, cy, h * 0.48f, p)
                    p.color = 0xFFFFFFFF.toInt(); c.drawCircle(cx, cy, h * 0.30f, p)
                }
                3 -> { p.color = 0x4DFFFFFF; c.drawCircle(cx, cy, h * 0.22f, p) }
                else -> { p.color = 0x2EFFFFFF; c.drawCircle(cx, cy, h * 0.18f, p) }
            }
        }
        return bmp
    }

    private fun sparkle(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        Path().apply {
            moveTo(cx, cy - r)
            quadTo(cx + r * 0.28f, cy - r * 0.28f, cx + r, cy)
            quadTo(cx + r * 0.28f, cy + r * 0.28f, cx, cy + r)
            quadTo(cx - r * 0.28f, cy + r * 0.28f, cx - r, cy)
            quadTo(cx - r * 0.28f, cy - r * 0.28f, cx, cy - r)
            close()
        }.also { c.drawPath(it, p) }
    }

    // ---- la bannière ------------------------------------------------------

    private const val BANNER_W = 1024
    private const val BANNER_MIN_H = 512

    /**
     * Android plafonne la hauteur d'une grande image de notification ; au-delà elle est
     * recadrée. 720 sur 1024 de large tient tout juste sous ce plafond une fois mise à
     * l'échelle, donc c'est la marge qu'on a pour laisser le texte respirer.
     */
    private const val BANNER_MAX_H = 720

    /**
     * Le rappel dessiné : le dragon à gauche, une bulle de bande dessinée à droite avec
     * tout le texte dedans.
     *
     * Le texte vit dans une bulle blanche et pas à même le décor parce que le décor
     * change de valeur selon l'humeur — braises sombres, lever de soleil clair — et
     * qu'aucune couleur d'encre unique ne se lit sur les deux. La bulle garantit le
     * contraste quoi qu'il arrive.
     *
     * Rien n'est jamais tronqué : la bannière grandit d'abord, et si le texte déborde
     * encore, la police rétrécit d'un cran. Une phrase coupée au milieu était le seul
     * vrai défaut de la version précédente.
     */
    fun banner(mood: Mood, vibe: Vibe, title: String, body: String): Bitmap {
        val w = BANNER_W.toFloat()
        val bubbleL = 368f
        val bubbleR = w - 34f
        val inset = 40f
        val maxText = bubbleR - bubbleL - inset * 2

        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            color = Dragon.Ink
        }
        val bodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = 0xFF6E4356.toInt()
        }

        var titleLines: List<String> = emptyList()
        var bodyLines: List<String> = emptyList()
        var titleStep = 0f
        var bodyStep = 0f
        var block = 0f

        for (k in listOf(1f, 0.9f, 0.8f, 0.72f, 0.64f, 0.56f)) {
            titleP.textSize = 64f * k
            bodyP.textSize = 46f * k
            titleStep = 76f * k
            bodyStep = 58f * k
            titleLines = wrap(title, titleP, maxText)
            bodyLines = if (body.isBlank()) emptyList() else wrap(body, bodyP, maxText)
            block = titleLines.size * titleStep +
                if (bodyLines.isEmpty()) 0f else 22f * k + bodyLines.size * bodyStep
            if (block + inset * 2 + 72f <= BANNER_MAX_H) break
        }

        val h = (block + inset * 2 + 72f)
            .coerceIn(BANNER_MIN_H.toFloat(), BANNER_MAX_H.toFloat())
        val bmp = Bitmap.createBitmap(BANNER_W, h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        scene(c, w, h, vibe, isNight(), 0f)

        // Le dragon, calé en bas à gauche, posé sur la bosse de sol du décor. Plafonné
        // à 348 : au-delà il passerait sous la bulle, et un dragon à moitié caché
        // derrière un rectangle blanc est pire qu'un dragon un peu plus petit.
        val dragonSize = minOf(h * 0.80f, 348f)
        val dragonY = h - dragonSize - h * 0.04f
        c.save()
        c.translate(14f, dragonY)
        Dragon.draw(c, mood, dragonSize)
        c.restore()

        // Ce qui flotte AU-DESSUS du dragon : une gélule quand il attend la dose, des
        // cœurs quand elle est prise. Au-dessus et pas à côté parce qu'il n'y a pas de
        // « à côté » : cornes et ailes occupent toute la largeur du dessin, et le ciel
        // au-dessus de sa tête est la seule zone libre entre lui et la bulle.
        val ds = dragonSize / 220f
        prop(c, p, vibe, 14f + dragonSize * 0.62f, dragonY * 0.52f, ds * 0.85f)

        // la bulle, centrée verticalement sur son propre contenu
        val bubbleH = block + inset * 2
        val top = (h - bubbleH) / 2f
        val bubble = RectF(bubbleL, top, bubbleR, top + bubbleH)
        val tailY = top + bubbleH * 0.34f
        val tail = Path().apply {                       // la queue, pointée vers la tête
            moveTo(bubbleL + 2f, tailY - 26f)
            lineTo(bubbleL - 36f, tailY + 6f)
            lineTo(bubbleL + 2f, tailY + 34f)
            close()
        }

        // L'ombre portée est dessinée sur la bulle ET sa queue d'un seul tenant : deux
        // ombres séparées laissent une couture visible là où elles se recouvrent.
        val shell = Path().apply {
            addRoundRect(bubble, 44f, 44f, Path.Direction.CW)
            op(tail, Path.Op.UNION)
        }
        c.save()
        c.translate(0f, 7f)
        p.color = 0x2E000000
        c.drawPath(shell, p)
        c.restore()

        p.color = 0xFCFFFFFF.toInt()
        c.drawPath(shell, p)
        // Un liseré dans la couleur du moment : la bulle reste blanche — c'est ce qui
        // garantit la lisibilité sur les huit décors — mais elle cesse d'être le même
        // rectangle blanc à toutes les heures de la journée.
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = withAlpha(skin(vibe).ground, 0x66)
        c.drawPath(shell, p)
        p.style = Paint.Style.FILL

        var y = top + inset + titleP.textSize * 0.82f
        titleLines.forEach { c.drawText(it, bubbleL + inset, y, titleP); y += titleStep }
        if (bodyLines.isNotEmpty()) {
            y += 22f - titleStep + bodyStep * 0.9f
            bodyLines.forEach { c.drawText(it, bubbleL + inset, y, bodyP); y += bodyStep }
        }
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    /**
     * Le petit objet qui flotte à côté du dragon. Trois seulement — une gélule, des
     * cœurs, une goutte de sueur — parce qu'au-delà ça devient un catalogue d'autocollants
     * et le personnage disparaît derrière ses accessoires.
     */
    private fun prop(c: Canvas, p: Paint, v: Vibe, cx: Float, cy: Float, s: Float) {
        when (v) {
            Vibe.WIN -> {
                p.color = 0xF2FF6B8A.toInt()
                heart(c, p, cx, cy, 17f * s)
                heart(c, p, cx + 26f * s, cy - 30f * s, 11f * s)
            }
            Vibe.REST_DAY, Vibe.REST_NIGHT -> Unit          // rien à tenir : il dort
            Vibe.SULK, Vibe.DRAMA, Vibe.ANGRY -> {
                // la goutte de sueur des mangas, penchée vers l'arrière
                p.color = 0xE6BFE4F2.toInt()
                Path().apply {
                    moveTo(cx, cy - 22f * s)
                    cubicTo(cx + 13f * s, cy - 3f * s, cx + 13f * s, cy + 13f * s, cx, cy + 13f * s)
                    cubicTo(cx - 13f * s, cy + 13f * s, cx - 13f * s, cy - 3f * s, cx, cy - 22f * s)
                    close()
                }.also { c.drawPath(it, p) }
            }
            Vibe.DUE, Vibe.NUDGE -> {
                // la gélule, en biais : moitié blanche, moitié framboise, comme dans l'app
                c.save()
                c.rotate(-28f, cx, cy)
                val pill = RectF(cx - 26f * s, cy - 13f * s, cx + 26f * s, cy + 13f * s)
                p.color = 0xFFFFFFFF.toInt()
                c.drawRoundRect(pill, 13f * s, 13f * s, p)
                c.save()
                c.clipRect(cx - 26f * s, cy - 13f * s, cx, cy + 13f * s)
                p.color = Dragon.Blush
                c.drawRoundRect(pill, 13f * s, 13f * s, p)
                c.restore()
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f * s
                p.color = 0x40000000
                c.drawRoundRect(pill, 13f * s, 13f * s, p)
                p.style = Paint.Style.FILL
                c.restore()
            }
        }
    }

    private fun heart(c: Canvas, p: Paint, cx: Float, cy: Float, r: Float) {
        Path().apply {
            moveTo(cx, cy + r * 0.85f)
            cubicTo(cx - r * 1.5f, cy - r * 0.2f, cx - r * 0.5f, cy - r * 1.2f, cx, cy - r * 0.35f)
            cubicTo(cx + r * 0.5f, cy - r * 1.2f, cx + r * 1.5f, cy - r * 0.2f, cx, cy + r * 0.85f)
            close()
        }.also { c.drawPath(it, p) }
    }

    /**
     * Coupe aux espaces, et à la lettre près si un seul mot est plus large que la bulle —
     * un nom de médicament peut l'être, et sans ce cas il déborderait silencieusement.
     */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        var line = StringBuilder()

        fun flush() {
            if (line.isNotEmpty()) { out.add(line.toString()); line = StringBuilder() }
        }

        text.split(" ").filter { it.isNotEmpty() }.forEach { word ->
            var w = word
            while (paint.measureText(w) > maxWidth && w.length > 1) {
                flush()
                var cut = w.length
                while (cut > 1 && paint.measureText(w.substring(0, cut)) > maxWidth) cut--
                out.add(w.substring(0, cut))
                w = w.substring(cut)
            }
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (paint.measureText(candidate) <= maxWidth) line = StringBuilder(candidate)
            else { flush(); line = StringBuilder(w) }
        }
        flush()
        return out
    }
}
