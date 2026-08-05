package com.example.medtap.data

import android.content.Context
import androidx.room.*
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
    val takenAt: Long                   // epoch millis of the scan
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

    @Query("SELECT * FROM DoseLog WHERE tagId = :tagId AND scheduledFor = :slot LIMIT 1")
    suspend fun logForSlot(tagId: String, slot: Long): DoseLog?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: DoseLog): Long

    @Query("DELETE FROM DoseLog WHERE tagId = :tagId")
    suspend fun deleteLogs(tagId: String)

    @Query("DELETE FROM Medication WHERE tagId = :tagId")
    suspend fun deleteMed(tagId: String)
}

/**
 * Consecutive days ending today where EVERY medication was logged. Lives here rather than
 * in the activity because both the UI and the reminder receiver need the same number, and
 * two copies of a streak calculation is two chances to disagree about what day it is.
 */
suspend fun MedDao.perfectDayStreak(meds: List<Medication>): Int {
    if (meds.isEmpty()) return 0
    var n = 0
    while (n < 3650 && meds.all { logForSlot(it.tagId, Slots.slotDaysAgo(it, n)) != null }) n++
    return n
}

/** Medications whose dose for today has not been logged yet. */
suspend fun MedDao.outstandingToday(meds: List<Medication>): List<Medication> =
    meds.filter { logForSlot(it.tagId, Slots.todayAt(it)) == null }

@Database(entities = [Medication::class, DoseLog::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): MedDao

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, Db::class.java, "medtap.db")
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

    /** True once the dose is due, or within [EARLY_WINDOW] of being due. */
    fun canLogNow(med: Medication, now: Long = System.currentTimeMillis()): Boolean =
        now >= todayAt(med, now) - EARLY_WINDOW
}
