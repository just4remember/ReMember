package dev.chaik.remember.core.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.net.toUri
import dev.chaik.remember.R

/** Звук будильника: громкость «Будильник» телефона, а не мультимедиа. */
val AlarmAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/**
 * Включить мелодию будильника по кругу. Своя мелодия могла пропасть (файл удалили), а до первой
 * разблокировки после перезагрузки файлы телефона недоступны — тогда мелодия телефона или, в крайнем
 * случае, сигнал из самого приложения: без звука будильник не остаётся.
 */
fun playAlarmSound(context: Context, ringtone: String?, wakeLock: Boolean = false): MediaPlayer? {
    val sources = listOfNotNull(
        ringtone?.toUri(),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        "android.resource://${context.packageName}/${R.raw.alarm_fallback}".toUri(),
    )
    return sources.firstNotNullOfOrNull { play(context, it, wakeLock) }
}

private fun play(context: Context, uri: Uri, wakeLock: Boolean): MediaPlayer? = try {
    MediaPlayer().apply {
        setAudioAttributes(AlarmAudioAttributes)
        if (wakeLock) setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
        setDataSource(context, uri)
        isLooping = true
        prepare()
        start()
    }
} catch (e: Exception) {
    Log.w("AlarmSound", "Не удалось включить мелодию $uri", e)
    null
}

/**
 * Проверка громкости в настройках будильника: выбранная мелодия на громкости будильника,
 * сама замолкает через [PREVIEW_MS]. Громкость меняется ползунком — это громкость «Будильник» телефона.
 */
class AlarmSoundPreview(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    var onStopped: () -> Unit = {}

    val playing: Boolean get() = player != null
    val maxVolume: Int get() = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    val minVolume: Int get() = audio.getStreamMinVolume(AudioManager.STREAM_ALARM)
    var volume: Int
        get() = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        set(value) = audio.setStreamVolume(AudioManager.STREAM_ALARM, value.coerceIn(minVolume, maxVolume), 0)

    fun play(ringtone: String?) {
        stop()
        player = playAlarmSound(context, ringtone)
        handler.postDelayed(::stop, PREVIEW_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        val wasPlaying = player != null
        player?.runCatching { stop(); release() }
        player = null
        if (wasPlaying) onStopped()
    }

    private companion object {
        const val PREVIEW_MS = 10_000L
    }
}
