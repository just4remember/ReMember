package dev.chaik.remember.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.chaik.remember.core.database.entity.DayEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DayEntryDao {
    @Query("SELECT * FROM day_entry WHERE date = :date")
    fun observe(date: LocalDate): Flow<DayEntryEntity?>

    @Query("SELECT * FROM day_entry WHERE date = :date")
    suspend fun get(date: LocalDate): DayEntryEntity?

    /** Записи за период, новые сверху. */
    @Query("SELECT * FROM day_entry WHERE date BETWEEN :from AND :to ORDER BY date DESC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayEntryEntity>>

    /** Только даты и оценки — для календаря, без тяжёлых текстов. */
    @Query("SELECT date, mood FROM day_entry WHERE date BETWEEN :from AND :to")
    fun observeMarks(from: LocalDate, to: LocalDate): Flow<List<DayMarkRow>>

    /** Дни с непустым текстом: одна оценка или теги записью не считаются. Фото хранятся в тексте. */
    @Query("SELECT date FROM day_entry WHERE trim(text, ' ' || char(9) || char(10) || char(13)) != ''")
    fun observeWrittenDates(): Flow<List<LocalDate>>

    /** Самый ранний день с записью; null — записей нет. */
    @Query("SELECT MIN(date) FROM day_entry")
    suspend fun firstDate(): LocalDate?

    // Upsert, а не REPLACE: REPLACE удаляет строку и каскадом сносит её теги
    @Upsert
    suspend fun upsert(entry: DayEntryEntity)

    @Query("DELETE FROM day_entry WHERE date = :date")
    suspend fun delete(date: LocalDate)
}

data class DayMarkRow(
    val date: LocalDate,
    val mood: Int?,
)
