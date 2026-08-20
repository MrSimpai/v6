package com.example.medtap.ui

import androidx.compose.runtime.mutableStateOf

/**
 * L'atelier du ciel : de quoi voir en une minute ce que l'app met une année à montrer.
 *
 * Le ciel dépend de la date, de l'heure, de la saison et de la lune. Tout vérifier « pour
 * de vrai » demanderait d'attendre douze mois, dont une nuit d'aurore et un matin de
 * première neige. L'atelier remplace l'horloge par une manette : on fait défiler une année
 * en trois minutes, et on voit les fondus au lieu de les imaginer.
 *
 * Il vit derrière le même mot que la cabine d'essayage. Rien ici ne touche aux
 * médicaments, aux rappels ni à la série — l'atelier ne déplace que le ciel, jamais les
 * données. On peut donc y jouer sans conséquence, ce qui est exactement ce qu'on demande
 * à un banc d'essai.
 */
object SkyLab {

    /** Ouvert par le mot du casier. Se referme au redémarrage : c'est un jouet, pas un réglage. */
    val unlocked = mutableStateOf(false)

    /** Vrai quand l'atelier tient l'horloge. Faux, et l'app retrouve l'heure qu'il est. */
    val active = mutableStateOf(false)

    /** L'instant que voit toute l'app quand [active]. */
    val instant = mutableStateOf(System.currentTimeMillis())

    /** Combien de temps s'écoule par seconde réelle. 0 = arrêté. */
    val speed = mutableStateOf(Speed.STOPPED)

    val forced = mutableStateOf(Sky.Forced())

    /**
     * Les vitesses utiles, et rien d'autre.
     *
     * [HOURS] fait passer une journée en une minute : c'est le rythme auquel on regarde
     * la nuit devenir aube puis midi puis couchant. [DAYS] fait un jour par seconde, soit
     * une année en six minutes : c'est celui auquel on regarde les saisons tourner.
     */
    enum class Speed(val label: String, val perSecond: Long) {
        STOPPED("Arrêt", 0),
        MINUTES("Minutes", 60 * 1000L * 20),          // 20 min par seconde
        HOURS("Heures", 60 * 60 * 1000L * 24 / 60),   // une journée en une minute
        DAYS("Jours", 86_400_000L)                    // un jour par seconde
    }

    /** Reprend l'heure vraie et relâche tout ce qui était imposé. */
    fun reset() {
        active.value = false
        speed.value = Speed.STOPPED
        instant.value = System.currentTimeMillis()
        forced.value = Sky.Forced()
    }

    /**
     * Avancer ou reculer de journées ENTIÈRES, au sens du calendrier.
     *
     * Surtout pas `+ jours * 86 400 000` : au Québec, deux journées de l'année ne font pas
     * vingt-quatre heures. En traversant le deuxième dimanche de mars ou le premier de
     * novembre, l'arithmétique en millisecondes décale l'heure affichée d'une heure et ne
     * la rend jamais — si bien qu'en faisant défiler une année, le soleil se lèverait de
     * plus en plus tôt sans raison. `Calendar` conserve l'heure murale et absorbe le
     * changement d'heure, ce qui est exactement ce qu'on veut regarder.
     */
    fun nudgeDays(days: Int) {
        instant.value = java.util.Calendar.getInstance().apply {
            timeInMillis = instant.value
            add(java.util.Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
        active.value = true
    }

    fun setMinuteOfDay(minute: Int) {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = instant.value
            set(java.util.Calendar.HOUR_OF_DAY, minute / 60)
            set(java.util.Calendar.MINUTE, minute % 60)
            set(java.util.Calendar.SECOND, 0)
        }
        instant.value = cal.timeInMillis
        active.value = true
    }

    /** Le premier jour d'une saison, pour y sauter sans chercher la date. */
    fun jumpTo(season: Sky.Season) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = instant.value }
        val year = cal.get(java.util.Calendar.YEAR)
        val (month, day) = when (season) {
            Sky.Season.WINTER -> 12 to 15
            Sky.Season.SPRING -> 4 to 15
            Sky.Season.SUMMER -> 7 to 15
            Sky.Season.AUTUMN -> 10 to 10
        }
        cal.set(year, month - 1, day)
        instant.value = cal.timeInMillis
        active.value = true
    }
}
