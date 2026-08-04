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
