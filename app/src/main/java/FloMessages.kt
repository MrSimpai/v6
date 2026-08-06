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

/**
 * [body] est optionnel : certaines lignes ne sont qu'un titre («🙄»), et une notification
 * avec un titre seul se lit très bien. Sans cette valeur par défaut, chaque ligne devrait
 * inventer une deuxième phrase pour rien.
 */
data class FloLine(val title: String, val body: String = "")

object FloMessages {

    /**
     * Avant l'heure, pas après. Tout le reste de l'échelle réagit à un retard ; ces
     * lignes-là arrivent quand il n'y a encore rien à se reprocher, donc elles sont
     * légères et ne demandent rien — juste un coup de coude.
     */
    private val AVANCE = listOf(
        FloLine("Dans un quart d'heure", "${Her.dragon} prépare la dose."),
        FloLine("Petit avertissement 🐉", "C'est bientôt l'heure. Aucune urgence."),
        FloLine("Ça s'en vient, ${Her.name}", "Quinze minutes. Le temps de finir ce que tu fais."),
        FloLine("Coucou", "Juste pour que tu saches : c'est presque l'heure."),
        FloLine("Bientôt 💊", "Si c'est plus simple maintenant, ça compte pareil."),
        FloLine("${Her.dragon} s'étire", "Elle se prépare. Toi aussi, tantôt.")
    )

    /** La ligne du rappel anticipé, semée sur le créneau pour ne pas changer à chaque pose. */
    fun early(slot: Long): FloLine = AVANCE[Random(slot / 60000).nextInt(AVANCE.size)]

    private val PONCTUEL = listOf(
        FloLine("C'est l'heure 💊", "${Her.dragon} a préparé ta petite dose, ${Her.name}."),
        FloLine("Psst ${Her.name}.", "C'est le moment. Une pilule, et je te laisse tranquille."),
        FloLine("Ding 🐉", "${Her.dragon} réclame son dû. Une pilule."),
        FloLine("Sup, Flomingo🦩", "Ta dose t'attend. Prends-la et je retourne dormir."),
        FloLine("Livraison d'une pilule", "Ceci est ton rappel officiel, dragon inclus."),
        FloLine("Rapport de mission", "Objectif : une pilule. Difficulté : facile. Récompense : un dragon content."),
        FloLine("C'est l'heure 🕐", "Allez Flozilla, on y va."),

        // Lignes écrites à la main en dessous :)
        FloLine("Floflosky, c'est pill time"),
        FloLine("toc toc", "Qui est là ? Ta médication. Pas compliqué lala."),
        FloLine("Pillule!! 🥰", "Bois de l'eau en même temps."),
        FloLine("Wow 🤭", "Tes cheveux sont vraiment beaux aujourd'hui. Ohh oui, ta pilule.")
    )

    private val RELANCE = listOf(
        FloLine("${Her.name} ?", "La pilule est toujours là. Je la regarde. Elle me regarde."),
        FloLine("J'attends, Floflosky", "J'ai compté les tuiles du plafond. Deux fois."),
        FloLine("Grande patience 🐉", "Grande, mais pas infinie."),
        FloLine("hey. hey ${Her.name}. hey.", "La. Pilule."),
        FloLine("🙄"),
        FloLine("Je sais, ${Her.name}", "Tu as vu l'autre notification. Et je sais que tu sais."),
        FloLine("Rappel gentil 🐉", "Gentil pour l'instant."),
        FloLine("Vingt minutes ⏳", "Je dis ça comme ça. Je ne juge pas. Un peu."),

        // Ligne écrite à la main en dessous :)
        FloLine("Yoooo, c'est encore moi!", "PILL TIME 💊")
    )

    private val BOUDERIE = listOf(
        FloLine("Aucune pression", "Je reste ici. À être un dragon. À attendre. Indéfiniment."),
        FloLine("C'est correct.", "Tout va bien. Je vais bien. La pilule va bien, toute seule, là-bas."),
        FloLine("Je ne suis pas fâchée", "Juste un petit dragon rose avec des sentiments et un calendrier."),
        FloLine("Une demi-heure, Floflosky", "J'ai commencé à raconter ma propre vie à voix haute, pour passer le temps."),
        FloLine("Cool. Très cool.", "C'est tout à fait normal de laisser un dragon attendre comme ça 💔"),
        FloLine("${Her.name}. ${Her.realName}. Flobert.", "J'invente des noms jusqu'à ce que la dose soit notée."),
        FloLine("🐉 phase méchante", "Prends la pilule et je redeviens adorable."),
        FloLine("J'attends toujours", "Comme un phare. Un phare rose. Et déçu.")
    )

