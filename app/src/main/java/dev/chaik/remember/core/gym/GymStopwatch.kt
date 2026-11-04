package dev.chaik.remember.core.gym

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import dev.chaik.remember.MainActivity
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.formatDuration
import dev.chaik.remember.core.reminders.OpenTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Секундомер подхода: какое упражнение в какой тренировке и сколько натикало. */
@Serializable
data class GymStopwatchState(
    val workoutId: Long,
    /** Упражнение внутри тренировки (WorkoutExercise.id). */
    val entryId: Long,
    val exerciseName: String,
    /** Когда запущен (мс с 1970); null — на паузе. */
    val startedAt: Long? = null,
    /** Сколько набежало до последней паузы. */
    val accumulatedMs: Long = 0,
) {
    val running: Boolean get() = startedAt != null

    /** Показан, но ещё не запущен: ждёт «Старт». */
    val ready: Boolean get() = startedAt == null && accumulatedMs == 0L

    fun elapsed(now: Long = System.currentTimeMillis()): Long =
        accumulatedMs + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
}

/**
 * Один секундомер подхода на всё приложение. Живёт дольше страницы тренировки: ушли с неё —
 * он висит в шторке, как секундомер из «Времени», с паузой и продолжением. Состояние
 * сохраняется, поэтому переживает и закрытие приложения.
 */
class GymStopwatch(private val context: Context) {
    private val prefs = context.getSharedPreferences("gym_stopwatch", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<GymStopwatchState?> = _state

    /** Открыта ли страница этой тренировки: пока да, уведомление не нужно — секундомер на экране. */
    private var shownWorkout: Long? = null

    /** Показать секундомер упражнения; считать он начнёт по [resume] («Старт»). */
    fun open(workoutId: Long, entryId: Long, name: String) = save(GymStopwatchState(workoutId, entryId, name))

    fun pause() {
        val current = _state.value ?: return
        if (!current.running) return
        save(current.copy(startedAt = null, accumulatedMs = current.elapsed()))
    }

    fun resume() {
        val current = _state.value ?: return
        if (current.running) return
        save(current.copy(startedAt = System.currentTimeMillis()))
    }

    /** Остановить и забыть; вернёт, сколько секунд набежало (не меньше одной). */
    fun finish(): Int? {
        val current = _state.value ?: return null
        save(null)
        return (current.elapsed() / 1000).toInt().coerceAtLeast(1)
    }

    fun cancel() = save(null)

    fun setScreenOpen(workoutId: Long?) {
        shownWorkout = workoutId
        refreshNotification()
    }

    private fun save(state: GymStopwatchState?) {
        _state.value = state
        prefs.edit { if (state == null) remove(KEY) else putString(KEY, json.encodeToString(GymStopwatchState.serializer(), state)) }
        refreshNotification()
    }

    private fun read(): GymStopwatchState? =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(GymStopwatchState.serializer(), it) }.getOrNull() }

    private fun refreshNotification() {
        val manager = NotificationManagerCompat.from(context)
        val current = _state.value
        // Не запущенный ни разу в шторке не нужен: считать нечего
        if (current == null || current.ready || current.workoutId == shownWorkout) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(current.exerciseName)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(open(current.workoutId))
        if (current.running) {
            // Время считает сам Android — уведомление не нужно обновлять каждую секунду
            builder.setWhen(System.currentTimeMillis() - current.elapsed()).setUsesChronometer(true).setShowWhen(true)
                .addAction(0, context.getString(R.string.time_pause), action(ACTION_PAUSE))
        } else {
            builder.setContentText(context.getString(R.string.gym_stopwatch_paused, formatDuration((current.elapsed() / 1000).toInt())))
                .setShowWhen(false)
                .addAction(0, context.getString(R.string.time_resume), action(ACTION_RESUME))
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Уведомления запрещены — секундомер всё равно идёт, просто без шторки
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_gym_stopwatch), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    private fun open(workoutId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        OpenTarget.put(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            OpenTarget.Workout(workoutId),
        ),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(name: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        if (name == ACTION_PAUSE) REQUEST_PAUSE else REQUEST_RESUME,
        Intent(context, GymStopwatchReceiver::class.java).setAction(name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        internal const val ACTION_PAUSE = "dev.chaik.remember.GYM_STOPWATCH_PAUSE"
        internal const val ACTION_RESUME = "dev.chaik.remember.GYM_STOPWATCH_RESUME"
        private const val KEY = "state"
        private const val CHANNEL = "gym_stopwatch"
        private const val NOTIFICATION_ID = 101
        private const val REQUEST_OPEN = 7101
        private const val REQUEST_PAUSE = 7102
        private const val REQUEST_RESUME = 7103
    }
}

/** Кнопки «Пауза» и «Продолжить» в уведомлении секундомера подхода. */
class GymStopwatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stopwatch = context.appContainer.gymStopwatch
        when (intent.action) {
            GymStopwatch.ACTION_PAUSE -> stopwatch.pause()
            GymStopwatch.ACTION_RESUME -> stopwatch.resume()
        }
    }
}
