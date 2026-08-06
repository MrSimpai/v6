package com.example.medtap.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Calendar
import java.util.TimeZone

/**
 * Un `MedDao` en mémoire.
 *
 * Tout ce qui compte les jours — [perfectDayStreak], [weekStatus], [logForSlot] — est
 * écrit contre l'interface et jamais contre Room, donc ces fonctions se testent sur la
 * JVM sans émulateur ni base de données. C'est la différence entre une suite qu'on lance
 * à chaque commit et une suite qu'on lance une fois.
 *
 * Seules les méthodes que la logique de série utilise réellement font quelque chose ; le
 * reste est là pour satisfaire l'interface.
 */
class TestDao(
    private val meds: MutableList<Medication> = mutableListOf(),
    private val logs: MutableList<DoseLog> = mutableListOf(),
    private val freezes: MutableList<StreakFreeze> = mutableListOf(),
    private val posts: MutableList<ReminderPost> = mutableListOf()
) : MedDao {

    override suspend fun recordPost(p: ReminderPost) {
        posts.removeAll { it.tagId == p.tagId && it.slot == p.slot }
        posts += p
    }

    override suspend fun postsSince(since: Long): List<ReminderPost> =
        posts.filter { it.slot >= since }

    override suspend fun prunePosts(before: Long) {
        posts.removeAll { it.slot < before }
    }

    override suspend fun logInDay(tagId: String, dayStart: Long, dayEnd: Long): DoseLog? =
        logs.firstOrNull { it.tagId == tagId && it.scheduledFor >= dayStart && it.scheduledFor < dayEnd }

    override suspend fun activeMedsOnce(): List<Medication> = meds.filter { it.active }
    override suspend fun med(tagId: String): Medication? = meds.firstOrNull { it.tagId == tagId }
    override suspend fun upsert(med: Medication) {
        meds.removeAll { it.tagId == med.tagId }; meds += med
    }

    override suspend fun insert(log: DoseLog): Long {
        // Room ignore les doublons sur (tagId, scheduledFor) : le faux fait pareil, sinon
        // un test qui note deux fois la même dose passerait ici et pas en vrai.
        if (logs.none { it.tagId == log.tagId && it.scheduledFor == log.scheduledFor }) logs += log
        return logs.size.toLong()
    }

    override suspend fun allLogs(): List<DoseLog> = logs.toList()
    override suspend fun deleteLogs(tagId: String) { logs.removeAll { it.tagId == tagId } }
    override suspend fun deleteMed(tagId: String) { meds.removeAll { it.tagId == tagId } }

    override suspend fun freezeDays(): List<Long> = freezes.map { it.dayStart }
    override suspend fun freezesSince(since: Long): List<StreakFreeze> =
        freezes.filter { it.usedAt >= since }
    override suspend fun freezeFor(day: Long): StreakFreeze? =
        freezes.firstOrNull { it.dayStart == day }
    override suspend fun insertFreeze(f: StreakFreeze) {
        if (freezes.none { it.dayStart == f.dayStart }) freezes += f
    }
    override suspend fun allFreezes(): List<StreakFreeze> = freezes.toList()

    override fun activeMeds(): Flow<List<Medication>> = flowOf(meds.filter { it.active })
    override fun logsSince(since: Long): Flow<List<DoseLog>> = flowOf(logs.toList())
    override fun cosmetics(): Flow<List<OwnedCosmetic>> = flowOf(emptyList())
    override suspend fun cosmeticsOnce(): List<OwnedCosmetic> = emptyList()
    override suspend fun grant(c: OwnedCosmetic) = Unit
    override suspend fun setEquipped(id: String, on: Boolean) = Unit
}

/**
 * Les tests parlent en dates lisibles et jamais en millisecondes brutes : un test dont on
 * ne peut pas lire l'intention à voix haute ne sert à rien le jour où il casse.
 *
 * Le fuseau est fixé à Montréal, celui pour lequel l'app est écrite. Sans ça, les tests
 * de changement d'heure passeraient ou casseraient selon la machine qui les lance.
 */
object T {
    val ZONE: TimeZone = TimeZone.getTimeZone("America/Montreal")

    fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance(ZONE).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    fun med(
        tagId: String = "A",
        hour: Int = 9,
        minute: Int = 0,
        createdAt: Long = 0L
    ) = Medication(
        tagId = tagId, name = tagId, doseText = "1", hourOfDay = hour, minute = minute,
        createdAt = createdAt
    )

    /** Une dose notée pour le créneau de [med] le jour donné. */
    fun dose(med: Medication, year: Int, month: Int, day: Int) = DoseLog(
        tagId = med.tagId,
        scheduledFor = at(year, month, day, med.hourOfDay, med.minute),
        takenAt = at(year, month, day, med.hourOfDay, med.minute)
    )

    /** La trace d'un rappel effectivement posé pour le créneau de [med] ce jour-là. */
    fun told(med: Medication, year: Int, month: Int, day: Int): ReminderPost {
        val slot = at(year, month, day, med.hourOfDay, med.minute)
        return ReminderPost(med.tagId, slot, slot)
    }
}
