package com.example.medtap.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Le ciel est du calcul, donc il se teste.
 *
 * Un lever de soleil faux ne casse rien et ne se remarque qu'un matin d'hiver, en ouvrant
 * l'app dans le noir devant un ciel de midi. C'est exactement le genre d'erreur qu'un test
 * attrape et qu'un œil ne rattrape jamais.
 */
class SkyTest {

    private var saved: TimeZone? = null
    private val zone: TimeZone = TimeZone.getTimeZone("America/Montreal")

    @Before fun fixZone() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(zone)
    }

    @After fun restoreZone() {
        TimeZone.setDefault(saved)
    }

    private fun at(y: Int, mo: Int, d: Int, h: Int = 12, mi: Int = 0): Long =
        Calendar.getInstance(zone).apply { clear(); set(y, mo - 1, d, h, mi, 0) }.timeInMillis

    private fun localHour(millis: Long): Double =
        Calendar.getInstance(zone).apply { timeInMillis = millis }
            .let { it.get(Calendar.HOUR_OF_DAY) + it.get(Calendar.MINUTE) / 60.0 }

    // ---- le soleil ---------------------------------------------------------

    /**
     * Au solstice d'été, Laval voit le soleil vers 5 h 06 et le perd vers 20 h 46. Une
     * demi-heure de tolérance : on dessine un ciel, pas un almanach de marine.
     */
    @Test fun `lever et coucher au solstice d ete`() {
        val (rise, set) = Sky.sunTimes(at(2026, 6, 21))!!
        assertTrue("lever à ${localHour(rise)}", localHour(rise) in 4.6..5.6)
        assertTrue("coucher à ${localHour(set)}", localHour(set) in 20.3..21.3)
    }

    /** Et au solstice d'hiver, vers 7 h 20 et 16 h 12 — l'inverse exact du problème. */
    @Test fun `lever et coucher au solstice d hiver`() {
        val (rise, set) = Sky.sunTimes(at(2026, 12, 21))!!
        assertTrue("lever à ${localHour(rise)}", localHour(rise) in 6.8..7.8)
        assertTrue("coucher à ${localHour(set)}", localHour(set) in 15.7..16.7)
    }

    /** L'écart entre les deux solstices est ce qui rend une heure fixe intenable. */
    @Test fun `le jour est bien plus long en juin qu en decembre`() {
        val ete = Sky.sunTimes(at(2026, 6, 21))!!
        val hiver = Sky.sunTimes(at(2026, 12, 21))!!
        val dureeEte = (ete.second - ete.first) / 3_600_000.0
        val dureeHiver = (hiver.second - hiver.first) / 3_600_000.0
        assertTrue("été $dureeEte h", dureeEte > 15.0)
        assertTrue("hiver $dureeHiver h", dureeHiver < 9.5)
    }

    /** Midi est en plein jour, trois heures du matin en pleine nuit. Le minimum vital. */
    @Test fun `midi est le jour et trois heures est la nuit`() {
        assertEquals(Sky.Phase.DAY, Sky.moment(at(2026, 6, 21, 12)).phase)
        assertEquals(Sky.Phase.NIGHT, Sky.moment(at(2026, 6, 21, 3)).phase)
    }

    // ---- la lune -----------------------------------------------------------

    /**
     * Pleine lune le 1er janvier 2026 : la phase doit être proche de 0,5.
     *
     * C'est la valeur qui décide de la forme du croissant. Se tromper de la moitié d'un
     * cycle donnerait une lune pleine la nuit où il n'y en a pas du tout.
     */
    @Test fun `la pleine lune du premier janvier 2026`() {
        val p = Sky.moonPhase(at(2026, 1, 3, 12))
        assertTrue("phase $p", p in 0.44f..0.56f)
    }

    /** Et le cycle boucle bien : vingt-neuf jours et demi plus tard, on est revenu au même point. */
    @Test fun `la lune revient au meme point apres un mois synodique`() {
        val a = Sky.moonPhase(at(2026, 3, 10, 12))
        val b = Sky.moonPhase(at(2026, 3, 10, 12) + (29.530588853 * 86_400_000L).toLong())
        assertTrue("$a vs $b", kotlin.math.abs(a - b) < 0.02f)
    }

    /**
     * La part éclairée, telle que le dessin la calcule : 0 à la nouvelle lune, 1 à la
     * pleine.
     *
     * C'est cette valeur qui pilotait la morsure du croissant, et elle était appliquée à
     * l'envers : à la nouvelle lune le disque restait entier, donc le soir où il ne devait
     * y avoir aucune lune, il y en avait une pleine. Le test fixe les deux extrêmes et le
     * quartier, qui sont exactement les trois points où l'inversion se voit.
     */
    private fun illum(p: Float) = 1f - kotlin.math.abs(p - 0.5f) * 2f

    @Test fun `la part eclairee va de zero a un`() {
        assertEquals(0f, illum(0f), 0.001f)          // nouvelle : rien
        assertEquals(0f, illum(1f), 0.001f)          // et l'autre bout du cycle aussi
        assertEquals(1f, illum(0.5f), 0.001f)        // pleine : tout
        assertEquals(0.5f, illum(0.25f), 0.001f)     // premier quartier : la moitié
        assertEquals(0.5f, illum(0.75f), 0.001f)     // dernier quartier : l'autre moitié
    }

    /** Une nouvelle lune de calendrier doit bien rendre une part éclairée quasi nulle. */
    @Test fun `la nouvelle lune n eclaire rien`() {
        // Nouvelle lune le 18 janvier 2026.
        val p = Sky.moonPhase(at(2026, 1, 18, 12))
        assertTrue("phase $p", illum(p) < 0.12f)
    }

    @Test fun `la phase reste toujours entre zero et un`() {
        listOf(at(2020, 1, 1), at(2026, 8, 6), at(2030, 12, 31)).forEach {
            val p = Sky.moonPhase(it)
            assertTrue("phase $p", p in 0f..1f)
        }
    }

    // ---- le petit extra ----------------------------------------------------

    /** Le cycle tombe bien où il doit, et se répète tous les vingt-huit jours. */
    @Test fun `l aurore suit le cycle de vingt-huit jours`() {
        val depart = at(2026, 7, 30, 23)
        assertTrue(Sky.aurora(depart) > 0f)
        assertTrue(Sky.aurora(depart + 1 * 86_400_000L) > 0f)
        assertEquals(0f, Sky.aurora(depart + 10 * 86_400_000L), 0.001f)
        // Vingt-huit jours plus tard, la même chose.
        assertTrue(Sky.aurora(depart + 28 * 86_400_000L) > 0f)
        assertEquals(0f, Sky.aurora(depart + 38 * 86_400_000L), 0.001f)
    }

    /** Et en arrière aussi : le modulo doit tenir des deux côtés de la date d'ancrage. */
    @Test fun `l aurore fonctionne avant la date d ancrage`() {
        val avant = at(2026, 7, 30, 23) - 28 * 86_400_000L
        assertTrue(Sky.aurora(avant) > 0f)
    }

    /** Jamais en plein jour : le vert dans le ciel de midi ne ressemblerait à rien. */
    @Test fun `pas d aurore en plein midi`() {
        assertEquals(0f, Sky.moment(at(2026, 7, 30, 12)).aurora, 0.001f)
    }

    // ---- les saisons -------------------------------------------------------

    @Test fun `les quatre saisons tombent aux bonnes dates`() {
        assertEquals(Sky.Season.WINTER, Sky.season(at(2026, 1, 15)))
        assertEquals(Sky.Season.WINTER, Sky.season(at(2026, 12, 20)))
        assertEquals(Sky.Season.SPRING, Sky.season(at(2026, 4, 15)))
        assertEquals(Sky.Season.SUMMER, Sky.season(at(2026, 7, 15)))
        assertEquals(Sky.Season.AUTUMN, Sky.season(at(2026, 10, 10)))
    }

    /** Le 20 mars est encore l'hiver ici, et le 21 le printemps. La bascule est nette. */
    @Test fun `la bascule de mars`() {
        assertEquals(Sky.Season.WINTER, Sky.season(at(2026, 3, 20)))
        assertEquals(Sky.Season.SPRING, Sky.season(at(2026, 3, 21)))
    }

    /** Il ne neige jamais en juillet, et il ne tombe jamais de feuilles en février. */
    @Test fun `ce qui tombe correspond a la saison`() {
        (1..28).forEach { d ->
            val ete = Sky.falling(at(2026, 7, d)).first
            assertTrue("juillet $d : $ete", ete == Sky.Falling.NONE || ete == Sky.Falling.RAIN)

            val hiver = Sky.falling(at(2026, 2, d)).first
            assertTrue("février $d : $hiver", hiver == Sky.Falling.NONE || hiver == Sky.Falling.SNOW)

            val automne = Sky.falling(at(2026, 10, d)).first
            assertTrue(
                "octobre $d : $automne",
                automne == Sky.Falling.LEAVES || automne == Sky.Falling.RAIN
            )
        }
    }

    /** Un mois de novembre doit être franchement mouillé, et juillet plutôt sec. */
    @Test fun `novembre est bien plus arrose que juillet`() {
        val pluieNov = (1..30).count { Sky.falling(at(2026, 11, it)).first == Sky.Falling.RAIN }
        val pluieJuil = (1..31).count { Sky.falling(at(2026, 7, it)).first == Sky.Falling.RAIN }
        assertTrue("nov $pluieNov vs juil $pluieJuil", pluieNov > pluieJuil)
    }

    /** Le même jour rend toujours le même temps : le ciel ne doit pas scintiller. */
    @Test fun `la meteo d un jour est stable`() {
        val matin = Sky.falling(at(2026, 11, 12, 8))
        val soir = Sky.falling(at(2026, 11, 12, 22))
        assertEquals(matin.first, soir.first)
    }

    // ---- la course des astres ----------------------------------------------

    /** De gauche à droite : au matin l'astre est à gauche, au soir à droite. */
    @Test fun `le soleil traverse de gauche a droite`() {
        val (rise, set) = Sky.sunTimes(at(2026, 6, 21))!!
        val matin = Sky.moment(rise + (set - rise) / 6).sunT
        val midi = Sky.moment(rise + (set - rise) / 2).sunT
        val soir = Sky.moment(set - (set - rise) / 6).sunT
        assertTrue("$matin < $midi < $soir", matin < midi && midi < soir)
    }

    /**
     * La course DÉBORDE de part et d'autre : sous l'horizon avant le lever, au-dessus
     * après. C'est ce qui rend le passage du soleil à la lune continu, au lieu de figer
     * l'astre au bord de l'écran pendant toute l'aube.
     */
    @Test fun `le soleil est sous l horizon avant le lever et apres le coucher`() {
        val (rise, set) = Sky.sunTimes(at(2026, 6, 21))!!
        assertTrue(Sky.moment(rise - 20 * 60_000L).sunT < 0f)
        assertTrue(Sky.moment(set + 20 * 60_000L).sunT > 1f)
        // Et pile au lever, il est exactement sur la ligne.
        assertEquals(0f, Sky.moment(rise).sunT, 0.01f)
    }

    /** L'obscurité suit la hauteur du soleil, sans palier : midi clair, minuit noir. */
    @Test fun `l obscurite suit la hauteur du soleil`() {
        val (rise, set) = Sky.sunTimes(at(2026, 6, 21))!!
        val midi = Sky.moment(rise + (set - rise) / 2).dark
        val leverPile = Sky.moment(rise).dark
        val nuit = Sky.moment(at(2026, 6, 21, 2)).dark
        assertEquals(0f, midi, 0.01f)
        assertTrue("au lever : $leverPile", leverPile in 0.4f..0.9f)
        assertEquals(1f, nuit, 0.01f)
    }

    // ---- l'heure avancée du Québec -----------------------------------------

    /**
     * Le 8 mars 2026, l'heure avance à 2 h. Le lever de soleil doit sauter d'une heure
     * dans l'affichage local, sans que le calcul ait rien à faire de particulier : il
     * travaille en instants absolus, et c'est le fuseau qui applique la règle.
     */
    @Test fun `le changement d heure du printemps decale l affichage`() {
        val veille = localHour(Sky.sunTimes(at(2026, 3, 7))!!.first)
        val apres = localHour(Sky.sunTimes(at(2026, 3, 9))!!.first)
        // Une heure de plus, à quelques minutes près : sans l'heure avancée, le lever
        // reculerait doucement au lieu de bondir.
        assertTrue("$veille puis $apres", apres - veille in 0.85..1.15)
    }

    /** Et à l'automne, le 1er novembre 2026, il recule d'autant. */
    @Test fun `le changement d heure de l automne decale l affichage`() {
        val veille = localHour(Sky.sunTimes(at(2026, 10, 31))!!.first)
        val apres = localHour(Sky.sunTimes(at(2026, 11, 2))!!.first)
        assertTrue("$veille puis $apres", veille - apres in 0.85..1.15)
    }

    /**
     * Et la journée reste une journée : midi le 7 mars, plus deux jours de calendrier,
     * doit toujours être midi le 9 — pas 13 h.
     *
     * C'est le piège que l'atelier tendait en avançant de 86 400 000 ms par jour. En
     * faisant défiler une année, l'heure affichée aurait glissé d'une heure au printemps
     * et d'une autre à l'automne, et le soleil se serait mis à se lever de travers.
     */
    @Test fun `une journee de calendrier garde l heure murale`() {
        val depart = at(2026, 3, 7, 12)
        val deuxJours = Calendar.getInstance(zone).apply {
            timeInMillis = depart
            add(Calendar.DAY_OF_YEAR, 2)
        }.timeInMillis
        assertEquals(12.0, localHour(deuxJours), 0.01)

        // Ce que l'ancienne arithmétique donnait, pour mémoire : une heure de trop.
        assertEquals(13.0, localHour(depart + 2 * 86_400_000L), 0.01)
    }

    // ---- l'atelier ---------------------------------------------------------

    /** Ce qu'on impose est rendu tel quel ; le reste continue d'être calculé. */
    @Test fun `l atelier impose ce qu on lui demande sans figer le reste`() {
        val quand = at(2026, 7, 15, 12)
        val m = Sky.moment(
            quand,
            Sky.Forced(season = Sky.Season.WINTER, falling = Sky.Falling.SNOW, moon = 0.5f)
        )
        assertEquals(Sky.Season.WINTER, m.season)
        assertEquals(Sky.Falling.SNOW, m.falling)
        assertEquals(0.5f, m.moon, 0.001f)
        // L'heure, elle, n'a pas été touchée : c'est toujours midi en juillet.
        assertEquals(Sky.Phase.DAY, m.phase)
    }
}
