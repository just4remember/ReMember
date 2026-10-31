package dev.chaik.remember.core.photos

import dev.chaik.remember.core.data.DayRepository
import dev.chaik.remember.core.data.NoteRepository
import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.editor.EditorDocument
import dev.chaik.remember.core.model.PhotoOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate

/** Фото в галерее: сведения о нём и где оно стоит. */
data class LibraryPhoto(
    val id: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val createdAt: Instant,
    val usages: List<PhotoUsage>,
)

/** Где стоит фото: запись дня или заметка. */
sealed interface PhotoUsage {
    data class Day(val date: LocalDate) : PhotoUsage
    data class Note(val id: Long, val title: String) : PhotoUsage
}

/**
 * Все фото приложения для галереи в «Данных»: список с тем, где каждое стоит,
 * удаление (вместе со ссылкой в записи) и копия в галерею телефона.
 */
class PhotoLibrary(
    db: ReMemberDatabase,
    private val storage: PhotoStorage,
    private val days: DayRepository,
    private val notes: NoteRepository,
) {
    private val photoDao = db.photoDao()

    private val removedEvents = MutableSharedFlow<Set<String>>(extraBufferCapacity = 8)

    /**
     * Фото удалили из галереи. Открытые редакторы убирают их у себя — иначе при следующем
     * сохранении запись вернула бы ссылку на фото, которого уже нет.
     */
    val removed: SharedFlow<Set<String>> = removedEvents.asSharedFlow()

    /** Все фото, новые сверху; фото из защищённых заметок в галерею не попадают. */
    fun observe(): Flow<List<LibraryPhoto>> =
        combine(photoDao.observeAll(), photoDao.observeRefs(), notes.observeAll()) { photos, refs, allNotes ->
            val titles = allNotes.associate { it.id to it.title }
            val byPhoto = refs.groupBy { it.photoId }
            val hidden = allNotes.filter { it.isProtected }.map { it.id }.toSet()
            photos.filterNot { photo ->
                byPhoto[photo.id].orEmpty().any { it.ownerType == PhotoOwner.NOTE && it.ownerId in hidden }
            }.map { photo ->
                LibraryPhoto(
                    id = photo.id,
                    width = photo.width,
                    height = photo.height,
                    sizeBytes = photo.sizeBytes,
                    createdAt = photo.createdAt,
                    usages = byPhoto[photo.id].orEmpty().map { ref ->
                        when (ref.ownerType) {
                            PhotoOwner.DAY_ENTRY -> PhotoUsage.Day(LocalDate.ofEpochDay(ref.ownerId))
                            PhotoOwner.NOTE -> PhotoUsage.Note(ref.ownerId, titles[ref.ownerId].orEmpty())
                        }
                    },
                )
            }
        }

    /** Удалить фото отовсюду: из записей, где оно стоит, из базы и с диска. */
    suspend fun delete(photoId: String) {
        val ids = setOf(photoId)
        // Каждая запись сохраняется своей транзакцией (внутри save); фото удаляем последним —
        // если что-то прервётся, останется ссылка на живое фото, а не дыра в записи
        for (ref in photoDao.refsOf(photoId)) {
            when (ref.ownerType) {
                PhotoOwner.DAY_ENTRY -> {
                    val date = LocalDate.ofEpochDay(ref.ownerId)
                    val entry = days.observe(date).first() ?: continue
                    val text = EditorDocument.removePhotos(entry.text, ids)
                    // Кроме фото в записи ничего не было — записи больше нет
                    if (text.isBlank() && entry.mood == null && entry.tags.isEmpty()) days.delete(date) else days.save(entry.copy(text = text))
                }
                PhotoOwner.NOTE -> {
                    val note = notes.get(ref.ownerId) ?: continue
                    notes.save(note.copy(text = EditorDocument.removePhotos(note.text, ids)))
                }
            }
        }
        storage.delete(photoId)
        removedEvents.emit(ids)
    }

    /** Копия в галерею телефона; false — не получилось. */
    suspend fun exportToGallery(photoId: String): Boolean = storage.exportToGallery(photoId)
}
