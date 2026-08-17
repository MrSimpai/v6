package com.example.medtap.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * One medication. The key is normally the UID of the NFC tag stuck to its bottle, but a
 * medication can also live without a tag at all -- its key is then a locally generated
 * "MANUAL-..." id and the dose gets logged with a button instead of a scan. Everything
 * downstream (slots, reminders, charts, the dragon) works identically either way, so
 * the app is fully usable, and testable, before a single sticker exists.
 */
@Entity
data class Medication(
    @PrimaryKey val tagId: String,      // uppercase hex UID, or "MANUAL-<millis>"
    val name: String,
    val doseText: String,               // "1 comprimé" / "20 mg"
    val hourOfDay: Int,
    val minute: Int,
    val nagEveryMinutes: Int = 10,      // how often the reminder re-announces itself
    val active: Boolean = true,
    /**
     * Les sept plages de la semaine, lundi d'abord : `"début-fin,début-fin,…"` en minutes
     * depuis minuit. Vide veut dire « la même heure tous les jours, sans plage », c'est-à-
     * dire exactement [hourOfDay]:[minute] — le comportement d'avant, pour tout le monde
     * qui existait avant ce champ.
     *
     * Un seul champ texte plutôt que sept colonnes, ou pire une table : ces sept valeurs
     * ne sont jamais interrogées séparément, jamais triées, jamais jointes. Elles ne sont
     * lues qu'ensemble et par le même code. Une table à part coûterait une migration, une
     * requête et un risque d'orphelins pour ranger ce qui tient en trente caractères.
     *
     * [hourOfDay] et [minute] restent la plus matinale des sept — c'est ce qui trie la
     * liste, et c'est le repli si jamais la chaîne devient illisible.
     */
    val schedule: String = "",
    /**
     * Le jour où ce médicament est entré dans l'app.
     *
     * Sans lui, la série exigeait une dose de CHAQUE médicament actif pour CHAQUE jour
     * passé — y compris les jours d'avant sa création, où il n'y avait évidemment rien à
     * prendre. Ajouter un deuxième médicament remettait donc la série à un et vidait la
     * semaine d'un coup. Personne ne pouvait deviner pourquoi.
     *
     * Les lignes existantes reçoivent 0 à la migration, c'est-à-dire « a toujours
     * existé » : le comportement des médicaments déjà là ne change pas d'un iota.
     */
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MANUAL_PREFIX = "MANUAL-"

        /** A key for a medication with no sticker behind it. */
        fun manualId(): String = MANUAL_PREFIX + System.currentTimeMillis()
    }
}

/** True when this medication has no NFC tag and is logged by hand. */
val Medication.isManual: Boolean get() = tagId.startsWith(Medication.MANUAL_PREFIX)

/**
 * La semaine, toujours dans le même sens : lundi en tête, dimanche en queue.
 *
 * `Calendar.DAY_OF_WEEK` vaut 1 pour dimanche, ce qui met dimanche à gauche de tout ce
 * qu'on affiche. La conversion vivait déjà en double dans `weekStatus` et dans `WeekDots` ;
 * maintenant que les créneaux eux-mêmes en dépendent, un seul endroit doit la connaître.
 */
object Week {
    const val DAYS = 7

    /** 0 = lundi … 6 = dimanche, à partir d'un `Calendar.DAY_OF_WEEK`. */
    fun index(dayOfWeek: Int): Int = (dayOfWeek + 5) % 7

    val LETTERS = listOf("L", "M", "M", "J", "V", "S", "D")
    val NAMES = listOf(
        "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"
    )
}

/**
 * Une journée de la semaine : l'heure du rappel, et jusqu'à quand la dose reste « à
 * l'heure ».
 *
 * La plage existe parce que l'heure du lever n'est pas la même tous les jours. Un rappel
 * à 7h qui escalade dès 7h10 engueule quelqu'un qui dort encore, et un dragon qui a tort
 * trois matins par semaine est un dragon qu'on finit par couper. Avec une plage 7h→10h,
 * le mot part quand même à 7h — il sera là au réveil — mais rien ne monte d'un cran avant
 * 10h, l'heure à laquelle elle est debout de toute façon.
 *
 * [endMinute] égal (ou inférieur) à [startMinute] veut dire « pas de plage » : l'escalade
 * démarre à l'heure pile, comme avant.
 */
