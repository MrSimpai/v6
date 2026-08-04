package com.example.medtap.ui

import android.graphics.*
import kotlin.math.sin

enum class Mood { Sleeping, Waiting, Overdue, Cheering, Sad }

/**
 * Le dragon de Flo, redessiné d'après le personnage de référence : framboise foncé,
 * quatre cornes, collerette de piquants, ailes de chauve-souris, assis, ventre rose pâle
 * et pattes prune.
 *
 * Tout est dessiné une seule fois sur un android.graphics.Canvas ordinaire, pour que
 * l'interface Compose, la grosse icône de notification et la bannière partagent
 * exactement le même dessin et ne puissent jamais diverger.
 *
 * Espace de conception : 220 x 220. Le dragon occupe y = 4 (pointe des cornes) à
 * y = 212 (ombre au sol), et x = 10 à 210 (envergure des ailes).
 */
object Dragon {

    // La palette vient directement de l'illustration de référence.
    const val Pink     = 0xFFC03765.toInt()   // corps, tête, membres
    const val PinkDeep = 0xFFA21E50.toInt()   // cornes, collerette, griffes
    const val PinkDark = 0xFF8E1F4C.toInt()   // phalanges des ailes
    const val Crown    = 0xFFAE2C5B.toInt()   // dessus du crâne
    const val WingMem  = 0xFFA83063.toInt()   // membrane des ailes
    const val Plum     = 0xFF7A1B45.toInt()   // pattes
    const val Belly    = 0xFFF1BBCB.toInt()   // ventre
    const val Ink      = 0xFF4E1330.toInt()   // yeux, bouche
    const val Blush    = 0xFFE78083.toInt()   // museau
    const val Teal     = 0xFF94C9CF.toInt()   // accent (fond d'icône)
    const val White    = 0xFFFFFFFF.toInt()
    const val Tear     = 0xFFB9DCE8.toInt()
    const val Shade    = 0xFFCAD1D9.toInt()   // ombre au sol

    // Ancien nom conservé pour compatibilité avec le code qui l'utilisait.
    const val Crest    = Blush

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fill(c: Int) = p.apply {
        style = Paint.Style.FILL; color = c; alpha = 255; strokeWidth = 0f
    }

    private fun stroke(c: Int, w: Float) = p.apply {
        style = Paint.Style.STROKE; color = c; alpha = 255
        strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) =
        RectF(cx - rx, cy - ry, cx + rx, cy + ry)

    /** Exécute [block] en miroir autour de l'axe vertical du dessin. */
    private inline fun mirrored(c: Canvas, block: () -> Unit) {
        c.save(); c.translate(220f, 0f); c.scale(-1f, 1f); block(); c.restore()
    }

    fun draw(c: Canvas, mood: Mood, size: Float, phase: Float = 0f) {
        val s = size / 220f
        c.save()
        c.scale(s, s)
        val flap = sin(phase * 2.0 * Math.PI).toFloat()
        c.drawOval(oval(110f, 204f, 54f, 8f), fill(Shade).apply { alpha = 190 })
        p.alpha = 255
        c.save()
        c.translate(0f, -flap * (if (mood == Mood.Cheering) 5f else 2f))
        tail(c)
        wing(c, flap); mirrored(c) { wing(c, flap) }
        leg(c); mirrored(c) { leg(c) }
        torso(c)
        arm(c); mirrored(c) { arm(c) }
        head(c, mood)
        c.restore()
        extras(c, mood)
        c.restore()
    }

    // ---- corps ------------------------------------------------------------

    /** La queue : un croissant épais qui balaie vers la gauche et s'affine en pointe. */
    private fun tail(c: Canvas) {
        Path().apply {
            moveTo(102f, 194f)
            cubicTo(62f, 204f, 16f, 196f, 10f, 164f)
            cubicTo(6f, 143f, 16f, 128f, 30f, 122f)
            cubicTo(20f, 136f, 18f, 152f, 23f, 165f)
            cubicTo(32f, 186f, 68f, 190f, 102f, 178f)
            close()
        }.also { c.drawPath(it, fill(Pink)) }
    }

