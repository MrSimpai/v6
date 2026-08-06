package com.example.medtap.ui

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
enum class Slot(val label: String) {
    HEAD("Tête"), BODY("Corps"), FEET("Pattes")
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
        )
    )

    fun byId(id: String): Cosmetic? = ALL.firstOrNull { it.id == id }

    /** La prochaine pièce à offrir, ou null quand tout est déjà gagné. */
    fun nextLocked(owned: Set<String>): Cosmetic? = ALL.firstOrNull { it.id !in owned }
}
