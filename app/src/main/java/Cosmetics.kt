package com.example.medtap.ui

import com.example.medtap.Her

/**
 * Le catalogue des cosmétiques.
 *
 * Ajouter une pièce, c'est deux gestes : une ligne dans [ALL], et une fonction de dessin
 * dans Dragon.kt branchée sur le même identifiant. Rien d'autre à toucher — le casier, le
 * coffre et la règle « une par jour » lisent tous cette liste.
 *
 * L'ordre compte : c'est l'ordre dans lequel les pièces sont gagnées, une par journée
 * complète. Les premières devraient donc être les plus faciles à aimer.
 */
/**
 * [WINGS] n'est pas un emplacement comme les autres : les ailes existent déjà sur le
 * dragon nu, donc une pièce d'aile REMPLACE une partie du corps au lieu de se poser
 * dessus. C'est pour ça que `Dragon.wing` prend la pièce en paramètre plutôt que d'être
 * suivie d'un dessin séparé.
 *
 * [FRIEND] n'est pas porté du tout : il est posé par terre à côté d'elle, hors du
 * balancement, parce qu'une peluche qui respire au même rythme que le dragon se lit
 * comme un bogue.
 */
enum class Slot(val label: String) {
    HEAD("Tête"), BODY("Corps"), FEET("Pattes"), WINGS("Ailes"), FRIEND("Compagnon")
}

data class Cosmetic(
    val id: String,
    val name: String,
    val slot: Slot,
    val blurb: String
)

object Cosmetics {