    /** Aile de chauve-souris : bord d'attaque courbe, trois festons, trois phalanges. */
    private fun wing(c: Canvas, lift: Float) {
        c.save()
        c.rotate(lift * 6f, 88f, 118f)      // le battement, pivot à l'épaule
        Path().apply {
            moveTo(86f, 116f)
            cubicTo(72f, 86f, 40f, 68f, 12f, 82f)
            quadTo(24f, 102f, 42f, 109f)
            quadTo(28f, 119f, 46f, 129f)
            quadTo(36f, 141f, 56f, 148f)
            quadTo(80f, 142f, 90f, 120f)
            close()
        }.also { c.drawPath(it, fill(WingMem)) }

        val bone = stroke(PinkDark, 3.2f)
        c.drawLine(86f, 118f, 16f, 84f, bone)
        c.drawLine(86f, 118f, 43f, 108f, bone)
        c.drawLine(86f, 118f, 47f, 128f, bone)
        c.restore()
    }

    /** Cuisse arrondie plus patte prune à trois orteils. */
    private fun leg(c: Canvas) {
        c.drawOval(oval(80f, 164f, 24f, 26f), fill(Pink))
        Path().apply {
            moveTo(86f, 182f)
            cubicTo(86f, 173f, 74f, 168f, 60f, 169f)
            cubicTo(47f, 170f, 40f, 177f, 40f, 186f)
            cubicTo(40f, 195f, 49f, 200f, 62f, 199f)
            cubicTo(76f, 198f, 86f, 191f, 86f, 182f)
            close()
        }.also { c.drawPath(it, fill(Plum)) }
        listOf(175f, 186f, 196f).forEach { c.drawCircle(43f, it, 6.2f, fill(Plum)) }
    }

    /** Corps en poire : épaules étroites, assise large. Le ventre pâle par-dessus. */
    private fun torso(c: Canvas) {
        Path().apply {
            moveTo(110f, 112f)
            cubicTo(90f, 112f, 80f, 124f, 77f, 142f)
            cubicTo(72f, 163f, 76f, 184f, 88f, 192f)
            cubicTo(99f, 198f, 121f, 198f, 132f, 192f)
            cubicTo(144f, 184f, 148f, 163f, 143f, 142f)
            cubicTo(140f, 124f, 130f, 112f, 110f, 112f)
            close()
        }.also { c.drawPath(it, fill(Pink)) }
        c.drawOval(oval(110f, 166f, 26f, 31f), fill(Belly))
    }

    /** Patte avant : moufle au bord festonné, deux sillons pour les griffes. */
    private fun arm(c: Canvas) {
        Path().apply {
            moveTo(90f, 130f)
            cubicTo(83f, 139f, 82f, 151f, 87f, 159f)
            quadTo(91f, 165f, 94f, 158f)
            quadTo(98f, 165f, 102f, 158f)
            quadTo(106f, 164f, 107f, 155f)
            cubicTo(110f, 144f, 102f, 133f, 97f, 128f)
            close()
        }.also { c.drawPath(it, fill(Pink)) }

        val claw = stroke(PinkDeep, 2.4f)
        Path().apply { moveTo(99f, 146f); quadTo(96f, 153f, 97f, 159f) }
            .also { c.drawPath(it, claw) }
        Path().apply { moveTo(92f, 148f); quadTo(89f, 154f, 90f, 158f) }
            .also { c.drawPath(it, claw) }
    }

    // ---- tête -------------------------------------------------------------

    private fun head(c: Canvas, mood: Mood) {
        horns(c); mirrored(c) { horns(c) }
        frills(c); mirrored(c) { frills(c) }

        c.drawOval(oval(110f, 74f, 45f, 40f), fill(Pink))

        // Calotte plus foncée avec une pointe entre les yeux : c'est ce qui donne
        // au crâne son relief sans dessiner de contour.
        Path().apply {
            moveTo(68f, 64f)
            cubicTo(70f, 42f, 88f, 34f, 110f, 34f)
            cubicTo(132f, 34f, 152f, 42f, 152f, 64f)
            cubicTo(144f, 54f, 130f, 48f, 120f, 50f)
            cubicTo(113f, 52f, 111f, 58f, 110f, 64f)
            cubicTo(109f, 58f, 107f, 52f, 100f, 50f)
            cubicTo(90f, 48f, 76f, 54f, 68f, 64f)
            close()
        }.also { c.drawPath(it, fill(Crown)) }

        snout(c)
        eyes(c, mood)
        mouth(c, mood)
        if (mood == Mood.Sad) tears(c)
    }

