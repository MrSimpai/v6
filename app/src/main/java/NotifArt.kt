package com.example.medtap.reminder

import android.graphics.*
import com.example.medtap.ui.Dragon
import com.example.medtap.ui.Mood

/**
 * The expanded notification is a drawn banner rather than text, which is the single
 * biggest reason Duolingo's reminders feel like a character talking to you instead of
 * a system message. Generated on a background thread each time it posts.
 */
object NotifArt {

    /**
     * Le fond du widget, peint plutôt que déclaré.
     *
     * Une forme XML ne sait faire qu'un dégradé : posé sur un fond d'écran, ça reste un
     * rectangle inerte. Ici on peut y mettre des écailles et des étincelles, et faire
     * varier la couleur selon l'humeur — le widget change donc d'ambiance dans la journée,
     * ce qui est la moitié de l'intérêt d'avoir un dragon sur son écran d'accueil.
     *
     * Les coins sont découpés dans le bitmap lui-même, pas dans un arrière-plan séparé :
     * un seul calque, aucun risque de liseré blanc qui dépasse.
     */
    fun widgetBg(px: Int, mood: Mood): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val s = px / 400f

        val (a, b) = when (mood) {
            Mood.Overdue  -> 0xFFF7D9D4.toInt() to 0xFFE9A9A2.toInt()
            Mood.Sad      -> 0xFFF3E4EE.toInt() to 0xFFD9C2DC.toInt()
            Mood.Cheering -> 0xFFDDF3EA.toInt() to 0xFFA9DCC8.toInt()
            Mood.Waiting  -> 0xFFFFEEDA.toInt() to 0xFFF6C9A4.toInt()
            Mood.Sleeping -> 0xFFDEF0F2.toInt() to 0xFFA8D4DA.toInt()
        }

        val round = RectF(0f, 0f, px.toFloat(), px.toFloat())
        val radius = 56f * s
        p.shader = LinearGradient(0f, 0f, px * 0.6f, px.toFloat(), a, b, Shader.TileMode.CLAMP)
        c.drawRoundRect(round, radius, radius, p)
        p.shader = null

        // écailles, très discrètes : la texture se sent plus qu'elle ne se voit
        c.save()
        Path().apply { addRoundRect(round, radius, radius, Path.Direction.CW) }
            .also { c.clipPath(it) }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * s
        p.color = 0x18FFFFFF
        for (row in 0..7) for (col in 0..7) {
            val ox = col * 58f * s + (row % 2) * 29f * s
            c.drawArc(
                RectF(ox, row * 52f * s, ox + 50f * s, row * 52f * s + 50f * s),
                180f, 180f, false, p
            )
        }
        p.style = Paint.Style.FILL

        // trois étincelles blanches, comme des reflets
        p.color = 0x66FFFFFF
        listOf(
            Triple(60f, 74f, 16f), Triple(344f, 120f, 12f), Triple(310f, 46f, 8f)
        ).forEach { (x, y, r) ->
            sparkle(c, p, x * s, y * s, r * s)
        }
        c.restore()
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

    fun banner(mood: Mood, title: String, body: String, w: Int = 1024, h: Int = 512): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // ground: warm pink wash, cooling toward the serious end of the scale
        val (top, bottom) = when (mood) {
            Mood.Overdue -> 0xFF3E1029.toInt() to 0xFF7A1B45.toInt()
            Mood.Sad     -> 0xFFFDECF1.toInt() to 0xFFF6CFDC.toInt()
            Mood.Cheering-> 0xFFE4F6EF.toInt() to 0xFFC3E9DA.toInt()
            else         -> 0xFFFEF4F7.toInt() to 0xFFF7D8E2.toInt()
        }
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // faint scale pattern, so the panel reads as dragon territory
        p.color = if (mood == Mood.Overdue) 0x14FFFFFF else 0x1AC03765
        for (row in 0..7) for (col in 0..15) {
            val ox = col * 72f + (row % 2) * 36f
            c.drawArc(RectF(ox, row * 64f, ox + 62f, row * 64f + 62f), 180f, 180f, false,
                p.apply { style = Paint.Style.STROKE; strokeWidth = 3f })
        }
        p.style = Paint.Style.FILL

        // dragon, left third
        c.save()
        c.translate(28f, (h - 400f) / 2f)
        Dragon.draw(c, mood, 400f)
        c.restore()

        val dark = mood == Mood.Overdue
        val textX = 470f
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFFFFFFFF.toInt() else Dragon.Ink
            textSize = 66f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xFFF2DCE5.toInt() else 0xFF7A4C60.toInt()
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val maxW = w - textX - 40f
        val titleLines = wrap(title, titleP, maxW)
        val bodyLines = wrap(body, bodyP, maxW)
        val block = titleLines.size * 78f + 22f + bodyLines.size * 58f
        var y = (h - block) / 2f + 60f

        titleLines.forEach { c.drawText(it, textX, y, titleP); y += 78f }
        y += 22f
        bodyLines.forEach { c.drawText(it, textX, y, bodyP); y += 58f }

        return bmp
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        var line = StringBuilder()
        text.split(" ").forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth) {
                line = StringBuilder(candidate)
            } else {
                if (line.isNotEmpty()) out.add(line.toString())
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) out.add(line.toString())
        return out.take(5)
    }
}
