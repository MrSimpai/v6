package com.example.medtap.reminder

import com.example.medtap.Her
import com.example.medtap.ui.Mood
import kotlin.random.Random

/**
 * Le truc de Duolingo, ce ne sont pas les blagues — c'est l'escalade, et le fait que
 * la mascotte finit par ARRÊTER de plaisanter. Toutes les dix minutes, le rappel se
 * repose avec une nouvelle ligne, un cran plus haut. Le dernier palier ne fonctionne
 * que parce que les quatre premiers étaient légers.
 *
 * Le registre change avec le palier : petits noms et emoji en haut de l'échelle,
 * français neutre et prénom complet tout en bas. Ce changement de ton fait autant de
 * travail que le texte lui-même.
 */
enum class Tier(val mood: Mood) {
    PONCTUEL(Mood.Waiting),
    RELANCE(Mood.Waiting),
    BOUDERIE(Mood.Waiting),
    DRAME(Mood.Sad),
    SERIEUX(Mood.Overdue);

    companion object {
        fun forLateness(minutes: Long) = when {
            minutes < 10  -> PONCTUEL
            minutes < 30  -> RELANCE
            minutes < 60  -> BOUDERIE
            minutes < 120 -> DRAME
            else          -> SERIEUX
        }
    }
}

data class FloLine(val title: String, val body: String)

object FloMessages {

    private val PONCTUEL = listOf(
        FloLine("C'est l'heure, ${Her.name} 💊", "${Her.dragon} a préparé ta petite dose."),
        FloLine("Psst. ${Her.name}.", "C'est le moment. Une pilule, et je te laisse tranquille."),
        FloLine("Ding 🐉", "${Her.dragon} réclame son dû. Une (1) pilule."),
        FloLine("Bonjour, Flomingo", "Ta dose t'attend. Note-la et je retourne dormir."),
        FloLine("Livraison : une pilule", "Ceci est ton rappel officiel, dragon inclus."),
        FloLine("Rapport de mission", "Objectif : une pilule. Difficulté : facile. Récompense : un dragon content."),
        FloLine("C'est l'heure 🕐", "Allez Flozilla, on y va."),
        FloLine("🐉 toc toc", "Qui est là ? Ta médication. Elle attend depuis un moment.")
    )

    private val RELANCE = listOf(
        FloLine("${Her.name} ?", "La pilule est toujours là. Je la regarde. Elle me regarde."),
        FloLine("J'attends encore, Floflosky", "J'ai compté les tuiles du plafond. Deux fois."),
        FloLine("Petit dragon, grande patience", "Grande, mais pas infinie."),
        FloLine("hey. hey ${Her.name}. hey.", "La. Pilule."),
        FloLine("Statut : aucune pilule", "Le dragon demeure sans tribut."),
        FloLine("${Her.name} 🍩", "Je sais que tu as vu l'autre notification. Et je sais que tu sais."),
        FloLine("Rappel gentil 🐉", "Gentil pour l'instant."),
        FloLine("🐉 tape du pied", "Vingt minutes. Je dis ça comme ça. Je ne juge pas. Un peu.")
    )

    private val BOUDERIE = listOf(
        FloLine("Aucune pression, ${Her.name}", "Je reste ici. À être un dragon. À attendre. Indéfiniment."),
        FloLine("C'est correct.", "Tout va bien. Je vais bien. La pilule va bien, toute seule, là-bas."),
        FloLine("Je ne suis pas fâchée", "Juste un petit dragon rose avec des sentiments et un calendrier."),
        FloLine("Une demi-heure, Floflosky", "J'ai commencé à raconter ma propre vie à voix haute, pour passer le temps."),
        FloLine("Cool. Très cool.", "C'est tout à fait normal de laisser un dragon attendre comme ça."),
        FloLine("${Her.name}. ${Her.realName}. Flobert.", "Je vais continuer d'inventer des noms jusqu'à ce que la dose soit notée."),
        FloLine("🐉 entre dans sa phase méchante", "Prends la pilule et je redeviens adorable."),
        FloLine("J'attends toujours", "Comme un phare. Un phare rose. Et déçu.")
    )