    /** Deux cornes par côté : la grande, presque droite, et la petite, très ouverte. */
    private fun horns(c: Canvas) {
        Path().apply {
            moveTo(84f, 58f)
            cubicTo(64f, 42f, 58f, 20f, 68f, 4f)
            cubicTo(80f, 22f, 100f, 42f, 108f, 54f)
            close()
        }.also { c.drawPath(it, fill(PinkDeep)) }
        Path().apply {
            moveTo(64f, 78f)
            cubicTo(46f, 64f, 36f, 44f, 40f, 28f)
            cubicTo(55f, 42f, 74f, 60f, 80f, 70f)
            close()
        }.also { c.drawPath(it, fill(PinkDeep)) }
    }

    /** Collerette de trois piquants le long de la mâchoire. */
    private fun frills(c: Canvas) {
        listOf(
            Triple(78f to 76f, 58f to 78f, 78f to 92f),
            Triple(76f to 88f, 58f to 96f, 80f to 102f),
            Triple(78f to 98f, 66f to 108f, 88f to 109f)
        ).forEach { (a, b, d) ->
            Path().apply {
                moveTo(a.first, a.second); lineTo(b.first, b.second); lineTo(d.first, d.second)
                close()
            }.also { c.drawPath(it, fill(PinkDeep)) }
        }
    }

    /**
     * Museau de la couleur du corps, avec une simple tache chaude et deux narines.
     * Pas de plaque pâle contrastante : ça se lirait comme un second visage.
     */
    private fun snout(c: Canvas) {
        c.drawOval(oval(110f, 94f, 15f, 11f), fill(Pink))
        c.drawOval(oval(110f, 92f, 7.5f, 5f), fill(Blush).apply { alpha = 205 })
        p.alpha = 255
        c.drawCircle(106f, 91f, 1.7f, fill(PinkDeep))
        c.drawCircle(114f, 91f, 1.7f, fill(PinkDeep))
    }

    private fun eyes(c: Canvas, mood: Mood) {
        val l = PointF(92f, 80f); val r = PointF(128f, 80f)
        when (mood) {
            Mood.Waiting -> listOf(l, r).forEach {
                c.drawCircle(it.x, it.y, 12.5f, fill(White))
                c.drawCircle(it.x, it.y + 1f, 7f, fill(Ink))
                c.drawCircle(it.x + 3.5f, it.y - 3.5f, 2.8f, fill(White))
            }
            Mood.Overdue -> listOf(l to 1f, r to -1f).forEach { (e, d) ->
                Path().apply {
                    moveTo(e.x - d * 12f, e.y - 4f)
                    cubicTo(e.x - d * 4f, e.y - 9f, e.x + d * 9f, e.y - 4f, e.x + d * 11f, e.y + 3f)
                    cubicTo(e.x + d * 3f, e.y + 8f, e.x - d * 8f, e.y + 5f, e.x - d * 12f, e.y - 4f)
                    close()
                }.also { c.drawPath(it, fill(Ink)) }
                c.drawLine(e.x - d * 14f, e.y - 13f, e.x + d * 11f, e.y - 6f, stroke(PinkDeep, 3.4f))
            }
            Mood.Sad -> listOf(l, r).forEach {
                Path().apply {
                    moveTo(it.x - 11f, it.y - 3f); quadTo(it.x, it.y + 7f, it.x + 11f, it.y - 3f)
                }.also { path -> c.drawPath(path, stroke(Ink, 3.4f)) }
            }
            Mood.Cheering -> listOf(l, r).forEach {
                Path().apply {
                    moveTo(it.x - 11f, it.y + 5f); quadTo(it.x, it.y - 8f, it.x + 11f, it.y + 5f)
                }.also { path -> c.drawPath(path, stroke(Ink, 3.6f)) }
            }
            Mood.Sleeping -> listOf(l, r).forEach {
                Path().apply {
                    moveTo(it.x - 11f, it.y - 2f); quadTo(it.x, it.y + 7f, it.x + 11f, it.y - 2f)
                }.also { path -> c.drawPath(path, stroke(Ink, 3.2f)) }
            }
        }
    }

