package com.example.medtap.ui

import android.graphics.*
import kotlin.math.sin

/**
 * Les visages du dragon.
 *
 * Les cinq premiers existaient depuis le début. Les quatre suivants ont été ajoutés parce
 * que l'échelle de relance compte cinq paliers et n'avait que trois têtes : bouder, supplier
 * et être vraiment fâchée se ressemblaient toutes. Une escalade qu'on lit dans le texte
 * mais pas sur la figure escalade à moitié.
 */
enum class Mood {
    Sleeping, Waiting, Overdue, Cheering, Sad,

    /** Trente minutes. Elle regarde ailleurs, exprès, et attend qu'on le remarque. */
    Sulking,

    /** Une heure. Grands yeux mouillés, sourcils en toit : elle demande, elle n'exige pas. */
    Pleading,

    /** Le mot de passe du casier, et rien d'autre. Des cœurs à la place des yeux. */
    Love,

    /** Une série qui tient depuis longtemps. Yeux fermés, menton haut, très content de soi. */
    Proud
}

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

    // Cosmétiques
    const val Knit     = 0xFF3FA98D.toInt()   // laine de la tuque
    const val Wool     = 0xFFFDF6F0.toInt()   // fourrure et pompon
    const val Xmas     = 0xFFC0392B.toInt()   // rouge du hoodie et des bottes
    const val XmasDeep = 0xFF8E2B22.toInt()   // ombres du rouge
    const val Gold     = 0xFFE8B84B.toInt()   // couronne
    const val GoldDeep = 0xFFC9932E.toInt()
    const val Night    = 0xFF5B6BA8.toInt()   // pyjama
    const val NightDim = 0xFF44528A.toInt()
    const val Fluff    = 0xFFF3C6D5.toInt()   // pantoufles

    // Ailes. Aucune n'utilise de dégradé : un `Shader` écraserait la couleur et
    // court-circuiterait le mode ombre chinoise du casier, où la pièce doit rester une
    // silhouette. Les fondus sont donc faits en bandes de couleurs pleines.
    const val Feather  = 0xFFFDFBF6.toInt()   // ange
    const val FeatherD = 0xFFDCD3C8.toInt()
    const val Fairy    = 0xFFF7DCEE.toInt()   // fée
    const val FairyRim = 0xFFFFFFFF.toInt()
    const val Frost    = 0xFFCDE9F5.toInt()   // givrées
    const val FrostD   = 0xFF74B6D6.toInt()
    const val Ember    = 0xFF7A1B12.toInt()   // braise
    const val EmberMid = 0xFFD9452F.toInt()
    const val EmberHot = 0xFFFFB65C.toInt()
    const val Rot      = 0xFFE8802B.toInt()   // déchirées
    const val RotDeep  = 0xFF8A3E12.toInt()
    const val Odo      = 0xFF8FDCD6.toInt()   // libellule
    const val OdoRim   = 0xFF3E8F92.toInt()
    const val Monarch  = 0xFFE8802B.toInt()   // monarque
    const val Beetle   = 0xFFD2382B.toInt()   // coccinelle
    const val Chitin   = 0xFF2A1A16.toInt()   // nervures et pois

    // Chapeaux
    const val Witch    = 0xFF4A2B6B.toInt()
    const val WitchDim = 0xFF2E1A44.toInt()
    const val Pumpkin  = 0xFFE8802B.toInt()
    const val PumpkinD = 0xFFB85A15.toInt()
    const val Stem     = 0xFF4E7A3A.toInt()
    const val Antler   = 0xFFC9A57A.toInt()
    const val AntlerD  = 0xFF9C7A52.toInt()
    const val Straw    = 0xFFF0D9A0.toInt()
    const val StrawD   = 0xFFCBAE72.toInt()
    const val Ribbon   = 0xFF5B8FB9.toInt()
    const val Petal    = 0xFFF9C6DA.toInt()
    const val Pollen   = 0xFFF2D04B.toInt()
    const val Leaf     = 0xFF6BA85C.toInt()
    const val Party    = 0xFF7A4FD1.toInt()
    const val Swim     = 0xFF3FB3D6.toInt()
    const val Glass    = 0xFF9EE4F2.toInt()
    const val Cap      = 0xFF3E5C8A.toInt()
    const val CapDeep  = 0xFF2A3F63.toInt()
    const val Lens     = 0xFF2A2F3A.toInt()
    const val Halo     = 0xFFF7E27A.toInt()

    // Corps
    const val Cozy     = 0xFFF2E7D8.toInt()
    const val CozyDeep = 0xFFD8C8B2.toInt()
    const val Ghost    = 0xFFF7F7F3.toInt()
    const val GhostDim = 0xFFD7D8D0.toInt()
    const val UglyRed  = 0xFFB33A3A.toInt()
    const val UglyGrn  = 0xFF3F7A55.toInt()
    const val Rain     = 0xFFF2C230.toInt()
    const val RainDeep = 0xFFC79A15.toInt()
    const val Navy     = 0xFF2F4670.toInt()
    const val Denim    = 0xFF5B7FA8.toInt()
    const val DenimD   = 0xFF3E5C7E.toInt()
    const val Puffer   = 0xFFE8734F.toInt()
    const val PufferD  = 0xFFC9553A.toInt()
    const val Suit     = 0xFFE84E7A.toInt()
    const val Apron    = 0xFFF2E9DC.toInt()

    // Pattes
    const val Sneak    = 0xFFF4F4F0.toInt()
    const val SneakD   = 0xFF9BA3AE.toInt()
    const val Skate    = 0xFF2A2F3A.toInt()
    const val Ballet   = 0xFFF7D7E2.toInt()

    // Compagnons
    const val Plush    = 0xFFF2A9C4.toInt()
    const val PlushD   = 0xFFD17C9B.toInt()
    const val Quilt    = 0xFFAFD4E8.toInt()
    const val QuiltDim = 0xFF7FAECB.toInt()
    const val Rhino    = 0xFFC2B7DB.toInt()
    const val RhinoD   = 0xFF9B8FBA.toInt()
    const val Bear     = 0xFFD9A96B.toInt()
    const val BearD    = 0xFFB88748.toInt()
    const val Kitty    = 0xFFB9BFC9.toInt()
    const val KittyD   = 0xFF8E96A3.toInt()
    const val Frog     = 0xFF86C96E.toInt()
    const val FrogD    = 0xFF57A046.toInt()
    const val Hedge    = 0xFFE0C49E.toInt()
    const val HedgeQ   = 0xFF8A6E4E.toInt()
    const val Whale    = 0xFF7FB2DB.toInt()
    const val WhaleD   = 0xFF4E86B5.toInt()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Quand il est posé, toutes les couleurs sont remplacées par celle-ci et le dragon
     * devient une ombre chinoise. C'est ce qui permet au casier de montrer la FORME d'une
     * pièce pas encore gagnée sans en révéler la couleur ni le détail.
     */
    private var tint: Int? = null

    /**
     * En mode ombre chinoise, le dragon vire au gris pâle mais la pièce reste presque
     * noire. Tout peindre du même ton donnerait un dragon noir coiffé d'un chapeau noir,
     * c'est-à-dire une tache — or c'est justement la forme de la pièce qu'on veut montrer.
     */
    private inline fun asWear(block: () -> Unit) {
        val saved = tint
        if (saved != null) tint = 0xFF1E0C16.toInt()
        block()
        tint = saved
    }

    private fun fill(c: Int) = p.apply {
        style = Paint.Style.FILL; color = tint ?: c; alpha = 255; strokeWidth = 0f
    }

    private fun stroke(c: Int, w: Float) = p.apply {
        style = Paint.Style.STROKE; color = tint ?: c; alpha = 255
        strokeWidth = w; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) =
        RectF(cx - rx, cy - ry, cx + rx, cy + ry)

    /** Exécute [block] en miroir autour de l'axe vertical du dessin. */
    private inline fun mirrored(c: Canvas, block: () -> Unit) {
        c.save(); c.translate(220f, 0f); c.scale(-1f, 1f); block(); c.restore()
    }

    fun draw(
        c: Canvas,
        mood: Mood,
        size: Float,
        phase: Float = 0f,
        worn: Set<String> = emptySet(),
        silhouette: Boolean = false
    ) {
        tint = if (silhouette) 0xFFDDCAD3.toInt() else null
        try {
            drawInner(c, mood, size, phase, worn)
        } finally {
            tint = null
        }
    }

    private fun drawInner(
        c: Canvas, mood: Mood, size: Float, phase: Float, worn: Set<String>
    ) {
        val s = size / 220f
        c.save()
        c.scale(s, s)
        val flap = sin(phase * 2.0 * Math.PI).toFloat()
        c.drawOval(oval(110f, 204f, 54f, 8f), fill(Shade).apply { alpha = 190 })
        p.alpha = 255
        c.save()
        c.translate(0f, -flap * (if (mood == Mood.Cheering) 5f else 2f))
        // Une pièce par emplacement : c'est le catalogue qui décide, pas une liste
        // d'identifiants codée en dur ici. Ajouter une pièce ne touche donc que deux
        // endroits — Cosmetics.ALL, et sa fonction de dessin plus bas.
        val slots = worn.mapNotNull { Cosmetics.byId(it) }.associate { it.slot to it.id }
        val feet = slots[Slot.FEET]
        val body = slots[Slot.BODY]
        val wings = slots[Slot.WINGS]

        tail(c)
        wing(c, flap, wings); mirrored(c) { wing(c, flap, wings) }
        // La cape tombe DERRIÈRE le corps. C'est la seule pièce de torse qui ne soit pas
        // découpée dans la silhouette : une cape par-dessus le ventre ne serait pas une
        // cape, ce serait un tablier.
        if (body == "cape") capeBehind(c)
        leg(c, feet); mirrored(c) { leg(c, feet) }
        torso(c, body)
        arm(c, body); mirrored(c) { arm(c, body) }
        collar(c, body)
        head(c, mood)
        headPiece(c, slots[Slot.HEAD])
        c.restore()
        // Le compagnon est dessiné APRÈS le `restore` du balancement : il est posé par
        // terre, pas porté. Le faire monter et descendre au rythme du dragon donnerait
        // l'impression qu'il lévite.
        friend(c, slots[Slot.FRIEND])
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

    /**
     * L'aile, nue ou remplacée par une pièce.
     *
     * Contrairement au chapeau ou aux bottes, une aile ne se POSE pas sur le dragon : elle
     * prend la place d'un morceau qui existe déjà. D'où le paramètre plutôt qu'un appel
     * séparé après coup — sinon les deux paires se superposeraient.
     */
    private fun wing(c: Canvas, lift: Float, wear: String?) {
        c.save()
        c.rotate(lift * 6f, 88f, 118f)      // le battement, pivot à l'épaule
        when (wear) {
            "ailes_arcenciel"  -> rainbowWing(c)
            "ailes_braise"     -> emberWing(c)
            "ailes_givrees"    -> frostWing(c)
            "ailes_dechirees"  -> tatteredWing(c)
            "ailes_ange"       -> angelWing(c)
            "ailes_fee"        -> fairyWing(c)
            "ailes_libellule"  -> dragonflyWing(c)
            "ailes_monarque"   -> butterflyWing(c)
            "ailes_coccinelle" -> ladybugWing(c)
            else -> { c.drawPath(batPath(), fill(WingMem)); batBones(c, PinkDark) }
        }
        c.restore()
    }

    /** Le contour de l'aile nue : bord d'attaque courbe, trois festons. */
    private fun batPath() = Path().apply {
        moveTo(86f, 116f)
        cubicTo(72f, 86f, 40f, 68f, 12f, 82f)
        quadTo(24f, 102f, 42f, 109f)
        quadTo(28f, 119f, 46f, 129f)
        quadTo(36f, 141f, 56f, 148f)
        quadTo(80f, 142f, 90f, 120f)
        close()
    }

    /** Les trois phalanges, en éventail depuis l'épaule. */
    private fun batBones(c: Canvas, color: Int) {
        val bone = stroke(color, 3.2f)
        c.drawLine(86f, 118f, 16f, 84f, bone)
        c.drawLine(86f, 118f, 43f, 108f, bone)
        c.drawLine(86f, 118f, 47f, 128f, bone)
    }

    /** Six bandes en biais, découpées dans la silhouette de l'aile. Aucune subtilité. */
    private fun rainbowWing(c: Canvas) {
        asWear {
            c.save()
            c.clipPath(batPath())
            intArrayOf(
                0xFFE2564F.toInt(), 0xFFE8913F.toInt(), 0xFFF2D04B.toInt(),
                0xFF4EBE8A.toInt(), 0xFF2F9FD8.toInt(), 0xFF8E3FBF.toInt()
            ).forEachIndexed { i, col ->
                c.drawRect(RectF(0f, 60f + i * 16f, 100f, 76f + i * 16f), fill(col))
            }
            c.restore()
            batBones(c, Wool)
        }
    }

    /** Sombre à la racine, chaude au bord : la même montée que la bannière en retard. */
    private fun emberWing(c: Canvas) {
        asWear {
            c.save()
            c.clipPath(batPath())
            c.drawRect(RectF(0f, 60f, 100f, 160f), fill(Ember))
            c.drawOval(oval(34f, 118f, 44f, 34f), fill(EmberMid))
            c.drawOval(oval(24f, 118f, 30f, 22f), fill(EmberHot))
            c.restore()
            batBones(c, Ember)
        }
    }

    /** Bleu glace, avec des cristaux plantés le long des festons. */
    private fun frostWing(c: Canvas) {
        asWear {
            c.drawPath(batPath(), fill(Frost))
            batBones(c, FrostD)
            listOf(
                Triple(20f, 86f, 7f), Triple(40f, 110f, 6f),
                Triple(44f, 130f, 5f), Triple(58f, 146f, 6f)
            ).forEach { (x, y, r) ->
                Path().apply {
                    moveTo(x, y - r); lineTo(x + r * 0.55f, y); lineTo(x, y + r)
                    lineTo(x - r * 0.55f, y); close()
                }.also { c.drawPath(it, fill(FairyRim)) }
            }
        }
    }

    /** Trouée. Les trous sont découpés DANS la forme, pas peints par-dessus. */
    private fun tatteredWing(c: Canvas) {
        asWear {
            val shape = batPath()
            listOf(Triple(46f, 98f, 9f), Triple(32f, 116f, 7f), Triple(58f, 130f, 6f))
                .forEach { (x, y, r) ->
                    shape.op(
                        Path().apply { addCircle(x, y, r, Path.Direction.CW) },
                        Path.Op.DIFFERENCE
                    )
                }
            c.drawPath(shape, fill(Rot))
            batBones(c, RotDeep)
        }
    }

    /** Trois rangs de plumes en écailles, du plus court au plus long. */
    private fun angelWing(c: Canvas) {
        asWear {
            Path().apply {
                moveTo(88f, 116f)
                cubicTo(70f, 80f, 34f, 62f, 10f, 78f)
                cubicTo(4f, 104f, 28f, 140f, 62f, 148f)
                cubicTo(80f, 146f, 88f, 132f, 88f, 116f)
                close()
            }.also { c.drawPath(it, fill(Feather)) }

            val rows = listOf(
                Triple(74f, 100f, 11f), Triple(56f, 92f, 12f), Triple(38f, 90f, 12f),
                Triple(70f, 124f, 13f), Triple(50f, 118f, 14f), Triple(32f, 112f, 13f),
                Triple(64f, 142f, 12f), Triple(46f, 138f, 12f)
            )
            rows.forEach { (x, y, r) ->
                Path().apply {
                    moveTo(x + r, y)
                    quadTo(x, y + r * 0.9f, x - r, y)
                    quadTo(x, y - r * 0.4f, x + r, y)
                    close()
                }.also { c.drawPath(it, fill(FeatherD)) }
            }
        }
    }

    /** Deux lobes translucides, cerclés de blanc. Elles ne servent à rien, c'est le but. */
    private fun fairyWing(c: Canvas) {
        asWear {
            val upper = Path().apply {
                moveTo(86f, 114f)
                cubicTo(72f, 76f, 34f, 60f, 14f, 78f)
                cubicTo(6f, 96f, 40f, 116f, 86f, 114f)
                close()
            }
            val lower = Path().apply {
                moveTo(86f, 118f)
                cubicTo(60f, 122f, 30f, 132f, 30f, 146f)
                cubicTo(34f, 156f, 68f, 148f, 86f, 128f)
                close()
            }
            c.drawPath(upper, fill(Fairy)); c.drawPath(lower, fill(Fairy))
            val rim = stroke(FairyRim, 2.6f)
            c.drawPath(upper, rim); c.drawPath(lower, rim)
            // les nervures, deux par lobe
            val vein = stroke(FairyRim, 1.8f)
            c.drawLine(84f, 112f, 26f, 82f, vein)
            c.drawLine(84f, 114f, 34f, 102f, vein)
            c.drawLine(84f, 122f, 38f, 142f, vein)
            sparkle(c, 20f, 76f, 5f, FairyRim)
            sparkle(c, 32f, 148f, 4f, FairyRim)
        }
    }

    /** Deux paires longues et fines, comme du papier de soie. */
    private fun dragonflyWing(c: Canvas) {
        asWear {
            listOf(
                Path().apply {
                    moveTo(88f, 114f)
                    cubicTo(60f, 88f, 26f, 78f, 8f, 84f)
                    cubicTo(16f, 100f, 54f, 114f, 88f, 118f)
                    close()
                },
                Path().apply {
                    moveTo(88f, 120f)
                    cubicTo(62f, 122f, 30f, 134f, 20f, 146f)
                    cubicTo(34f, 154f, 66f, 142f, 88f, 126f)
                    close()
                }
            ).forEach {
                c.drawPath(it, fill(Odo))
                c.drawPath(it, stroke(OdoRim, 2.2f))
            }
            val vein = stroke(OdoRim, 1.4f)
            c.drawLine(84f, 112f, 18f, 88f, vein)
            c.drawLine(84f, 124f, 28f, 144f, vein)
        }
    }

    /** Monarque : orange, nervures noires, points blancs sur le bord. */
    private fun butterflyWing(c: Canvas) {
        asWear {
            val upper = Path().apply {
                moveTo(86f, 112f)
                cubicTo(74f, 78f, 40f, 60f, 16f, 72f)
                cubicTo(6f, 92f, 38f, 112f, 86f, 112f)
                close()
            }
            val lower = Path().apply {
                moveTo(86f, 118f)
                cubicTo(58f, 120f, 28f, 130f, 28f, 146f)
                cubicTo(38f, 158f, 74f, 144f, 86f, 126f)
                close()
            }
            c.drawPath(upper, fill(Monarch)); c.drawPath(lower, fill(Monarch))
            val rim = stroke(Chitin, 4f)
            c.drawPath(upper, rim); c.drawPath(lower, rim)
            val vein = stroke(Chitin, 2.2f)
            c.drawLine(82f, 108f, 30f, 76f, vein)
            c.drawLine(82f, 110f, 26f, 94f, vein)
            c.drawLine(82f, 122f, 40f, 144f, vein)
            listOf(20f to 76f, 32f to 66f, 34f to 148f).forEach { (x, y) ->
                c.drawCircle(x, y, 3f, fill(White))
            }
        }
    }

    /** Coccinelle : deux élytres rouges à pois, bien trop petites pour la porter. */
    private fun ladybugWing(c: Canvas) {
        asWear {
            val shell = Path().apply {
                moveTo(88f, 112f)
                cubicTo(72f, 88f, 46f, 84f, 34f, 100f)
                cubicTo(28f, 118f, 50f, 134f, 78f, 128f)
                cubicTo(86f, 124f, 89f, 118f, 88f, 112f)
                close()
            }
            c.drawPath(shell, fill(Beetle))
            c.drawPath(shell, stroke(Chitin, 3.4f))
            listOf(
                Triple(56f, 100f, 5f), Triple(46f, 116f, 4.5f), Triple(70f, 118f, 4f)
            ).forEach { (x, y, r) -> c.drawCircle(x, y, r, fill(Chitin)) }
        }
    }

    /** Cuisse arrondie plus patte prune à trois orteils, ou une botte par-dessus. */
    private fun leg(c: Canvas, wear: String?) {
        c.drawOval(oval(80f, 164f, 24f, 26f), fill(Pink))

        val foot = Path().apply {
            moveTo(86f, 182f)
            cubicTo(86f, 173f, 74f, 168f, 60f, 169f)
            cubicTo(47f, 170f, 40f, 177f, 40f, 186f)
            cubicTo(40f, 195f, 49f, 200f, 62f, 199f)
            cubicTo(76f, 198f, 86f, 191f, 86f, 182f)
            close()
        }

        if (wear == null) {
            c.drawPath(foot, fill(Plum))
            listOf(175f, 186f, 196f).forEach { c.drawCircle(43f, it, 6.2f, fill(Plum)) }
            return
        }

        // Toute chaussure reprend EXACTEMENT la silhouette de la patte : elle se lit comme
        // une chaussure enfilée et pas comme une forme colorée posée à côté.
        asWear {
            when (wear) {
                "pantoufles" -> {
                    c.drawPath(foot, fill(Fluff))
                    // le bourrelet de fourrure : trois bosses, pour que ça lise « mou »
                    listOf(46f, 62f, 78f).forEach { c.drawCircle(it, 166f, 11f, fill(Wool)) }
                    c.drawRoundRect(RectF(38f, 158f, 90f, 174f), 8f, 8f, fill(Wool))
                    c.drawCircle(44f, 188f, 8f, fill(Wool))      // pompon
                }

                "bottes" -> {
                    c.drawPath(foot, fill(Xmas))
                    c.drawRoundRect(RectF(36f, 190f, 90f, 201f), 6f, 6f, fill(XmasDeep))
                    c.drawRoundRect(RectF(42f, 176f, 88f, 183f), 3.5f, 3.5f, fill(Wool))
                    c.drawRoundRect(RectF(40f, 157f, 94f, 174f), 8f, 8f, fill(Wool))
                    heart(c, 62f, 166f, 5f, Xmas)
                }

                "bas_noel" -> stripedSock(c, foot, Xmas, Wool)
                "bas_halloween" -> stripedSock(c, foot, Pumpkin, Chitin)

                "bas_laine" -> {
                    c.drawPath(foot, fill(Knit))
                    // le tricot affaissé sur la cheville : trois plis, jamais alignés
                    c.drawRoundRect(RectF(38f, 152f, 92f, 170f), 9f, 9f, fill(Knit))
                    c.drawRoundRect(RectF(40f, 158f, 90f, 162f), 2f, 2f, fill(Wool))
                    c.drawRoundRect(RectF(42f, 166f, 88f, 170f), 2f, 2f, fill(Wool))
                    val rib = stroke(Wool, 2f)
                    listOf(50f, 62f, 74f, 86f).forEach { c.drawLine(it, 172f, it, 196f, rib) }
                }

                "bottes_pluie" -> {
                    c.drawPath(foot, fill(Rain))
                    c.drawRoundRect(RectF(36f, 191f, 90f, 201f), 5f, 5f, fill(RainDeep))
                    c.drawRoundRect(RectF(40f, 150f, 92f, 176f), 8f, 8f, fill(Rain))
                    c.drawRoundRect(RectF(40f, 150f, 92f, 156f), 3f, 3f, fill(RainDeep))
                }

                "gougounes" -> {
                    // La patte reste nue : une gougoune, c'est une semelle et une lanière.
                    c.drawPath(foot, fill(Plum))
                    listOf(175f, 186f, 196f).forEach { c.drawCircle(43f, it, 6.2f, fill(Plum)) }
                    c.drawRoundRect(RectF(34f, 192f, 90f, 203f), 6f, 6f, fill(Swim))
                    val strap = stroke(Wool, 4.5f)
                    c.drawLine(46f, 178f, 62f, 190f, strap)
                    c.drawLine(78f, 180f, 62f, 190f, strap)
                }

                "patins" -> {
                    c.drawPath(foot, fill(Skate))
                    c.drawRoundRect(RectF(40f, 148f, 92f, 176f), 7f, 7f, fill(Skate))
                    val lace = stroke(Wool, 2.2f)
                    listOf(154f, 162f, 170f).forEach {
                        c.drawLine(46f, it, 86f, it - 4f, lace)
                    }
                    // la lame : une semelle fine et deux montants
                    c.drawRoundRect(RectF(30f, 204f, 92f, 209f), 2.5f, 2.5f, fill(Shade))
                    val post = stroke(Shade, 3f)
                    c.drawLine(46f, 199f, 46f, 205f, post)
                    c.drawLine(80f, 198f, 80f, 205f, post)
                }

                "espadrilles" -> {
                    c.drawPath(foot, fill(Sneak))
                    c.drawRoundRect(RectF(34f, 190f, 90f, 202f), 6f, 6f, fill(Wool))
                    c.drawRoundRect(RectF(34f, 190f, 90f, 194f), 2f, 2f, fill(SneakD))
                    val lace = stroke(SneakD, 2.2f)
                    listOf(174f, 181f, 188f).forEach { c.drawLine(50f, it, 82f, it - 3f, lace) }
                    c.drawCircle(66f, 172f, 4.5f, fill(Swim))    // l'œillet de couleur
                }

                "ballerines" -> {
                    c.drawPath(foot, fill(Ballet))
                    c.drawRoundRect(RectF(38f, 194f, 88f, 201f), 4f, 4f, fill(Petal))
                    // les rubans croisés qui montent sur la cheville
                    val silk = stroke(Petal, 3f)
                    c.drawLine(48f, 172f, 78f, 156f, silk)
                    c.drawLine(78f, 172f, 48f, 156f, silk)
                    c.drawCircle(63f, 176f, 5f, fill(Petal))
                }

                else -> c.drawPath(foot, fill(Fluff))
            }
        }
    }

    /** Un bas rayé : les bandes sont découpées dans la patte, pas peintes autour. */
    private fun stripedSock(c: Canvas, foot: Path, a: Int, b: Int) {
        val cuff = Path().apply { addRoundRect(RectF(38f, 150f, 92f, 176f), 9f, 9f, Path.Direction.CW) }
        val whole = Path(foot).apply { op(cuff, Path.Op.UNION) }
        c.drawPath(whole, fill(a))
        c.save()
        c.clipPath(whole)
        var y = 152f
        while (y < 204f) {
            c.drawRect(RectF(28f, y, 96f, y + 7f), fill(b))
            y += 14f
        }
        c.restore()
    }

    // ---- le compagnon -----------------------------------------------------

    /**
     * Ce qui est posé par terre à sa droite.
     *
     * À droite parce que la queue balaie vers la gauche et que l'ombre au sol s'arrête
     * vers x=164 : c'est le seul coin de l'espace de dessin qui soit réellement vide,
     * quelles que soient les autres pièces portées.
     */
    /**
     * La taille du compagnon.
     *
     * Les peluches sont dessinées à leur taille d'origine — une quarantaine d'unités sur
     * les 220 de l'espace de dessin — puis agrandies d'un bloc. Un seul nombre à changer
     * plutôt que quarante coordonnées dans huit fonctions, et toutes gardent exactement
     * les mêmes proportions entre elles.
     */
    private const val FRIEND_SCALE = 2f

    /**
     * Le point fixe de l'agrandissement : son appui au sol, à droite.
     *
     * Elle grandit donc vers le haut et vers la gauche depuis ce point, et reste posée sur
     * la même ligne de sol que le dragon. Elle passe devant lui — c'est voulu, une peluche
     * s'appuie contre quelqu'un.
     *
     * 2x est à peu de chose près la limite haute : à cette taille son sommet arrive juste
     * sous le menton du dragon, donc le visage reste entièrement dégagé. Au-delà elle
     * commence à le manger, et à 3x elle occupait presque tout le cadre.
     */
    private const val FRIEND_PIVOT_X = 204f
    private const val FRIEND_PIVOT_Y = 206f

    private fun friend(c: Canvas, wear: String?) {
        if (wear == null) return
        c.save()
        c.scale(FRIEND_SCALE, FRIEND_SCALE, FRIEND_PIVOT_X, FRIEND_PIVOT_Y)
        when (wear) {
            "peluche" -> peluche(c)
            "doudou" -> doudou(c)
            "rhino" -> rhino(c)
            "ours" -> ours(c)
            "chat" -> chat(c)
            "grenouille" -> grenouille(c)
            "herisson" -> herisson(c)
            "baleine" -> baleine(c)
        }
        c.restore()
    }

    /**
     * Le corps commun à toutes les peluches : une boule assise, son ombre, un ventre plus
     * clair. Ce qui les distingue tient entièrement à ce qu'on pose dessus — une corne,
     * des oreilles rondes ou pointues, une nageoire.
     *
     * Faire varier la silhouette plutôt que la seule couleur : six boules identiques
     * repeintes se reconnaîtraient à peine côte à côte dans le casier, et une vignette de
     * soixante pixels ne laisse pas beaucoup de place pour les distinguer autrement.
     */
    private fun plushBody(c: Canvas, body: Int, belly: Int) {
        c.drawOval(oval(186f, 202f, 20f, 6f), fill(Shade).apply { alpha = 170 })
        p.alpha = 255
        c.drawOval(oval(186f, 188f, 18f, 15f), fill(body))
        c.drawOval(oval(186f, 193f, 9f, 8f), fill(belly))
    }

    /** Deux yeux et une bouche, toujours au même endroit : c'est ce qui fait la famille. */
    private fun plushFace(c: Canvas, cy: Float, smile: Boolean = true) {
        c.drawCircle(181f, cy, 1.9f, fill(Ink))
        c.drawCircle(191f, cy, 1.9f, fill(Ink))
        if (smile) {
            Path().apply { moveTo(183f, cy + 6f); quadTo(186f, cy + 9f, 189f, cy + 6f) }
                .also { c.drawPath(it, stroke(Ink, 1.8f)) }
        }
    }

    /** Rond, avec une petite corne sur le museau et deux oreilles en pastille. */
    private fun rhino(c: Canvas) {
        asWear {
            plushBody(c, Rhino, Wool)
            c.drawOval(oval(175f, 168f, 5f, 6f), fill(RhinoD))       // oreilles
            c.drawOval(oval(197f, 168f, 5f, 6f), fill(RhinoD))
            c.drawOval(oval(186f, 174f, 15f, 13f), fill(Rhino))      // tête
            c.drawOval(oval(186f, 181f, 9f, 6f), fill(RhinoD))       // museau
            Path().apply {                                           // la corne
                moveTo(183f, 176f); quadTo(186f, 166f, 189f, 176f); close()
            }.also { c.drawPath(it, fill(Wool)) }
            plushFace(c, 172f, smile = false)
        }
    }

    /** Deux oreilles bien rondes, et un museau clair. La forme la plus évidente. */
    private fun ours(c: Canvas) {
        asWear {
            plushBody(c, Bear, Wool)
            c.drawCircle(174f, 168f, 7f, fill(BearD))
            c.drawCircle(198f, 168f, 7f, fill(BearD))
            c.drawOval(oval(186f, 174f, 14f, 13f), fill(Bear))
            c.drawOval(oval(186f, 180f, 8f, 6f), fill(Wool))
            c.drawCircle(186f, 178f, 2.4f, fill(Ink))                // truffe
            plushFace(c, 171f, smile = false)
        }
    }

    /** Oreilles pointues et une queue qui s'enroule : la seule à dépasser du corps. */
    private fun chat(c: Canvas) {
        asWear {
            Path().apply {                                           // la queue, derrière
                moveTo(202f, 194f)
                cubicTo(216f, 192f, 216f, 176f, 206f, 176f)
            }.also { c.drawPath(it, stroke(Kitty, 5f)) }
            plushBody(c, Kitty, Wool)
            listOf(176f to -1f, 196f to 1f).forEach { (x, d) ->
                Path().apply {
                    moveTo(x - 6f * d, 174f); lineTo(x + 1f * d, 160f)
                    lineTo(x + 7f * d, 173f); close()
                }.also { c.drawPath(it, fill(KittyD)) }
            }
            c.drawOval(oval(186f, 174f, 14f, 12f), fill(Kitty))
            plushFace(c, 172f)
            val whisker = stroke(KittyD, 1.4f)
            c.drawLine(172f, 178f, 180f, 179f, whisker)
            c.drawLine(200f, 178f, 192f, 179f, whisker)
        }
    }

    /** Les yeux SUR la tête, en deux bulles : c'est ça qui fait la grenouille. */
    private fun grenouille(c: Canvas) {
        asWear {
            plushBody(c, Frog, 0xFFE8F2C9.toInt())
            c.drawOval(oval(186f, 176f, 15f, 11f), fill(Frog))       // tête large et basse
            c.drawCircle(178f, 166f, 7f, fill(Frog))                 // les bulles oculaires
            c.drawCircle(194f, 166f, 7f, fill(Frog))
            c.drawCircle(178f, 165f, 4f, fill(Wool))
            c.drawCircle(194f, 165f, 4f, fill(Wool))
            c.drawCircle(178f, 165f, 2f, fill(Ink))
            c.drawCircle(194f, 165f, 2f, fill(Ink))
            Path().apply { moveTo(176f, 180f); quadTo(186f, 187f, 196f, 180f) }
                .also { c.drawPath(it, stroke(FrogD, 2.2f)) }        // la bouche, très large
        }
    }

    /** Le dos entièrement en piquants, la face claire : deux textures, une silhouette. */
    private fun herisson(c: Canvas) {
        asWear {
            c.drawOval(oval(186f, 202f, 20f, 6f), fill(Shade).apply { alpha = 170 })
            p.alpha = 255
            c.drawOval(oval(186f, 188f, 19f, 16f), fill(HedgeQ))
            // Les piquants, un par un le long de l'arc du dos. Un seul contour dentelé
            // serait plus court à écrire et donnerait une scie, pas un hérisson.
            var a = 190.0
            while (a < 350.0) {
                val r = Math.toRadians(a)
                val cx = 186f + (kotlin.math.cos(r) * 17f).toFloat()
                val cy = 188f + (kotlin.math.sin(r) * 14f).toFloat()
                val tx = 186f + (kotlin.math.cos(r) * 25f).toFloat()
                val ty = 188f + (kotlin.math.sin(r) * 22f).toFloat()
                val n = Math.toRadians(a + 90.0)
                val ox = (kotlin.math.cos(n) * 4f).toFloat()
                val oy = (kotlin.math.sin(n) * 4f).toFloat()
                Path().apply {
                    moveTo(cx + ox, cy + oy); lineTo(tx, ty); lineTo(cx - ox, cy - oy); close()
                }.also { c.drawPath(it, fill(HedgeQ)) }
                a += 16.0
            }
            c.drawOval(oval(190f, 184f, 13f, 12f), fill(Hedge))      // la face, décalée
            c.drawCircle(197f, 188f, 2.2f, fill(Ink))                // truffe
            c.drawCircle(188f, 182f, 1.9f, fill(Ink))
            c.drawCircle(195f, 181f, 1.9f, fill(Ink))
        }
    }

    /** Pas de pattes, pas d'oreilles : un corps, une nageoire, un jet. */
    private fun baleine(c: Canvas) {
        asWear {
            c.drawOval(oval(186f, 202f, 20f, 6f), fill(Shade).apply { alpha = 170 })
            p.alpha = 255
            c.drawOval(oval(184f, 188f, 20f, 14f), fill(Whale))
            Path().apply {                                           // la queue
                moveTo(203f, 188f)
                lineTo(216f, 176f); lineTo(214f, 190f)
                lineTo(216f, 200f); close()
            }.also { c.drawPath(it, fill(WhaleD)) }
            c.drawOval(oval(182f, 194f, 12f, 6f), fill(Wool))        // le ventre
            Path().apply { moveTo(178f, 180f); quadTo(174f, 170f, 180f, 166f) }
                .also { c.drawPath(it, stroke(Glass, 3.4f)) }        // le jet
            c.drawCircle(176f, 186f, 2f, fill(Ink))
            Path().apply { moveTo(170f, 192f); quadTo(174f, 195f, 179f, 192f) }
                .also { c.drawPath(it, stroke(Ink, 1.8f)) }
        }
    }

    /** Une petite chose rose assise. Corps, oreilles, tête, deux yeux, et c'est tout. */
    private fun peluche(c: Canvas) {
        asWear {
            c.drawOval(oval(186f, 202f, 20f, 6f), fill(Shade).apply { alpha = 170 })
            p.alpha = 255
            c.drawOval(oval(186f, 188f, 17f, 15f), fill(Plush))       // corps
            c.drawCircle(174f, 170f, 6.5f, fill(PlushD))              // oreilles
            c.drawCircle(198f, 170f, 6.5f, fill(PlushD))
            c.drawOval(oval(186f, 174f, 14f, 12f), fill(Plush))       // tête
            c.drawOval(oval(186f, 192f, 8f, 6f), fill(Wool))          // ventre
            c.drawCircle(181f, 173f, 1.9f, fill(Ink))
            c.drawCircle(191f, 173f, 1.9f, fill(Ink))
            Path().apply { moveTo(183f, 179f); quadTo(186f, 182f, 189f, 179f) }
                .also { c.drawPath(it, stroke(Ink, 1.8f)) }
        }
    }

    /** Un tissu mou en tas, avec le coin relevé — celui qu'on tient, donc le meilleur. */
    private fun doudou(c: Canvas) {
        asWear {
            Path().apply {
                moveTo(154f, 200f)
                cubicTo(158f, 180f, 184f, 174f, 204f, 181f)
                cubicTo(214f, 185f, 212f, 198f, 202f, 203f)
                cubicTo(186f, 209f, 164f, 208f, 154f, 200f)
                close()
            }.also { c.drawPath(it, fill(Quilt)) }

            Path().apply { moveTo(164f, 196f); quadTo(184f, 189f, 204f, 194f) }
                .also { c.drawPath(it, stroke(QuiltDim, 2.4f)) }
            Path().apply { moveTo(166f, 202f); quadTo(184f, 197f, 202f, 200f) }
                .also { c.drawPath(it, stroke(QuiltDim, 2f)) }

            Path().apply {
                moveTo(192f, 178f)
                quadTo(204f, 164f, 213f, 171f)
                quadTo(206f, 180f, 192f, 180f)
                close()
            }.also { c.drawPath(it, fill(QuiltDim)) }
        }
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        Path().apply {
            moveTo(cx, cy + r * 0.85f)
            cubicTo(cx - r * 1.5f, cy - r * 0.2f, cx - r * 0.5f, cy - r * 1.2f, cx, cy - r * 0.35f)
            cubicTo(cx + r * 0.5f, cy - r * 1.2f, cx + r * 1.5f, cy - r * 0.2f, cx, cy + r * 0.85f)
            close()
        }.also { c.drawPath(it, fill(color)) }
    }

    /** Corps en poire : épaules étroites, assise large. Le ventre pâle par-dessus. */
    private fun torso(c: Canvas, wear: String?) {
        val shape = Path().apply {
            moveTo(110f, 112f)
            cubicTo(90f, 112f, 80f, 124f, 77f, 142f)
            cubicTo(72f, 163f, 76f, 184f, 88f, 192f)
            cubicTo(99f, 198f, 121f, 198f, 132f, 192f)
            cubicTo(144f, 184f, 148f, 163f, 143f, 142f)
            cubicTo(140f, 124f, 130f, 112f, 110f, 112f)
            close()
        }
        c.drawPath(shape, fill(Pink))

        if (wear == null) {
            c.drawOval(oval(110f, 166f, 26f, 31f), fill(Belly))
            return
        }

        // Chaque vêtement est découpé DANS la silhouette du corps, jamais dessiné
        // par-dessus : sinon il déborde et le dragon a l'air d'avoir avalé un rectangle.
        asWear {
            c.save()
            c.clipPath(shape)
            val all = RectF(58f, 106f, 162f, 198f)

            when (wear) {
                "pyjama" -> {
                    c.drawRect(all, fill(Night))
                    c.drawRect(RectF(60f, 150f, 160f, 154f), fill(NightDim))   // couture
                    listOf(
                        Triple(86f, 128f, 7f), Triple(132f, 140f, 6f),
                        Triple(94f, 176f, 6f), Triple(128f, 166f, 7f)
                    ).forEach { (x, y, r) -> sparkle(c, x, y, r, Wool) }
                }

                "hoodie" -> {
                    c.drawRect(all, fill(Xmas))
                    c.drawRect(RectF(58f, 184f, 162f, 198f), fill(Wool))       // bord côtelé
                    c.drawRoundRect(RectF(90f, 152f, 130f, 176f), 9f, 9f, fill(XmasDeep))
                    c.drawRect(RectF(90f, 152f, 130f, 155f), fill(Wool))
                }

                "hoodie_douillet" -> {
                    c.drawRect(all, fill(Cozy))
                    c.drawRect(RectF(58f, 182f, 162f, 198f), fill(CozyDeep))   // bord côtelé
                    c.drawRoundRect(RectF(88f, 150f, 132f, 176f), 10f, 10f, fill(CozyDeep))
                    // les cordons, trop longs, jamais de la même longueur
                    val cord = stroke(Wool, 3f)
                    c.drawLine(102f, 124f, 100f, 146f, cord)
                    c.drawLine(118f, 124f, 121f, 138f, cord)
                }

                "hoodie_citrouille" -> {
                    c.drawRect(all, fill(Pumpkin))
                    c.drawRect(RectF(58f, 184f, 162f, 198f), fill(PumpkinD))
                    // la face de citrouille, sur le ventre
                    triangle(c, 98f, 152f, 9f, Chitin)
                    triangle(c, 122f, 152f, 9f, Chitin)
                    Path().apply {
                        moveTo(94f, 170f)
                        lineTo(102f, 178f); lineTo(110f, 170f)
                        lineTo(118f, 178f); lineTo(126f, 170f)
                        lineTo(122f, 184f); lineTo(98f, 184f)
                        close()
                    }.also { c.drawPath(it, fill(Chitin)) }
                }

                "robe" -> {
                    // La jupe s'évase : le haut serré, le bas large, sinon c'est un sac.
                    c.drawRect(RectF(58f, 106f, 162f, 150f), fill(Petal))
                    Path().apply {
                        moveTo(84f, 150f); lineTo(136f, 150f)
                        lineTo(162f, 198f); lineTo(58f, 198f); close()
                    }.also { c.drawPath(it, fill(Petal)) }
                    c.drawRect(RectF(58f, 148f, 162f, 154f), fill(Pollen))     // la ceinture
                    listOf(
                        Triple(92f, 170f, 5f), Triple(126f, 164f, 4.5f),
                        Triple(110f, 186f, 5f), Triple(84f, 130f, 4f)
                    ).forEach { (x, y, r) -> flower(c, x, y, r) }
                }

                "fantome" -> {
                    c.drawRect(all, fill(Ghost))
                    // l'ourlet en vagues, celui qui fait le drap
                    Path().apply {
                        moveTo(58f, 182f)
                        quadTo(74f, 200f, 90f, 184f); quadTo(106f, 200f, 122f, 184f)
                        quadTo(138f, 200f, 154f, 184f); lineTo(162f, 198f)
                        lineTo(58f, 198f); close()
                    }.also { c.drawPath(it, fill(GhostDim)) }
                    c.drawOval(oval(96f, 140f, 7f, 9f), fill(GhostDim))
                    c.drawOval(oval(124f, 140f, 7f, 9f), fill(GhostDim))
                }

                "noel_moche" -> {
                    c.drawRect(all, fill(UglyRed))
                    c.drawRect(RectF(58f, 184f, 162f, 198f), fill(UglyGrn))
                    // deux frises en zigzag, l'ingrédient obligatoire du genre
                    listOf(138f, 168f).forEach { y ->
                        val zig = stroke(Wool, 3f)
                        var x = 62f
                        while (x < 158f) {
                            c.drawLine(x, y + 6f, x + 8f, y - 6f, zig)
                            c.drawLine(x + 8f, y - 6f, x + 16f, y + 6f, zig)
                            x += 16f
                        }
                    }
                    c.drawRect(RectF(58f, 150f, 162f, 156f), fill(UglyGrn))
                }

                "impermeable" -> {
                    c.drawRect(all, fill(Rain))
                    c.drawRect(RectF(58f, 184f, 162f, 198f), fill(RainDeep))
                    // la patte de boutonnage et trois boutons
                    c.drawRect(RectF(104f, 106f, 116f, 198f), fill(RainDeep))
                    listOf(134f, 156f, 178f).forEach { c.drawCircle(110f, it, 3.4f, fill(Wool)) }
                }

                "mariniere" -> {
                    c.drawRect(all, fill(Wool))
                    var y = 118f
                    while (y < 198f) {
                        c.drawRect(RectF(58f, y, 162f, y + 8f), fill(Navy))
                        y += 16f
                    }
                }

                "salopette" -> {
                    c.drawRect(all, fill(Wool))                                // le t-shirt dessous
                    Path().apply {                                             // le pantalon
                        moveTo(74f, 146f); lineTo(146f, 146f)
                        lineTo(162f, 198f); lineTo(58f, 198f); close()
                    }.also { c.drawPath(it, fill(Denim)) }
                    c.drawRoundRect(RectF(92f, 152f, 128f, 176f), 4f, 4f, fill(DenimD))
                    val brace = stroke(Denim, 7f)                              // les bretelles
                    c.drawLine(92f, 114f, 88f, 148f, brace)
                    c.drawLine(128f, 114f, 132f, 148f, brace)
                    c.drawCircle(90f, 148f, 3.4f, fill(Pollen))
                    c.drawCircle(130f, 148f, 3.4f, fill(Pollen))
                }

                "doudoune" -> {
                    c.drawRect(all, fill(Puffer))
                    // les boudins horizontaux : c'est eux qui font « matelassé »
                    var y = 116f
                    while (y < 198f) {
                        c.drawRect(RectF(58f, y, 162f, y + 3f), fill(PufferD))
                        y += 17f
                    }
                    c.drawRect(RectF(106f, 106f, 114f, 198f), fill(PufferD))   // la fermeture
                }

                "maillot" -> {
                    c.drawOval(oval(110f, 158f, 30f, 34f), fill(Suit))
                    c.drawRect(RectF(94f, 106f, 126f, 150f), fill(Suit))
                    val strap = stroke(Suit, 6f)
                    c.drawLine(96f, 112f, 90f, 138f, strap)
                    c.drawLine(124f, 112f, 130f, 138f, strap)
                    flower(c, 122f, 172f, 6f)
                }

                "tablier" -> {
                    c.drawRoundRect(RectF(86f, 132f, 134f, 198f), 8f, 8f, fill(Apron))
                    c.drawRoundRect(RectF(94f, 158f, 126f, 180f), 5f, 5f, fill(CozyDeep))
                    val tie = stroke(AntlerD, 4f)
                    c.drawLine(86f, 146f, 62f, 152f, tie)
                    c.drawLine(134f, 146f, 158f, 152f, tie)
                    c.drawLine(110f, 132f, 110f, 118f, tie)
                }

                "cape" -> {
                    // Le devant n'est qu'une agrafe : tout le tissu est derrière, posé
                    // avant le corps par `capeBehind`.
                    c.drawOval(oval(110f, 166f, 26f, 31f), fill(Belly))
                }

                else -> c.drawOval(oval(110f, 166f, 26f, 31f), fill(Belly))
            }
            c.restore()

            if (wear == "hoodie") c.drawLine(110f, 124f, 110f, 148f, stroke(XmasDeep, 2f))
            if (wear == "cape") {
                c.drawCircle(102f, 120f, 5f, fill(Gold))
                c.drawCircle(118f, 120f, 5f, fill(Gold))
                c.drawLine(102f, 120f, 118f, 120f, stroke(GoldDeep, 3f))
            }
        }
    }

    /** Le tissu de la cape, tombant derrière tout le reste. */
    private fun capeBehind(c: Canvas) {
        asWear {
            Path().apply {
                moveTo(104f, 116f); lineTo(116f, 116f)
                cubicTo(150f, 130f, 166f, 172f, 158f, 200f)
                quadTo(110f, 210f, 62f, 200f)
                cubicTo(54f, 172f, 70f, 130f, 104f, 116f)
                close()
            }.also { c.drawPath(it, fill(Plum)) }
            val fold = stroke(PinkDark, 2.6f)
            c.drawLine(92f, 130f, 78f, 194f, fold)
            c.drawLine(128f, 130f, 142f, 194f, fold)
        }
    }

    /** Le col : fourrure pour les hoodies, capuchon pour l'imperméable. */
    private fun collar(c: Canvas, wear: String?) {
        when (wear) {
            "hoodie" -> hoodCollar(c, Wool)
            "hoodie_douillet" -> hoodCollar(c, Cozy)
            "hoodie_citrouille" -> hoodCollar(c, PumpkinD)
            "impermeable" -> hoodCollar(c, RainDeep)
        }
    }

    private fun triangle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r, cy + r); lineTo(cx - r, cy + r); close()
        }.also { c.drawPath(it, fill(color)) }
    }

    /** Cinq pétales et un cœur. Sert à la robe, au maillot et à la couronne de fleurs. */
    private fun flower(c: Canvas, cx: Float, cy: Float, r: Float, petal: Int = Wool) {
        for (i in 0 until 5) {
            val a = i * 2.0 * Math.PI / 5.0
            c.drawCircle(
                cx + (kotlin.math.cos(a) * r).toFloat(),
                cy + (kotlin.math.sin(a) * r).toFloat(),
                r * 0.62f, fill(petal)
            )
        }
        c.drawCircle(cx, cy, r * 0.5f, fill(Pollen))
    }

    /** Le col, posé au creux du cou avant que la tête soit dessinée. */
    private fun hoodCollar(c: Canvas, color: Int) {
        asWear {
            c.drawOval(oval(110f, 120f, 33f, 10f), fill(color))
            val cord = stroke(color, 3f)
            c.drawLine(102f, 126f, 100f, 138f, cord)
            c.drawLine(118f, 126f, 120f, 138f, cord)
        }
    }

    /**
     * Ce qui se pose sur la tête, une fois la tête dessinée.
     *
     * Toutes ces pièces vivent au-dessus de y=60 sauf les lunettes, qui sont sur le museau
     * à hauteur des yeux : c'est le seul endroit où elles veulent dire quelque chose.
     */
    private fun headPiece(c: Canvas, wear: String?) {
        when (wear) {
            "tuque" -> tuque(c)
            "couronne" -> couronne(c)
            "sorciere" -> witchHat(c)
            "chapeau_citrouille" -> pumpkinHat(c)
            "bois" -> antlers(c)
            "paille" -> strawHat(c)
            "fleurs" -> flowerCrown(c)
            "tuque_cotelee" -> ribbedBeanie(c)
            "oreilles_chat" -> catEars(c)
            "fete" -> partyHat(c)
            "bonnet_bain" -> swimCap(c)
            "casquette" -> backwardsCap(c)
            "lunettes" -> sunglasses(c)
            "aureole" -> halo(c)
        }
    }

    /** Cône penché et bord large : sans le pli, un chapeau pointu lit comme un cornet. */
    private fun witchHat(c: Canvas) {
        asWear {
            c.drawOval(oval(110f, 48f, 60f, 12f), fill(Witch))       // le bord
            Path().apply {
                moveTo(88f, 48f); lineTo(132f, 48f)
                cubicTo(128f, 26f, 122f, 10f, 138f, 0f)
                cubicTo(112f, 4f, 100f, 24f, 88f, 48f)
                close()
            }.also { c.drawPath(it, fill(Witch)) }
            c.drawRoundRect(RectF(86f, 38f, 134f, 48f), 3f, 3f, fill(WitchDim))
            c.drawCircle(120f, 43f, 4f, fill(Pollen))                // la boucle
        }
    }

    /** Une citrouille portée comme un bonnet : côtes verticales et queue en tire-bouchon. */
    private fun pumpkinHat(c: Canvas) {
        asWear {
            c.drawOval(oval(110f, 34f, 40f, 26f), fill(Pumpkin))
            val rib = stroke(PumpkinD, 2.6f)
            listOf(92f, 110f, 128f).forEach { x ->
                Path().apply { moveTo(x, 12f); quadTo(x - 6f, 34f, x, 56f) }
                    .also { c.drawPath(it, rib) }
            }
            c.drawRoundRect(RectF(104f, 4f, 116f, 16f), 4f, 4f, fill(Stem))
            Path().apply { moveTo(116f, 8f); quadTo(128f, 2f, 124f, 14f) }
                .also { c.drawPath(it, stroke(Stem, 3f)) }
        }
    }

    /** Deux bois, chacun trois pointes, plantés entre les cornes. */
    private fun antlers(c: Canvas) {
        asWear {
            val branch = stroke(Antler, 5f)
            c.drawLine(96f, 46f, 84f, 12f, branch)
            c.drawLine(90f, 32f, 72f, 22f, branch)
            c.drawLine(87f, 22f, 74f, 6f, branch)
            c.drawLine(124f, 46f, 136f, 12f, branch)
            c.drawLine(130f, 32f, 148f, 22f, branch)
            c.drawLine(133f, 22f, 146f, 6f, branch)
            c.drawCircle(96f, 46f, 4.5f, fill(AntlerD))
            c.drawCircle(124f, 46f, 4.5f, fill(AntlerD))
        }
    }

    /** Bord très large et calotte basse : c'est le bord qui fait l'été, pas la calotte. */
    private fun strawHat(c: Canvas) {
        asWear {
            c.drawOval(oval(110f, 50f, 66f, 13f), fill(Straw))
            c.drawOval(oval(110f, 50f, 66f, 13f), stroke(StrawD, 2.4f))
            c.drawOval(oval(110f, 36f, 34f, 20f), fill(Straw))
            c.drawRoundRect(RectF(78f, 42f, 142f, 52f), 4f, 4f, fill(Ribbon))
            Path().apply {
                moveTo(140f, 44f); lineTo(156f, 38f); lineTo(154f, 52f); close()
            }.also { c.drawPath(it, fill(Ribbon)) }
        }
    }

    /** Un arceau de fleurs qui suit la courbe du crâne. */
    private fun flowerCrown(c: Canvas) {
        asWear {
            Path().apply { moveTo(72f, 52f); quadTo(110f, 22f, 148f, 52f) }
                .also { c.drawPath(it, stroke(Leaf, 4f)) }
            listOf(
                Triple(76f, 50f, 7f), Triple(93f, 36f, 8f), Triple(110f, 30f, 9f),
                Triple(127f, 36f, 8f), Triple(144f, 50f, 7f)
            ).forEach { (x, y, r) -> flower(c, x, y, r, Petal) }
        }
    }

    /** Bonnet côtelé à revers, sans pompon : le cousin sobre de la tuque. */
    private fun ribbedBeanie(c: Canvas) {
        asWear {
            Path().apply {
                moveTo(76f, 50f)
                cubicTo(80f, 16f, 140f, 16f, 144f, 50f)
                close()
            }.also { c.drawPath(it, fill(Teal)) }
            val rib = stroke(0xFF6FA8AE.toInt(), 2.6f)
            listOf(90f, 102f, 118f, 130f).forEach { x -> c.drawLine(x, 22f, x, 46f, rib) }
            c.drawRoundRect(RectF(72f, 42f, 148f, 58f), 8f, 8f, fill(Teal))
            c.drawRoundRect(RectF(72f, 48f, 148f, 52f), 2f, 2f, fill(0xFF6FA8AE.toInt()))
        }
    }

    /** Serre-tête à oreilles : deux triangles doublés de rose, posés sur un arceau fin. */
    private fun catEars(c: Canvas) {
        asWear {
            Path().apply { moveTo(76f, 54f); quadTo(110f, 34f, 144f, 54f) }
                .also { c.drawPath(it, stroke(Ink, 4f)) }
            listOf(88f to -1f, 132f to 1f).forEach { (x, d) ->
                Path().apply {
                    moveTo(x - 13f * d, 48f); lineTo(x + 3f * d, 16f)
                    lineTo(x + 15f * d, 46f); close()
                }.also { c.drawPath(it, fill(Ink)) }
                Path().apply {
                    moveTo(x - 7f * d, 45f); lineTo(x + 3f * d, 26f)
                    lineTo(x + 9f * d, 44f); close()
                }.also { c.drawPath(it, fill(Petal)) }
            }
        }
    }

    /** Cône de fête, rayé, pompon au sommet. Porté n'importe quel jour de l'année. */
    private fun partyHat(c: Canvas) {
        asWear {
            val cone = Path().apply {
                moveTo(110f, 6f); lineTo(136f, 52f); lineTo(84f, 52f); close()
            }
            c.drawPath(cone, fill(Party))
            c.save(); c.clipPath(cone)
            var y = 10f
            while (y < 54f) {
                c.drawRect(RectF(80f, y, 140f, y + 6f), fill(Pollen))
                y += 12f
            }
            c.restore()
            c.drawCircle(110f, 6f, 7f, fill(Wool))
        }
    }

    /** Bonnet de bain moulant, et les lunettes remontées sur le front. */
    private fun swimCap(c: Canvas) {
        asWear {
            Path().apply {
                moveTo(70f, 58f)
                cubicTo(72f, 20f, 148f, 20f, 150f, 58f)
                close()
            }.also { c.drawPath(it, fill(Swim)) }
            c.drawOval(oval(92f, 52f, 13f, 10f), fill(Glass))
            c.drawOval(oval(128f, 52f, 13f, 10f), fill(Glass))
            c.drawOval(oval(92f, 52f, 13f, 10f), stroke(Wool, 2.6f))
            c.drawOval(oval(128f, 52f, 13f, 10f), stroke(Wool, 2.6f))
            c.drawLine(105f, 52f, 115f, 52f, stroke(Wool, 3f))
        }
    }

    /** Casquette à l'envers : la visière part vers l'arrière, c'est tout l'intérêt. */
    private fun backwardsCap(c: Canvas) {
        asWear {
            Path().apply {
                moveTo(74f, 54f)
                cubicTo(78f, 22f, 142f, 22f, 146f, 54f)
                close()
            }.also { c.drawPath(it, fill(Cap)) }
            c.drawRoundRect(RectF(70f, 48f, 150f, 58f), 5f, 5f, fill(CapDeep))
            // la visière, derrière la tête, donc à gauche du dessin
            Path().apply {
                moveTo(74f, 50f)
                cubicTo(52f, 48f, 40f, 54f, 42f, 62f)
                cubicTo(56f, 62f, 68f, 58f, 76f, 56f)
                close()
            }.also { c.drawPath(it, fill(CapDeep)) }
            c.drawCircle(110f, 26f, 4f, fill(Wool))
            c.drawRect(RectF(126f, 50f, 146f, 56f), fill(Wool))   // la bande de réglage
        }
    }

    /** Sur le museau, à hauteur des yeux : ailleurs elles ne veulent rien dire. */
    private fun sunglasses(c: Canvas) {
        asWear {
            c.drawRoundRect(RectF(74f, 68f, 104f, 92f), 9f, 9f, fill(Lens))
            c.drawRoundRect(RectF(116f, 68f, 146f, 92f), 9f, 9f, fill(Lens))
            c.drawRect(RectF(104f, 74f, 116f, 80f), fill(Lens))
            c.drawLine(74f, 74f, 62f, 70f, stroke(Lens, 4f))
            c.drawLine(146f, 74f, 158f, 70f, stroke(Lens, 4f))
            // le reflet, deux traits fins : sans lui, les verres lisent comme des trous
            val gleam = stroke(Wool, 2.4f)
            c.drawLine(80f, 86f, 92f, 72f, gleam)
            c.drawLine(122f, 86f, 134f, 72f, gleam)
        }
    }

    /** Un anneau qui flotte, posé de travers. Le contraire d'une preuve de sagesse. */
    private fun halo(c: Canvas) {
        asWear {
            c.save()
            c.rotate(-9f, 110f, 16f)
            c.drawOval(oval(110f, 16f, 30f, 9f), stroke(Halo, 6f))
            c.drawOval(oval(110f, 16f, 30f, 9f), stroke(0x66FFFFFF, 2f))
            c.restore()
        }
    }

    /** Tuque tricotée, cornes passées au travers. */
    private fun tuque(c: Canvas) {
        asWear {
            Path().apply {
                moveTo(78f, 48f)
                cubicTo(82f, 12f, 138f, 12f, 142f, 48f)
                close()
            }.also { c.drawPath(it, fill(Knit)) }
            // deux côtes tricotées, pour que la laine ne lise pas comme du plastique
            val rib = stroke(0xFF2F8A72.toInt(), 2.4f)
            c.drawLine(98f, 20f, 94f, 42f, rib)
            c.drawLine(122f, 20f, 126f, 42f, rib)
            c.drawRoundRect(RectF(74f, 38f, 146f, 53f), 7.5f, 7.5f, fill(Wool))
            c.drawCircle(110f, 13f, 11f, fill(Wool))
        }
    }

    /** Patte avant : moufle au bord festonné, deux sillons pour les griffes. */
    /**
     * Le tissu et le poignet de la manche, ou `null` pour les pièces sans manches.
     *
     * Une robe d'été, un maillot, un tablier : ils laissent les bras nus, et leur coller
     * une manche de la couleur du corsage donnerait un vêtement que personne ne porte.
     */
    private fun sleeve(wear: String?): Pair<Int, Int>? = when (wear) {
        "pyjama" -> Night to NightDim
        "hoodie" -> Xmas to Wool
        "hoodie_douillet" -> Cozy to CozyDeep
        "hoodie_citrouille" -> Pumpkin to PumpkinD
        "fantome" -> Ghost to GhostDim
        "noel_moche" -> UglyRed to UglyGrn
        "impermeable" -> Rain to RainDeep
        "mariniere" -> Wool to Navy
        "doudoune" -> Puffer to PufferD
        else -> null                       // robe, salopette, maillot, tablier, cape
    }

    private fun arm(c: Canvas, wear: String?) {
        val shape = Path().apply {
            moveTo(90f, 130f)
            cubicTo(83f, 139f, 82f, 151f, 87f, 159f)
            quadTo(91f, 165f, 94f, 158f)
            quadTo(98f, 165f, 102f, 158f)
            quadTo(106f, 164f, 107f, 155f)
            cubicTo(110f, 144f, 102f, 133f, 97f, 128f)
            close()
        }
        c.drawPath(shape, fill(Pink))

        // La manche s'arrête au poignet : la patte et ses griffes restent visibles, sinon
        // on perd le geste des deux pattes jointes qui fait tout le personnage.
        sleeve(wear)?.let { (cloth, cuff) ->
            asWear {
                c.save(); c.clipPath(shape)
                c.drawRect(RectF(70f, 120f, 112f, 150f), fill(cloth))
                c.drawRect(RectF(70f, 150f, 112f, 156f), fill(cuff))
                c.restore()
            }
        }

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
        lashes(c, mood)
        mouth(c, mood)
        // Les joues par-dessus l'œil : posées avant, le blanc de l'œil les mangeait.
        cheeks(c, mood)
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
    /**
     * Le museau : un petit nez en cœur, et rien d'autre.
     *
     * Il y avait ici un large ovale plus clair percé de deux narines rondes, à hauteur du
     * milieu de la figure. Deux trous côte à côte sur un groin pâle, ça ne se lit que d'une
     * façon, et ce n'était pas « dragon ». Le museau est maintenant de la couleur du
     * visage — donc invisible en tant que forme — et il ne reste qu'un cœur minuscule posé
     * haut, comme le nez d'un chat.
     *
     * Les joues roses et les deux reflets font le reste : c'est de ça qu'est faite une
     * frimousse, pas d'un nez détaillé.
     */
    private fun snout(c: Canvas) {
        // Une ombre très douce sous les yeux, juste pour donner du volume au museau.
        c.drawOval(oval(110f, 96f, 13f, 9f), fill(Crown).apply { alpha = 70 })
        p.alpha = 255

        // Le nez en cœur, petit et haut placé.
        Path().apply {
            moveTo(110f, 95f)
            cubicTo(103f, 89f, 104f, 84f, 107.5f, 84f)
            cubicTo(109.3f, 84f, 110f, 85.6f, 110f, 86.6f)
            cubicTo(110f, 85.6f, 110.7f, 84f, 112.5f, 84f)
            cubicTo(116f, 84f, 117f, 89f, 110f, 95f)
            close()
        }.also { c.drawPath(it, fill(PinkDeep)) }
        c.drawCircle(107.6f, 87f, 1.15f, fill(White).apply { alpha = 190 })
        p.alpha = 255
    }

    /** Les joues, posées après les yeux pour rester au-dessus du blanc de l'œil. */
    private fun cheeks(c: Canvas, mood: Mood) {
        val a = when (mood) {
            Mood.Love, Mood.Cheering, Mood.Proud -> 175
            Mood.Sad, Mood.Pleading -> 150
            else -> 120
        }
        listOf(78f, 142f).forEach { x ->
            c.drawOval(oval(x, 90f, 11f, 7f), fill(Blush).apply { alpha = a })
        }
        p.alpha = 255
    }

    /**
     * Trois cils au coin externe de chaque œil.
     *
     * Ils ne sont pas dessinés sur les humeurs où les yeux sont des traits fermés : sur un
     * arc, les cils se confondent avec le trait et le visage devient une tache.
     */
    private fun lashes(c: Canvas, mood: Mood) {
        if (mood == Mood.Sleeping || mood == Mood.Cheering || mood == Mood.Proud) return
        val lash = stroke(Ink, 2.2f)
        listOf(92f to -1f, 128f to 1f).forEach { (x, d) ->
            c.drawLine(x + d * 13f, 74f, x + d * 19f, 69f, lash)
            c.drawLine(x + d * 14f, 79f, x + d * 21f, 77f, lash)
            c.drawLine(x + d * 12f, 68f, x + d * 16f, 62f, lash)
        }
    }

    private fun eyes(c: Canvas, mood: Mood) {
        val l = PointF(92f, 80f); val r = PointF(128f, 80f)
        when (mood) {
            // Deux reflets et non un : le petit en bas à l'opposé du grand est ce qui
            // donne le vernis. Un œil à un seul reflet reste un bouton.
            Mood.Waiting -> listOf(l, r).forEach {
                c.drawCircle(it.x, it.y, 13.5f, fill(White))
                c.drawCircle(it.x, it.y + 1.5f, 8.4f, fill(Ink))
                c.drawCircle(it.x, it.y + 4f, 3.4f, fill(Crown).apply { alpha = 150 })
                p.alpha = 255
                c.drawCircle(it.x + 4f, it.y - 4f, 3.4f, fill(White))
                c.drawCircle(it.x - 4f, it.y + 5f, 1.7f, fill(White).apply { alpha = 205 })
                p.alpha = 255
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

            // Paupières à mi-hauteur et pupilles poussées d'un même côté : c'est le regard
            // de travers qui boude, pas les yeux fermés qui dorment.
            Mood.Sulking -> listOf(l, r).forEach {
                c.drawCircle(it.x, it.y, 11.5f, fill(White))
                c.drawCircle(it.x - 4f, it.y + 2f, 6f, fill(Ink))
                Path().apply {
                    moveTo(it.x - 12f, it.y - 1f); quadTo(it.x, it.y - 9f, it.x + 12f, it.y - 1f)
                    lineTo(it.x + 12f, it.y - 12f); lineTo(it.x - 12f, it.y - 12f); close()
                }.also { path -> c.drawPath(path, fill(Pink)) }
                c.drawLine(it.x - 12f, it.y - 2f, it.x + 12f, it.y - 2f, stroke(Ink, 2.6f))
            }

            // Très grands, très brillants, avec le sourcil en toit. Les deux reflets sont
            // ce qui fait toute la différence entre supplier et fixer.
            Mood.Pleading -> listOf(l to 1f, r to -1f).forEach { (e, d) ->
                c.drawCircle(e.x, e.y + 1f, 14f, fill(White))
                c.drawCircle(e.x, e.y + 3f, 9.5f, fill(Ink))
                c.drawCircle(e.x + d * 4f, e.y - 2f, 4.2f, fill(White))
                c.drawCircle(e.x - d * 4f, e.y + 7f, 2.4f, fill(White))
                Path().apply {
                    moveTo(e.x - d * 13f, e.y - 14f); quadTo(e.x, e.y - 20f, e.x + d * 12f, e.y - 12f)
                }.also { path -> c.drawPath(path, stroke(PinkDeep, 3f)) }
            }

            Mood.Love -> listOf(l, r).forEach {
                heart(c, it.x, it.y + 1f, 11f, 0xFFE84E7A.toInt())
                heart(c, it.x - 3f, it.y - 2f, 3.5f, White)
            }

            // Yeux fermés en arc INVERSE de la joie : la satisfaction regarde vers le bas,
            // le rire vers le haut. Même trait, sens contraire, tout le sens change.
            Mood.Proud -> listOf(l, r).forEach {
                Path().apply {
                    moveTo(it.x - 11f, it.y - 4f); quadTo(it.x, it.y + 6f, it.x + 11f, it.y - 4f)
                }.also { path -> c.drawPath(path, stroke(Ink, 3.4f)) }
                c.drawCircle(it.x, it.y + 13f, 5f, fill(Blush).apply { alpha = 150 })
                p.alpha = 255
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

            // Une bouche minuscule, poussée d'un côté. Boudeuse, pas triste : le trait est
            // court et décentré au lieu d'être long et tombant.
            Mood.Sulking -> Path().apply {
                moveTo(100f, 108f); quadTo(106f, 104f, 112f, 107f)
            }.also { c.drawPath(it, stroke(Ink, 3.2f)) }

            // La bouche ondulée, celle qui essaie de ne pas pleurer.
            Mood.Pleading -> Path().apply {
                moveTo(99f, 108f)
                quadTo(104f, 103f, 110f, 108f)
                quadTo(116f, 113f, 121f, 107f)
            }.also { c.drawPath(it, stroke(Ink, 3.2f)) }

            Mood.Love -> {
                Path().apply {
                    moveTo(97f, 103f); quadTo(110f, 117f, 123f, 103f)
                    quadTo(110f, 109f, 97f, 103f); close()
                }.also { c.drawPath(it, fill(Ink)) }
            }

            // Un petit sourire fermé, tiré vers le haut d'un seul côté.
            Mood.Proud -> Path().apply {
                moveTo(99f, 104f); quadTo(110f, 111f, 122f, 101f)
            }.also { c.drawPath(it, stroke(Ink, 3.4f)) }
        }
    }

    /**
     * Des larmes, pas de la pluie.
     *
     * La version précédente semait huit gouttes autour du corps, entre x=55 et x=165 :
     * elles ne touchaient ni les yeux ni les joues, flottaient dans le vide, et le dessin
     * se lisait comme un dragon sous l'averse plutôt que comme un dragon qui pleure.
     *
     * Ce qui fait une larme, c'est le CONTACT : une coulée qui part de la paupière basse,
     * suit la joue et s'arrête au bord du menton. La goutte détachée n'arrive qu'après, et
     * seulement sous la coulée, sinon on retombe dans l'averse.
     */
    private fun tears(c: Canvas) {
        listOf(92f, 128f).forEach { ex ->
            // la coulée, collée à la joue : large sous l'œil, effilée vers le bas
            Path().apply {
                moveTo(ex - 5f, 86f)
                cubicTo(ex - 8f, 96f, ex - 6f, 106f, ex - 2f, 111f)
                cubicTo(ex + 3f, 106f, ex + 5f, 96f, ex + 4f, 86f)
                close()
            }.also { c.drawPath(it, fill(Tear)) }

            // le reflet, une seule ligne fine : c'est lui qui donne le mouillé
            Path().apply { moveTo(ex - 2f, 90f); quadTo(ex - 4f, 99f, ex - 2f, 106f) }
                .also { c.drawPath(it, stroke(White, 1.6f)) }

            drop(c, ex - 2f, 122f, 4.6f)     // celle qui vient de se détacher
        }
    }

    /** Une goutte : pointe en haut, ventre en bas. */
    private fun drop(c: Canvas, cx: Float, cy: Float, r: Float) {
        Path().apply {
            moveTo(cx, cy - r * 1.7f)
            cubicTo(cx + r, cy - r * 0.2f, cx + r, cy + r, cx, cy + r)
            cubicTo(cx - r, cy + r, cx - r, cy - r * 0.2f, cx, cy - r * 1.7f)
            close()
        }.also { c.drawPath(it, fill(Tear)) }
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

            // Le petit nuage d'orage au-dessus de la tête, celui des bandes dessinées.
            Mood.Sulking -> {
                c.drawRoundRect(RectF(150f, 22f, 206f, 42f), 10f, 10f, fill(Shade))
                c.drawCircle(166f, 26f, 11f, fill(Shade))
                c.drawCircle(190f, 26f, 9f, fill(Shade))
                val bolt = stroke(PinkDeep, 3f)
                c.drawLine(176f, 44f, 170f, 54f, bolt)
                c.drawLine(170f, 54f, 178f, 52f, bolt)
                c.drawLine(178f, 52f, 172f, 62f, bolt)
            }

            Mood.Love -> listOf(
                Triple(30f, 52f, 9f), Triple(192f, 40f, 11f), Triple(200f, 128f, 7f)
            ).forEach { (x, y, r) -> heart(c, x, y, r, 0xFFE84E7A.toInt()) }

            Mood.Proud -> listOf(
                Triple(26f, 46f, 10f), Triple(196f, 38f, 11f), Triple(204f, 130f, 8f)
            ).forEach { (x, y, r) -> sparkle(c, x, y, r, Gold) }

            else -> Unit
        }
    }

    /** Couronne à trois pointes, portée légèrement de travers. */
    private fun couronne(c: Canvas) {
        asWear {
            c.save()
            c.rotate(-7f, 110f, 46f)
            Path().apply {
                moveTo(74f, 50f)
                lineTo(82f, 20f); lineTo(96f, 40f)
                lineTo(110f, 12f); lineTo(124f, 40f)
                lineTo(138f, 20f); lineTo(146f, 50f)
                close()
            }.also { c.drawPath(it, fill(Gold)) }
            c.drawRoundRect(RectF(72f, 44f, 148f, 58f), 6f, 6f, fill(GoldDeep))
            c.drawCircle(82f, 22f, 5f, fill(Pink))
            c.drawCircle(110f, 14f, 6f, fill(Teal))
            c.drawCircle(138f, 22f, 5f, fill(Pink))
            c.restore()
        }
    }

    private fun sparkle(c: Canvas, cx: Float, cy: Float, r: Float, color: Int = Blush) {
        Path().apply {
            moveTo(cx, cy - r)
            quadTo(cx + r * 0.28f, cy - r * 0.28f, cx + r, cy)
            quadTo(cx + r * 0.28f, cy + r * 0.28f, cx, cy + r)
            quadTo(cx - r * 0.28f, cy + r * 0.28f, cx - r, cy)
            quadTo(cx - r * 0.28f, cy - r * 0.28f, cx, cy - r)
            close()
        }.also { c.drawPath(it, fill(color)) }
    }

    /** Le dragon entier sur fond transparent — widget, aperçus, tout ce qui n'est pas Compose. */
    fun bitmap(px: Int, mood: Mood, worn: Set<String> = emptySet()): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), mood, px.toFloat(), 0f, worn)
        return bmp
    }

    /** Tête recadrée en rond, pour la grosse icône de la notification. */
    fun faceBitmap(px: Int, mood: Mood, worn: Set<String> = emptySet()): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawCircle(px / 2f, px / 2f, px / 2f, fill(0xFFFCEFF4.toInt()))
        val k = px / 220f
        c.save()
        c.translate(px * 0.5f, px * 0.56f)
        c.scale(1.55f, 1.55f)
        c.translate(-110f * k, -76f * k)
        draw(c, mood, 220f * k, 0f, worn)
        c.restore()
        return bmp
    }
}
