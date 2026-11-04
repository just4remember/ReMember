package dev.chaik.remember.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chaik.remember.core.database.entity.NoteEntity
import dev.chaik.remember.core.database.entity.NoteFolderEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface NoteDao {
    // Заметки: закреплённые сверху, дальше — недавно изменённые

    @Query("SELECT * FROM note ORDER BY pinned DESC, updated_at DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    /**
     * Лёгкий список для карточек: вместо всего текста — только начало ([NOTE_HEAD_CHARS] символов)
     * и id первого фото (ищем прямо в базе). Порядок: закреплённые, потом недавно изменённые.
     */
    @Query(
        """
        SELECT id, title,
            substr(text, 1, $NOTE_HEAD_CHARS) AS head,
            CASE WHEN instr(text, '](photo:') > 0
                THEN substr(text, instr(text, '](photo:') + 8, 36)
            END AS coverPhotoId,
            folder_id AS folderId, pinned, is_protected AS isProtected, created_at AS createdAt, updated_at AS updatedAt,
            sort_order AS sortOrder
        FROM note
        ORDER BY pinned DESC, updated_at DESC
        """,
    )
    fun observeSummaries(): Flow<List<NoteSummaryRow>>

    @Query("SELECT * FROM note WHERE folder_id IS :folderId ORDER BY pinned DESC, updated_at DESC")
    fun observeInFolder(folderId: Long?): Flow<List<NoteEntity>>

    @Query("SELECT * FROM note WHERE id = :id")
    fun observe(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM note WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT MIN(sort_order) FROM note")
    suspend fun minSortOrder(): Int?

    @Query("UPDATE note SET sort_order = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE note SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE note SET folder_id = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    // Папки

    @Query("SELECT * FROM note_folder ORDER BY sort_order, name")
    fun observeFolders(): Flow<List<NoteFolderEntity>>

    @Insert
    suspend fun insertFolder(folder: NoteFolderEntity): Long

    @Query("UPDATE note_folder SET name = :name, sort_order = :sortOrder WHERE id = :id")
    suspend fun updateFolder(id: Long, name: String, sortOrder: Int)

    @Query("DELETE FROM note_folder WHERE id = :id")
    suspend fun deleteFolder(id: Long)
}

/**
 * Сколько символов текста брать для карточки. На карточке — до 6 строк (~150 символов),
 * но в базе текст с разметкой: строка с фото сама по себе ~50 символов, поэтому с запасом.
 */
const val NOTE_HEAD_CHARS = 300

data class NoteSummaryRow(
    val id: Long,
    val title: String,
    val head: String,
    val coverPhotoId: String?,
    val folderId: Long?,
    val pinned: Boolean,
    val isProtected: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val sortOrder: Int,
)