    private fun mouth(c: Canvas, mood: Mood) {
        when (mood) {
            Mood.Cheering -> Path().apply {
                moveTo(96f, 102f); quadTo(110f, 116f, 124f, 102f); quadTo(110f, 108f, 96f, 102f)
                close()
            }.also { c.drawPath(it, fill(Ink)) }

            Mood.Sad -> {
                Path().apply {
                    moveTo(98f, 104f); quadTo(110f, 98f, 122f, 104f)
                    quadTo(122f, 117f, 110f, 117f); quadTo(98f, 117f, 98f, 104f)
                    close()
                }.also { c.drawPath(it, fill(Ink)) }
                // deux petits crocs
                Path().apply { moveTo(103f, 103f); lineTo(107.5f, 103f); lineTo(105f, 110f); close() }
                    .also { c.drawPath(it, fill(White)) }
                Path().apply { moveTo(112.5f, 103f); lineTo(117f, 103f); lineTo(115f, 110f); close() }
                    .also { c.drawPath(it, fill(White)) }
            }

            Mood.Overdue -> Path().apply {
                moveTo(97f, 110f); quadTo(110f, 101f, 123f, 110f)
            }.also { c.drawPath(it, stroke(Ink, 3.8f)) }

            Mood.Waiting -> Path().apply {
                moveTo(99f, 103f); quadTo(110f, 112f, 121f, 103f)
            }.also { c.drawPath(it, stroke(Ink, 3.4f)) }

            Mood.Sleeping -> c.drawLine(102f, 106f, 118f, 106f, stroke(Ink, 3f))
        }
    }

    private fun tears(c: Canvas) {
        listOf(
            Triple(68f, 88f, 5f), Triple(55f, 97f, 4.2f),
            Triple(152f, 88f, 5f), Triple(165f, 97f, 4.2f),
            Triple(84f, 128f, 4.6f), Triple(136f, 128f, 4.6f),
            Triple(64f, 150f, 4f), Triple(156f, 150f, 4f)
        ).forEach { (x, y, r) ->
            Path().apply {
                moveTo(x, y - r * 1.7f)
                cubicTo(x + r, y - r * 0.2f, x + r, y + r, x, y + r)
                cubicTo(x - r, y + r, x - r, y - r * 0.2f, x, y - r * 1.7f)
                close()
            }.also { c.drawPath(it, fill(Tear)) }
        }
    }

    // ---- décor par humeur -------------------------------------------------

    private fun extras(c: Canvas, mood: Mood) {
        when (mood) {
            Mood.Waiting -> {
                c.drawRoundRect(RectF(94f, 172f, 126f, 188f), 8f, 8f, fill(White))
                c.save(); c.clipRect(94f, 172f, 110f, 188f)
                c.drawRoundRect(RectF(94f, 172f, 126f, 188f), 8f, 8f, fill(Blush))
                c.restore()
            }
            Mood.Cheering -> listOf(
                Triple(24f, 50f, 9f), Triple(194f, 42f, 10f), Triple(202f, 134f, 7f)
            ).forEach { (x, y, r) -> sparkle(c, x, y, r) }

            Mood.Sleeping -> listOf(Triple(172f, 44f, 12f), Triple(192f, 22f, 9f))
                .forEach { (x, y, r) ->
                    val z = stroke(PinkDeep, 3f)
                    c.drawLine(x, y, x + r, y, z)
                    c.drawLine(x + r, y, x, y + r, z)
                    c.drawLine(x, y + r, x + r, y + r, z)
                }
            else -> Unit
        }
    }

    private fun sparkle(c: Canvas, cx: Float, cy: Float, r: Float) {
        Path().apply {
            moveTo(cx, cy - r)
            quadTo(cx + r * 0.28f, cy - r * 0.28f, cx + r, cy)
            quadTo(cx + r * 0.28f, cy + r * 0.28f, cx, cy + r)
            quadTo(cx - r * 0.28f, cy + r * 0.28f, cx - r, cy)
            quadTo(cx - r * 0.28f, cy - r * 0.28f, cx, cy - r)
            close()
        }.also { c.drawPath(it, fill(Blush)) }
    }

    /** Tête recadrée en rond, pour la grosse icône de la notification. */
    fun faceBitmap(px: Int, mood: Mood): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawCircle(px / 2f, px / 2f, px / 2f, fill(0xFFFCEFF4.toInt()))
        val k = px / 220f
        c.save()
        c.translate(px * 0.5f, px * 0.56f)
        c.scale(1.55f, 1.55f)
        c.translate(-110f * k, -76f * k)
        draw(c, mood, 220f * k)
        c.restore()
        return bmp
    }
}
