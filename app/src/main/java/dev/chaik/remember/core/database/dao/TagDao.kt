package dev.chaik.remember.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.chaik.remember.core.database.entity.DayEntryTagEntity
import dev.chaik.remember.core.database.entity.NoteTagEntity
import dev.chaik.remember.core.database.entity.TagEntity
import dev.chaik.remember.core.database.entity.tagKey
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TagDao {
    @Query("SELECT * FROM tag WHERE `key` = :key")
    suspend fun findByKey(key: String): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    /** Тег по имени без учёта регистра; если такого нет — создаётся. */
    @Transaction
    suspend fun getOrCreate(name: String): TagEntity =
        findByKey(tagKey(name)) ?: TagEntity(name = name).let { it.copy(id = insert(it)) }

    /** Теги дней: чаще всего встречающиеся — первыми. */
    @Query(
        """
        SELECT tag.* FROM tag
        JOIN day_entry_tag ON tag.id = day_entry_tag.tag_id
        GROUP BY tag.id
        ORDER BY COUNT(*) DESC, tag.`key`
        """,
    )
    fun observeDayTagsByUse(): Flow<List<TagEntity>>

    /** Теги заметок: чаще всего встречающиеся — первыми. Теги защищённых заметок сюда не попадают. */
    @Query(
        """
        SELECT tag.* FROM tag
        JOIN note_tag ON tag.id = note_tag.tag_id
        JOIN note ON note.id = note_tag.note_id
        WHERE note.is_protected = 0
        GROUP BY tag.id
        ORDER BY COUNT(*) DESC, tag.`key`
        """,
    )
    fun observeNoteTagsByUse(): Flow<List<TagEntity>>

    /** Удалить теги, которые больше нигде не используются. */
    @Query(
        """
        DELETE FROM tag
        WHERE id NOT IN (SELECT tag_id FROM day_entry_tag)
          AND id NOT IN (SELECT tag_id FROM note_tag)
        """,
    )
    suspend fun deleteUnused()

    // Теги записи дня

    @Query(
        """
        SELECT tag.* FROM tag
        JOIN day_entry_tag ON tag.id = day_entry_tag.tag_id
        WHERE day_entry_tag.date = :date
        ORDER BY tag.`key`
        """,
    )
    fun observeForDay(date: LocalDate): Flow<List<TagEntity>>

    /** Теги всех дней разом — для списка дней (без запроса на каждый день). */
    @Query(
        """
        SELECT day_entry_tag.date AS date, tag.id AS tagId, tag.name AS name FROM day_entry_tag
        JOIN tag ON tag.id = day_entry_tag.tag_id
        ORDER BY tag.`key`
        """,
    )
    fun observeAllDayTags(): Flow<List<DayTagRow>>

    @Query("DELETE FROM day_entry_tag WHERE date = :date")
    suspend fun clearDayTags(date: LocalDate)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDayTags(tags: List<DayEntryTagEntity>)

    @Transaction
    suspend fun setDayTags(date: LocalDate, tagIds: Collection<Long>) {
        clearDayTags(date)
        insertDayTags(tagIds.map { DayEntryTagEntity(date, it) })
    }

    // Теги заметки

    @Query(
        """
        SELECT tag.* FROM tag
        JOIN note_tag ON tag.id = note_tag.tag_id
        WHERE note_tag.note_id = :noteId
        ORDER BY tag.`key`
        """,
    )
    fun observeForNote(noteId: Long): Flow<List<TagEntity>>

    /** Теги всех заметок разом — для списка заметок (без запроса на каждую карточку). */
    @Query(
        """
        SELECT note_tag.note_id AS noteId, tag.id AS tagId, tag.name AS name FROM note_tag
        JOIN tag ON tag.id = note_tag.tag_id
        ORDER BY tag.`key`
        """,
    )
    fun observeAllNoteTags(): Flow<List<NoteTagRow>>

    @Query("DELETE FROM note_tag WHERE note_id = :noteId")
    suspend fun clearNoteTags(noteId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteTags(tags: List<NoteTagEntity>)

    @Transaction
    suspend fun setNoteTags(noteId: Long, tagIds: Collection<Long>) {
        clearNoteTags(noteId)
        insertNoteTags(tagIds.map { NoteTagEntity(noteId, it) })
    }
}

data class DayTagRow(val date: LocalDate, val tagId: Long, val name: String)

data class NoteTagRow(
    val noteId: Long,
    val tagId: Long,
    val name: String,
)
