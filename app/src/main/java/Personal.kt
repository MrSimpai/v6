package com.example.medtap

import java.util.Calendar

/**
 * Tout ce qui appartient à Flo est ici, et nulle part ailleurs.
 *
 * C'est volontaire : l'app n'a pas d'écran de réglages, pas de compte, pas de champ
 * « votre prénom ». Elle a été écrite pour une personne. Si un jour il faut la donner
 * à quelqu'un d'autre, ces quelques lignes suffisent — le reste du code ne connaît
 * personne d'autre que ce fichier.
 */
object Her {

    /** Le nom qu'elle utilise partout. */
    const val name = "Flo"

    /** Son vrai prénom. Le dragon ne s'en sert que quand ça devient sérieux. */
    const val realName = "Florie"

    /** Le dragon. Framboise, pour la couleur. */
    const val dragon = "Framboise"

    // Il y avait ici un tirage de petits noms — Floflo, Flomingo, Flozilla, Floflosky —
    // que plus personne n'appelait. Les surnoms n'ont pas disparu pour autant : ils sont
    // écrits à la main dans les lignes de `FloMessages`, au cas par cas, ce qui les place
    // toujours mieux qu'un tirage au sort ne le ferait.

    /** « Bonjour, Flo », « Bonsoir, Flo »… selon l'heure qu'il est chez elle. */
    fun greeting(now: Long = System.currentTimeMillis()): String {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val word = when (h) {
            in 5..11  -> "Bonjour"
            in 12..17 -> "Bon après-midi"
            in 18..22 -> "Bonsoir"
            else      -> "Bonne nuit"
        }
        return "$word, $name"
    }

    /** La ligne discrète en bas de l'écran d'accueil. */
    const val dedication = "Fait à la main pour toi, et pour personne d'autre."
}
