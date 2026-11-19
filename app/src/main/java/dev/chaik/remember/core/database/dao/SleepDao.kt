package dev.chaik.remember.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.chaik.remember.core.database.entity.SleepEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_entry WHERE date = :date")
    fun observe(date: LocalDate): Flow<SleepEntryEntity?>

    @Query("SELECT * FROM sleep_entry WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SleepEntryEntity>>

    /** Дни, за которые записан сон: пустых записей в таблице не бывает. */
    @Query("SELECT date FROM sleep_entry")
    fun observeDates(): Flow<List<LocalDate>>

    /** Дни, за которые записан сам сон (текст «что приснилось»); время и оценка без текста не в счёт. */
    @Query("SELECT date FROM sleep_entry WHERE TRIM(dream) != ''")
    fun observeDreamDates(): Flow<List<LocalDate>>

    @Upsert
    suspend fun upsert(entry: SleepEntryEntity)

    @Query("DELETE FROM sleep_entry WHERE date = :date")
    suspend fun delete(date: LocalDate)
}
