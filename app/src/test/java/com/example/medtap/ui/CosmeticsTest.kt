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

    /**
     * Aucune pièce muette.
     *
     * Le nom sort dans le coffre en trente points et le blurb juste en dessous : une
     * chaîne vide ne planterait rien, elle donnerait un écran de récompense à moitié
     * blanc le jour où la pièce tombe — c'est-à-dire une fois, et sans rattrapage.
     */
    @Test fun `chaque piece a un nom et une phrase`() {
        Cosmetics.ALL.forEach {
            assertTrue(it.id, it.name.isNotBlank())
            assertTrue(it.id, it.blurb.isNotBlank())
            assertEquals(it.id, it, Cosmetics.byId(it.id))
        }
    }

    /**
     * L'identifiant sert de clé dans la base ET de branche dans le `when` du dessin. Une
     * majuscule ou un espace passerait l'un et raterait l'autre en silence : la pièce
     * serait gagnée, rangée dans le casier, et le dragon apparaîtrait tout nu.
     */
    @Test fun `les identifiants tiennent dans la convention`() {
        Cosmetics.ALL.forEach {
            assertTrue(it.id, it.id.matches(Regex("[a-z][a-z0-9_]*")))
        }
    }

    /** Chaque emplacement a de quoi être porté : un casier avec un onglet vide est un bogue. */
    @Test fun `aucun emplacement n est vide`() {
        Slot.entries.forEach { slot ->
            assertTrue(slot.name, Cosmetics.ALL.any { it.slot == slot })
        }
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
