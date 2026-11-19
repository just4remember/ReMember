package dev.chaik.remember.core.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * Видео для записей: сжатие до 720p (H.264 + AAC, около 2 Мбит/с — минута ≈ 15 МБ),
 * кадр-обложка и длительность. Сжимает Media3 Transformer аппаратным кодеком телефона.
 */
@OptIn(UnstableApi::class)
internal object VideoImport {
    /** Короткая сторона после сжатия: больше не нужно для заметок, качество как в мессенджерах. */
    private const val SHORT_SIDE = 720
    private const val VIDEO_BITRATE = 2_000_000

    /** Сведения о ролике: размер на экране (с учётом поворота) и длительность. */
    class Info(val width: Int, val height: Int, val rotation: Int, val durationMs: Long)

    fun info(context: Context, source: Uri): Info = retriever(context, source) { it.info() }

    fun info(file: File): Info = MediaMetadataRetriever().run {
        try {
            setDataSource(file.path)
            info()
        } finally {
            release()
        }
    }

    /**
     * Сжать [source] в [target]. [onProgress] — доля от 0 до 1, на главном потоке.
     * Transformer работает только с потоком, у которого есть Looper, — поэтому главный.
     */
    suspend fun compress(context: Context, source: Uri, target: File, onProgress: (Float) -> Unit) {
        val info = info(context, source)
        val big = min(info.width, info.height) > SHORT_SIDE
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                lateinit var transformer: Transformer
                val holder = ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                        handler.postDelayed(this, 200)
                    }
                }
                transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
                            .build(),
                    )
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(poll)
                            onProgress(1f)
                            cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            handler.removeCallbacks(poll)
                            target.delete()
                            cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                // Уменьшаем только большие ролики: маленькие не растягиваем
                val effects = if (big) Effects(emptyList(), listOf(Presentation.createForShortSide(SHORT_SIDE))) else Effects.EMPTY
                val item = EditedMediaItem.Builder(MediaItem.fromUri(source)).setEffects(effects).build()
                transformer.start(item, target.path)
                handler.post(poll)
                cont.invokeOnCancellation {
                    handler.removeCallbacks(poll)
                    transformer.cancel()
                    target.delete()
                }
            }
        }
    }

    /**
     * Кадр-обложка из начала ролика, повёрнутый как на экране. Одни телефоны отдают кадр
     * уже повёрнутым, другие — нет: сверяем стороны с ожидаемыми и доворачиваем сами.
     */
    fun poster(file: File): Bitmap = MediaMetadataRetriever().run {
        try {
            setDataSource(file.path)
            val info = info()
            val frame = getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("Не удалось взять кадр из видео")
            val wantLandscape = info.width >= info.height
            val isLandscape = frame.width >= frame.height
            if (info.rotation % 180 != 0 && wantLandscape != isLandscape && frame.width != frame.height) {
                val rotated = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(info.rotation.toFloat()) }, true)
                frame.recycle()
                rotated
            } else {
                frame
            }
        } finally {
            release()
        }
    }

    private fun MediaMetadataRetriever.info(): Info {
        val w = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val duration = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        // Ширина и высота — как на экране: повёрнутый на 90° ролик меняет их местами
        return if (rotation % 180 != 0) Info(h, w, rotation, duration) else Info(w, h, rotation, duration)
    }

    private fun <T> retriever(context: Context, source: Uri, block: (MediaMetadataRetriever) -> T): T =
        MediaMetadataRetriever().run {
            try {
                setDataSource(context, source)
                block(this)
            } finally {
                release()
            }
        }
}
