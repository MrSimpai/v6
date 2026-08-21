package com.example.medtap.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Le lieu du jour.
 *
 * Tout ce fichier vérifie une seule promesse : le décor CHANGE, et rien n'y est
 * permanent. C'est une promesse facile à casser sans s'en rendre compte — il suffit
 * d'ajouter une bête à une réserve saisonnière ou de retoucher un seuil — et elle est
 * invisible à l'œil nu, parce qu'il faudrait ouvrir l'app tous les matins pendant un an
 * pour s'apercevoir que la rivière n'est jamais à sec.
 */
class WorldTest {

    private fun at(y: Int, mo: Int, d: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("America/Toronto")).apply {
            clear(); set(y, mo - 1, d, 14, 0)
        }.timeInMillis

    /** Une année entière, une journée à la fois. */
    private fun year(from: Long = at(2026, 1, 1)): List<Sky.Moment> =
        (0 until 365).map { Sky.moment(from + it * 86_400_000L) }

    /**
     * L'invariant qui compte VRAIMENT.
     *
     * Un huard, un canard, un castor ou une libellule les jours où la rivière est à sec,
     * ce serait la seule chose de tout le décor à avoir l'air franchement cassée : une
     * bête d'eau posée sur un pré. C'est aussi l'erreur la plus facile à commettre, parce
     * qu'elle s'écrit en ajoutant innocemment une ligne à une réserve saisonnière.
     */
    @Test fun `aucune bete d eau les jours sans eau`() {
        val eau = setOf(Visitor.DUCKS, Visitor.LOON, Visitor.BEAVER, Visitor.DRAGONFLY)
        var jours = 0
        year().forEach { m ->
            val w = worldOf(m)
            if (w.river > 0f) return@forEach
            jours++
            visitorsFor(m, w).forEach {
                assertFalse("jour ${m.day} : $it sur un sol sec", it in eau)
            }
        }
        // Et si la rivière ne s'asséchait jamais, le test ci-dessus ne prouverait rien.
        assertTrue("aucun jour à sec dans l'année", jours > 20)
    }

    /**
     * Rien n'est permanent.
     *
     * C'était tout le problème d'avant : les montagnes étaient semées sur des constantes
     * et le chalet restait posé du premier flocon au dégel. Une couche fixe suffit à
     * faire lire tout le reste comme un fond d'écran.
     */
    @Test fun `le decor n est jamais figé`() {
        val an = year()
        val lieux = an.map { worldOf(it).land }.toSet()
        assertTrue("un seul lieu dans l'année : $lieux", lieux.size >= 5)

        val cretes = an.map { worldOf(it).ridges }.toSet()
        assertTrue("le relief ne change pas : $cretes", cretes.size >= 3)
        assertTrue("jamais de plaine", cretes.contains(0))

        val secs = an.count { worldOf(it).river <= 0f }
        assertTrue("la rivière est éternelle", secs > 20)
        assertTrue("la rivière est trop rare ($secs jours à sec)", secs < 200)

        val vides = an.count { worldOf(it).house == null }
        assertTrue("il y a toujours une maison", vides > 40)
        assertTrue("il n'y a jamais personne ($vides jours vides)", vides < 300)
    }

    /** La maison suit la saison : pas de chalet en rondins au mois de juillet. */
    @Test fun `chaque saison a sa maison`() {
        year().forEach { m ->
            val attendu = when (m.season) {
                Sky.Season.WINTER -> House.CABIN
                Sky.Season.AUTUMN -> House.HALLOWEEN
                else -> House.COTTAGE
            }
            worldOf(m).house?.let {
                assertEquals("jour ${m.day} en ${m.season}", attendu, it)
            }
        }
    }

    /** Chaque saison propose de quoi choisir : une liste vide planterait le tirage. */
    @Test fun `chaque saison a plusieurs lieux`() {
        Sky.Season.entries.forEach {
            assertTrue(it.name, landsFor(it).size >= 4)
        }
    }

    /**
     * Le lieu tiré appartient à la saison.
     *
     * Un champ de maïs en février ou une érablière en fleurs au mois d'août sont le genre
     * de faute qu'on ne voit qu'en ouvrant l'app le bon jour — c'est-à-dire jamais.
     */
    @Test fun `le lieu du jour appartient a sa saison`() {
        year().forEach { m ->
            assertTrue(
                "jour ${m.day} : ${worldOf(m).land} en ${m.season}",
                worldOf(m).land in landsFor(m.season)
            )
        }
    }

    /** Le tirage est STABLE : deux lectures du même jour donnent le même endroit. */
    @Test fun `le meme jour donne le meme endroit`() {
        val matin = Sky.moment(at(2026, 8, 21) - 6 * 3_600_000L)
        val soir = Sky.moment(at(2026, 8, 21) + 6 * 3_600_000L)
        val a = worldOf(matin)
        val b = worldOf(soir)
        assertEquals(a.land, b.land)
        assertEquals(a.ridges, b.ridges)
        assertEquals(a.river, b.river, 0.0001f)
        assertEquals(a.house, b.house)
    }

    /** La ruche ne passe pas l'hiver : les abeilles sont en grappe dans la ruche. */
    @Test fun `pas de ruche en hiver`() {
        year().forEach { m ->
            if (m.season == Sky.Season.WINTER) {
                assertFalse("jour ${m.day}", worldOf(m).hive)
            }
        }
    }
}
