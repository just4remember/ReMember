package dev.chaik.remember.core.photos

import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.scale
import dev.chaik.remember.core.database.dao.PhotoDao
import dev.chaik.remember.core.database.entity.PhotoEntity
import dev.chaik.remember.core.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Хранилище фото и видео во внутренней памяти приложения.
 *
 * Видео сжимается до 720p и лежит рядом как `<id>.mp4`; его кадр-обложка — обычное «фото»
 * `<id>.webp` с миниатюрой, поэтому сетки, галерея и ссылки `photo:<id>` работают как с фото.
 *
 * При добавлении фото копируется к себе (оригинал в галерее можно удалить),
 * уменьшается до [MAX_SIDE] по длинной стороне и сохраняется в WebP.
 * Рядом лежит миниатюра [THUMB_SIDE] — её грузят списки и лента, чтобы не тормозить.
 *
 * Папка приложения недоступна другим приложениям и исключена из бэкапа.
 */
class PhotoStorage(
    private val context: Context,
    private val photoDao: PhotoDao,
) {
    private val photosDir = File(context.filesDir, "photos")
    private val thumbsDir = File(photosDir, "thumbs")

    fun file(photoId: String): File = File(photosDir, "$photoId.webp")

    fun thumbnailFile(photoId: String): File = File(thumbsDir, "$photoId.webp")

    /** Ролик видео; у фото такого файла нет. */
    fun videoFile(photoId: String): File = File(photosDir, "$photoId.mp4")

    /** Сжатие видео при вставке: доля от 0 до 1; null — сейчас ничего не сжимается. */
    private val _videoProgress = MutableStateFlow<Float?>(null)
    val videoProgress: StateFlow<Float?> = _videoProgress.asStateFlow()

    /** Сведения о фото (размеры) по id; отсутствующих в ответе нет. */
    suspend fun photos(ids: Collection<String>): Map<String, Photo> =
        if (ids.isEmpty()) emptyMap() else photoDao.getMany(ids).associate { it.id to Photo(it.id, it.width, it.height, it.durationMs) }

    /**
     * Скопировать фото или видео (из галереи, камеры и т. п.) к себе.
     * [onProgress] — доля готового от 0 до 1, на потоке вызывающего.
     */
    suspend fun import(source: Uri, onProgress: (Float) -> Unit = {}): Photo {
        val type = context.contentResolver.getType(source).orEmpty()
        val photo = if (type.startsWith("video/")) {
            importVideo(source) { onProgress(it * 0.95f) }
        } else {
            onProgress(0.15f)
            importImage(source)
        }
        onProgress(1f)
        return photo
    }

    /**
     * Видео: сжать до 720p, взять кадр-обложку и записать как фото с длительностью.
     * Ход сжатия — в [videoProgress].
     */
    private suspend fun importVideo(source: Uri, onProgress: (Float) -> Unit): Photo {
        val id = UUID.randomUUID().toString()
        thumbsDir.mkdirs()
        val temp = File(photosDir, "$id.mp4.tmp")
        try {
            _videoProgress.value = 0f
            VideoImport.compress(context, source, temp) {
                _videoProgress.value = it
                onProgress(it)
            }
        } finally {
            _videoProgress.value = null
        }
        return withContext(Dispatchers.IO) {
            try {
                check(temp.renameTo(videoFile(id))) { "Не удалось сохранить видео" }
                val info = VideoImport.info(videoFile(id))
                val poster = VideoImport.poster(videoFile(id))
                try {
                    val frame = poster.fitInto(MAX_SIDE)
                    writeWebp(frame, file(id), PHOTO_QUALITY)
                    frame.fitInto(THUMB_SIDE).let { thumb ->
                        writeWebp(thumb, thumbnailFile(id), THUMB_QUALITY)
                        if (thumb !== frame) thumb.recycle()
                    }
                    if (frame !== poster) frame.recycle()
                    val entity = PhotoEntity(
                        id = id,
                        width = poster.width,
                        height = poster.height,
                        sizeBytes = file(id).length() + videoFile(id).length(),
                        createdAt = Instant.now(),
                        durationMs = info.durationMs.coerceAtLeast(1),
                    )
                    photoDao.insert(entity)
                    Photo(id, entity.width, entity.height, entity.durationMs)
                } finally {
                    poster.recycle()
                }
            } catch (e: Exception) {
                temp.delete()
                deleteFiles(id)
                throw e
            }
        }
    }

    private suspend fun importImage(source: Uri): Photo = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(source)
        try {
            val id = UUID.randomUUID().toString()
            thumbsDir.mkdirs()
            writeWebp(bitmap, file(id), PHOTO_QUALITY)
            bitmap.fitInto(THUMB_SIDE).let { thumb ->
                writeWebp(thumb, thumbnailFile(id), THUMB_QUALITY)
                if (thumb !== bitmap) thumb.recycle()
            }

            val entity = PhotoEntity(
                id = id,
                width = bitmap.width,
                height = bitmap.height,
                sizeBytes = file(id).length(),
                createdAt = Instant.now(),
            )
            try {
                photoDao.insert(entity)
            } catch (e: Exception) {
                deleteFiles(id)
                throw e
            }
            Photo(id, entity.width, entity.height)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Удалить фото, на которые больше не ссылается ни одна запись.
     * [olderThan] — запас, чтобы не тронуть только что добавленное, ещё не сохранённое фото:
     * запись сохраняется меньше чем через секунду после правки, так что минут хватает с лихвой.
     * Возвращает количество удалённых.
     */
    suspend fun cleanupUnreferenced(olderThan: Duration = UNUSED_GRACE): Int = withContext(Dispatchers.IO) {
        val orphans = photoDao.unreferencedBefore(Instant.now() - olderThan.toJavaDuration())
        if (orphans.isEmpty()) return@withContext 0
        photoDao.delete(orphans.map { it.id })
        orphans.forEach { deleteFiles(it.id) }
        orphans.size
    }

    /** Удалить фото совсем: строку в базе (ссылки уйдут каскадом) и файлы. */
    suspend fun delete(photoId: String) = withContext(Dispatchers.IO) {
        photoDao.delete(listOf(photoId))
        deleteFiles(photoId)
    }

    /**
     * Скопировать фото в общую галерею телефона (Pictures/ReMember) в JPEG — его открывает
     * любое приложение. Разрешения не нужны: своё приложение пишет туда свободно.
     */
    suspend fun exportToGallery(photoId: String): Boolean = withContext(Dispatchers.IO) {
        if (videoFile(photoId).exists()) return@withContext exportVideo(photoId)
        val bitmap = BitmapFactory.decodeFile(file(photoId).path) ?: return@withContext false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ReMember_${photoId.take(8)}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ReMember")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        try {
            val written = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, EXPORT_QUALITY, it) } == true
            if (!written) {
                resolver.delete(uri, null, null)
                return@withContext false
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        } finally {
            bitmap.recycle()
        }
    }

    /** Видео — в Movies/ReMember как есть (MP4 открывает любой плеер). */
    private fun exportVideo(photoId: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ReMember_${photoId.take(8)}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ReMember")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val written = resolver.openOutputStream(uri)?.use { out -> videoFile(photoId).inputStream().use { it.copyTo(out) } } != null
            if (!written) {
                resolver.delete(uri, null, null)
                return false
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun deleteFiles(photoId: String) {
        file(photoId).delete()
        thumbnailFile(photoId).delete()
        videoFile(photoId).delete()
    }

    /** Декодирует сразу в уменьшенном размере — полноразмерный снимок в память не грузится. */
    private fun decodeScaled(source: Uri): Bitmap {
        val imageSource = ImageDecoder.createSource(context.contentResolver, source)
        return ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
            // Поворот по EXIF ImageDecoder применяет сам
            val target = info.size.fitInto(MAX_SIDE)
            decoder.setTargetSize(target.width, target.height)
            // Программный битмап: аппаратный нельзя сжать в файл
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun writeWebp(bitmap: Bitmap, target: File, quality: Int) {
        // Пишем во временный файл и переименовываем: недописанный файл не останется
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)) { "Не удалось сохранить фото" }
        }
        check(temp.renameTo(target)) { "Не удалось сохранить фото" }
    }

    companion object {
        /** Столько живёт фото, которое ни в одной записи не стоит, — потом удаляется само. */
        val UNUSED_GRACE: Duration = 10.minutes

        private const val MAX_SIDE = 2560
        private const val THUMB_SIDE = 480
        private const val PHOTO_QUALITY = 85
        private const val THUMB_QUALITY = 75
        private const val EXPORT_QUALITY = 95
    }
}

/** Размер, вписанный в квадрат [maxSide]; маленькие картинки не увеличиваются. */
internal fun Size.fitInto(maxSide: Int): Size {
    val longest = max(width, height)
    if (longest <= maxSide) return this
    val ratio = maxSide.toFloat() / longest
    return Size((width * ratio).roundToInt().coerceAtLeast(1), (height * ratio).roundToInt().coerceAtLeast(1))
}

private fun Bitmap.fitInto(maxSide: Int): Bitmap {
    val target = Size(width, height).fitInto(maxSide)
    return if (target.width == width && target.height == height) this else scale(target.width, target.height)
}
