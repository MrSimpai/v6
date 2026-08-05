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
    val active: Boolean = true
) {
    companion object {
        const val MANUAL_PREFIX = "MANUAL-"

        /** A key for a medication with no sticker behind it. */
        fun manualId(): String = MANUAL_PREFIX + System.currentTimeMillis()
    }
}

/** True when this medication has no NFC tag and is logged by hand. */
val Medication.isManual: Boolean get() = tagId.startsWith(Medication.MANUAL_PREFIX)

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

    @Query("SELECT * FROM DoseLog WHERE tagId = :tagId AND scheduledFor = :slot LIMIT 1")
    suspend fun logForSlot(tagId: String, slot: Long): DoseLog?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: DoseLog): Long

    @Query("DELETE FROM DoseLog WHERE tagId = :tagId")
    suspend fun deleteLogs(tagId: String)

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

@Entity
data class OwnedCosmetic(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
    val equipped: Boolean = true
)

/**
 * Consecutive days ending today where EVERY medication was logged. Lives here rather than
 * in the activity because both the UI and the reminder receiver need the same number, and
 * two copies of a streak calculation is two chances to disagree about what day it is.
 */
suspend fun MedDao.perfectDayStreak(meds: List<Medication>, from: Int = 0): Int {
    if (meds.isEmpty()) return 0
    val frozen = freezeDays().toSet()
    var n = from
    while (n < 3650) {
        val done = meds.all { logForSlot(it.tagId, Slots.slotDaysAgo(it, n)) != null }
        if (!done && Slots.dayStart(n) !in frozen) return n - from
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
suspend fun MedDao.currentStreak(meds: List<Medication>): Int {
    if (meds.isEmpty()) return 0
    val todayDone = meds.all { logForSlot(it.tagId, Slots.todayAt(it)) != null }
    return if (todayDone) perfectDayStreak(meds) else perfectDayStreak(meds, from = 1)
}

/**
 * Appelée au démarrage : si hier a sauté alors qu'une série était en cours, on dépense le
 * gel de la semaine et la série survit. Silencieux — [Reminders] s'occupe de le dire.
 *
 * Le gel n'est posé que s'il y avait quelque chose à protéger : geler avant-hier quand la
 * série était déjà morte gaspillerait le seul de la semaine pour rien.
 */
suspend fun MedDao.useFreezeIfNeeded(): Boolean {
    val meds = activeMedsOnce()
    if (meds.isEmpty()) return false

    val yesterday = Slots.dayStart(1)
    if (freezeFor(yesterday) != null) return false
    if (meds.all { logForSlot(it.tagId, Slots.slotDaysAgo(it, 1)) != null }) return false

    val hadStreak = meds.all { logForSlot(it.tagId, Slots.slotDaysAgo(it, 2)) != null } ||
        Slots.dayStart(2) in freezeDays().toSet()
    if (!hadStreak) return false

    val week = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    if (freezesSince(week).isNotEmpty()) return false

    insertFreeze(StreakFreeze(yesterday, System.currentTimeMillis()))
    return true
}

/** Medications whose dose for today has not been logged yet. */
suspend fun MedDao.outstandingToday(meds: List<Medication>): List<Medication> =
    meds.filter { logForSlot(it.tagId, Slots.todayAt(it)) == null }

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

@Database(
    entities = [Medication::class, DoseLog::class, OwnedCosmetic::class, StreakFreeze::class],
    version = 3, exportSchema = false
)
abstract class Db : RoomDatabase() {
    abstract fun dao(): MedDao

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, Db::class.java, "medtap.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

    private fun atTimeOn(med: Medication, base: Long) = Calendar.getInstance().apply {
        timeInMillis = base
        set(Calendar.HOUR_OF_DAY, med.hourOfDay)
        set(Calendar.MINUTE, med.minute)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    fun nextAfter(med: Medication, from: Long = System.currentTimeMillis()): Long {
        val c = atTimeOn(med, from)
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Today's dose time, whether or not it has already passed. */
    fun todayAt(med: Medication, now: Long = System.currentTimeMillis()): Long =
        atTimeOn(med, now).timeInMillis

    /**
     * The same medication's slot [days] days ago.
     *
     * This walks the calendar rather than subtracting 86_400_000 ms, because slots are
     * stored as local wall-clock times. On the two DST changeovers a day is 23 or 25
     * hours long, so millisecond arithmetic lands an hour off, finds no log, and silently
     * resets a streak of any length back to one. Twice a year, invisibly.
     */
    fun slotDaysAgo(med: Medication, days: Int, now: Long = System.currentTimeMillis()): Long =
        atTimeOn(med, now).apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis

    /** Minuit du jour situé [daysAgo] jours en arrière. */
    fun dayStart(daysAgo: Int, now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** True once the dose is due, or within [EARLY_WINDOW] of being due. */
    fun canLogNow(med: Medication, now: Long = System.currentTimeMillis()): Boolean =
        now >= todayAt(med, now) - EARLY_WINDOW
}
