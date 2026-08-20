package com.example.medtap.ui

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le ciel de l'app : ce qu'il fait dehors, calculé plutôt que deviné.
 *
 * Tout ici est une fonction pure du temps. Aucun réseau, aucune permission, aucun état —
 * ce qui veut dire que le lever du soleil, la phase de la lune et la première neige se
 * testent sur la JVM comme le reste, et qu'un ciel faux se voit dans un test plutôt qu'un
 * soir de novembre.
 *
 * La météo est SEMÉE et non mesurée. Il y a eu une version qui interrogeait un service en
 * ligne pour savoir s'il pleuvait vraiment à Laval ; elle marchait, et elle a été retirée.
 * Une app de médication qui n'avait besoin d'aucune connexion en gagnait une, pour de la
 * décoration, et la pluie tombait quand même faux la moitié du temps faute de réseau. Un
 * tirage semé sur la journée donne un mois d'avril pluvieux, un janvier neigeux et un
 * octobre roux — ce qui est tout ce qu'on demandait — sans rien devoir à personne.
 */
object Sky {

    /** Laval. La longitude est comptée POSITIVE VERS L'OUEST, comme l'exige la formule. */
    const val LAT = 45.57
    const val LON_WEST = 73.75

    private const val J2000 = 2451545.0
    private const val DAY_MS = 86_400_000L

    private const val CYCLE_DAYS = 28
    private val CYCLE_ANCHOR = dayIndexOf(2026, 7, 30)

    enum class Phase { NIGHT, DAWN, DAY, DUSK }

    enum class Season { WINTER, SPRING, SUMMER, AUTUMN }

    /** Ce qui tombe du ciel. Les feuilles en font partie : elles tombent aussi. */
    enum class Falling { NONE, RAIN, SNOW, LEAVES }

    data class Moment(
        val phase: Phase,
        /** 0 à 1 À L'INTÉRIEUR de la phase : c'est lui qui fait glisser les couleurs. */
        val blend: Float,
        /**
         * La course du soleil : 0 au lever, 1 au coucher, et il SORT de cet intervalle.
         *
         * C'est ce débordement qui rend la transition continue. Avant, l'aube et le
         * couchant étaient des paliers à part où l'astre restait planté au bord de
         * l'écran ; ici le soleil monte depuis sous l'horizon, le traverse au lever, et
         * repasse dessous au couchant sans qu'aucune étape ne saute.
         */
        val sunT: Float,
        /** La même course pour la lune, du coucher au lever suivant. */
        val moonT: Float,
        /** 0 en plein jour, 1 au cœur de la nuit. Décide de tout le contraste. */
        val dark: Float,
        /** Le numéro du jour, pour semer ce qui doit rester stable toute la journée. */
        val day: Long,
        /** 0 = nouvelle lune, 0,5 = pleine lune. */
        val moon: Float,
        val season: Season,
        val falling: Falling,
        /** L'intensité de ce qui tombe, 0 à 1. */
        val fall: Float,
        val aurora: Float,
        val rainbow: Boolean,
        val shootingStar: Float
    ) {
        val night: Boolean get() = phase == Phase.NIGHT
    }

    // ---- le temps ----------------------------------------------------------

    /** Le numéro du jour local, un entier qui ne change qu'à minuit. */
    fun dayIndex(now: Long): Long = floor(
        (now + TimeZone.getDefault().getOffset(now)) / DAY_MS.toDouble()
    ).toLong()

    private fun dayIndexOf(year: Int, month: Int, day: Int): Long {
        val c = Calendar.getInstance().apply { clear(); set(year, month - 1, day, 12, 0) }
        return dayIndex(c.timeInMillis)
    }

    private fun cal(now: Long) = Calendar.getInstance().apply { timeInMillis = now }

    private fun julian(millis: Long): Double = millis / 86_400_000.0 + 2440587.5

    private fun millisOf(jd: Double): Long = ((jd - 2440587.5) * 86_400_000.0).toLong()

    // ---- le soleil ---------------------------------------------------------

    /**
     * Lever et coucher du soleil, en millisecondes, pour la journée qui contient [now].
     *
     * Une heure fixe — « 6 h » — serait fausse la moitié de l'année : à Laval, le soleil se
     * lève à 4 h 10 en juin et à 7 h 20 en décembre. Un ciel qui se trompe de trois heures
     * est pire qu'un ciel immobile.
     */
    fun sunTimes(now: Long): Pair<Long, Long>? {
        val jd = julian(now)
        val n = Math.round(jd - J2000 - 0.0009 - LON_WEST / 360.0).toDouble()
        val jStar = J2000 + 0.0009 + LON_WEST / 360.0 + n
        val mDeg = (357.5291 + 0.98560028 * (jStar - J2000)) % 360.0
        val m = Math.toRadians(mDeg)
        val c = 1.9148 * sin(m) + 0.0200 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lambda = Math.toRadians((mDeg + c + 180.0 + 102.9372) % 360.0)
        val transit = jStar + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)

