package dev.chaik.remember.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.chaik.remember.appContainer

/**
 * Системные будильники раздела «Время» (звонок, конец «отложить», повтор усиленного, конец
 * таймера), кнопки в уведомлениях — и перезагрузка или смена времени: тогда всё ставится заново.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = context.appContainer.alarmController
        val id = intent.getLongExtra(EXTRA_ID, 0)
        when (intent.action) {
            ACTION_RING -> controller.onAlarm(id)
            ACTION_SNOOZE_END -> controller.onSnoozeEnd(id)
            ACTION_AGAIN -> controller.onAgain()
            ACTION_TIMER -> controller.onTimerEnd()
            ACTION_DISMISS -> controller.dismiss()
            ACTION_SNOOZE -> controller.snooze()
            ACTION_TIMER_PAUSE -> controller.pauseTimer()
            ACTION_TIMER_RESUME -> controller.resumeTimer()
            ACTION_TIMER_RESET -> controller.resetTimer()
            ACTION_STOPWATCH_PAUSE -> controller.pauseStopwatch()
            ACTION_STOPWATCH_RESUME -> controller.startStopwatch()
            ACTION_STOPWATCH_RESET -> controller.resetStopwatch()
            // Первое событие приходит ещё до разблокировки, второе — после неё; повторно ничего не зазвонит
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED -> controller.rescheduleAll(AlarmController.Reschedule.BOOT)
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> controller.rescheduleAll(AlarmController.Reschedule.TIME_CHANGED)
            // Обновление приложения
            else -> controller.rescheduleAll()
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val ACTION_RING = "dev.chaik.remember.time.RING"
        const val ACTION_SNOOZE_END = "dev.chaik.remember.time.SNOOZE_END"
        const val ACTION_AGAIN = "dev.chaik.remember.time.AGAIN"
        const val ACTION_TIMER = "dev.chaik.remember.time.TIMER"
        const val ACTION_DISMISS = "dev.chaik.remember.time.DISMISS"
        const val ACTION_SNOOZE = "dev.chaik.remember.time.SNOOZE"
        const val ACTION_TIMER_PAUSE = "dev.chaik.remember.time.TIMER_PAUSE"
        const val ACTION_TIMER_RESUME = "dev.chaik.remember.time.TIMER_RESUME"
        const val ACTION_TIMER_RESET = "dev.chaik.remember.time.TIMER_RESET"
        const val ACTION_STOPWATCH_PAUSE = "dev.chaik.remember.time.SW_PAUSE"
        const val ACTION_STOPWATCH_RESUME = "dev.chaik.remember.time.SW_RESUME"
        const val ACTION_STOPWATCH_RESET = "dev.chaik.remember.time.SW_RESET"
    }
}
