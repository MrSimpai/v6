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
}

@Database(entities = [Medication::class, DoseLog::class], version = 1)
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

/** A "slot" is the exact millisecond a dose was due. */
object Slots {
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

    /** The slot a scan right now counts against. Scanning up to 2h early still counts. */
    fun currentOrPrevious(med: Medication, now: Long = System.currentTimeMillis()): Long {
        val c = atTimeOn(med, now)
        if (c.timeInMillis - now > 2 * 60 * 60 * 1000L) c.add(Calendar.DAY_OF_YEAR, -1)
        return c.timeInMillis
    }
}
