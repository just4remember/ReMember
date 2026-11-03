package dev.chaik.remember.core.data

import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.model.SleepEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Сон и сны. */
class SleepRepository(db: ReMemberDatabase) {
    private val dao = db.sleepDao()
    private val _dreamAdded = MutableSharedFlow<LocalDate>(extraBufferCapacity = 1)

    /** Сон дописали мимо открытого экрана (из будильника) — экрану пора перечитать запись. */
    val dreamAdded: SharedFlow<LocalDate> = _dreamAdded

    fun observe(date: LocalDate): Flow<SleepEntry?> = dao.observe(date).map { it?.toModel() }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SleepEntry>> =
        dao.observeRange(from, to).map { list -> list.map { it.toModel() } }

    /** Дни с записанным сном — для луны в календаре. */
    fun observeDates(): Flow<Set<LocalDate>> = dao.observeDates().map { it.toSet() }

    suspend fun save(entry: SleepEntry) = dao.upsert(entry.toEntity(updatedAt = Instant.now()))

    suspend fun delete(date: LocalDate) = dao.delete(date)

    /** Дописать сон новым абзацем в конец записи дня, без пустой строки перед ним. */
    suspend fun appendDream(date: LocalDate, text: String) {
        val entry = observe(date).first() ?: SleepEntry(date)
        val dream = if (entry.dream.isBlank()) text else entry.dream.trimEnd() + "\n" + text
        save(entry.copy(dream = dream))
        _dreamAdded.tryEmit(date)
    }
}
