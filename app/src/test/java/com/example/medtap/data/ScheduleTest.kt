package com.example.medtap.data

import com.example.medtap.reminder.Tier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * Les horaires qui changent selon le jour, et la plage horaire.
 *
 * Les deux existent pour la même raison : elle ne se lève pas à la même heure tous les
 * jours. Un dragon qui escalade à 7h10 un jeudi où elle dort jusqu'à 10h a tort trois fois
 * par semaine, et un rappel qui a régulièrement tort est un rappel qu'on coupe.
 *
 * Les dates sont toutes en juin 2025, où le 2 est un lundi et le 5 un jeudi. Les tests
 * disent le jour de semaine dans leur nom quand il compte, parce que « le 5 juin » ne se
 * vérifie pas de tête et que c'est justement ce qui est testé.
 */
class ScheduleTest {

    private var saved: TimeZone? = null

    /** Lundi à mercredi 7h, jeudi 10h (grasse matinée), vendredi 7h, fin de semaine 9h. */
    private val varie = listOf(7, 7, 7, 10, 7, 9, 9)

    @Before fun fixZone() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(T.ZONE)
    }

    @After fun restoreZone() {
        TimeZone.setDefault(saved)
    }

    // ---- l'heure du jour ----------------------------------------------------

    /** Chaque jour de la semaine a son heure, et c'est la sienne qui s'applique. */
    @Test fun `le creneau suit le jour de la semaine`() {
        val med = T.weekly(hours = varie)
        assertEquals(T.at(2025, 6, 2, 7, 0), Slots.todayAt(med, T.at(2025, 6, 2, 12, 0)))
        assertEquals(T.at(2025, 6, 5, 10, 0), Slots.todayAt(med, T.at(2025, 6, 5, 12, 0)))
        assertEquals(T.at(2025, 6, 7, 9, 0), Slots.todayAt(med, T.at(2025, 6, 7, 12, 0)))
        assertEquals(T.at(2025, 6, 8, 9, 0), Slots.todayAt(med, T.at(2025, 6, 8, 12, 0)))
    }

    /**
     * Le piège de toute la fonctionnalité : `slotDaysAgo` doit reculer d'abord et poser
     * l'heure ensuite.
     *
     * La version évidente — poser l'heure d'aujourd'hui puis reculer de trois jours —
     * cherche la dose du samedi à l'heure du mardi. Aucune ne se retrouve, et une série de
     * n'importe quelle longueur revient à un le jour où on règle un horaire par jour.
     */
    @Test fun `slotDaysAgo prend l heure du jour vise`() {
        val med = T.weekly(hours = varie)
        val vendrediMidi = T.at(2025, 6, 6, 12, 0)
        assertEquals(T.at(2025, 6, 5, 10, 0), Slots.slotDaysAgo(med, 1, vendrediMidi))
        assertEquals(T.at(2025, 6, 2, 7, 0), Slots.slotDaysAgo(med, 4, vendrediMidi))
    }

    /**
     * `nextAfter` avance jour par jour. « Demain à la même heure » n'existe plus : le
     * mercredi soir, la prochaine dose est le jeudi à 10h et pas le jeudi à 7h.
     */
    @Test fun `nextAfter trouve l heure du lendemain`() {
        val med = T.weekly(hours = varie)
        assertEquals(
            T.at(2025, 6, 5, 10, 0),
            Slots.nextAfter(med, T.at(2025, 6, 4, 20, 0))       // mercredi soir
        )
        assertEquals(
            T.at(2025, 6, 5, 10, 0),
            Slots.nextAfter(med, T.at(2025, 6, 5, 8, 0))        // jeudi matin, avant l'heure
        )
        // À la seconde près, le créneau du jour ne compte plus : c'est le suivant.
        assertEquals(
            T.at(2025, 6, 6, 7, 0),
            Slots.nextAfter(med, T.at(2025, 6, 5, 10, 0))
        )
    }

    /** La dose du dimanche soir reste notable le lundi matin, même si le lundi commence à 7h. */
    @Test fun `la dose du soir se note encore apres minuit avec un horaire variable`() {
        val med = T.weekly(hours = listOf(7, 7, 7, 7, 7, 7, 21))     // dimanche 21h
        val lundi0h30 = T.at(2025, 6, 9, 0, 30)
        assertEquals(
            listOf(T.at(2025, 6, 8, 21, 0)),
            Slots.loggableSlots(med, lundi0h30)
        )
    }

    // ---- la série tient ------------------------------------------------------

    /** Cinq jours d'affilée à cinq heures différentes, c'est cinq jours. */
    @Test fun `la serie tient avec des heures differentes selon les jours`() = runBlocking {
        val med = T.weekly(hours = varie, createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            (2..6).map { T.dose(med, 2025, 6, it) }.toMutableList()
        )
        assertEquals(5, dao.perfectDayStreak(listOf(med), now = T.at(2025, 6, 6, 20, 0)))
    }

    /**
     * Passer d'une heure unique à un horaire par jour ne doit rien effacer, exactement
     * comme déplacer un rappel de cinq minutes : c'est la JOURNÉE qui relie une dose à son
     * créneau, jamais la milliseconde.
     */
    @Test fun `passer a un horaire par jour garde l historique`() = runBlocking {
        val avant = T.med(hour = 9, createdAt = T.at(2025, 6, 1))
        val logs = (2..4).map { T.dose(avant, 2025, 6, it) }.toMutableList()
        val apres = avant.withWindows(
            varie.map { h -> DayWindow(h * 60, h * 60) }
        )
        val dao = TestDao(mutableListOf(apres), logs)
        assertEquals(3, dao.perfectDayStreak(listOf(apres), now = T.at(2025, 6, 4, 20, 0)))
    }

    /** La semaine affichée suit les mêmes heures, jour par jour. */
    @Test fun `la semaine se lit avec des heures variables`() = runBlocking {
        val med = T.weekly(hours = varie, createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            (2..4).map { T.dose(med, 2025, 6, it) }.toMutableList()
        )
        val semaine = dao.weekStatus(listOf(med), now = T.at(2025, 6, 5, 12, 0))
        assertEquals(DayState.DONE, semaine[0])         // lundi 2, 7h
        assertEquals(DayState.DONE, semaine[1])         // mardi 3, 7h
        assertEquals(DayState.DONE, semaine[2])         // mercredi 4, 7h
        assertEquals(DayState.TODAY, semaine[3])        // jeudi 5, 10h
        assertEquals(DayState.FUTURE, semaine[4])
    }

    // ---- la plage horaire ----------------------------------------------------

    /**
     * Le cœur de la chose : entre 7h et 10h, il n'y a AUCUN retard.
     *
     * Le temps écoulé, lui, avance bel et bien — deux heures cinquante-neuf à 9h59 — et
     * c'est précisément ce que l'ancienne version montrait au dragon. Le palier ne doit
     * plus le voir.
     */
    @Test fun `aucun retard tant que la plage court`() {
        val med = T.weekly(hours = List(7) { 7 }, until = List(7) { 10 })
        val creneau = T.slot(med, 2025, 6, 2)

        assertEquals(T.at(2025, 6, 2, 10, 0), Slots.windowEnd(med, creneau))
        assertEquals(0L, Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 7, 1)))
        assertEquals(0L, Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 9, 59)))
        assertEquals(0L, Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 10, 0)))

        // Sans la plage, 9h59 valait 179 minutes de retard, c'est-à-dire l'avant-dernier
        // palier — le dragon en larmes, à quelqu'un qui dort.
        assertEquals(
            Tier.PONCTUEL,
            Tier.forLateness(Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 9, 59)))
        )
    }

    /** Et une fois la plage passée, l'escalade repart de zéro à partir de la fin. */
    @Test fun `l escalade compte a partir de la fin de la plage`() {
        val med = T.weekly(hours = List(7) { 7 }, until = List(7) { 10 })
        val creneau = T.slot(med, 2025, 6, 2)

        assertEquals(90L, Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 11, 30)))
        assertEquals(
            Tier.DRAME,
            Tier.forLateness(Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 11, 30)))
        )
        // Deux heures après la FIN de la plage, soit cinq heures après le rappel.
        assertEquals(
            Tier.SERIEUX,
            Tier.forLateness(Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 12, 1)))
        )
    }

    /** Chaque jour a sa plage : 7h→10h la semaine, 9h→12h le dimanche. */
    @Test fun `la plage aussi change selon le jour`() {
        val med = T.weekly(
            hours = listOf(7, 7, 7, 7, 7, 9, 9),
            until = listOf(10, 10, 10, 10, 10, 12, 12)
        )
        assertEquals(
            T.at(2025, 6, 2, 10, 0),
            Slots.windowEnd(med, T.slot(med, 2025, 6, 2))       // lundi
        )
        assertEquals(
            T.at(2025, 6, 8, 12, 0),
            Slots.windowEnd(med, T.slot(med, 2025, 6, 8))       // dimanche
        )
    }

    /**
     * Un médicament d'avant la fonctionnalité escalade à la minute près, comme toujours.
     * C'est la seule chose que la mise à jour n'a pas le droit de changer.
     */
    @Test fun `sans plage rien ne change`() {
        val med = T.med(hour = 9)
        val creneau = Slots.todayAt(med, T.at(2025, 6, 2, 12, 0))
        assertEquals(creneau, Slots.windowEnd(med, creneau))
        assertEquals(60L, Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 10, 0)))
        assertEquals(
            Tier.DRAME,
            Tier.forLateness(Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 10, 0)))
        )
    }

    // ---- la chaîne d'horaire -------------------------------------------------

    /** Aller-retour : ce qu'on écrit est ce qu'on relit, et l'heure de tri est la plus matinale. */
    @Test fun `les sept plages font l aller retour`() {
        val week = varie.map { DayWindow(it * 60, it * 60 + 120) }
        val med = T.med(hour = 9).withWindows(week)
        assertEquals(week, med.windows())
        assertEquals(7, med.hourOfDay)          // la plus matinale des sept
        assertEquals(0, med.minute)
        assertFalse(med.uniformWeek)
    }

    /** Sept jours identiques se reconnaissent comme tels : c'est ce qui décide de l'affichage. */
    @Test fun `sept jours identiques sont uniformes`() {
        val med = T.med(hour = 9).withWindows(List(7) { DayWindow(420, 600) })
        assertTrue(med.uniformWeek)
        assertEquals(7, med.hourOfDay)
    }

    /**
     * Une chaîne illisible retombe sur l'ancienne heure plutôt que de tomber tout court.
     *
     * Une base à moitié écrite ou un fichier de sauvegarde bricolé à la main ne doit pas
     * faire disparaître les rappels : le pire mode de panne de cette app est le silence.
     */
    @Test fun `un horaire illisible retombe sur l ancienne heure`() {
        val neuf = DayWindow(540, 540)
        listOf(
            "patate",                       // rien à voir
            "420-600,420-600",              // pas sept jours
            "420600,,,,,,",                 // pas de tiret
            "1500-600,,,,,,",               // hors de la journée
            "-600,,,,,,"                    // début vide
        ).forEach { bidon ->
            val med = T.med(hour = 9, schedule = bidon)
            assertEquals("horaire « $bidon »", List(7) { neuf }, med.windows())
            assertEquals(
                "horaire « $bidon »",
                T.at(2025, 6, 2, 9, 0),
                Slots.todayAt(med, T.at(2025, 6, 2, 12, 0))
            )
        }
    }

    /** Une fin avant le début n'est pas une plage qui déborde sur la veille : c'est pas de plage. */
    @Test fun `une fin avant le debut ne fait pas de plage`() {
        val med = T.med(hour = 9).withWindows(List(7) { DayWindow(600, 420) })
        val creneau = Slots.todayAt(med, T.at(2025, 6, 2, 12, 0))
        assertEquals(T.at(2025, 6, 2, 10, 0), creneau)
        assertEquals(creneau, Slots.windowEnd(med, creneau))
        assertEquals(60L, Slots.pressureMinutes(med, creneau, T.at(2025, 6, 2, 11, 0)))
    }

    /** Lundi est le jour zéro, quoi qu'en pense `Calendar`. */
    @Test fun `l index de la semaine commence lundi`() {
        assertEquals(0, Week.index(java.util.Calendar.MONDAY))
        assertEquals(3, Week.index(java.util.Calendar.THURSDAY))
        assertEquals(5, Week.index(java.util.Calendar.SATURDAY))
        assertEquals(6, Week.index(java.util.Calendar.SUNDAY))
    }
}