    private val DRAME = listOf(
        FloLine("Ça ne marche pas 🐉", "Je plaisante, je n'arrêterai jamais. Prends ta pilule, ${Her.name}."),
        FloLine("Une heure, ${Her.name}.", "Je l'ai dit aux autres dragons. Ils sont déçus eux aussi."),
        FloLine("J'ai réfléchi 🐉", "À toi. Qui ne prends pas ta pilule. Pendant soixante longues minutes."),
        FloLine("🐉💔", "Un bouton et c'est réglé. Un seul. C'est tout ce que je demande."),
        FloLine("Dommages émotionnels", "Flo, j'ai un trou en forme de pilule dans le cœur."),
        FloLine("Pas dramatique du tout", "J'ai composé une ballade. Elle parle de toi. Elle est triste."),
        FloLine("Soixante minutes 😔", "Floflosky, j'aurais eu le temps de faire une salade de pâtes."),
        FloLine("Toujours là 🐉", "Toujours rose. Toujours en attente. Je t'aime quand même."),
        FloLine("PILL PILL PILLLL!")
    )

    /** Pas de petit nom, pas d'emoji, pas de numéro. Le changement de registre EST le message. */
    private val SERIEUX = listOf(
        FloLine("${Her.realName}",
            "Ta dose a plus de deux heures de retard. Prends-la dès que tu peux."),
        FloLine("C'est important",
            "${Her.realName}, ta dose n'est pas enregistrée. Note-la une fois prise."),
        FloLine("Dose non enregistrée",
            "${Her.realName}, elle est très en retard. Si tu l'as prise, note-la."),
        FloLine("Deux heures de retard",
            "Pas de plaisanterie, ${Her.realName}. Prends ta dose, ou note-la."),
        FloLine("Toujours rien aujourd'hui",
            "${Her.realName}, si tu sautes volontairement, note-la dans l'app.")
    )

    private val PRIS = listOf(
        "Good job Floflo 🐉✨",
        "C'est noté, une autre belle journée",
        "${Her.dragon} est satisfaite",
        "💊 fier de toi",
        "Flozilla : 1 — L'oubli : 0",
        "Voilà ma pref 🥰",
        "🥰 C'est tu du parfum Lacoste pour femme que je sens?",
        "Dragon et Simon contents!",
        "Flooo let's gooo (bois de l'eauuu!😘)",
        "check la aller💃",
        "🤗 Une autre chose de faite sur la To-Do list",
        "Tu mérites un bisou💋",
        "Trop facile pour toi!",
        "👅 MIAM!",
        "J'espère que tu passes une belle journée",
        "Une autre, et non la moindre",
        "une pillule et du pickleball?",
        "Let'sss goo",
        "Célèbre, c'est faite!",
        "Ok, c'est bon tu peux partir 🙄",
        "🌸 une fleur pour toi",
        "Je te donne une crème glacée virtuelle, enjoy🤭🍦",
        "🤗 c'est tout",
        "10 minutes de réseaux sociaux c'est cadeau 🎁",
        "Téléphone à portée de main, dis allo mammy de ma part"
    )

    /** Le lendemain d'une dose manquée. Léger, jamais culpabilisant. */
    private val RETOUR = listOf(
        FloLine("T'as rien oublié? 🐉",
            "On n'en fait pas un drame. Aujourd'hui est un nouveau jour."),
        FloLine("Je sers à quoi 😔", "Moi, au juste."),
        FloLine("Nuit solitaire 😴", "${Her.dragon} a dormi toute seule."),
        FloLine("Il a fait froid 😴", "Cette nuit, sans toi."),
        FloLine("Mmhhh, j'ai oublié…", "Quelque chose, mais je sais pas quoi. Indice : 💊"),
        FloLine("🤔 Me semble que…", "Un dragon rose essaye de me rappeler quelque chose.")
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
        val body = if (tier == Tier.SERIEUX && picked.body.isNotBlank())
            "$medName — ${picked.body}" else picked.body
        return tier to FloLine(picked.title, body)
    }

    /**
     * La confirmation d'UNE dose, quand il en reste d'autres pour la journée. Court
     * exprès : les lignes écrites à la main sont réservées à la journée complète, sinon
     * elles se feraient brûler par un médicament pris trois fois par jour.
     */
    fun celebration(streak: Int, seed: Long): FloLine {
        val title = PRIS[Random(seed).nextInt(PRIS.size)]
        val body = when {
            streak <= 1 -> "Enregistré."
            streak < 7  -> "$streak jours de suite pour celui-là."
            streak < 30 -> "Série de $streak jours pour celui-là."
            else        -> "$streak jours pour celui-là. Impressionnant."
        }
        return FloLine(title, body)
    }

