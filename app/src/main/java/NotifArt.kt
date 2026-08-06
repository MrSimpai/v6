package com.example.medtap.reminder

import android.graphics.*
import com.example.medtap.ui.Dragon
import com.example.medtap.ui.Mood
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
     * Une ambiance par humeur. Le widget et la bannière tirent du même endroit, sinon
     * les deux surfaces racontent la même minute avec deux palettes différentes.
     */
    private class Skin(
        val sky: Int,
        val ground: Int,
        /** Ce qui se pose PAR-DESSUS le ciel : lune, soleil, braises. */
        val glow: Int
    )

    private fun skin(mood: Mood): Skin = when (mood) {
        // Rien à prendre : nuit calme. C'est la seule ambiance qui n'appelle à rien.
        Mood.Sleeping -> Skin(0xFF262A63.toInt(), 0xFF5B4E96.toInt(), 0xFFFFF3C4.toInt())
        // L'heure vient d'arriver : lever de soleil, chaud, sans reproche.
        Mood.Waiting  -> Skin(0xFFE8913F.toInt(), 0xFFE2564F.toInt(), 0xFFFFF0B8.toInt())
        // Une heure de retard : le jour tombe.
        Mood.Sad      -> Skin(0xFF6B3E9B.toInt(), 0xFFA92E6D.toInt(), 0xFFE7D3F5.toInt())
        // Deux heures : braises. La seule ambiance vraiment sombre de toute l'échelle.
        Mood.Overdue  -> Skin(0xFF5E0C27.toInt(), 0xFFB0301F.toInt(), 0xFFFFB65C.toInt())
        // C'est noté : menthe, confettis.
        Mood.Cheering -> Skin(0xFF2FA98D.toInt(), 0xFF1E7F76.toInt(), 0xFFFFFFFF.toInt())
    }

    // ---- le décor ---------------------------------------------------------

    /**
     * Le paysage, peint sur toute la surface qu'on lui donne.
     *
     * Une forme XML ne sait faire qu'un dégradé : posée sur un fond d'écran ça reste un
     * rectangle inerte. Ici il y a une lune, des braises, de la pluie — et ça vaut la
     * peine parce que c'est ce qu'on regarde toute la journée sur l'écran d'accueil.
     */
    private fun scene(c: Canvas, w: Float, h: Float, mood: Mood, radius: Float) {
        val sk = skin(mood)
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
        val rng = Random(mood.ordinal * 31 + 7)

        when (mood) {
            Mood.Sleeping -> {
                halo(c, p, w * 0.80f, h * 0.20f, 62f * u, 0x33FFFFFF)
                // Le croissant est une différence de deux disques et non un disque
                // repeint par-dessus : le ciel est un dégradé, donc aucune couleur unie
                // ne saurait refermer la morsure sans laisser une tache.
                p.color = sk.glow
                val disc = Path().apply { addCircle(w * 0.80f, h * 0.20f, 30f * u, Path.Direction.CW) }
                val bite = Path().apply { addCircle(w * 0.71f, h * 0.155f, 27f * u, Path.Direction.CW) }
                disc.op(bite, Path.Op.DIFFERENCE)
                c.drawPath(disc, p)
                p.color = 0xCCFFFFFF.toInt()
                repeat(16) {
                    val x = rng.nextFloat() * w
                    val y = rng.nextFloat() * h * 0.72f
                    sparkle(c, p, x, y, (2.5f + rng.nextFloat() * 4.5f) * u)
                }
            }

            Mood.Waiting -> {
                halo(c, p, w * 0.82f, h * 0.24f, 78f * u, 0x40FFFFFF)
                p.color = sk.glow
                c.drawCircle(w * 0.82f, h * 0.24f, 34f * u, p)
                p.color = 0x33FFFFFF                              // deux nuages plats
                c.drawRoundRect(RectF(w * 0.05f, h * 0.34f, w * 0.42f, h * 0.40f), h, h, p)
                c.drawRoundRect(RectF(w * 0.55f, h * 0.46f, w * 0.92f, h * 0.51f), h, h, p)
            }

            Mood.Sad -> {
                p.color = 0x33FFFFFF
                c.drawRoundRect(RectF(w * 0.08f, h * 0.14f, w * 0.52f, h * 0.22f), h, h, p)
                c.drawRoundRect(RectF(w * 0.52f, h * 0.26f, w * 0.94f, h * 0.33f), h, h, p)
                p.strokeWidth = 2.4f * u                          // la pluie, en biais
                p.strokeCap = Paint.Cap.ROUND
                p.style = Paint.Style.STROKE
                p.color = 0x59FFFFFF
                repeat(18) {
                    val x = rng.nextFloat() * w * 1.1f - w * 0.05f
                    val y = h * 0.18f + rng.nextFloat() * h * 0.62f
                    c.drawLine(x, y, x - 5f * u, y + 16f * u, p)
                }
                p.style = Paint.Style.FILL
            }

            Mood.Overdue -> {
                halo(c, p, w * 0.5f, h * 1.02f, h * 0.66f, 0x3DFFB65C)
                repeat(14) {                                      // les braises qui montent
                    val x = rng.nextFloat() * w
                    val y = h * 0.28f + rng.nextFloat() * h * 0.62f
                    val r = (2f + rng.nextFloat() * 4f) * u
                    p.color = if (rng.nextBoolean()) 0x99FFC46B.toInt() else 0x66FFFFFF
                    c.drawCircle(x, y, r, p)
                }
            }

            Mood.Cheering -> {
                halo(c, p, w * 0.5f, h * 0.42f, minOf(w, h) * 0.52f, 0x33FFFFFF)
                repeat(14) {
                    val x = rng.nextFloat() * w
                    val y = rng.nextFloat() * h * 0.82f
                    p.color = 0x99FFFFFF.toInt()
                    sparkle(c, p, x, y, (3f + rng.nextFloat() * 5f) * u)
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

        // Un voile sombre en haut. Le texte du widget est écrit à même le décor, et le
        // décor va du bleu nuit au jaune levant : sans ce voile, aucune couleur d'encre
        // ne tiendrait sur les cinq humeurs. Avec, le blanc marche partout.
        p.shader = LinearGradient(
            0f, 0f, 0f, h * 0.62f, 0x59000000, 0x00000000, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h * 0.62f, p)
        p.shader = null

        c.restore()
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
    fun widgetBg(px: Int, mood: Mood): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        scene(Canvas(bmp), px.toFloat(), px.toFloat(), mood, px * 0.14f)
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
    fun banner(mood: Mood, title: String, body: String): Bitmap {
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

        scene(c, w, h, mood, 0f)

        // Le dragon, calé en bas à gauche, posé sur la bosse de sol du décor. Plafonné
        // à 348 : au-delà il passerait sous la bulle, et un dragon à moitié caché
        // derrière un rectangle blanc est pire qu'un dragon un peu plus petit.
        val dragonSize = minOf(h * 0.80f, 348f)
        c.save()
        c.translate(14f, h - dragonSize - h * 0.04f)
        Dragon.draw(c, mood, dragonSize)
        c.restore()

        // la bulle, centrée verticalement sur son propre contenu
        val bubbleH = block + inset * 2
        val top = (h - bubbleH) / 2f
        val bubble = RectF(bubbleL, top, bubbleR, top + bubbleH)
        p.color = 0x33000000
        c.drawRoundRect(RectF(bubble).apply { offset(0f, 6f) }, 44f, 44f, p)
        p.color = 0xFAFFFFFF.toInt()
        c.drawRoundRect(bubble, 44f, 44f, p)
        Path().apply {                                  // la queue, pointée vers la tête
            val ty = top + bubbleH * 0.34f
            moveTo(bubbleL + 2f, ty - 26f)
            lineTo(bubbleL - 34f, ty + 6f)
            lineTo(bubbleL + 2f, ty + 34f)
            close()
        }.also { c.drawPath(it, p) }

        var y = top + inset + titleP.textSize * 0.82f
        titleLines.forEach { c.drawText(it, bubbleL + inset, y, titleP); y += titleStep }
        if (bodyLines.isNotEmpty()) {
            y += 22f - titleStep + bodyStep * 0.9f
            bodyLines.forEach { c.drawText(it, bubbleL + inset, y, bodyP); y += bodyStep }
        }
        return bmp
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
