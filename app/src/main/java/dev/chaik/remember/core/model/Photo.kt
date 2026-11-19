package dev.chaik.remember.core.model

/**
 * Сохранённое фото или видео. Файлы — через PhotoStorage.file / thumbnailFile (у видео это
 * кадр-обложка) и videoFile. [durationMs] — длительность видео; null — фото.
 */
data class Photo(
    val id: String,
    val width: Int,
    val height: Int,
    val durationMs: Long? = null,
) {
    val isVideo: Boolean get() = durationMs != null
}

/** «0:42», «12:05», «1:02:03» — длительность видео. */
fun formatVideoDuration(ms: Long): String {
    val total = (ms + 500) / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Чей текст ссылается на фото. */
enum class PhotoOwner { DAY_ENTRY, NOTE }
