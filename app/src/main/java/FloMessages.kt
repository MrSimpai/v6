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
        FloLine("Psst ${Her.name}.", "C'est le moment. Une pilule, et je te laisse tranquille."),
        FloLine("Ding 🐉", "${Her.dragon} réclame son dû. Une pilule."),
        FloLine("Sup, Flomingo🦩", "Ta dose t'attend. Prend la et je retourne dormir."),
        FloLine("Livraison d'une pilule", "Ceci est ton rappel officiel, dragon inclus."),
        FloLine("Rapport de mission", "Objectif : une pilule. Difficulté : facile. Récompense : un dragon content."),
        FloLine("C'est l'heure 🕐", "Allez Flozilla, on y va."),
    
        // Ligne écrit a la main en dessous :)
        FloLine("Floflosky, c'est pill time")
        FloLine("toc toc", "Qui est là ? Ta médication. Pas compliqué lala.")
        FloLine("Pillule!! boit de l'eau en meme temps 🥰"),
        Floline("Wow, t'est cheveux sont vraiment beau aujourd'hui","Ohh oui ta pilule 🤭")
        
        
        
    )

    private val RELANCE = listOf(
        FloLine("${Her.name} ?", "La pilule est toujours là. Je la regarde. Elle me regarde."),
        FloLine("J'attends encore, Floflosky", "J'ai compté les tuiles du plafond. Deux fois."),
        FloLine("Petit dragon, grande patience", "Grande, mais pas infinie."),
        FloLine("hey. hey ${Her.name}. hey.", "La. Pilule."),
        FloLine("🙄"),
        FloLine("Je sais que tu as vu l'autre notification. Et je sais que tu sais."),
        FloLine("Rappel gentil 🐉", "Gentil pour l'instant."),
        FloLine("Vingt minutes. Je dis ça comme ça. Je ne juge pas. Un peu.")
        
        // Ligne écrit a la main en dessous :)
        FloLine("Yoooo, c'est encore moi! PILLULE TIME"),
    )

    private val BOUDERIE = listOf(
        FloLine("Aucune pression, ${Her.name}", "Je reste ici. À être un dragon. À attendre. Indéfiniment."),
        FloLine("C'est correct.", "Tout va bien. Je vais bien. La pilule va bien, toute seule, là-bas."),
        FloLine("Je ne suis pas fâchée", "Juste un petit dragon rose avec des sentiments et un calendrier."),
        FloLine("Une demi-heure, Floflosky", "J'ai commencé à raconter ma propre vie à voix haute, pour passer le temps."),
        FloLine("Cool. Très cool.", "C'est tout à fait normal de laisser un dragon attendre comme ça 💔"),
        FloLine("${Her.name}. ${Her.realName}. Flobert.", "Je vais continuer d'inventer des noms jusqu'à ce que la dose soit notée."),
        FloLine("🐉 entre dans sa phase méchante", "Prends la pilule et je redeviens adorable."),
        FloLine("J'attends toujours", "Comme un phare. Un phare rose. Et déçu.")
    )

    private val DRAME = listOf(
        FloLine("Ces rappels ne fonctionnent pas", "Je plaisante, je n'arrêterai jamais. Prends ta pilule, ${Her.name}."),
        FloLine("Une heure, ${Her.name}.", "Je l'ai dit aux autres dragons. Ils sont déçus eux aussi."),
        FloLine("J'ai réfléchi 🐉", "À toi. Qui ne prends pas ta pilule. Pendant soixante longues minutes."),
        FloLine("🐉💔", "Un bouton et c'est réglé. Un seul. C'est tout ce que je demande."),
        FloLine("Dommages émotionnels", "Flo, j'ai un trou en forme de pilule dans le coeur."),
        FloLine("Je ne veux pas être dramatique", "Mais j'ai composé une ballade. Elle parle de toi. Elle est triste."),
        FloLine("Soixante minutes, Floflosky", "J'aurais eu le temps de faire une salade de pate"),
        FloLine("Toujours là 🐉", "Toujours rose. Toujours en attente. Je t'aime quand même.")
        Floline("PILL PILL PILLLL!")
    )

    /** Pas de petit nom, pas d'emoji, pas de numéro. Le changement de registre EST le message. */
    private val SERIEUX = listOf(
        FloLine("Florie, ta médication",
            "La dose d'aujourd'hui a plus de deux heures de retard. Prends-la dès que tu peux"),
        FloLine("${Her.realName}, c'est important",
            "La dose d'aujourd'hui n'est toujours pas enregistrée. Note-la dans l'application une fois que tu l'as prise."),
        FloLine("Médication non enregistrée",
            "${Her.realName}, la dose d'aujourd'hui est très en retard. Si tu l'as déjà prise, note-la pour arrêter les rappels."),
        FloLine("Deux heures de retard",
            "Pas de plaisanterie cette fois, ${Her.realName}. Prends la dose d'aujourd'hui, ou note-la si c'est déjà fait."),
        FloLine("${Her.realName}",
            "Toujours rien d'enregistré aujourd'hui. Si tu sautes la dose volontairement, c'est ton choix — note-la pour pas perdre ton streak")
    )

    private val PRIS = listOf(
        "Good job Floflo 🐉✨",
        "C'est noté, une autre belle journée",
        "${Her.dragon} est satisfaite",
        "💊 fier de toi",
        "Flozilla : 1 — L'oubli : 0",
        "Voilà ma pref 🥰 ",
        "🥰 C'est tu du parfum Lacoste pour femme que je sens?",
        "Dragon et Simon content!",
        "Flooo let's gooo (boit de l'eauuu!😘)"
        "check la aller💃"
        "🤗 Une autre chose de faite sur la To-Do list"
        "Tu mérite un bisoux💋"
        "Trop facile pour toi!"
        "👅 MIAM!"
        "J'espère que tu passe une belle journée"
        "Une autre, mais non la moindre"
        "une pillule et du pickleball?"
        "Let'sss goo"
        "Célèbre, c'est faite!"
        "Ok, c'est bon tu peux partir 🙄"
        "🌸 une fleur pour toi"
        "Je te donne une crème glacé virtuelle enjoy🤭🍦"
        "🤗 c'est tout"
        "10 minutes de réseau sociaux c'est cadeau 🎁"
        "Téléphone a porter de main, dit allo mammy de ma part"

    
    )

    private val RETOUR = FloLine(
        "Flo, ta pas oublier quelque chose?",
        "On n'en fait pas un drame. Aujourd'hui est un nouveau jour. 🐉",
        "Je sert a quoi moi au juste 😔",
        "${Her.dragon} à dormi toute seule"
        "Il a fait froid cette nuit sans toi 😴"
        "Mmhhh j'ai oublié quelque chose mais je sais pas quoi, indice : 💊"
        "🤔 Messemble qu'un dragon rose essaye de me rappeler quelque chose.."
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
            streak <= 1 -> "Streak uno, let's go FLOOoo! (Je touche du bois que l'app marche 😅) "
            streak = 2 -> "$streak jours, c'est bon signe tu est revenu!"
            streak = 3 -> "jamais deux sens toi 😊"
            streak = 4 -> "Jour 4 je veux une kitkat"
            streak = 5 -> "$streak jours, jour pour jour"
            streak = 6 -> "$streak jours, vélo jusqu'a la crémerie la plus proche? 😙🍦😛"
            streak = 7 -> "UNE SEMAINE! ça passe vite"
            streak = 8 -> "$streak jours, 🤘 STREAK ON! STEAK ONN! STREAKK ONnnn🎸"
            streak = 9 -> "$streak jours, j'espère que tu passe la meilleurs journée!"
            steak = 10 -> " Tu as gagné un coupon pour 10 bisoux gratuis 😘"
            streak = 11 -> "$streak jours,j'aime bien procrastiner mon rapport de stage 🤭"
            streak = 12 -> "$streak jours, salut flo du future! Est-ce que tu est rend chef de quart 🤔"
            streak = 13 -> "$streak jours, recommande moi un de tes albums préf 💽"
            streak = 14 -> "14 jours youpi, j'espère qu'on a eu de belle date depuis que j'écris ca haha"
            streak = 15 -> "$streak jours, je t'aime toujours"
            streak = 16 -> "$streak jours, raconte moi ta journée 🤗🍿"
            streak = 17 -> "Jour 17 des cacahuète? 😆 jsp quoi écrire"
            streak = 18 -> "dix huîtres jours, faudrait aller manger des huîtres😋"
            streak = 19 -> "$streak jours, on est du pour aller cueillir des nouvelles fleurs 🌸💮💘"
            streak = 20 -> "$streak jours,dit moi le mot avocat sans contexte hihi"
            streak = 21 -> "$streak jours,Je tiens le compte floski mais mettre des emoji c'est long😭"
            streak = 22 -> "$streak jours,l'été s'achève! est-ce qu'on a fait du bateau🥺"
            streak = 23 -> "LIFE IS A HIGHWAYyy I WANT TO RIDE IT ALL NIGHT LONggg, ah euhh 23 jour youpiii"
            streak = 24 -> "$streak jours, perd pas ton streak demain c'est un bon 💋"
            streak = 25 -> "25 jours = 25 sushis date 🍣😏"
            streak = 26 -> "$streak jours, pickles ball?!"
            streak = 27 -> "Jour 27 ta des trous dans tes bobettes🎜🎝🎜"
            streak = 28 -> "$streak jours, est-ce que je t'est déja dit que tu as des beau yeux?👀"
            streak = 29 -> "$streak jours, Pis McGill pas trop pire ?!"
            streak = 30 -> "$streak jours! c'est pas 5 ans de dualingo mais quand même! Fini les messages custom😅, je t'aime fort 😍"
            streak = 31-> "$streak jours! 👏 fini les message custom 😔"
            streak = 32-> "$streak jours..👏 dernier message custom pour de vrai😔"
            else -> "$streak jours!"
           
        }
        return FloLine(title, body)
    }

    fun comeback() = RETOUR
}
