package dev.chaik.remember.core.data

import androidx.room.withTransaction
import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.model.DayEntry
import dev.chaik.remember.core.model.PhotoOwner
import dev.chaik.remember.core.model.Tag
import dev.chaik.remember.core.photos.PhotoRefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/** Записи «как прошёл день». */
class DayRepository(private val db: ReMemberDatabase) {
    private val dayDao = db.dayEntryDao()
    private val tagDao = db.tagDao()
    private val photoDao = db.photoDao()

    /** Запись за день вместе с тегами; null — записи ещё нет. */
    fun observe(date: LocalDate): Flow<DayEntry?> =
        combine(dayDao.observe(date), tagDao.observeForDay(date)) { entry, tags -> entry?.toModel(tags) }

    /** Записи за период, новые сверху. Без тегов — для ленты и календаря они не нужны. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayEntry>> =
        dayDao.observeRange(from, to).map { list -> list.map { it.toModel(emptyList()) } }

    /**
     * Какие дни периода заполнены и их оценки — для календаря.
     * В словаре есть только дни с записью; значение — оценка (null — не выставлена).
     */
    fun observeMarks(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int?>> =
        dayDao.observeMarks(from, to).map { rows -> rows.associate { it.date to it.mood } }

    /** Теги всех дней: день → его теги. В словаре только дни с тегами. */
    fun observeTags(): Flow<Map<LocalDate, List<Tag>>> =
        tagDao.observeAllDayTags().map { rows -> rows.groupBy({ it.date }, { Tag(it.tagId, it.name) }) }

    /** Дни, где написана запись (текст или фото), — для огонька на «Сегодня». */
    fun observeWrittenDates(): Flow<Set<LocalDate>> = dayDao.observeWrittenDates().map { it.toSet() }

    /** Самый ранний день с записью; null — записей нет. */
    suspend fun firstDate(): LocalDate? = dayDao.firstDate()

    /** Сохранить запись целиком: текст, настроение, теги и ссылки на фото. */
    suspend fun save(entry: DayEntry) = db.withTransaction {
        val now = Instant.now()
        val createdAt = dayDao.get(entry.date)?.createdAt ?: now
        dayDao.upsert(entry.toEntity(createdAt = createdAt, updatedAt = now))
        tagDao.setDayTags(entry.date, entry.tags.map { it.id })
        photoDao.replaceRefs(PhotoOwner.DAY_ENTRY, entry.date.toEpochDay(), PhotoRefs.extract(entry.text))
    }

    suspend fun delete(date: LocalDate) = db.withTransaction {
        dayDao.delete(date) // теги удалятся каскадом
        photoDao.clearRefs(PhotoOwner.DAY_ENTRY, date.toEpochDay())
    }
}