    val ALL = listOf(
        Cosmetic(
            id = "tuque",
            name = "La tuque à pompon",
            slot = Slot.HEAD,
            blurb = "Tricotée serré, avec deux trous pour les cornes. Framboise n'a plus froid aux oreilles."
        ),
        Cosmetic(
            id = "bottes",
            name = "Les petites bottes",
            slot = Slot.FEET,
            blurb = "Rouges, avec un cœur sur le côté. Elles couinent un peu quand elle marche."
        ),
        Cosmetic(
            id = "pantoufles",
            name = "Les pantoufles nuages",
            slot = Slot.FEET,
            blurb = "Molles, roses, avec un pompon. Framboise refuse de les enlever pour sortir."
        ),
        Cosmetic(
            id = "couronne",
            name = "La petite couronne",
            slot = Slot.HEAD,
            blurb = "Trois pointes, trois pierres. Elle la porte de travers et personne n'ose le dire."
        ),
        Cosmetic(
            id = "pyjama",
            name = "Le pyjama étoilé",
            slot = Slot.BODY,
            blurb = "Bleu nuit, semé d'étoiles. Techniquement fait pour dormir. Porté à toute heure."
        ),
        Cosmetic(
            id = "hoodie",
            name = "Le hoodie de Noël",
            slot = Slot.BODY,
            blurb = "Fourrure blanche au col, poche devant pour cacher des biscuits. Porté à l'année."
        ),

        // Les deux compagnons d'abord : ce sont les plus faciles à aimer, et l'ordre de
        // cette liste est l'ordre dans lequel les pièces sont gagnées.
        Cosmetic(
            id = "peluche",
            name = "La peluche",
            slot = Slot.FRIEND,
            blurb = "Un petit truc rose assis à côté d'elle. Il n'a pas de nom. Il n'en a pas besoin."
        ),
        Cosmetic(
            id = "doudou",
            name = "Le doudou",
            slot = Slot.FRIEND,
            blurb = "Bleu, râpé sur un coin, traîné partout. Le coin râpé, c'est le meilleur bout."
        ),

        Cosmetic(
            id = "ailes_fee",
            name = "Les ailes de fée",
            slot = Slot.WINGS,
            blurb = "Translucides, veinées de blanc. Elles ne servent absolument à rien et c'est parfait."
        ),
        Cosmetic(
            id = "ailes_ange",
            name = "Les ailes d'ange",
            slot = Slot.WINGS,
            blurb = "Blanches, en plumes. Framboise trouve que ça lui donne un genre. Elle a raison."
        ),
        Cosmetic(
            id = "ailes_monarque",
            name = "Les ailes de monarque",
            slot = Slot.WINGS,
            blurb = "Orange et noir, comme ceux qui traversent le fleuve à l'automne."
        ),
        Cosmetic(
            id = "ailes_coccinelle",
            name = "Les ailes de coccinelle",
            slot = Slot.WINGS,
            blurb = "Rouges à pois, deux fois trop petites. Elle décolle quand même. Un peu."
        ),
        Cosmetic(
            id = "ailes_libellule",
            name = "Les ailes de libellule",
            slot = Slot.WINGS,
            blurb = "Longues, fines, turquoise. Elles font un bruit de papier quand elle bouge."
        ),
        Cosmetic(
            id = "ailes_givrees",
            name = "Les ailes givrées",
            slot = Slot.WINGS,
            blurb = "Bleu glace, bordées de cristaux. Elles fondent un peu près du calorifère."
        ),
        Cosmetic(
            id = "ailes_arcenciel",
            name = "Les ailes arc-en-ciel",
            slot = Slot.WINGS,
            blurb = "Six bandes, aucune subtilité. C'est exactement l'idée."
        ),
        Cosmetic(
            id = "ailes_braise",
            name = "Les ailes de braise",
            slot = Slot.WINGS,
            blurb = "Rouge sombre qui vire à l'orange sur les bords. Elles ont l'air chaudes. Elles le sont."
        ),
        Cosmetic(
            id = "ailes_dechirees",
            name = "Les ailes déchirées",
            slot = Slot.WINGS,
            blurb = "Trouées, orange d'Halloween. Elle jure que c'était déjà comme ça en les achetant."
        ),

        // ---- les autres compagnons ----
        Cosmetic("rhino", "Le rhino en peluche", Slot.FRIEND,
            "Tout rond, une corne minuscule. Il a l'air de s'excuser d'être là."),
        Cosmetic("ours", "L'ours en peluche", Slot.FRIEND,
            "Le classique. Deux oreilles rondes, un museau clair, aucune ambition."),
        Cosmetic("chat", "Le chat en peluche", Slot.FRIEND,
            "Gris, la queue enroulée, l'air de juger tout le monde. Framboise l'adore."),
        // Elle a un nom, elle. C'est la seule pièce du catalogue qui en ait un, et c'est
        // ce qui la sort du rang des accessoires.
        Cosmetic("grenouille", "Bernadette", Slot.FRIEND,
            "La grenouille. Yeux sur le dessus du crâne, sourire beaucoup trop large, " +
                "aucune opinion. ${Her.dragon} lui raconte tout."),
        Cosmetic("herisson", "Le hérisson en peluche", Slot.FRIEND,
            "Piquant sur le dessus, doux sur le ventre. Il faut le prendre par en dessous."),
        Cosmetic("baleine", "La baleine en peluche", Slot.FRIEND,
            "Elle n'a ni pattes ni oreilles et s'en porte très bien. Elle souffle un petit jet."),

        // ---- tête ----
        Cosmetic("oreilles_chat", "Les oreilles de chat", Slot.HEAD,
            "Un serre-tête, deux triangles. Elle nie catégoriquement les avoir mises elle-même."),
        Cosmetic("fleurs", "La couronne de fleurs", Slot.HEAD,
            "Cinq fleurs sur un arceau de feuilles. Elle sent bon pendant deux jours."),
        Cosmetic("tuque_cotelee", "La tuque côtelée", Slot.HEAD,
            "Sans pompon, pour les jours où on ne veut pas se faire remarquer."),
        Cosmetic("paille", "Le chapeau de paille", Slot.HEAD,
            "Bord immense, ruban bleu. Il ne tient pas au vent et c'est son seul défaut."),
        Cosmetic("casquette", "La casquette à l'envers", Slot.HEAD,
            "Visière derrière. Personne ne sait pourquoi, tout le monde l'a fait."),
        Cosmetic("lunettes", "Les lunettes de soleil", Slot.HEAD,
            "Verres sombres, deux reflets. Elle les garde à l'intérieur."),
        Cosmetic("fete", "Le chapeau de fête", Slot.HEAD,
            "Rayé, pompon au sommet, élastique sous le menton. Porté un mardi ordinaire."),
        Cosmetic("bonnet_bain", "Le bonnet de bain", Slot.HEAD,
            "Avec les lunettes remontées sur le front, comme quelqu'un qui va vraiment nager."),
        Cosmetic("bois", "Les bois de renne", Slot.HEAD,
            "Trois pointes de chaque côté, plantés entre les cornes. Ça fait beaucoup."),
        Cosmetic("aureole", "L'auréole", Slot.HEAD,
            "Elle flotte de travers. Ce n'est absolument pas une preuve de sagesse."),
        Cosmetic("sorciere", "Le chapeau de sorcière", Slot.HEAD,
            "Pointu, plié, une boucle dorée. Elle prétend savoir s'en servir."),
        Cosmetic("chapeau_citrouille", "Le chapeau-citrouille", Slot.HEAD,
            "Une citrouille entière sur la tête, queue en tire-bouchon comprise."),

        // ---- corps ----
        Cosmetic("hoodie_douillet", "Le hoodie douillet", Slot.BODY,
            "Trop grand, crème, cordons de longueurs différentes. Le vêtement du dimanche."),
        Cosmetic("mariniere", "La marinière", Slot.BODY,
            "Rayures marine. Elle regarde le fleuve avec un air de capitaine."),
        Cosmetic("robe", "La robe d'été", Slot.BODY,
            "Légère, fleurie, ceinture jaune. Elle tourne sur elle-même pour la faire voler."),
        Cosmetic("salopette", "La salopette en jean", Slot.BODY,
            "Poche devant, boutons dorés. Une bretelle tombe toujours."),
        Cosmetic("impermeable", "L'imperméable jaune", Slot.BODY,
            "Capuchon, trois boutons. Elle attend qu'il pleuve pour avoir une raison."),
        Cosmetic("tablier", "Le tablier", Slot.BODY,
            "Noué dans le dos, déjà taché. Ce qu'elle cuisine reste un mystère."),
        Cosmetic("doudoune", "La doudoune", Slot.BODY,
            "Matelassée par boudins, orange vif. Elle ne peut plus baisser les bras."),
        Cosmetic("maillot", "Le maillot de bain", Slot.BODY,
            "Une pièce, framboise, une fleur sur la hanche. Elle ne se baigne jamais."),
        Cosmetic("cape", "La cape", Slot.BODY,
            "Prune, doublée, agrafe dorée. Aucun pouvoir, beaucoup d'allure."),
        Cosmetic("noel_moche", "Le chandail moche de Noël", Slot.BODY,
            "Zigzags rouges et verts. Volontairement affreux, ce qui est tout le concept."),
        Cosmetic("fantome", "Le drap de fantôme", Slot.BODY,
            "Un drap, deux trous, un ourlet en vagues. Le déguisement le plus honnête."),
        Cosmetic("hoodie_citrouille", "Le hoodie citrouille", Slot.BODY,
            "Orange, avec une face sculptée sur le ventre. Elle grogne quand on le regarde."),

        // ---- pattes ----
        Cosmetic("bas_laine", "Les bas de laine", Slot.FEET,
            "Épais, avachis sur la cheville. Le plancher est froid en février."),
        Cosmetic("espadrilles", "Les espadrilles", Slot.FEET,
            "Blanches, un œillet bleu. Propres pour l'instant."),
        Cosmetic("gougounes", "Les gougounes", Slot.FEET,
            "Une semelle, une lanière. Elle claque des pattes en marchant."),
        Cosmetic("bottes_pluie", "Les bottes de pluie", Slot.FEET,
            "Jaunes, montantes. Faites pour sauter dans les flaques, et elle le fait."),
        Cosmetic("ballerines", "Les chaussons de ballet", Slot.FEET,
            "Rose pâle, rubans croisés. Elle tient la pose environ deux secondes."),
        Cosmetic("patins", "Les patins", Slot.FEET,
            "Lames neuves, lacets serrés. Elle tient debout, c'est déjà énorme."),
        Cosmetic("bas_noel", "Les bas de Noël", Slot.FEET,
            "Rouges et blancs, rayés. Portés du premier décembre au mois de mars."),
        Cosmetic("bas_halloween", "Les bas d'Halloween", Slot.FEET,
            "Orange et noir. Assortis à absolument rien d'autre."),

        // ---- la deuxième fournée ----
        Cosmetic("noeud", "Le gros nœud", Slot.HEAD,
            "Deux fois trop grand pour sa tête, ce qui est exactement la bonne taille."),
        Cosmetic("cache_oreilles", "Les cache-oreilles", Slot.HEAD,
            "En fourrure, sur un arceau. Elle n'entend plus rien et ça lui va très bien."),
        Cosmetic("licorne", "La corne de licorne", Slot.HEAD,
            "Dorée, torsadée, tenue par un élastique. Un dragon licorne. Personne ne conteste."),
        Cosmetic("foulard", "Le foulard rayé", Slot.BODY,
            "Trois tours de cou, les deux bouts qui traînent. Tricoté par quelqu'un de patient."),
        Cosmetic("tutu", "Le tutu", Slot.BODY,
            "Rose, en trois épaisseurs. Elle a décidé qu'elle faisait du ballet aujourd'hui."),
        Cosmetic("raquettes", "Les raquettes", Slot.FEET,
            "Babiches tressées, lanières de cuir. Elle traverse le banc de neige sans caler."),
        Cosmetic("pantoufles_lapin", "Les pantoufles-lapin", Slot.FEET,
            "Deux oreilles, deux yeux, un nez rose. Elles la regardent pendant qu'elle marche."),
        Cosmetic("licorne_peluche", "La licorne en peluche", Slot.FRIEND,
            "Crinière arc-en-ciel, corne dorée, air profondément serein."),
        Cosmetic("pingouin", "Le pingouin en peluche", Slot.FRIEND,
            "Ventre blanc, deux nageoires, des pattes orange. Il tombe tout le temps vers l'avant."),
        Cosmetic("ailes_etoilees", "Les ailes étoilées", Slot.WINGS,
            "Bleu nuit percé d'étoiles. On dirait qu'elle a découpé un morceau de ciel.")
    )

    /** Combien de temps dure la cabine d'essayage. */
    const val PREVIEW_MINUTES = 5L

    /**
     * Le mot qui ouvre tout le casier pour cinq minutes.
     *
     * La comparaison enlève les accents, les apostrophes, les espaces et la casse : un mot
     * de passe qu'il faut taper au caractère près est un mot de passe qui ne marche pas,
     * et l'apostrophe de « t'aime » sort courbe sur la moitié des claviers.
     *
     * Ça ne donne rien de permanent. Les pièces se gagnent une par journée complète, et
     * c'est ce qui leur donne leur valeur — si le mot les offrait pour de bon, le casier
     * n'aurait plus aucune raison d'être ouvert le lendemain.
     */
    fun isPreviewCode(input: String): Boolean = flatten(input) == flatten("Je t'aime")

    private fun flatten(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("[^a-z]"), "")

    fun byId(id: String): Cosmetic? = ALL.firstOrNull { it.id == id }

    /** La prochaine pièce à offrir, ou null quand tout est déjà gagné. */
    fun nextLocked(owned: Set<String>): Cosmetic? = ALL.firstOrNull { it.id !in owned }
}
