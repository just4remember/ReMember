package dev.chaik.remember.core.data

import androidx.room.withTransaction
import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.database.entity.NoteFolderEntity
import dev.chaik.remember.core.editor.MarkdownPreview
import dev.chaik.remember.core.model.Note
import dev.chaik.remember.core.model.NoteFolder
import dev.chaik.remember.core.model.NoteSummary
import dev.chaik.remember.core.model.PhotoOwner
import dev.chaik.remember.core.model.Tag
import dev.chaik.remember.core.photos.PhotoRefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant

/** Заметки и папки. */
class NoteRepository(private val db: ReMemberDatabase) {
    private val noteDao = db.noteDao()
    private val tagDao = db.tagDao()
    private val photoDao = db.photoDao()

    /** Все заметки (без тегов): закреплённые сверху, дальше — недавно изменённые. */
    fun observeAll(): Flow<List<Note>> = noteDao.observeAll().map { list -> list.map { it.toModel() } }

    /**
     * Список для карточек вместе с тегами. Полный текст не читается: только начало,
     * из которого в фоновом потоке готовится превью без разметки.
     */
    fun observeSummaries(): Flow<List<NoteSummary>> =
        combine(noteDao.observeSummaries(), tagDao.observeAllNoteTags()) { rows, tagRows ->
            val tags = tagRows.groupBy({ it.noteId }, { Tag(it.tagId, it.name) })
            rows.map { row ->
                NoteSummary(
                    id = row.id,
                    title = row.title,
                    preview = if (row.isProtected) "" else MarkdownPreview.plain(row.head, maxChars = PREVIEW_CHARS),
                    coverPhotoId = row.coverPhotoId.takeUnless { row.isProtected },
                    folderId = row.folderId,
                    pinned = row.pinned,
                    isProtected = row.isProtected,
                    tags = if (row.isProtected) emptyList() else tags[row.id].orEmpty(),
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                )
            }
        }.flowOn(Dispatchers.Default)

    /** Заметка один раз (для открытия редактора); null — нет такой. */
    suspend fun get(id: Long): Note? =
        noteDao.get(id)?.let { note -> note.toModel(tagDao.observeForNote(id).first()) }

    /** Закрепить или открепить, не трогая дату изменения. */
    suspend fun setPinned(id: Long, pinned: Boolean) = noteDao.setPinned(id, pinned)

    /** Перенести в папку (null — без папки), не трогая дату изменения. */
    suspend fun moveToFolder(id: Long, folderId: Long?) = noteDao.setFolder(id, folderId)

    /** Заметки папки; null — заметки без папки. */
    fun observeInFolder(folderId: Long?): Flow<List<Note>> =
        noteDao.observeInFolder(folderId).map { list -> list.map { it.toModel() } }

    /** Заметка вместе с тегами; null — удалена. */
    fun observe(id: Long): Flow<Note?> =
        combine(noteDao.observe(id), tagDao.observeForNote(id)) { note, tags -> note?.toModel(tags) }

    /** Создаёт заметку (id = 0) или обновляет существующую. Возвращает id. */
    suspend fun save(note: Note): Long = db.withTransaction {
        val now = Instant.now()
        val id = if (note.id == 0L) {
            noteDao.insert(note.toEntity(createdAt = now, updatedAt = now))
        } else {
            val createdAt = noteDao.get(note.id)?.createdAt ?: now
            noteDao.update(note.toEntity(createdAt = createdAt, updatedAt = now))
            note.id
        }
        tagDao.setNoteTags(id, note.tags.map { it.id })
        photoDao.replaceRefs(PhotoOwner.NOTE, id, PhotoRefs.extract(note.text))
        id
    }

    suspend fun delete(id: Long) = db.withTransaction {
        noteDao.delete(id) // теги удалятся каскадом
        photoDao.clearRefs(PhotoOwner.NOTE, id)
    }

    // Папки

    fun observeFolders(): Flow<List<NoteFolder>> =
        noteDao.observeFolders().map { list -> list.map { it.toModel() } }

    /** Создаёт папку (id = 0) или переименовывает/двигает существующую. Возвращает id. */
    suspend fun saveFolder(folder: NoteFolder): Long =
        if (folder.id == 0L) {
            noteDao.insertFolder(NoteFolderEntity(name = folder.name, sortOrder = folder.sortOrder, createdAt = Instant.now()))
        } else {
            noteDao.updateFolder(folder.id, folder.name, folder.sortOrder)
            folder.id
        }

    /** Удаляет папку; её заметки остаются без папки. */
    suspend fun deleteFolder(id: Long) = noteDao.deleteFolder(id)
}

/** Сколько символов превью показывать на карточке: ~6 строк по ~23 символа. */
private const val PREVIEW_CHARS = 150