        val decl = asin(sin(lambda) * sin(Math.toRadians(23.44)))
        val lat = Math.toRadians(LAT)
        val cosOmega =
            (sin(Math.toRadians(-0.833)) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl))
        if (cosOmega < -1.0 || cosOmega > 1.0) return null   // jour ou nuit polaire

        val omega = Math.toDegrees(acos(cosOmega)) / 360.0
        return millisOf(transit - omega) to millisOf(transit + omega)
    }

    // ---- la lune -----------------------------------------------------------

    /**
     * La phase de la lune, 0 à 1 : 0 nouvelle, 0,5 pleine. Comptée depuis une nouvelle lune
     * connue et le mois synodique moyen — à une heure près sur des décennies.
     */
    fun moonPhase(now: Long): Float {
        val since = julian(now) - 2451550.26
        val p = (since / 29.530588853) % 1.0
        return (if (p < 0) p + 1.0 else p).toFloat()
    }

    // ---- les saisons -------------------------------------------------------

    /**
     * La saison au Québec, aux dates où elle se voit dehors plutôt qu'aux équinoxes
     * astronomiques : ici la neige tient jusqu'en mars et les feuilles tournent avant le
     * 21 septembre.
     */
    fun season(now: Long): Season {
        val c = cal(now)
        val md = (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
        return when (md) {
            in 321..531 -> Season.SPRING
            in 601..915 -> Season.SUMMER
            in 916..1130 -> Season.AUTUMN
            else -> Season.WINTER          // 1er décembre au 20 mars
        }
    }

    /**
     * Ce qui tombe aujourd'hui, tiré au sort une fois pour la journée.
     *
     * Les proportions sont celles du sud du Québec : un jour sur deux avec quelque chose en
     * novembre, un ciel d'été le plus souvent dégagé. L'automne rend des feuilles par
     * défaut — elles tombent tous les jours, c'est la saison — et la pluie les remplace les
     * jours où il pleut.
     */
    fun falling(now: Long): Pair<Falling, Float> {
        val rng = Random(dayIndex(now) * 977 + 13)
        val roll = rng.nextFloat()
        val strength = 0.35f + rng.nextFloat() * 0.65f
        return when (season(now)) {
            Season.WINTER -> if (roll < 0.45f) Falling.SNOW to strength else Falling.NONE to 0f
            Season.SPRING -> if (roll < 0.40f) Falling.RAIN to strength else Falling.NONE to 0f
            Season.SUMMER -> if (roll < 0.22f) Falling.RAIN to strength else Falling.NONE to 0f
            Season.AUTUMN ->
                if (roll < 0.35f) Falling.RAIN to strength
                else Falling.LEAVES to (0.4f + rng.nextFloat() * 0.6f)
        }
    }

    // ---- ce qui n'arrive pas souvent ---------------------------------------

    /**
     * Une aurore, quelques nuits par cycle.
     *
     * Rien à l'écran n'en dit la raison, et c'est délibéré : ce n'est pas un suivi, ce
     * n'est pas un rappel, ce n'est pas une remarque. C'est du vert et du violet dans le
     * ciel de l'app pendant les jours où ça peut faire plaisir, et personne n'a à le
     * commenter — surtout pas une application.
     */
    fun aurora(now: Long): Float {
        val day = Math.floorMod(dayIndex(now) - CYCLE_ANCHOR, CYCLE_DAYS.toLong()).toInt()
        return when (day) {
            0 -> 0.55f
            1, 2 -> 1f
            3 -> 0.7f
            4 -> 0.35f
            else -> 0f
        }
    }

    /**
     * L'étoile filante : une nuit sur sept environ, et seulement vingt minutes. Le fait
     * qu'elle dure si peu est tout l'intérêt — une étoile filante permanente est une
     * décoration, une qu'on attrape est un petit événement.
     */
    fun shootingStar(now: Long): Float {
        val day = dayIndex(now)
        val rng = Random(day * 31 + 5)
        if (rng.nextFloat() > 0.15f) return 0f
        val startMin = 20 * 60 + rng.nextInt(9 * 60)
        val raw = minuteOfDay(now)
        val minute = raw + if (raw < 6 * 60) 24 * 60 else 0
        val d = minute - startMin
        return if (d in 0..19) 1f - d / 20f else 0f
    }

    /** Un arc-en-ciel : souvent après la pluie, très rarement sans. Jamais sur la neige. */
    fun rainbow(now: Long, falling: Falling): Boolean {
        val rng = Random(dayIndex(now) * 17 + 3)
        return rng.nextFloat() < if (falling == Falling.RAIN) 0.55f else 0.04f
    }

    fun minuteOfDay(now: Long): Int =
        cal(now).let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    // ---- tout ensemble -----------------------------------------------------

    /** Une trois quarts d'heure de fondu de part et d'autre du lever et du coucher. */
    private const val TWILIGHT_MS = 45 * 60 * 1000L

    /**
     * L'état du ciel à un instant.
     *
     * [forced] permet à l'atelier de remplacer n'importe quelle pièce sans toucher au
     * calcul : le reste continue de venir de la date, donc régler la saison ne fige ni
     * l'heure ni la lune.
     */
    fun moment(now: Long = System.currentTimeMillis(), forced: Forced = Forced()): Moment {
        val times = sunTimes(now)
        var phase = Phase.NIGHT
        var blend = 0.5f
        var sunT = -0.5f
        var moonT = 0.5f

        if (times != null) {
            val (rise, set) = times

            // Une seule course continue, sans palier : le soleil est simplement SOUS
            // l'horizon avant le lever et après le coucher. C'est ce qui supprime le saut
            // qu'on voyait entre l'aube et le jour.
            sunT = (now - rise).toFloat() / (set - rise).toFloat()

            // La nuit enjambe minuit : du coucher d'un jour au lever du suivant.
            val from: Long
            val to: Long
            if (now >= set) {
                from = set
                to = sunTimes(now + DAY_MS)?.first ?: (set + 10 * 3600_000L)
            } else {
                from = sunTimes(now - DAY_MS)?.second ?: (rise - 10 * 3600_000L)
                to = rise
            }
            moonT = (now - from).toFloat() / (to - from).toFloat()

            phase = when {
                now in (rise - TWILIGHT_MS)..(rise + TWILIGHT_MS) -> Phase.DAWN
                now in (set - TWILIGHT_MS)..(set + TWILIGHT_MS) -> Phase.DUSK
                now in rise..set -> Phase.DAY
                else -> Phase.NIGHT
            }
            blend = when (phase) {
                Phase.DAWN -> (now - rise + TWILIGHT_MS).toFloat() / (2 * TWILIGHT_MS)
                Phase.DUSK -> (now - set + TWILIGHT_MS).toFloat() / (2 * TWILIGHT_MS)
                Phase.DAY -> sunT
                Phase.NIGHT -> moonT.coerceIn(0f, 1f)
            }
        }

        // La hauteur du soleil décide de tout le contraste, et elle est continue : le ciel
        // s'assombrit donc en fondu au lieu de basculer d'un palier à l'autre.
        val altitude = sin(sunT * Math.PI).toFloat()
        val dark = (1f - (altitude + 0.14f) / 0.34f).coerceIn(0f, 1f)

        val season = forced.season ?: season(now)
        val (fallKind, fallAmount) = forced.falling?.let { it to (forced.fall ?: 0.7f) }
            ?: falling(now)
        val duskOrNight = phase == Phase.NIGHT || phase == Phase.DUSK

        return Moment(
            phase = phase,
            blend = blend.coerceIn(0f, 1f),
            sunT = sunT,
            moonT = moonT,
            dark = dark,
            day = dayIndex(now),
            moon = forced.moon ?: moonPhase(now),
            season = season,
            falling = fallKind,
            fall = fallAmount,
            aurora = forced.aurora ?: if (duskOrNight) aurora(now) else 0f,
            rainbow = forced.rainbow ?: (phase != Phase.NIGHT && rainbow(now, fallKind)),
            shootingStar = forced.shootingStar
                ?: if (phase == Phase.NIGHT) shootingStar(now) else 0f
        )
    }

    /** Ce que l'atelier impose. Tout ce qui reste `null` continue d'être calculé. */
    data class Forced(
        val season: Season? = null,
        val falling: Falling? = null,
        val fall: Float? = null,
        val moon: Float? = null,
        val aurora: Float? = null,
        val rainbow: Boolean? = null,
        val shootingStar: Float? = null
    )
}
