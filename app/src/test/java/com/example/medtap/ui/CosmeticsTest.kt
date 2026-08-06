package com.example.medtap.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticsTest {

    /**
     * Le mot doit passer quel que soit le clavier. L'apostrophe sort courbe sur iOS et
     * sur Gboard, droite ailleurs, et personne ne pense à la casse : exiger la frappe
     * exacte, c'est écrire un mot de passe qui ne marche pas le jour où on en a envie.
     */
    @Test fun `le mot passe quelle que soit la frappe`() {
        listOf(
            "Je t'aime",
            "je t'aime",
            "JE T'AIME",
            "Je t’aime",          // apostrophe courbe
            "je taime",
            "  Je   t'aime  ",
            "Je t'aime !"
        ).forEach { assertTrue(it, Cosmetics.isPreviewCode(it)) }
    }

    @Test fun `un autre mot ne passe pas`() {
        listOf("", "je", "aime", "je t'aimes", "moi aussi", "jetaimee")
            .forEach { assertFalse(it, Cosmetics.isPreviewCode(it)) }
    }

    /** Chaque pièce du catalogue a une clé unique : c'est elle qui relie la ligne au dessin. */
    @Test fun `les identifiants sont uniques`() {
        val ids = Cosmetics.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    /** `nextLocked` suit l'ordre de la liste, et rend null une fois tout gagné. */
    @Test fun `la prochaine piece suit l ordre du catalogue`() {
        assertEquals(Cosmetics.ALL[0].id, Cosmetics.nextLocked(emptySet())?.id)
        assertEquals(
            Cosmetics.ALL[1].id,
            Cosmetics.nextLocked(setOf(Cosmetics.ALL[0].id))?.id
        )
        assertEquals(null, Cosmetics.nextLocked(Cosmetics.ALL.map { it.id }.toSet()))
    }
}