    private val DRAME = listOf(
        FloLine("Ces rappels ne fonctionnent pas", "Je plaisante, je n'arrêterai jamais. Prends ta pilule, ${Her.name}."),
        FloLine("Une heure, ${Her.name}.", "Je l'ai dit aux autres dragons. Ils sont déçus eux aussi."),
        FloLine("J'ai réfléchi 🐉", "À toi. Qui ne prends pas ta pilule. Pendant soixante longues minutes."),
        FloLine("🐉💔", "Un bouton et c'est réglé. Un seul. C'est tout ce que je demande."),
        FloLine("Dommages émotionnels", "${Her.name}, j'ai un trou en forme de pilule dans le cœur."),
        FloLine("Je ne veux pas être dramatique", "Mais j'ai composé une ballade. Elle parle de toi. Elle est triste."),
        FloLine("Soixante minutes, Floflosky", "J'aurais pu faire éclore un œuf. Enfin, je crois. Je ne suis pas médecin."),
        FloLine("Toujours là 🐉", "Toujours rose. Toujours en attente. Je t'aime quand même. Prends-la.")
    )

    /** Pas de petit nom, pas d'emoji, pas de numéro. Le changement de registre EST le message. */
    private val SERIEUX = listOf(
        FloLine("${Her.realName} — ta médication",
            "La dose d'aujourd'hui a plus de deux heures de retard. Prends-la dès que tu peux, et parle à ton médecin ou à ton pharmacien si tu as un doute sur le moment."),
        FloLine("${Her.realName}, c'est important",
            "La dose d'aujourd'hui n'est toujours pas enregistrée. Note-la dans l'application une fois que tu l'as prise."),
        FloLine("Médication non enregistrée",
            "${Her.realName}, la dose d'aujourd'hui est très en retard. Si tu l'as déjà prise, note-la pour arrêter les rappels."),
        FloLine("Deux heures de retard",
            "Pas de plaisanterie cette fois, ${Her.realName}. Prends la dose d'aujourd'hui, ou note-la si c'est déjà fait."),
        FloLine("${Her.realName}",
            "Toujours rien d'enregistré aujourd'hui. Si tu sautes la dose volontairement, c'est ton choix — note-la ou désactive le rappel pour que ça s'arrête.")
    )

    private val PRIS = listOf(
        "Merci, ${Her.name} 🐉✨", "C'est noté", "${Her.dragon} est satisfaite",
        "Tribut accepté 💊", "Flozilla : 1 — L'oubli : 0", "Voilà ma ${Her.name}",
        "Enregistré", "Moral du dragon : rétabli"
    )

    private val RETOUR = FloLine(
        "Hier a sauté, ${Her.name}",
        "On n'en fait pas un drame. Aujourd'hui est un nouveau jour. 🐉"
    )

    private fun pool(tier: Tier) = when (tier) {
        Tier.PONCTUEL -> PONCTUEL; Tier.RELANCE -> RELANCE; Tier.BOUDERIE -> BOUDERIE
        Tier.DRAME -> DRAME; Tier.SERIEUX -> SERIEUX
    }

    /**
     * Semé sur le créneau plus le nombre de relances, pour que chaque relance dise autre
     * chose, mais qu'un simple rappel remis en place ne rebrasse pas le texte.
     */
    fun line(minutesLate: Long, slot: Long, medName: String): Pair<Tier, FloLine> {
        val tier = Tier.forLateness(minutesLate)
        val p = pool(tier)
        val nagIndex = (minutesLate / 10).coerceAtLeast(0)
        val picked = p[Random(slot / 60000 + nagIndex * 7919).nextInt(p.size)]
        val body = if (tier == Tier.SERIEUX) "$medName — ${picked.body}" else picked.body
        return tier to FloLine(picked.title, body)
    }

    fun celebration(streak: Int, seed: Long): FloLine {
        val title = PRIS[Random(seed).nextInt(PRIS.size)]
        val body = when {
            streak <= 1 -> "Enregistré. À la prochaine."
            streak < 7  -> "$streak jours de suite. Je tiens le compte, ${Her.name}."
            streak < 30 -> "Série de $streak jours. Franchement impressionnant."
            else        -> "$streak jours. ${Her.name}, tu es une machine et je t'adore."
        }
        return FloLine(title, body)
    }

    fun comeback() = RETOUR
}
