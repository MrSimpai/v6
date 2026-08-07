package com.example.medtap.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le dragon ne doit pas radoter.
 *
 * C'est le genre de défaut qui ne casse rien et qui coule quand même l'app : une mascotte
 * qui répète la même phrase cesse d'être quelqu'un qui parle et redevient un logiciel qui
 * insiste. On coupe ses notifications, et ça met fin à tout le reste.
 *
 * Le bogue signalé : « je reçois souvent PILL PILL PILLLL ». Rien n'était cassé — chaque
 * relance tirait au sort dans son palier, honnêtement et indépendamment. C'était justement
 * le problème. Le palier `DRAME` dure une heure, la relance revient toutes les dix
 * minutes : six tirages dans neuf lignes, tous les jours. 89 % de chances qu'une ligne
 * sorte deux fois dans la même heure, et 51 % qu'une ligne donnée sorte dans la journée —
 * soit trois ou quatre fois par semaine pour « PILL PILL PILLLL! ».
 *
 * Ces tests ne vérifient donc pas que le tirage est bon. Ils vérifient qu'il n'y en a plus.
 */
class FloMessagesTest {

    /**
     * Un créneau fixe, à 1h20 du matin UTC. Ces tests ne parlent pas d'horloge : ce qui
     * compte est seulement qu'ajouter vingt-quatre heures avance d'un jour et ne change
     * pas l'heure, ce qui est vrai pour n'importe quelle valeur.
     */
    private val slot = 1_749_604_800_000L

    private val jour = 24 * 60 * 60 * 1000L

    /** Les relances aux minutes où elles partent réellement, quand rien ne les arrête. */
    private fun titles(minutes: List<Long>): List<String> =
        minutes.map { FloMessages.line(it, it, slot, "Truc").second.title }

    // ---- l'échelle de relance -----------------------------------------------

    /**
     * Le cas signalé : une heure de palier `DRAME`, six relances, six lignes différentes.
     *
     * Garanti par construction et pas par chance — l'indice est `nagIndex % 9`, et six
     * entiers consécutifs modulo neuf sont toujours distincts.
     */
    @Test fun `une heure de relances ne repete aucune ligne`() {
        assertEquals(Tier.DRAME, FloMessages.line(60, 60, slot, "Truc").first)
        val drame = titles(listOf(60L, 70L, 80L, 90L, 100L, 110L))
        assertEquals("six relances, six lignes : $drame", 6, drame.toSet().size)
    }

    /** La même chose sur les autres paliers, qui ont tous plus de lignes que de relances. */
    @Test fun `aucun palier ne repete une ligne dans sa duree`() {
        mapOf(
            Tier.PONCTUEL to listOf(0L),
            Tier.RELANCE to listOf(10L, 20L),
            Tier.BOUDERIE to listOf(30L, 40L, 50L),
            Tier.DRAME to listOf(60L, 70L, 80L, 90L, 100L, 110L)
        ).forEach { (tier, minutes) ->
            assertEquals("$tier commence bien où on croit", tier, Tier.forLateness(minutes.first()))
            val lines = titles(minutes)
            assertEquals("$tier répète une ligne : $lines", minutes.size, lines.toSet().size)
        }
    }

    /**
     * Deux journées ne servent pas la même séquence. Ce n'est pas garanti par construction
     * — c'est le brassage — donc le test regarde un mois plutôt que deux jours : un
     * brassage dégénéré rendrait les trente identiques, et c'est ça qu'on veut attraper.
     * Le seuil est bas exprès, pour qu'aucun tirage honnête ne puisse le faire échouer.
     */
    @Test fun `l ordre des lignes change d une journee a l autre`() {
        val premieres = (0 until 30).map {
            FloMessages.line(60, 60, slot + it * jour, "Truc").second.title
        }
        assertTrue(
            "la première ligne de DRAME ne varie presque pas : ${premieres.toSet()}",
            premieres.toSet().size >= 4
        )
    }

