package com.example.medtap.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * Le gel de série : la pièce la plus délicate du compteur, et la seule qui n'avait aucun
 * test.
 *
 * Elle est délicate parce qu'elle doit se tromper dans le bon sens des deux côtés. Trop
 * généreuse, la série ne veut plus rien dire et le chiffre cesse d'être une raison de se
 * lever. Trop stricte, un seul mauvais jour efface deux cents jours d'un coup — et c'est
 * exactement la rupture nette qui fait abandonner les gens.
 *
 * Elle est aussi complètement invisible : rien ne se passe à l'écran, personne n'appuie sur
 * rien, et le seul moment où on découvrirait qu'elle est cassée serait le matin où la série
 * est tombée pour de bon.
 *
 * Juin 2025 : le 2 est un lundi.
 */
class FreezeTest {

    private var saved: TimeZone? = null

    @Before fun fixZone() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(T.ZONE)
    }

    @After fun restoreZone() {
        TimeZone.setDefault(saved)
    }

    /** Le cas pour lequel le gel existe : deux bons jours, un trou, et la série survit. */
    @Test fun `un jour manque avec une serie en cours est gele`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(T.dose(med, 2025, 6, 2), T.dose(med, 2025, 6, 3))
        )
        val jeudiMatin = T.at(2025, 6, 5, 8, 0)          // le 4 a sauté

        assertTrue(dao.useFreezeIfNeeded(jeudiMatin))
        assertNotNull(dao.freezeFor(T.at(2025, 6, 4)))
        // Et la série traverse : lundi, mardi, et le mercredi pardonné.
        assertEquals(3, dao.currentStreak(listOf(med), now = jeudiMatin))
    }

    /**
     * Geler quand la série était déjà morte gaspillerait le seul gel de la semaine pour
     * rien — et le vrai coût est là : le gel manquerait le jour suivant, celui où il
     * aurait servi.
     */
    @Test fun `sans serie a proteger aucun gel`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(T.dose(med, 2025, 6, 2))       // ni le 3, ni le 4
        )
        assertFalse(dao.useFreezeIfNeeded(T.at(2025, 6, 5, 8, 0)))
        assertNull(dao.freezeFor(T.at(2025, 6, 4)))
    }

    /** Rien à pardonner : hier était complet. */
    @Test fun `un jour deja complet n est pas gele`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            (2..4).map { T.dose(med, 2025, 6, it) }.toMutableList()
        )
        assertFalse(dao.useFreezeIfNeeded(T.at(2025, 6, 5, 8, 0)))
    }

    /** Un par semaine, pas plus, sinon la série ne veut plus rien dire. */
    @Test fun `un seul gel par semaine`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(T.dose(med, 2025, 6, 2), T.dose(med, 2025, 6, 3)),
            mutableListOf(StreakFreeze(T.at(2025, 6, 1), T.at(2025, 6, 2, 8, 0)))
        )
        assertFalse(dao.useFreezeIfNeeded(T.at(2025, 6, 5, 8, 0)))
        assertNull(dao.freezeFor(T.at(2025, 6, 4)))
    }

    /** Mais la semaine d'après, oui : le gel se recharge, il ne s'épuise pas. */
    @Test fun `un gel de plus d une semaine ne bloque plus`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(T.dose(med, 2025, 6, 10)),     // le 11 a sauté
            mutableListOf(StreakFreeze(T.at(2025, 6, 1), T.at(2025, 6, 2, 8, 0)))
        )
        assertTrue(dao.useFreezeIfNeeded(T.at(2025, 6, 12, 8, 0)))
    }

    /**
     * L'app appelle ceci à chaque retour à l'avant-plan. Deux ouvertures dans la même
     * matinée ne doivent pas poser deux gels, ni surtout consommer celui de la semaine
     * deux fois.
     */
    @Test fun `deux appels de suite ne consomment qu un gel`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(T.dose(med, 2025, 6, 2), T.dose(med, 2025, 6, 3))
        )
        val jeudiMatin = T.at(2025, 6, 5, 8, 0)
        assertTrue(dao.useFreezeIfNeeded(jeudiMatin))
        assertFalse(dao.useFreezeIfNeeded(jeudiMatin))
        assertEquals(1, dao.allFreezes().size)
    }

    /** Un médicament ajouté aujourd'hui n'a rien manqué hier : il n'existait pas. */
    @Test fun `un medicament ajoute aujourd hui ne declenche pas de gel`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 5, 8, 0))
        val dao = TestDao(mutableListOf(med))
        assertFalse(dao.useFreezeIfNeeded(T.at(2025, 6, 5, 20, 0)))
    }

    /** Sans médicament, il n'y a pas de série, donc rien à geler. */
    @Test fun `aucun medicament aucun gel`() = runBlocking {
        val dao = TestDao()
        assertFalse(dao.useFreezeIfNeeded(T.at(2025, 6, 5, 8, 0)))
    }

    /**
     * Le jour gelé se voit dans la semaine, en bleu et non en rose : c'est la seule preuve
     * visible que le gel a servi, et le seul état que rien ne vérifiait jusqu'ici.
     */
    @Test fun `le jour gele se voit dans la semaine`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(T.dose(med, 2025, 6, 2), T.dose(med, 2025, 6, 4)),
            mutableListOf(StreakFreeze(T.at(2025, 6, 3), T.at(2025, 6, 4, 8, 0)))
        )
        val semaine = dao.weekStatus(listOf(med), now = T.at(2025, 6, 5, 12, 0))
        assertEquals(DayState.DONE, semaine[0])         // lundi 2
        assertEquals(DayState.FROZEN, semaine[1])       // mardi 3, pardonné
        assertEquals(DayState.DONE, semaine[2])         // mercredi 4
        assertEquals(DayState.TODAY, semaine[3])        // jeudi 5
    }
}