data class DayWindow(val startMinute: Int, val endMinute: Int) {
    /** Vraie quand il n'y a aucune plage : le retard commence à la minute même. */
    val instant: Boolean get() = endMinute <= startMinute
}

/**
 * Les sept plages, toujours sept, quoi qu'il y ait dans [Medication.schedule].
 *
 * Toute chaîne qui n'a pas exactement la forme attendue retombe sur [Medication.hourOfDay]
 * — une base à moitié écrite ou un fichier de sauvegarde bricolé à la main ne doit pas
 * faire disparaître les rappels, il doit faire revenir l'ancien comportement.
 */
fun Medication.windows(): List<DayWindow> = List(Week.DAYS) { windowOn(it) }

/** La plage du jour [dayIndex], 0 = lundi. */
fun Medication.windowOn(dayIndex: Int): DayWindow {
    val fallback = (hourOfDay * 60 + minute).let { DayWindow(it, it) }
    if (schedule.isBlank()) return fallback
    val parts = schedule.split(',')
    if (parts.size != Week.DAYS) return fallback
    val token = parts[dayIndex.coerceIn(0, Week.DAYS - 1)]
    val dash = token.indexOf('-')
    if (dash <= 0) return fallback
    val start = token.substring(0, dash).trim().toIntOrNull() ?: return fallback
    val end = token.substring(dash + 1).trim().toIntOrNull() ?: return fallback
    if (start !in 0..1439 || end !in 0..1439) return fallback
    return DayWindow(start, end)
}

/** Vraie quand les sept jours ont exactement la même plage : le cas de presque tout le monde. */
val Medication.uniformWeek: Boolean get() = windows().distinct().size == 1

/**
 * Le même médicament avec ces sept plages.
 *
 * [Medication.hourOfDay] suit la plus matinale des sept. Ce n'est plus l'heure du rappel —
 * c'est [Slots] qui la connaît maintenant — mais c'est encore ce qui trie la liste, et
 * surtout c'est le repli le jour où la chaîne ne se relit pas.
 */
fun Medication.withWindows(week: List<DayWindow>): Medication {
    if (week.size != Week.DAYS) return this
    val earliest = week.minOf { it.startMinute }
    return copy(
        hourOfDay = earliest / 60,
        minute = earliest % 60,
        schedule = week.joinToString(",") { "${it.startMinute}-${it.endMinute}" }
    )
}

