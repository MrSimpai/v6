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
            "Bleu nuit percé d'étoiles. On dirait qu'elle a découpé un morceau de ciel."),
        // ---- la troisième fournée : cinquante de plus, dix par emplacement ----
        Cosmetic("lapin", "Le lapin", Slot.FRIEND,
            "Deux grandes oreilles, dont une qui ne tient jamais debout. Il écoute tout."),
        Cosmetic("beret", "Le béret", Slot.HEAD,
            "Rose, posé de côté, avec la petite queue sur le dessus. Très artiste. Très parisienne."),
        Cosmetic("tshirt_coeur", "Le t-shirt cœur", Slot.BODY,
            "Blanc, avec un gros cœur dessus. Elle dit que c'est pour toi. C'est pour toi."),
        Cosmetic("chaussettes_coeur", "Les bas à cœurs", Slot.FEET,
            "Roses, semés de petits cœurs. Parfaits pour glisser sur le plancher de la cuisine."),
        Cosmetic("ailes_coeur", "Les ailes en cœur", Slot.WINGS,
            "Deux moitiés de cœur. Quand elle les replie, ça fait un cœur entier."),
        Cosmetic("renard", "Le renard", Slot.FRIEND,
            "Orange, le bout de la queue blanc, l'air de savoir quelque chose que tu ne sais pas."),
        Cosmetic("fraise", "Le chapeau fraise", Slot.HEAD,
            "Une fraise géante, avec ses pépins et sa feuille. Elle sent un peu le bonbon."),
        Cosmetic("chandail_arcenciel", "Le chandail arc-en-ciel", Slot.BODY,
            "Six couleurs, une par rayure. Tricoté avec tous les restes de laine de la maison."),
        Cosmetic("pantoufles_dragon", "Les pantoufles dragon", Slot.FEET,
            "Deux mini-Framboise aux pieds, avec des cornes molles. Elles la regardent marcher."),
        Cosmetic("ailes_nuage", "Les ailes nuages", Slot.WINGS,
            "Douces comme de la ouate. Elle flotte un peu plus haut les jours de beau temps."),
        Cosmetic("panda", "Le panda", Slot.FRIEND,
            "Noir et blanc, les yeux cernés comme quelqu'un qui a trop regardé de séries."),
        Cosmetic("diademe", "Le diadème", Slot.HEAD,
            "Une fine bande dorée, une perle au milieu. Princesse, mais de semaine."),
        Cosmetic("kimono", "Le kimono fleuri", Slot.BODY,
            "Rose tendre, ceinture large, fleurs partout. Elle fait des révérences à tout le monde."),
        Cosmetic("bottes_neige", "Les bottes de neige", Slot.FEET,
            "Doublées de fourrure, prêtes pour février. Elle saute dans tous les bancs de neige."),
        Cosmetic("ailes_bonbon", "Les ailes bonbon", Slot.WINGS,
            "Rayées rose et blanc comme une canne de Noël. Ne pas lécher."),
        Cosmetic("poussin", "Le poussin", Slot.FRIEND,
            "Jaune, rond, une seule plume sur la tête. Il pépie quand on le serre."),
        Cosmetic("haut_de_forme", "Le haut-de-forme", Slot.HEAD,
            "Noir, très haut, ruban rose. Elle soulève son chapeau pour saluer les pigeons."),
        Cosmetic("veste_jean", "La veste en jean", Slot.BODY,
            "Délavée juste comme il faut, avec deux boutons dorés. Un classique."),
        Cosmetic("souliers_vernis", "Les souliers vernis", Slot.FEET,
            "Rouges, brillants, une petite boucle. Ils font clac-clac sur le plancher."),
        Cosmetic("ailes_morpho", "Les ailes de morpho", Slot.WINGS,
            "Bleu électrique, bordées de noir. Elles changent de couleur selon la lumière."),
        Cosmetic("souris", "La souris", Slot.FRIEND,
            "Grise, oreilles immenses, queue en tire-bouchon. Elle garde un morceau de fromage caché."),
        Cosmetic("antennes", "Les antennes d'abeille", Slot.HEAD,
            "Un serre-tête, deux ressorts, deux pompons qui rebondissent à chaque pas."),
        Cosmetic("robe_bal", "La robe de bal", Slot.BODY,
            "Violette, pleine de brillants, qui tourne quand elle tourne. Et elle tourne beaucoup."),
        Cosmetic("palmes", "Les palmes", Slot.FEET,
            "Bleues, beaucoup trop grandes. Pour la piscine, et pour faire rire."),
        Cosmetic("ailes_dorees", "Les ailes dorées", Slot.WINGS,
            "En or, qui brillent. Pour les jours où elle est particulièrement fière d'elle."),
        Cosmetic("mouton", "Le mouton", Slot.FRIEND,
            "Une boule de laine avec une petite face noire. Il compte les dragons pour s'endormir."),
        Cosmetic("lunettes_coeur", "Les lunettes cœur", Slot.HEAD,
            "Des verres en forme de cœur. Elle voit tout en rose, et c'est voulu."),
        Cosmetic("smoking", "Le petit smoking", Slot.BODY,
            "Noir, chemise blanche, nœud papillon rose. Pour les grandes occasions, comme mardi."),
        Cosmetic("patins_roulettes", "Les patins à roulettes", Slot.FEET,
            "Blancs, roues roses. Elle tourne en rond dans le salon en chantant."),
        Cosmetic("ailes_aurore", "Les ailes aurore", Slot.WINGS,
            "Vert, turquoise et violet, comme le ciel du Nord par une nuit froide."),
        Cosmetic("pieuvre", "La pieuvre", Slot.FRIEND,
            "Violette, huit bras, et elle veut faire un câlin avec chacun."),
        Cosmetic("bandana", "Le bandana de pirate", Slot.HEAD,
            "Rouge à pois blancs, noué sur le côté. À l'abordage de la boîte à biscuits."),
        Cosmetic("chandail_fraise", "Le chandail fraise", Slot.BODY,
            "Rouge avec des pépins jaunes et un col vert. Elle est une fraise, maintenant."),
        Cosmetic("bottes_cowboy", "Les bottes de cowboy", Slot.FEET,
            "Brunes, surpiquées, avec un petit talon. Yee-haw, mais doucement."),
        Cosmetic("ailes_vitrail", "Les ailes vitrail", Slot.WINGS,
            "Des carreaux de couleur sertis de noir. Le soleil passe au travers."),
        Cosmetic("dino", "Le petit dino", Slot.FRIEND,
            "Vert, des piquants mous sur le dos. Il se prend pour un grand dragon."),
        Cosmetic("toque_chef", "La toque de chef", Slot.HEAD,
            "Blanche, gonflée comme un soufflé. Elle prépare des crêpes imaginaires."),
        Cosmetic("sarrau", "Le sarrau de labo", Slot.BODY,
            "Blanc, deux crayons dans la poche. Docteure Framboise vérifie que tu as pris ta pilule."),
        Cosmetic("sabots", "Les sabots troués", Slot.FEET,
            "Verts, pleins de petits trous, une sangle derrière. Laids et parfaits."),
        Cosmetic("ailes_sirene", "Les ailes de sirène", Slot.WINGS,
            "Turquoise, couvertes d'écailles qui brillent. Elle nage dans les airs."),
        Cosmetic("abeille", "L'abeille", Slot.FRIEND,
            "Rayée jaune et noir, deux petites ailes. Elle ne pique pas, elle fait bzz."),
        Cosmetic("cowboy", "Le chapeau de cowboy", Slot.HEAD,
            "Brun, bord relevé, cordon sous le menton. Pour galoper jusqu'à la pharmacie."),
        Cosmetic("gilet_sauvetage", "La veste de sauvetage", Slot.BODY,
            "Orange vif, deux sangles. Elle ne sait pas nager. Elle vole, de toute façon."),
        Cosmetic("bas_arcenciel", "Les bas arc-en-ciel", Slot.FEET,
            "Une rayure de chaque couleur, du rouge au violet. Jamais pareils des deux côtés."),
        Cosmetic("ailes_automne", "Les ailes d'automne", Slot.WINGS,
            "Rouge érable, orange et jaune, comme une forêt en octobre."),
        Cosmetic("raton", "Le raton laveur", Slot.FRIEND,
            "Masque noir, queue rayée. Il fouille dans tes poches pour voir s'il y a des noix."),
        Cosmetic("bandeau_sport", "Le bandeau de sport", Slot.HEAD,
            "Blanc avec une rayure rose. Pour l'aérobie du dimanche matin."),
        Cosmetic("habit_neige", "L'habit de neige", Slot.BODY,
            "Turquoise, matelassé, une bande réfléchissante. Elle ne sent plus ses bras, mais elle n'a pas froid."),
        Cosmetic("bottes_dorees", "Les bottes dorées", Slot.FEET,
            "Tout en or, avec une étincelle. Pour marcher comme une vedette."),
        Cosmetic("ailes_abeille", "Les ailes d'abeille", Slot.WINGS,
            "Petites, transparentes, elles bourdonnent. Bien trop petites, comme toujours.")
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
