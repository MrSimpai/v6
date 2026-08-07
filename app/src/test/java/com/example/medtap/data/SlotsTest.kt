package com.example.medtap.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/** Les créneaux : quel jour une dose remplit, et quand elle peut encore être notée. */
class SlotsTest {

    private var saved: TimeZone? = null

    @Before fun fixZone() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(T.ZONE)
    }

    @After fun restoreZone() {
        TimeZone.setDefault(saved)
    }

    // ---- juste après minuit -------------------------------------------------

    /**
     * Le bogue : une pilule de 21h prise à 0h30 n'était enregistrable nulle part.
     *
     * `todayAt` résolvait vers le créneau de CE soir, dans vingt heures et demie, donc la
     * fenêtre n'était pas ouverte et le bouton restait gris. La dose qu'elle avait dans la
     * main ne pouvait pas être notée, et la journée d'hier restait manquée.
     */
    @Test fun `la dose du soir se note encore apres minuit`() {
        val med = T.med(hour = 21)
        val slots = Slots.loggableSlots(med, T.at(2025, 6, 3, 0, 30))
        assertEquals(listOf(T.at(2025, 6, 2, 21, 0)), slots)
    }

    /**
     * Mais pas indéfiniment. Une pilule de 9h « prise » le lendemain à 1h du matin est une
     * journée manquée qu'on maquillerait, pas une dose du soir notée en retard.
     */
    @Test fun `une dose du matin ne se rattrape pas la nuit suivante`() {
        val med = T.med(hour = 9)
        val slots = Slots.loggableSlots(med, T.at(2025, 6, 3, 1, 0))
        assertTrue(slots.isEmpty())
    }

    /**
     * Mais pas pour un médicament qui n'existait pas hier soir.
     *
     * Le piège du tout premier jour : ajouté à 0h30 avec une pilule du soir, il tombait
     * pile dans la fenêtre de rattrapage. Le bouton proposait « Je l'ai prise (hier
     * soir) », la dose s'écrivait, et elle ne comptait pour rien — ni la série ni la
     * semaine ne regardent les journées d'avant la création. Première prise notée,
     * compteur à zéro, aucune explication à l'écran.
     */
    @Test fun `un medicament cree cette nuit ne propose pas la dose d hier`() {
        val med = T.med(hour = 21, createdAt = T.at(2025, 6, 3, 0, 30))
        assertTrue(Slots.loggableSlots(med, T.at(2025, 6, 3, 0, 35)).isEmpty())
    }

    /** Alors qu'un médicament déjà là la veille, oui : c'est bien sa dose d'hier. */
    @Test fun `un medicament plus ancien propose bien la dose d hier`() {
        val med = T.med(hour = 21, createdAt = T.at(2025, 6, 1))
        assertEquals(
            listOf(T.at(2025, 6, 2, 21, 0)),
            Slots.loggableSlots(med, T.at(2025, 6, 3, 0, 30))
        )
    }

    /** En journée, il n'y a qu'un candidat : le créneau du jour. */
    @Test fun `en journee seul le creneau du jour est candidat`() {
        val med = T.med(hour = 9)
        val slots = Slots.loggableSlots(med, T.at(2025, 6, 3, 10, 0))
        assertEquals(listOf(T.at(2025, 6, 3, 9, 0)), slots)
    }

    /** La fenêtre s'ouvre deux heures avant l'heure prévue, et pas plus tôt. */
    @Test fun `la fenetre s ouvre deux heures avant`() {
        val med = T.med(hour = 9)
        assertTrue(Slots.loggableSlots(med, T.at(2025, 6, 3, 7, 30)).isNotEmpty())
        assertTrue(Slots.loggableSlots(med, T.at(2025, 6, 3, 6, 30)).isEmpty())
    }

    /** Une dose du matin reste notable tard le soir même : la fenêtre ne se referme pas. */
    @Test fun `la fenetre du jour ne se referme pas`() {
        val med = T.med(hour = 9)
        val slots = Slots.loggableSlots(med, T.at(2025, 6, 3, 23, 0))
        assertEquals(listOf(T.at(2025, 6, 3, 9, 0)), slots)
    }

    /** Une dose déjà notée n'est plus proposée — et rien d'autre ne la remplace. */
    @Test fun `rien a noter quand la dose est deja prise`() = runBlocking {
        val med = T.med(hour = 21, createdAt = T.at(2025, 6, 1))
        val dao = TestDao(mutableListOf(med), mutableListOf(T.dose(med, 2025, 6, 2)))
        assertNull(dao.slotToLog(med, T.at(2025, 6, 3, 0, 30)))
    }

    /** Et quand elle ne l'est pas, c'est bien le créneau d'hier qui est retenu. */
    @Test fun `apres minuit c est le creneau d hier qui est retenu`() = runBlocking {
        val med = T.med(hour = 21, createdAt = T.at(2025, 6, 1))
        val dao = TestDao(mutableListOf(med))
        assertEquals(
            T.at(2025, 6, 2, 21, 0),
            dao.slotToLog(med, T.at(2025, 6, 3, 0, 30))
        )
    }

    // ---- les jours ----------------------------------------------------------

    /** `dayOf` ramène n'importe quel instant à minuit, le même jour. */
    @Test fun `dayOf ramene a minuit`() {
        assertEquals(T.at(2025, 6, 3), Slots.dayOf(T.at(2025, 6, 3, 23, 59)))
        assertEquals(T.at(2025, 6, 3), Slots.dayOf(T.at(2025, 6, 3, 0, 0)))
    }

    /**
     * `dayAfter` passe par le calendrier et non par `+ 86_400_000` : le 9 mars 2025 ne
     * fait que 23 heures, et une borne de journée une heure trop loin ferait tomber la
     * dose du lendemain dans la journée de la veille.
     */
    @Test fun `dayAfter tient compte du changement d heure`() {
        assertEquals(T.at(2025, 3, 10), Slots.dayAfter(T.at(2025, 3, 9)))
        assertEquals(T.at(2025, 11, 3), Slots.dayAfter(T.at(2025, 11, 2)))
    }

    /** `slotDaysAgo` garde l'heure murale de part et d'autre du changement d'heure. */
    @Test fun `slotDaysAgo garde l heure murale`() {
        val med = T.med(hour = 9)
        assertEquals(
            T.at(2025, 3, 8, 9, 0),
            Slots.slotDaysAgo(med, 2, T.at(2025, 3, 10, 12, 0))
        )
    }

    /**
     * Une dose enregistrée à l'ancienne heure du rappel se retrouve quand même : c'est la
     * JOURNÉE qui fait la correspondance, pas la milliseconde.
     */
    @Test fun `logForSlot trouve la dose posee a une autre heure`() = runBlocking {
        val med = T.med(hour = 9, createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            mutableListOf(
                DoseLog(
                    tagId = med.tagId,
                    scheduledFor = T.at(2025, 6, 2, 14, 30),   // l'heure d'avant
                    takenAt = T.at(2025, 6, 2, 14, 35)
                )
            )
        )
        assertTrue(dao.logForSlot(med.tagId, T.at(2025, 6, 2, 9, 0)) != null)
        assertNull(dao.logForSlot(med.tagId, T.at(2025, 6, 1, 9, 0)))
    }
}
