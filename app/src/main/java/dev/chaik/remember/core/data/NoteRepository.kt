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
        combine(noteDao.observeSummaries(), tagDao.observeAllNoteTags(), noteDao.observeFolders()) { rows, tagRows, folders ->
            val tags = tagRows.groupBy({ it.noteId }, { Tag(it.tagId, it.name) })
            val protectedFolders = folders.filter { it.isProtected }.map { it.id }.toSet()
            rows.map { row ->
                val inProtectedFolder = row.folderId in protectedFolders
                val hidden = row.isProtected || inProtectedFolder
                NoteSummary(
                    id = row.id,
                    title = row.title,
                    preview = if (hidden) "" else MarkdownPreview.plain(row.head, maxChars = PREVIEW_CHARS),
                    coverPhotoId = row.coverPhotoId.takeUnless { hidden },
                    folderId = row.folderId,
                    pinned = row.pinned,
                    isProtected = row.isProtected,
                    tags = if (hidden) emptyList() else tags[row.id].orEmpty(),
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    sortOrder = row.sortOrder,
                    inProtectedFolder = inProtectedFolder,
                )
            }
        }.flowOn(Dispatchers.Default)

    /** Заметка один раз (для открытия редактора); null — нет такой. */
    suspend fun get(id: Long): Note? =
        noteDao.get(id)?.let { note -> note.toModel(tagDao.observeForNote(id).first()) }

    /** Закрепить или открепить, не трогая дату изменения. */
    suspend fun setPinned(id: Long, pinned: Boolean) = noteDao.setPinned(id, pinned)

    /** Ручной порядок: заметки [ids] встают одна за другой, остальные — как были. */
    suspend fun reorder(ids: List<Long>) = db.withTransaction {
        ids.forEachIndexed { index, id -> noteDao.setSortOrder(id, index) }
    }

    /**
     * Перенести в папку (null — без папки), не трогая дату изменения. Защита следует
     * [protectionAfterMove]: в защищённой папке своя защита снимается, а из неё заметка
     * выходит защищённой.
     */
    suspend fun moveToFolder(id: Long, folderId: Long?) = db.withTransaction {
        val note = noteDao.get(id) ?: return@withTransaction
        noteDao.setFolder(id, folderId)
        val protect = protectionAfterMove(note.isProtected, isFolderProtected(note.folderId), isFolderProtected(folderId))
        if (protect != note.isProtected) noteDao.setProtected(id, protect)
    }

    /** Защищена ли папка; null (без папки) — нет. */
    suspend fun isFolderProtected(folderId: Long?): Boolean =
        folderId != null && noteDao.isFolderProtected(folderId) == true

    /** Заметки папки; null — заметки без папки. */
    fun observeInFolder(folderId: Long?): Flow<List<Note>> =
        noteDao.observeInFolder(folderId).map { list -> list.map { it.toModel() } }

    /** Заметка вместе с тегами; null — удалена. */
    fun observe(id: Long): Flow<Note?> =
        combine(noteDao.observe(id), tagDao.observeForNote(id)) { note, tags -> note?.toModel(tags) }

    /** Создаёт заметку (id = 0) или обновляет существующую. Возвращает id. */
    suspend fun save(note: Note): Long = db.withTransaction {
        val now = Instant.now()
        // В защищённой папке заметка сама не защищена — защищает папка
        @Suppress("NAME_SHADOWING")
        val note = if (note.isProtected && isFolderProtected(note.folderId)) note.copy(isProtected = false) else note
        val id = if (note.id == 0L) {
            // Новая — первой в ручном порядке
            val first = (noteDao.minSortOrder() ?: 0) - 1
            noteDao.insert(note.toEntity(createdAt = now, updatedAt = now).copy(sortOrder = first))
        } else {
            val saved = noteDao.get(note.id)
            noteDao.update(note.toEntity(createdAt = saved?.createdAt ?: now, updatedAt = now).copy(sortOrder = saved?.sortOrder ?: 0))
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
            noteDao.insertFolder(
                NoteFolderEntity(name = folder.name, sortOrder = folder.sortOrder, createdAt = Instant.now(), isProtected = folder.isProtected),
            )
        } else {
            noteDao.updateFolder(folder.id, folder.name, folder.sortOrder)
            folder.id
        }

    /**
     * Удаляет папку; её заметки остаются без папки. Заметки из защищённой папки при этом
     * становятся защищёнными — чтобы скрытое не оказалось на виду.
     */
    suspend fun deleteFolder(id: Long) = db.withTransaction {
        if (isFolderProtected(id)) noteDao.setProtectedInFolder(id, true)
        noteDao.deleteFolder(id)
    }

    /**
     * Защитить папку или снять защиту. Защищённая папка прячет свои заметки сама, поэтому
     * их собственная защита снимается; после снятия защиты с папки они обычные.
     */
    suspend fun setFolderProtected(id: Long, isProtected: Boolean) = db.withTransaction {
        noteDao.setFolderProtected(id, isProtected)
        noteDao.setProtectedInFolder(id, false)
    }
}

/**
 * Своя защита заметки после переноса из папки (защищена ли [from]) в папку [to]:
 * в защищённую — снимается (защищает папка), из защищённой в обычную — включается,
 * между обычными — как была.
 */
fun protectionAfterMove(wasProtected: Boolean, from: Boolean, to: Boolean): Boolean = when {
    to -> false
    from -> true
    else -> wasProtected
}

/** Сколько символов превью показывать на карточке: ~6 строк по ~23 символа. */
private const val PREVIEW_CHARS = 150