    /**
     * Mais dans la même tranche de dix minutes, c'est la même ligne. Le rappel se repose
     * tel quel quand elle le balaie : rebrasser le texte à ce moment-là donnerait
     * l'impression que le dragon vient de dire autre chose alors qu'il ne s'est rien passé.
     */
    @Test fun `remettre le rappel en place ne change pas le texte`() {
        assertEquals(
            FloMessages.line(62, 62, slot, "Truc").second.title,
            FloMessages.line(68, 68, slot, "Truc").second.title
        )
    }

    /**
     * Le palier ne dépend QUE de la pression, le texte que du temps écoulé. C'est ce qui
     * laisse une plage horaire de trois heures rester au premier palier sans pour autant
     * servir la même phrase du début à la fin.
     */
    @Test fun `la plage garde le palier mais fait tourner le texte`() {
        val ouverture = FloMessages.line(0, 0, slot, "Truc")
        val troisHeures = FloMessages.line(180, 0, slot, "Truc")
        assertEquals(Tier.PONCTUEL, ouverture.first)
        assertEquals(Tier.PONCTUEL, troisHeures.first)
        assertNotEquals(ouverture.second.title, troisHeures.second.title)
    }

    /** Le dernier palier nomme le médicament : c'est le seul qui en a besoin. */
    @Test fun `le palier serieux nomme le medicament`() {
        val (tier, line) = FloMessages.line(200, 200, slot, "Truc")
        assertEquals(Tier.SERIEUX, tier)
        assertTrue("« ${line.body} »", line.body.startsWith("Truc — "))
    }

    // ---- les listes servies une fois par jour --------------------------------

    /**
     * Soixante jours suffisent toujours à contenir un tour complet — n'importe quelle
     * fenêtre de `2n` jours en contient un — donc toutes les lignes de la liste doivent
     * être passées. Le seuil est le compte d'aujourd'hui : en ajouter ne fera pas échouer
     * le test, en perdre oui.
     */
    private fun deuxMois(of: (Long) -> String): List<String> =
        (0 until 60).map { of(slot + it * jour) }

    /** Aucune ligne deux jours de suite : c'est la répétition qui se remarque. */
    private fun assertJamaisDeuxJoursDeSuite(lines: List<String>) {
        lines.zipWithNext().forEachIndexed { i, (a, b) ->
            assertNotEquals("jours $i et ${i + 1} identiques", a, b)
        }
    }

    /** Le mot d'avance : six lignes, une par jour. Sans défilé, celle d'hier revient vite. */
    @Test fun `le mot d avance defile sans se repeter`() {
        val mots = deuxMois { FloMessages.early(it).title }
        assertTrue("le catalogue n'est pas passé en entier : ${mots.toSet()}", mots.toSet().size >= 6)
        assertJamaisDeuxJoursDeSuite(mots)
    }

    /**
     * La félicitation : vingt-cinq lignes pour une dose par jour. Avec un tirage
     * indépendant, six chances sur dix d'en revoir une dans la semaine.
     */
    @Test fun `la felicitation defile sur tout le catalogue`() {
        val titres = deuxMois { FloMessages.celebration(3, it).title }
        assertTrue("catalogue incomplet : ${titres.toSet().size} lignes", titres.toSet().size >= 25)
        assertJamaisDeuxJoursDeSuite(titres)
    }

    /**
     * Deux médicaments ne doivent pas se répondre en écho. Ils partagent la journée mais
     * pas l'heure, et c'est l'heure qui les sépare.
     *
     * La comparaison porte sur un mois entier et non sur une journée : rien n'interdit à
     * deux défilés indépendants de tomber sur la même ligne un jour donné, et l'exiger
     * ferait échouer le test une fois sur six pour une raison qui n'est pas un défaut.
     * Deux mois identiques, eux, voudraient dire que l'heure ne sépare rien du tout.
     */
    @Test fun `deux medicaments ne disent pas la meme chose`() {
        val soir = slot + 12 * 60 * 60 * 1000L
        assertNotEquals(
            (0 until 30).map { FloMessages.early(slot + it * jour).title },
            (0 until 30).map { FloMessages.early(soir + it * jour).title }
        )
        assertNotEquals(
            (0 until 30).map { FloMessages.celebration(3, slot + it * jour).title },
            (0 until 30).map { FloMessages.celebration(3, soir + it * jour).title }
        )
    }
}
