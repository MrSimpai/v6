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

    /**
     * Le tirage est STABLE : le décor ne change pas sous les pieds pendant la journée.
     *
     * C'est la propriété qui rend tout le reste crédible. Si le lieu était retiré à
     * chaque lecture, la forêt du matin serait un champ de maïs à midi et le chevreuil
     * qu'on a croisé n'aurait jamais existé — or c'est exactement ce qu'on promet en
     * disant « le décor du jour ».
     *
     * La journée est délimitée par `Sky.dayIndex`, PAS par le calendrier du test. Une
     * première version fabriquait deux instants à 8 h et 20 h heure de Laval et affirmait
     * qu'ils tombaient le même jour ; sur le runner, réglé sur UTC, 20 h à Laval est
     * minuit passé, donc `dayIndex` avait raison de rendre deux journées différentes et
     * c'est l'affirmation du test qui était fausse. Le code sous test décide où commence
     * une journée ; un test qui en décide autrement de son côté ne mesure que l'écart
     * entre les deux.
     */
    @Test fun `le decor ne bouge pas de la journee`() {
        val base = at(2026, 8, 21)
        val jour = Sky.moment(base)
        val attendu = worldOf(jour)
        var heures = 0

        (-11..11).forEach { h ->
            val m = Sky.moment(base + h * 3_600_000L)
            if (m.day != jour.day) return@forEach
            heures++
            val w = worldOf(m)
            assertEquals("à ${h}h du midi", attendu.land, w.land)
            assertEquals("à ${h}h du midi", attendu.ridges, w.ridges)
            assertEquals("à ${h}h du midi", attendu.river, w.river, 0.0001f)
            assertEquals("à ${h}h du midi", attendu.house, w.house)
            assertEquals("à ${h}h du midi", attendu.hive, w.hive)
        }
        // Sans ce garde-fou, un `dayIndex` qui changerait à chaque heure ferait passer
        // le test avec une seule lecture comparée à elle-même.
        assertTrue("seulement $heures h dans la journée", heures >= 12)
    }

    /** La ruche ne passe pas l'hiver : les abeilles sont en grappe à l'intérieur. */
    @Test fun `pas de ruche en hiver`() {
        var hivers = 0
        year().forEach { m ->
            if (m.season != Sky.Season.WINTER) return@forEach
            hivers++
            assertFalse("jour ${m.day}", worldOf(m).hive)
        }
        assertTrue("aucun jour d'hiver dans l'année", hivers > 40)
    }
}
