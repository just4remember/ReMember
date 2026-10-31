package dev.chaik.remember.core.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.chaik.remember.MainActivity
import dev.chaik.remember.R
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.feature.time.ring.AlarmActivity

/**
 * Уведомления раздела «Время»: звонок (на весь экран, поверх блокировки) и висящие
 * уведомления идущего таймера и секундомера с кнопками.
 */
class TimeNotifications(private val context: Context, private val store: TimeStore) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Можно ли показывать звонок на весь экран поверх блокировки (с Android 14 — по разрешению). */
    val canUseFullScreen: Boolean get() = manager.canUseFullScreenIntent()

    private fun ensureChannels() {
        if (manager.getNotificationChannel(RING_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(RING_CHANNEL, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                    // Звук и вибрацию даёт сам звонок — у уведомления их нет
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        if (manager.getNotificationChannel(LIVE_CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(LIVE_CHANNEL, context.getString(R.string.channel_time_live), NotificationManager.IMPORTANCE_LOW).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }
    }

    /** Уведомление идущего звонка — с ним работает служба звонка. */
    fun ring(session: RingSession): Notification {
        ensureChannels()
        val screen = PendingIntent.getActivity(
            context,
            REQUEST_RING_SCREEN,
            Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when {
            session.kind == RingKind.TIMER -> context.getString(R.string.time_timer_done)
            session.again -> context.getString(R.string.time_ring_again)
            else -> session.alarm?.label?.takeIf { it.isNotBlank() } ?: context.getString(R.string.time_alarm)
        }
        val builder = NotificationCompat.Builder(context, RING_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(session.alarm?.time?.toString().orEmpty())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
        when {
            // Усиленный выключается только игрой — из шторки его можно лишь открыть
            session.hard -> builder.addAction(0, context.getString(R.string.time_open_game), screen)
            else -> {
                if (session.canSnooze) builder.addAction(0, context.getString(R.string.time_snooze), broadcast(AlarmReceiver.ACTION_SNOOZE, REQUEST_SNOOZE))
                builder.addAction(0, context.getString(if (session.kind == RingKind.TIMER) R.string.time_stop else R.string.time_dismiss), broadcast(AlarmReceiver.ACTION_DISMISS, REQUEST_DISMISS))
            }
        }
        return builder.build()
    }

    /** Телефон был выключен слишком долго: звонить уже поздно, но сказать об этом надо. */
    fun missed(alarm: Alarm) {
        ensureChannels()
        post(
            ID_MISSED,
            NotificationCompat.Builder(context, RING_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(context.getColor(R.color.notification_accent))
                .setContentTitle(context.getString(R.string.time_alarm_missed, alarm.time.toString()))
                .setContentText(context.getString(R.string.time_alarm_missed_text))
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Висящие уведомления таймера и секундомера — по их текущему состоянию. */
    fun refreshLive() {
        val notify = NotificationManagerCompat.from(context)
        if (!notify.areNotificationsEnabled()) return
        ensureChannels()
        val now = System.currentTimeMillis()

        val timer = store.timer.value
        if (timer.idle) {
            notify.cancel(ID_TIMER)
        } else {
            val builder = live(context.getString(R.string.time_tab_timer))
            if (timer.running) {
                builder.setWhen(timer.endsAt ?: now).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                    .addAction(0, context.getString(R.string.time_pause), broadcast(AlarmReceiver.ACTION_TIMER_PAUSE, REQUEST_TIMER_PAUSE))
            } else {
                val left = formatDuration(timer.left(now))
                builder.setContentText(context.getString(R.string.time_paused_left, left))
                    .setShortCriticalText(left)
                    .addAction(0, context.getString(R.string.time_resume), broadcast(AlarmReceiver.ACTION_TIMER_RESUME, REQUEST_TIMER_RESUME))
            }
            builder.addAction(0, context.getString(R.string.time_reset), broadcast(AlarmReceiver.ACTION_TIMER_RESET, REQUEST_TIMER_RESET))
            post(ID_TIMER, builder.build())
        }

        val stopwatch = store.stopwatch.value
        if (stopwatch.idle) {
            notify.cancel(ID_STOPWATCH)
        } else {
            val builder = live(context.getString(R.string.time_tab_stopwatch))
            if (stopwatch.running) {
                builder.setWhen(now - stopwatch.elapsed(now)).setUsesChronometer(true).setShowWhen(true)
                    .addAction(0, context.getString(R.string.time_pause), broadcast(AlarmReceiver.ACTION_STOPWATCH_PAUSE, REQUEST_SW_PAUSE))
            } else {
                val elapsed = formatDuration(stopwatch.elapsed(now))
                builder.setContentText(context.getString(R.string.time_paused_left, elapsed))
                    .setShortCriticalText(elapsed)
                    .addAction(0, context.getString(R.string.time_resume), broadcast(AlarmReceiver.ACTION_STOPWATCH_RESUME, REQUEST_SW_RESUME))
            }
            builder.addAction(0, context.getString(R.string.time_reset), broadcast(AlarmReceiver.ACTION_STOPWATCH_RESET, REQUEST_SW_RESET))
            post(ID_STOPWATCH, builder.build())
        }
    }

    private fun live(title: String) = NotificationCompat.Builder(context, LIVE_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(context.getColor(R.color.notification_accent))
        .setContentTitle(title)
        .setOngoing(true)
        // Live Update: время прямо в строке состояния. Своя вёрстка, даже только развёрнутая, это отключает (проверено)
        .setRequestPromotedOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                OpenTarget.put(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), OpenTarget.Time),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    private fun post(id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Разрешение на уведомления отозвали
        }
    }

    private fun broadcast(action: String, request: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        request,
        Intent(context, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val RING_CHANNEL = "alarm_ring"
        const val LIVE_CHANNEL = "time_live"

        /** Каналы раздела — их не трогает пересоздание каналов напоминаний. */
        val Channels = setOf(RING_CHANNEL, LIVE_CHANNEL)

        const val ID_RING = 300
        private const val ID_TIMER = 301
        private const val ID_STOPWATCH = 302
        private const val ID_MISSED = 303
        private const val REQUEST_RING_SCREEN = 310
        private const val REQUEST_SNOOZE = 311
        private const val REQUEST_DISMISS = 312
        private const val REQUEST_TIMER_PAUSE = 313
        private const val REQUEST_TIMER_RESUME = 314
        private const val REQUEST_TIMER_RESET = 315
        private const val REQUEST_SW_PAUSE = 316
        private const val REQUEST_SW_RESUME = 317
        private const val REQUEST_SW_RESET = 318
        private const val REQUEST_OPEN = 319
    }
}

/** 1:05:09 или 5:09. */
fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