    /**
     * La journée complète : l'écran plein si elle est dans l'app, la notification sinon.
     *
     * C'est ICI que vivent les lignes écrites à la main, une par jour. Elles se lisent
     * comme un journal, donc elles appartiennent au compteur de journées complètes et pas
     * à celui d'un médicament en particulier -- « jour 17 des cacahuètes » n'a aucun sens
     * si le chiffre remonte trois fois par jour.
     */
    fun dayStreakLine(days: Int): String = when (days) {
        in Int.MIN_VALUE..1 -> "Streak uno, let's go FLOOoo! (Je touche du bois que l'app marche 😅)"
        2  -> "$days jours, c'est bon signe tu es revenue!"
        3  -> "jamais deux sans toi 😊"
        4  -> "Jour 4, je veux une kitkat"
        5  -> "$days jours, jour pour jour"
        6  -> "$days jours, vélo jusqu'à la crémerie la plus proche? 😙🍦😛"
        7  -> "UNE SEMAINE! ça passe vite"
        8  -> "$days jours, 🤘 STREAK ON! STEAK ONN! STREAKK ONnnn🎸"
        9  -> "$days jours, j'espère que tu passes la meilleure journée!"
        10 -> "Tu as gagné un coupon pour 10 bisous gratuits 😘"
        11 -> "$days jours, j'aime bien procrastiner mon rapport de stage 🤭"
        12 -> "$days jours, salut Flo du futur! Est-ce que tu es rendue chef de quart 🤔"
        13 -> "$days jours, recommande-moi un de tes albums préf 💽"
        14 -> "14 jours youpi, j'espère qu'on a eu de belles dates depuis que j'écris ça haha"
        15 -> "$days jours, je t'aime toujours"
        16 -> "$days jours, raconte-moi ta journée 🤗🍿"
        17 -> "Jour 17, des cacahuètes? 😆 jsp quoi écrire"
        18 -> "dix huîtres jours, faudrait aller manger des huîtres😋"
        19 -> "$days jours, on est dus pour aller cueillir des nouvelles fleurs 🌸💮💘"
        20 -> "$days jours, dis-moi le mot avocat sans contexte hihi"
        21 -> "$days jours, je tiens le compte Floski mais mettre des emoji c'est long😭"
        22 -> "$days jours, l'été s'achève! est-ce qu'on a fait du bateau🥺"
        23 -> "LIFE IS A HIGHWAYyy I WANT TO RIDE IT ALL NIGHT LONggg, ah euhh 23 jours youpiii"
        24 -> "$days jours, perds pas ta série demain c'est un bon 💋"
        25 -> "25 jours = 25 sushis date 🍣😏"
        26 -> "$days jours, pickles ball?!"
        27 -> "Jour 27, t'as des trous dans tes bobettes🎜🎝🎜"
        28 -> "$days jours, est-ce que je t'ai déjà dit que tu as de beaux yeux?👀"
        29 -> "$days jours, pis McGill pas trop pire?!"
        30 -> "$days jours! c'est pas 5 ans de Duolingo mais quand même! Fini les messages custom😅, je t'aime fort 😍"
        31 -> "$days jours! 👏 fini les messages custom 😔"
        32 -> "$days jours.. 👏 dernier message custom pour de vrai😔"
        else -> "$days jours!"
    }

    /**
     * Le dernier appel du soir, avec le compte à rebours vers minuit. Écrit pour être lu
     * en diagonale : ce qui reste à prendre, et ce qu'on perd si on ne le fait pas.
     */
    fun lastCall(dayStreak: Int, remaining: List<String>): FloLine {
        val what = remaining.joinToString(", ")
        return if (dayStreak > 0)
            FloLine(
                "$dayStreak jours en jeu 🐉",
                "$what — jusqu'à minuit. ${Her.dragon} croise les griffes."
            )
        else
            FloLine(
                "Pas encore fini 🐉",
                "$what — il te reste jusqu'à minuit, ${Her.name}."
            )
    }

    /**
     * Ce que dit le dragon selon son humeur. Sortie ici pour que l'app et le widget ne
     * puissent pas afficher deux phrases différentes au même moment — c'est le genre de
     * décalage qui fait douter de tout le reste.
     */
    fun moodLine(mood: Mood): String = when (mood) {
        Mood.Cheering -> "Merci. ${Her.dragon} est contente."
        Mood.Overdue  -> "Prends ta dose d'aujourd'hui, s'il te plaît."
        Mood.Sad      -> "${Her.dragon} attend depuis un moment."
        Mood.Waiting  -> "Une dose t'attend."
        Mood.Sleeping -> "Rien de prévu. ${Her.dragon} fait la sieste."
    }

    /**
     * La même chose, en trois mots, pour la tuile de l'écran d'accueil.
     *
     * [moodLine] est écrite pour une phrase posée sous le dragon dans l'app ; sur un
     * carré de deux cases elle se réduit à une taille illisible ou se fait couper. Une
     * ligne séparée coûte moins cher qu'un widget qu'on n'arrive pas à lire.
     */
    fun widgetLine(mood: Mood): String = when (mood) {
        Mood.Cheering -> "C'est noté ✨"
        Mood.Overdue  -> "Ta dose t'attend"
        Mood.Sad      -> "${Her.dragon} attend…"
        Mood.Waiting  -> "C'est l'heure 💊"
        Mood.Sleeping -> "Rien de prévu 😴"
    }


    fun comeback(seed: Long = System.currentTimeMillis()): FloLine =
        RETOUR[Random(seed).nextInt(RETOUR.size)]
}
