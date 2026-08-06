package com.example.medtap.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * Le compteur de jours, qui est la seule chose que cette app ait vraiment à faire
 * correctement.
 *
 * Chaque test ici correspond à un bogue qui est réellement arrivé, ou à un risque écrit
 * noir sur blanc dans un commentaire du code sans avoir jamais été exécuté une fois. Ce
 * ne sont pas des tests de couverture.
 */
class StreakTest {

    private var saved: TimeZone? = null

    @Before fun fixZone() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(T.ZONE)
    }

    @After fun restoreZone() {
        TimeZone.setDefault(saved)
    }

    // ---- la série -----------------------------------------------------------

    /**
     * Le bogue signalé : au deuxième jour, l'app affichait encore « jour 1 ».
     *
     * Deux nombres différents et tous les deux justes : avant la dose du jour, la série
     * est celle d'hier ; une fois notée, elle inclut aujourd'hui.
     */
    @Test fun `deuxieme jour compte deux`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(T.dose(med, 2025, 6, 1), T.dose(med, 2025, 6, 2))
        )
        val day2Evening = T.at(2025, 6, 2, 20, 0)
        assertEquals(2, dao.perfectDayStreak(listOf(med), now = day2Evening))
        assertEquals(2, dao.currentStreak(listOf(med), now = day2Evening))
    }

    /** Avant la dose du jour, le compteur montre celle d'hier plutôt que zéro. */
    @Test fun `avant la dose du jour la serie est celle d hier`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(mutableListOf(med), mutableListOf(T.dose(med, 2025, 6, 1)))
        assertEquals(1, dao.currentStreak(listOf(med), now = T.at(2025, 6, 2, 7, 0)))
    }

    /**
     * Changer l'heure d'un rappel effaçait tout l'historique du médicament : les doses
     * étaient enregistrées à l'ancienne heure et cherchées à la nouvelle.
     */
    @Test fun `changer l heure du rappel garde l historique`() = runBlocking {
        val before = T.med(hour = 9, createdAt = T.at(2025, 6, 1))
        val logs = mutableListOf(
            T.dose(before, 2025, 6, 1), T.dose(before, 2025, 6, 2), T.dose(before, 2025, 6, 3)
        )
        // Même clé, même historique, nouvelle heure.
        val after = before.copy(hourOfDay = 14, minute = 30)
        val dao = TestDao(mutableListOf(after), logs)
        assertEquals(3, dao.perfectDayStreak(listOf(after), now = T.at(2025, 6, 3, 20, 0)))
    }

    /**
     * Ajouter un deuxième médicament remettait la série à un : le nouveau n'avait aucune
     * dose enregistrée les jours d'avant, donc ces jours cessaient d'être complets.
     */
    @Test fun `ajouter un medicament ne casse pas la serie`() = runBlocking {
        val old = T.med("A", createdAt = T.at(2025, 6, 1))
        val new = T.med("B", hour = 20, createdAt = T.at(2025, 6, 4, 10, 0))
        val logs = mutableListOf(
            T.dose(old, 2025, 6, 1), T.dose(old, 2025, 6, 2),
            T.dose(old, 2025, 6, 3), T.dose(old, 2025, 6, 4),
            T.dose(new, 2025, 6, 4)          // le nouveau n'existe que depuis aujourd'hui
        )
        val dao = TestDao(mutableListOf(old, new), logs)
        assertEquals(4, dao.perfectDayStreak(listOf(old, new), now = T.at(2025, 6, 4, 22, 0)))
    }

    /**
     * Le garde-fou de la boucle : sans lui, les jours d'avant l'installation sont
     * vacuement « complets » et la série remonte jusqu'à la borne de dix ans.
     */
    @Test fun `la serie s arrete avant le premier medicament`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 10))
        val dao = TestDao(mutableListOf(med), mutableListOf(T.dose(med, 2025, 6, 10)))
        assertEquals(1, dao.perfectDayStreak(listOf(med), now = T.at(2025, 6, 10, 20, 0)))
    }

    /** Un trou coupe la série, et seul ce qui suit compte. */
    @Test fun `un jour manque coupe la serie`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(
                T.dose(med, 2025, 6, 1),
                // 2 juin manquant
                T.dose(med, 2025, 6, 3), T.dose(med, 2025, 6, 4)
            )
        )
        assertEquals(2, dao.perfectDayStreak(listOf(med), now = T.at(2025, 6, 4, 20, 0)))
    }

    /** Un gel posé sur le jour manqué laisse la série le traverser. */
    @Test fun `un gel sauve le jour manque`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(
                T.dose(med, 2025, 6, 1), T.dose(med, 2025, 6, 3), T.dose(med, 2025, 6, 4)
            ),
            mutableListOf(StreakFreeze(T.at(2025, 6, 2), T.at(2025, 6, 3)))
        )
        assertEquals(4, dao.perfectDayStreak(listOf(med), now = T.at(2025, 6, 4, 20, 0)))
    }

    /**
     * Le risque écrit dans `Slots.slotDaysAgo` et jamais exécuté : au printemps la
     * journée fait 23 heures. En soustrayant 86 400 000 ms on tombe une heure à côté du
     * créneau, on ne trouve aucune dose, et une série de n'importe quelle longueur revient
     * à un — deux fois par an, sans que rien ne le signale.
     *
     * Au Québec, l'heure avance le 9 mars 2025.
     */
    @Test fun `la serie survit au changement d heure du printemps`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 3, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(
                T.dose(med, 2025, 3, 7), T.dose(med, 2025, 3, 8),
                T.dose(med, 2025, 3, 9),          // le jour de 23 heures
                T.dose(med, 2025, 3, 10)
            )
        )
        assertEquals(4, dao.perfectDayStreak(listOf(med), now = T.at(2025, 3, 10, 20, 0)))
    }

    /** Et à l'automne, où la journée en fait 25. L'heure recule le 2 novembre 2025. */
    @Test fun `la serie survit au changement d heure de l automne`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 10, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(
                T.dose(med, 2025, 10, 31), T.dose(med, 2025, 11, 1),
                T.dose(med, 2025, 11, 2),         // le jour de 25 heures
                T.dose(med, 2025, 11, 3)
            )
        )
        assertEquals(4, dao.perfectDayStreak(listOf(med), now = T.at(2025, 11, 3, 20, 0)))
    }

    // ---- la semaine ---------------------------------------------------------

    /**
     * Les jours d'avant le premier médicament ne sont pas des jours manqués : rien n'était
     * attendu d'eux. Avant le correctif, ajouter un médicament vidait la semaine entière.
     */
    @Test fun `les jours d avant la creation ne sont pas manques`() = runBlocking {
        // Jeudi 5 juin 2025. Le médicament est créé ce jour-là.
        val med = T.med(createdAt = T.at(2025, 6, 5, 8, 0))
        val dao = TestDao(mutableListOf(med), mutableListOf(T.dose(med, 2025, 6, 5)))
        val week = dao.weekStatus(listOf(med), now = T.at(2025, 6, 5, 20, 0))

        // Lundi à mercredi : avant sa création, donc rien à dire.
        assertEquals(DayState.FUTURE, week[0])
        assertEquals(DayState.FUTURE, week[1])
        assertEquals(DayState.FUTURE, week[2])
        assertEquals(DayState.DONE, week[3])        // jeudi, noté
        assertEquals(DayState.FUTURE, week[4])      // vendredi, pas encore arrivé
    }

    /** Lundi reste à gauche quel que soit le jour où on regarde. */
    @Test fun `la semaine commence lundi`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(T.dose(med, 2025, 6, 2), T.dose(med, 2025, 6, 4))
        )
        // Jeudi 5 juin.
        val week = dao.weekStatus(listOf(med), now = T.at(2025, 6, 5, 12, 0))
        assertEquals(DayState.DONE, week[0])        // lundi 2, noté
        assertEquals(DayState.MISSED, week[1])      // mardi 3, manqué
        assertEquals(DayState.DONE, week[2])        // mercredi 4, noté
        assertEquals(DayState.TODAY, week[3])       // jeudi 5
        assertEquals(DayState.FUTURE, week[6])      // dimanche
    }
}
