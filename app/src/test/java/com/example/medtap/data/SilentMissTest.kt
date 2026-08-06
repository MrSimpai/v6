package com.example.medtap.data

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * La détection de panne silencieuse.
 *
 * Le mode d'échec qui compte pour une app de médication est celui où rien n'arrive et où
 * personne ne s'en aperçoit. Ces tests décrivent la seule situation qui mérite une alerte
 * — prévenue nulle part, dose nulle part — et surtout toutes celles qui n'en méritent
 * pas : une fausse alerte sur ce sujet-là et on n'écoute plus jamais les vraies.
 *
 * `now` est mardi midi, et la fenêtre remonte de sept jours : les journées examinées sont
 * donc le 3 au 9 juin. Les jeux de données couvrent exactement cette plage, sinon un
 * trou en bordure ferait échouer les tests pour une raison qui n'a rien à voir.
 */
class SilentMissTest {

    private var saved: TimeZone? = null
    private val now = T.at(2025, 6, 10, 12, 0)          // mardi midi
    private val week = 3..9                             // les journées que la fenêtre couvre

    @Before fun fixZone() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(T.ZONE)
    }

    @After fun restoreZone() {
        TimeZone.setDefault(saved)
    }

    /** Prévenue et prise, tous les jours : rien à signaler. */
    @Test fun `une semaine normale ne signale rien`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 5, 1))
        val dao = TestDao(
            mutableListOf(med),
            week.map { T.dose(med, 2025, 6, it) }.toMutableList(),
            mutableListOf(),
            week.map { T.told(med, 2025, 6, it) }.toMutableList()
        )
        assertEquals(0, dao.silentMisses(listOf(med), now = now))
    }

    /**
     * La vraie panne : le créneau est passé, aucun rappel n'est parti, aucune dose n'a
     * été notée. C'est le cas Samsung — l'app mise en veille, les alarmes jamais
     * déclenchées, et personne au courant.
     */
    @Test fun `un rappel jamais parti est signale`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 5, 1))
        val sauf8 = week.filter { it != 8 }
        val dao = TestDao(
            mutableListOf(med),
            sauf8.map { T.dose(med, 2025, 6, it) }.toMutableList(),
            mutableListOf(),
            sauf8.map { T.told(med, 2025, 6, it) }.toMutableList()
        )
        assertEquals(1, dao.silentMisses(listOf(med), now = now))
    }

    /**
     * Prévenue mais pas prise : c'est un oubli, pas une panne. L'app a fait son travail
     * et n'a rien à confesser — le dire serait s'accuser à la place de quelqu'un d'autre.
     */
    @Test fun `une dose oubliee apres un rappel n est pas une panne`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 5, 1))
        val dao = TestDao(
            mutableListOf(med),
            week.filter { it != 8 }.map { T.dose(med, 2025, 6, it) }.toMutableList(),
            mutableListOf(),
            week.map { T.told(med, 2025, 6, it) }.toMutableList()      // le 8 aussi
        )
        assertEquals(0, dao.silentMisses(listOf(med), now = now))
    }

    /**
     * Prise en avance : le rappel est annulé avant d'avoir servi, donc il n'existe aucune
     * trace de rappel — et c'est parfaitement normal. Sans la vérification de la dose,
     * chaque prise en avance passerait pour une panne.
     */
    @Test fun `une dose prise en avance n est pas une panne`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 5, 1))
        val dao = TestDao(
            mutableListOf(med),
            week.map { T.dose(med, 2025, 6, it) }.toMutableList(),
            mutableListOf(),
            mutableListOf()                                            // jamais prévenue
        )
        assertEquals(0, dao.silentMisses(listOf(med), now = now))
    }

    /** Un médicament ajouté hier ne peut pas avoir raté les jours d'avant-hier. */
    @Test fun `un medicament recent n est pas reproche pour avant`() = runBlocking {
        val med = T.med(createdAt = T.at(2025, 6, 9, 8, 0))
        val dao = TestDao(mutableListOf(med))
        // Le 9 juin est le seul jour où il existait : ni prévenue, ni prise, donc un.
        assertEquals(1, dao.silentMisses(listOf(med), now = now))
    }

    /**
     * Le créneau d'aujourd'hui est peut-être passé d'une minute et son alarme est
     * peut-être en train de partir. Le compter serait une course perdue contre l'horloge,
     * et une alerte qui apparaît puis s'efface toute seule ne veut plus rien dire.
     */
    @Test fun `aujourd hui n est jamais compte`() = runBlocking {
        val med = T.med(hour = 9, createdAt = T.at(2025, 6, 1))
        val dao = TestDao(
            mutableListOf(med),
            week.map { T.dose(med, 2025, 6, it) }.toMutableList(),
            mutableListOf(),
            week.map { T.told(med, 2025, 6, it) }.toMutableList()
        )
        // Aujourd'hui, le créneau de 9h est passé depuis trois heures, sans rappel ni dose.
        assertEquals(0, dao.silentMisses(listOf(med), now = now))
    }

    /** Chaque médicament compte pour lui-même. */
    @Test fun `deux medicaments muets comptent deux fois`() = runBlocking {
        val a = T.med("A", hour = 9, createdAt = T.at(2025, 5, 1))
        val b = T.med("B", hour = 20, createdAt = T.at(2025, 5, 1))
        val sauf8 = week.filter { it != 8 }
        val dao = TestDao(
            mutableListOf(a, b),
            sauf8.flatMap { listOf(T.dose(a, 2025, 6, it), T.dose(b, 2025, 6, it)) }
                .toMutableList(),
            mutableListOf(),
            sauf8.flatMap { listOf(T.told(a, 2025, 6, it), T.told(b, 2025, 6, it)) }
                .toMutableList()
        )
        assertEquals(2, dao.silentMisses(listOf(a, b), now = now))
    }
}