/** A dose that was actually taken. scheduledFor identifies which slot it satisfies. */
@Entity(
    foreignKeys = [ForeignKey(
        entity = Medication::class,
        parentColumns = ["tagId"], childColumns = ["tagId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["tagId", "scheduledFor"], unique = true)]
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tagId: String,
    val scheduledFor: Long,             // epoch millis of the slot
    val takenAt: Long,                  // epoch millis of the scan
    /** Vraie quand la dose a été volontairement sautée plutôt que prise. */
    val skipped: Boolean = false
)

/** Minutes late (negative = early). An extension, so Room doesn't try to persist it. */
val DoseLog.driftMinutes: Int get() = ((takenAt - scheduledFor) / 60_000L).toInt()

@Dao
interface MedDao {
    @Query("SELECT * FROM Medication WHERE active = 1 ORDER BY hourOfDay, minute")
    fun activeMeds(): Flow<List<Medication>>

    @Query("SELECT * FROM Medication WHERE active = 1")
    suspend fun activeMedsOnce(): List<Medication>

    @Query("SELECT * FROM Medication WHERE tagId = :tagId")
    suspend fun med(tagId: String): Medication?

    @Upsert suspend fun upsert(med: Medication)

    @Query("SELECT * FROM DoseLog WHERE takenAt >= :since ORDER BY takenAt DESC")
    fun logsSince(since: Long): Flow<List<DoseLog>>

    @Query("SELECT * FROM DoseLog")
    suspend fun allLogs(): List<DoseLog>

    /**
     * La dose de ce médicament pour CE JOUR-LÀ, quelle que soit l'heure inscrite dessus.
     *
     * Passer par les bornes du jour plutôt que par l'horodatage exact du créneau : un
     * `DoseLog` garde l'heure qu'avait le médicament au moment de la prise, alors que
     * [Slots.slotDaysAgo] recalcule celle qu'il a maintenant. Déplacer un rappel de cinq
     * minutes suffisait donc à rendre invisible tout l'historique du médicament — série,
     * semaine et graphique d'un coup — parce que plus aucune recherche ne tombait sur la
     * bonne milliseconde. Un médicament n'a qu'un créneau par jour, donc « ce jour-là »
     * désigne toujours une dose et une seule.
     */
    @Query(
        "SELECT * FROM DoseLog WHERE tagId = :tagId " +
            "AND scheduledFor >= :dayStart AND scheduledFor < :dayEnd LIMIT 1"
    )
    suspend fun logInDay(tagId: String, dayStart: Long, dayEnd: Long): DoseLog?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: DoseLog): Long

    @Query("DELETE FROM DoseLog WHERE tagId = :tagId")
    suspend fun deleteLogs(tagId: String)

    /**
     * Déplace l'historique d'une clé à l'autre, quand on colle une étiquette sur un
     * médicament qui existait déjà sans.
     *
     * Sans ça, changer la clé signifierait supprimer la ligne du médicament — et la
     * cascade de la clé étrangère emporterait toutes ses doses avec elle. Des mois
     * d'historique effacés pour avoir collé un autocollant sur une bouteille.
     */
    @Query("UPDATE DoseLog SET tagId = :to WHERE tagId = :from")
    suspend fun retagLogs(from: String, to: String)

    @Query("DELETE FROM Medication WHERE tagId = :tagId")
    suspend fun deleteMed(tagId: String)

    @Query("SELECT * FROM OwnedCosmetic")
    fun cosmetics(): Flow<List<OwnedCosmetic>>

    @Query("SELECT * FROM OwnedCosmetic")
    suspend fun cosmeticsOnce(): List<OwnedCosmetic>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun grant(c: OwnedCosmetic)

    @Query("UPDATE OwnedCosmetic SET equipped = :on WHERE id = :id")
    suspend fun setEquipped(id: String, on: Boolean)

    @Query("SELECT dayStart FROM StreakFreeze")
    suspend fun freezeDays(): List<Long>

    @Query("SELECT * FROM StreakFreeze WHERE usedAt >= :since")
    suspend fun freezesSince(since: Long): List<StreakFreeze>

    @Query("SELECT * FROM StreakFreeze WHERE dayStart = :day LIMIT 1")
    suspend fun freezeFor(day: Long): StreakFreeze?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFreeze(f: StreakFreeze)

    @Query("SELECT * FROM StreakFreeze")
    suspend fun allFreezes(): List<StreakFreeze>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordPost(p: ReminderPost)

    @Query("SELECT * FROM ReminderPost WHERE slot >= :since")
    suspend fun postsSince(since: Long): List<ReminderPost>

    /** La table ne sert qu'à regarder la semaine écoulée : le reste ne vaut pas d'être gardé. */
    @Query("DELETE FROM ReminderPost WHERE slot < :before")
    suspend fun prunePosts(before: Long)
}

/** Une pièce cosmétique gagnée. La table ne grandit jamais que d'une ligne par jour. */
/**
 * Une journée manquée mais pardonnée.
 *
 * Sans ça, un seul mauvais jour remet tout à zéro — et c'est précisément la rupture nette
 * qui fait abandonner les gens. Le gel est gratuit, automatique et silencieux : elle
 * n'a rien à activer et rien à acheter. Un par semaine, pas plus, sinon la série ne veut
 * plus rien dire.
 */
@Entity
data class StreakFreeze(
    @PrimaryKey val dayStart: Long,     // minuit du jour gelé
    val usedAt: Long
)

/**
 * La trace qu'un rappel a bel et bien été posé pour ce créneau.
 *
 * Le mode de panne qui compte vraiment pour une app de médication n'est pas un mauvais
 * texte, c'est le silence : One UI met l'app en veille, les alarmes ne partent jamais, et
 * personne ne s'en aperçoit — surtout pas celle qui a arrêté de vérifier elle-même parce
 * qu'elle fait confiance à l'app. Une app qui échoue sans le dire est pire que pas d'app.
 *
 * [ReminderHealth] savait détecter que la batterie est bridée, c'est-à-dire un risque.
 * Ceci détecte une panne réelle : le créneau est passé, aucun rappel n'a été posé, aucune
 * dose n'a été notée.
 */
@Entity(primaryKeys = ["tagId", "slot"])
data class ReminderPost(
    val tagId: String,
    val slot: Long,
    val postedAt: Long
)

@Entity
data class OwnedCosmetic(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
    val equipped: Boolean = true
)

/**
 * La dose qui satisfait [slot], cherchée par journée et non à la milliseconde.
 *
 * Extension plutôt que méthode du DAO pour que les appelants continuent de raisonner en
 * créneaux — c'est bien la question qu'ils posent — sans avoir à calculer des bornes de
 * jour chacun de leur côté.
 */
suspend fun MedDao.logForSlot(tagId: String, slot: Long): DoseLog? {
    val start = Slots.dayOf(slot)
    return logInDay(tagId, start, Slots.dayAfter(start))
}

/**
 * Les médicaments dont une dose était réellement attendue le jour commençant à [dayStart].
 *
 * La granularité est la JOURNÉE et non l'heure du créneau. Compter à l'heure près serait
 * plus juste sur le papier — « tu n'es responsable que des doses dont l'heure est passée
 * après que tu l'aies ajouté » — mais ça rendrait la journée d'installation gratuite pour
 * tout le monde qui installe l'app le soir avec une pilule du matin, c'est-à-dire presque
 * tout le monde. Or c'est justement le jour un, celui qu'on veut voir compter.
 *
 * Le prix : ajouter un médicament à 23h50 alors que son heure était 9h rend la journée
 * incomplète le soir même. Il reste enregistrable dans l'instant ([Slots.loggableSlots]
 * ouvre deux heures avant et ne referme pas), et le gel hebdomadaire couvre l'oubli.
 */
private fun List<Medication>.dueOn(dayStart: Long): List<Medication> =
    filter { Slots.dayOf(it.createdAt) <= dayStart }

/** L'état d'une journée dans la semaine affichée. */
enum class DayState { DONE, FROZEN, TODAY, MISSED, FUTURE }

/**
 * Les sept jours de la semaine en cours, **toujours de lundi à dimanche**.
 *
 * Une série de 47 jours ne dit rien sur la semaine qu'on est en train de vivre. C'est la
 * seule vue qui répond honnêtement à « est-ce que ça va en ce moment », et l'ordre fixe
 * compte : des points qui glissent chaque jour obligeraient à les relire à chaque fois.
 */
suspend fun MedDao.weekStatus(
    meds: List<Medication>,
    now: Long = System.currentTimeMillis()
): List<DayState> {
    val todayIdx = Week.index(
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
    )
    val frozen = freezeDays().toSet()
    return (0..6).map { i ->
        val back = todayIdx - i
        if (back < 0) return@map DayState.FUTURE

        val dayStart = Slots.dayStart(back, now)
        val due = meds.dueOn(dayStart)
        when {
            // Un jour d'avant le premier médicament n'a rien manqué : il n'était rien
            // attendu de lui. FUTURE plutôt que MISSED — c'est le même point à peine
            // visible, et c'est la même chose à dire.
            due.isEmpty() -> DayState.FUTURE
            due.all { logForSlot(it.tagId, Slots.slotDaysAgo(it, back, now)) != null } ->
                DayState.DONE
            dayStart in frozen -> DayState.FROZEN
            back == 0 -> DayState.TODAY
            else -> DayState.MISSED
        }
    }
}

/**
 * Consecutive days ending today where EVERY medication was logged. Lives here rather than
 * in the activity because both the UI and the reminder receiver need the same number, and
 * two copies of a streak calculation is two chances to disagree about what day it is.
 *
 * Chaque jour n'est jugé que sur les médicaments qui existaient ce jour-là. Sans ce
 * filtre, ajouter un deuxième médicament remettait la série à un : le nouveau n'avait
 * aucune dose enregistrée hier, donc hier cessait d'être une journée complète, et toute
 * l'histoire d'avant devenait inatteignable.
 *
 * Le compte s'arrête au premier jour où plus aucun médicament n'existait — sinon la
 * boucle traverserait vacuement les dix ans qui précèdent l'installation et rapporterait
 * une série de 3650 jours.
 */
suspend fun MedDao.perfectDayStreak(
    meds: List<Medication>,
    from: Int = 0,
    now: Long = System.currentTimeMillis()
): Int {
    if (meds.isEmpty()) return 0
    val frozen = freezeDays().toSet()
    var n = from
    while (n < 3650) {
        val dayStart = Slots.dayStart(n, now)
        val due = meds.dueOn(dayStart)
        if (due.isEmpty()) return n - from
        val done = due.all { logForSlot(it.tagId, Slots.slotDaysAgo(it, n, now)) != null }
        if (!done && dayStart !in frozen) return n - from
        n++
    }
    return n - from
}

/**
 * La série telle qu'elle se lit maintenant : celle d'hier tant que la journée n'est pas
 * finie, celle d'aujourd'hui dès qu'elle l'est.
 *
 * Sans ça le compteur du widget tomberait à zéro chaque matin et remonterait le soir, ce
 * qui donnerait l'impression de tout perdre toutes les nuits.
 */
suspend fun MedDao.currentStreak(
    meds: List<Medication>,
    now: Long = System.currentTimeMillis()
): Int {
    if (meds.isEmpty()) return 0
    val todayDone = meds.all { logForSlot(it.tagId, Slots.todayAt(it, now)) != null }
    return if (todayDone) perfectDayStreak(meds, 0, now) else perfectDayStreak(meds, 1, now)
}

/**
 * Appelée au démarrage : si hier a sauté alors qu'une série était en cours, on dépense le
 * gel de la semaine et la série survit. Silencieux — [Reminders] s'occupe de le dire.
 *
 * Le gel n'est posé que s'il y avait quelque chose à protéger : geler avant-hier quand la
 * série était déjà morte gaspillerait le seul de la semaine pour rien.
 */
suspend fun MedDao.useFreezeIfNeeded(now: Long = System.currentTimeMillis()): Boolean {
    val meds = activeMedsOnce()
    if (meds.isEmpty()) return false

    // Sur la même base que la série : un médicament ajouté aujourd'hui n'a pas « manqué »
    // hier, et ne doit donc pas déclencher un gel — il n'y a rien à protéger.
    val yesterday = Slots.dayStart(1, now)
    if (freezeFor(yesterday) != null) return false
    val dueYesterday = meds.dueOn(yesterday)
    if (dueYesterday.isEmpty()) return false
    if (dueYesterday.all { logForSlot(it.tagId, Slots.slotDaysAgo(it, 1, now)) != null })
        return false

    val dayBefore = Slots.dayStart(2, now)
    val dueBefore = meds.dueOn(dayBefore)
    val hadStreak =
        (dueBefore.isNotEmpty() &&
            dueBefore.all { logForSlot(it.tagId, Slots.slotDaysAgo(it, 2, now)) != null }) ||
            dayBefore in freezeDays().toSet()
    if (!hadStreak) return false

    val week = now - 7L * 24 * 60 * 60 * 1000
    if (freezesSince(week).isNotEmpty()) return false

    insertFreeze(StreakFreeze(yesterday, now))
    return true
}

/** Medications whose dose for today has not been logged yet. */
suspend fun MedDao.outstandingToday(meds: List<Medication>): List<Medication> =
    meds.filter { logForSlot(it.tagId, Slots.todayAt(it)) == null }

/**
 * Combien de fois, ces [days] derniers jours, l'app a échoué EN SILENCE : le créneau est
 * passé, aucun rappel n'a été posé, et aucune dose n'a été notée.
 *
 * Les trois conditions comptent. Sans la dernière, prendre sa dose en avance — auquel cas
 * le rappel est annulé avant d'avoir servi — serait signalé comme une panne. Sans
 * [dueOn], un médicament ajouté mardi serait reproché pour le lundi.
 *
 * On ne regarde que les journées terminées. Le créneau d'aujourd'hui vient peut-être de
 * passer et son alarme est peut-être en train de partir : le compter serait une course
 * perdue d'avance contre l'horloge.
 */
suspend fun MedDao.silentMisses(
    meds: List<Medication>,
    days: Int = 7,
    now: Long = System.currentTimeMillis()
): Int {
    if (meds.isEmpty()) return 0
    val told = postsSince(Slots.dayStart(days, now))
        .map { it.tagId to Slots.dayOf(it.slot) }
        .toSet()

    var n = 0
    for (back in 1..days) {
        val dayStart = Slots.dayStart(back, now)
        for (med in meds.dueOn(dayStart)) {
            val slot = Slots.slotDaysAgo(med, back, now)
            if ((med.tagId to Slots.dayOf(slot)) in told) continue
            if (logForSlot(med.tagId, slot) != null) continue
            n++
        }
    }
    return n
}

/**
 * Le créneau qu'une prise notée maintenant vient remplir, ou `null` s'il n'y a rien à
 * noter. Le premier candidat de [Slots.loggableSlots] qui n'a pas déjà sa dose.
 */
suspend fun MedDao.slotToLog(med: Medication, now: Long = System.currentTimeMillis()): Long? =
    Slots.loggableSlots(med, now).firstOrNull { logForSlot(med.tagId, it) == null }

/**
 * Version 2 ajoute la table des cosmétiques. La migration crée simplement la nouvelle
 * table : surtout pas de `fallbackToDestructiveMigration`, qui effacerait des mois
 * d'historique pour ajouter un chapeau.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS OwnedCosmetic (" +
                "id TEXT NOT NULL, unlockedAt INTEGER NOT NULL, " +
                "equipped INTEGER NOT NULL, PRIMARY KEY(id))"
        )
    }
}

/**
 * Version 3 : les doses sautées et les gels de série. La colonne `skipped` arrive avec une
 * valeur par défaut, donc tout l'historique existant reste lisible tel quel.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE DoseLog ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS StreakFreeze (" +
                "dayStart INTEGER NOT NULL, usedAt INTEGER NOT NULL, PRIMARY KEY(dayStart))"
        )
    }
}

/**
 * Version 4 : la date de création d'un médicament, pour que la série cesse de lui
 * reprocher les jours d'avant son existence.
 *
 * La valeur par défaut est 0 — « a toujours existé ». C'est délibérément le choix qui ne
 * change rien : les médicaments déjà en place gardent exactement la série et la semaine
 * qu'ils avaient avant la mise à jour. Mettre `System.currentTimeMillis()` ici aurait
 * remis tout l'historique existant à zéro, ce qui est précisément le bogue qu'on répare.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Medication ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Version 5 : la trace des rappels réellement posés, pour pouvoir constater une panne
 * silencieuse plutôt que de seulement soupçonner un risque.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ReminderPost (" +
                "tagId TEXT NOT NULL, slot INTEGER NOT NULL, postedAt INTEGER NOT NULL, " +
                "PRIMARY KEY(tagId, slot))"
        )
    }
}

/**
 * Version 6 : les sept plages de la semaine.
 *
 * La chaîne vide est le choix qui ne change rien — c'est « la même heure tous les jours,
 * sans plage », donc exactement [hourOfDay]:[minute] et exactement l'escalade d'avant.
 * Personne ne voit sa journée bouger d'une minute en installant la mise à jour, et la
 * fonctionnalité n'existe que pour qui va la régler.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Medication ADD COLUMN schedule TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [
        Medication::class, DoseLog::class, OwnedCosmetic::class,
        StreakFreeze::class, ReminderPost::class
    ],
    version = 6, exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): MedDao

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, Db::class.java, "medtap.db")
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6
                )
                .build().also { instance = it }
        }
    }
}

/**
 * A "slot" is the exact millisecond a dose was due.
 *
 * Everything the user sees keys on TODAY's slot and nothing else. An earlier version
 * asked for "the current slot, or yesterday's if today's is more than 2h away", which
 * quietly meant that at 4am a 9am dose resolved to *yesterday* -- unlogged, nineteen
 * hours old, so the screen announced a dose was critically overdue when it was actually
 * five hours in the future. Yesterday is not recoverable and should never drive today's
 * screen.
 */
object Slots {
    /** How early a dose may be logged before it's actually due. */
    const val EARLY_WINDOW = 2 * 60 * 60 * 1000L

    /**
     * L'heure du rappel appliquée à la JOURNÉE de [base], selon le jour de semaine de
     * cette journée-là.
     *
     * Le jour de semaine se lit avant que l'heure soit posée, et sur le bon jour. C'est
     * tout le piège des horaires variables : la version évidente — poser l'heure
     * d'aujourd'hui puis reculer de trois jours — donne l'heure du mardi au créneau du
     * samedi, donc plus aucune dose ne se retrouve et la série s'effondre.
     */
    private fun startOn(med: Medication, base: Long) = Calendar.getInstance().apply {
        timeInMillis = base
        val w = med.windowOn(Week.index(get(Calendar.DAY_OF_WEEK)))
        set(Calendar.HOUR_OF_DAY, w.startMinute / 60)
        set(Calendar.MINUTE, w.startMinute % 60)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    /**
     * Le prochain créneau strictement après [from].
     *
     * On avance jour par jour au lieu d'ajouter vingt-quatre heures : avec des heures
     * différentes selon les jours, « demain à la même heure » n'existe pas. Huit essais
     * couvrent la semaine entière plus le jour de départ, donc la boucle se termine
     * toujours — même si les sept jours tombaient à minuit pile.
     */
    fun nextAfter(med: Medication, from: Long = System.currentTimeMillis()): Long {
        val probe = Calendar.getInstance().apply { timeInMillis = from }
        repeat(Week.DAYS + 1) {
            val slot = startOn(med, probe.timeInMillis).timeInMillis
            if (slot > from) return slot
            probe.add(Calendar.DAY_OF_YEAR, 1)
        }
        return startOn(med, probe.timeInMillis).timeInMillis
    }

    /** Today's dose time, whether or not it has already passed. */
    fun todayAt(med: Medication, now: Long = System.currentTimeMillis()): Long =
        startOn(med, now).timeInMillis

    /**
     * The same medication's slot [days] days ago.
     *
     * This walks the calendar rather than subtracting 86_400_000 ms, because slots are
     * stored as local wall-clock times. On the two DST changeovers a day is 23 or 25
     * hours long, so millisecond arithmetic lands an hour off, finds no log, and silently
     * resets a streak of any length back to one. Twice a year, invisibly.
     *
     * Le recul se fait AVANT que l'heure soit posée, pour que ce soit bien l'heure de ce
     * jour-là de la semaine qui s'applique.
     */
    fun slotDaysAgo(med: Medication, days: Int, now: Long = System.currentTimeMillis()): Long {
        val day = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -days)
        }.timeInMillis
        return startOn(med, day).timeInMillis
    }

    /**
     * L'instant où la plage se referme et où le dragon a le droit de monter le ton.
     *
     * Égal au créneau lui-même quand aucune plage n'est réglée, ce qui est le cas de tous
     * les médicaments d'avant cette fonctionnalité : ils escaladent exactement comme
     * avant, à la minute près.
     */
    fun windowEnd(med: Medication, slot: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = slot }
        val w = med.windowOn(Week.index(c.get(Calendar.DAY_OF_WEEK)))
        if (w.instant) return slot
        c.set(Calendar.HOUR_OF_DAY, w.endMinute / 60)
        c.set(Calendar.MINUTE, w.endMinute % 60)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return maxOf(c.timeInMillis, slot)
    }

    /**
     * Les minutes de retard qui COMPTENT, c'est-à-dire celles d'après la plage.
     *
     * C'est le seul nombre que [Tier] a le droit de voir. Le temps écoulé depuis le
     * créneau sert encore à faire varier le texte d'une relance à l'autre, mais il ne
     * décide plus de rien : un médicament réglé de 7h à 10h a zéro minute de retard à
     * 9h59, quoi qu'en dise l'horloge.
     */
    fun pressureMinutes(
        med: Medication,
        slot: Long,
        now: Long = System.currentTimeMillis()
    ): Long = ((now - windowEnd(med, slot)) / 60_000L).coerceAtLeast(0)

    /** Minuit du jour situé [daysAgo] jours en arrière. */
    fun dayStart(daysAgo: Int, now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Minuit du jour auquel appartient [t], heure locale. */
    fun dayOf(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Minuit du lendemain de [dayStart]. Passe par le calendrier et non par
     * `+ 86_400_000` : aux deux changements d'heure la journée fait 23 ou 25 heures, et
     * la borne tomberait une heure à côté.
     */
    fun dayAfter(dayStart: Long): Long = Calendar.getInstance().apply {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    /**
     * Jusqu'à quand, après son heure, un créneau d'hier soir reste enregistrable.
     *
     * Six heures : une pilule de 21h prise à 0h30 est manifestement la pilule d'hier, une
     * pilule de 9h « prise » le lendemain à 1h du matin ne l'est plus, c'est une journée
     * manquée qu'on maquillerait. La borne tombe entre les deux.
     */
    const val LATE_WINDOW = 6 * 60 * 60 * 1000L

    /**
     * Les créneaux qu'une prise faite maintenant peut satisfaire, du plus probable au
     * moins probable. Vide s'il n'y a rien à noter à cette heure-ci.
     *
     * Le deuxième cas est la raison d'être de cette fonction. [todayAt] résout toujours
     * vers la journée civile en cours — c'est délibéré et il ne faut pas y toucher, voir
     * plus haut — donc à 0h30 le créneau de 21h désignait ce soir, dans vingt heures, et
     * la dose qu'elle avait littéralement dans la main était impossible à enregistrer. Le
     * bouton était grisé, la journée d'hier restait manquée, et la série tombait.
     *
     * C'est une fonction pure, sans base de données, parce que trois appelants doivent
     * répondre à la même question avec des données différentes — l'écran a la liste des
     * jours déjà notés, le reste a le DAO. Ce qui ne doit pas diverger, c'est la règle ;
     * chacun vérifie ensuite lui-même ce qui est déjà enregistré.
     */
    fun loggableSlots(med: Medication, now: Long = System.currentTimeMillis()): List<Long> {
        val out = mutableListOf<Long>()
        val today = todayAt(med, now)
        if (now >= today - EARLY_WINDOW) out += today
        val yesterday = slotDaysAgo(med, 1, now)
        // La deuxième condition ferme un piège du tout premier jour. Un médicament du soir
        // ajouté après minuit voyait sa fenêtre de rattrapage ouverte sur un créneau
        // d'hier — alors qu'il n'existait pas hier. Le bouton proposait « Je l'ai prise
        // (hier soir) », écrivait la dose, et cette dose ne comptait pour rien : ni la
        // série ni la semaine ne regardent les journées d'avant la création (voir
        // `dueOn`). Elle notait sa première prise et le compteur restait à zéro, sans que
        // rien n'explique pourquoi.
        //
        // La même règle des deux côtés, donc : ce que le bouton propose d'écrire est
        // exactement ce que la série accepte de compter.
        if (now - yesterday in 0..LATE_WINDOW && dayOf(med.createdAt) <= dayOf(yesterday)) {
            out += yesterday
        }
        return out
    }

}
