package dev.chaik.remember.core.health

import dev.chaik.remember.core.widgets.HealthWidget
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import dev.chaik.remember.R
import dev.chaik.remember.core.reminders.Message
import dev.chaik.remember.core.reminders.NotificationIds
import dev.chaik.remember.core.reminders.Notifier
import dev.chaik.remember.core.reminders.NotificationType
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.core.reminders.QuietHours
import dev.chaik.remember.core.reminders.QuietMode
import dev.chaik.remember.core.reminders.ReminderReceiver
import dev.chaik.remember.core.reminders.ReminderSettingsStore
import dev.chaik.remember.core.settings.AppSettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime

/** Что делать при очередной проверке шагов. */
enum class StepsGoalAction {
    /** Ничего: цель не достигнута, уже поздравили или уведомления выключены. */
    NONE,

    /** Прислать уведомление. */
    NOTIFY,

    /** Прислать без звука (тихие часы, режим «без звука»). */
    NOTIFY_SILENT,

    /** Тихие часы, режим «пропустить»: не присылать, но и больше не напоминать сегодня. */
    SKIP,
}

object StepsGoalRules {
    /**
     * Решение по шагам за сегодня. Тихие часы — как у напоминаний: «отложить» значит подождать
     * (следующая проверка после тихих часов пришлёт), «без звука» — прислать тихо, «пропустить» — не присылать.
     */
    fun decide(
        steps: Long,
        goal: Long,
        alreadyNotified: Boolean,
        notificationsEnabled: Boolean,
        quiet: QuietHours,
        now: LocalTime,
    ): StepsGoalAction = decide(goal > 0 && steps >= goal, alreadyNotified, notificationsEnabled, quiet, now)

    /** То же для любой цели: [reached] — достигнута ли она. */
    fun decide(
        reached: Boolean,
        alreadyNotified: Boolean,
        notificationsEnabled: Boolean,
        quiet: QuietHours,
        now: LocalTime,
    ): StepsGoalAction = when {
        !notificationsEnabled || alreadyNotified || !reached -> StepsGoalAction.NONE
        !quiet.contains(now) -> StepsGoalAction.NOTIFY
        else -> when (quiet.mode) {
            QuietMode.POSTPONE -> StepsGoalAction.NONE
            QuietMode.SILENT -> StepsGoalAction.NOTIFY_SILENT
            QuietMode.SKIP -> StepsGoalAction.SKIP
        }
    }

    /** Выполнены ли сразу все три цели «Здоровья». */
    fun allGoalsMet(activity: DayActivity, goals: HealthGoals): Boolean =
        goals.steps > 0 && goals.activeMinutes > 0 && goals.calories > 0 &&
            activity.steps >= goals.steps &&
            activity.activeMinutes >= goals.activeMinutes &&
            activity.calories >= goals.calories
}

/**
 * Уведомления «цель по шагам выполнена» и «все цели выполнены». Android не сообщает о новых шагах сам, поэтому
 * раз в [AppSettings.stepsGoalCheckMinutes][dev.chaik.remember.core.settings.AppSettings.stepsGoalCheckMinutes]
 * минут приложение просыпается и смотрит активность за сегодня в Health Connect (нужно разрешение
 * на чтение в фоне). Каждое поздравление — один раз в день; если шаги и все цели выполнены
 * к одной проверке, приходит только «все цели».
 */
class StepsGoalWatcher(
    private val context: Context,
    private val settings: AppSettingsStore,
    private val reminders: ReminderSettingsStore,
    private val goals: HealthGoalsStore,
    private val health: HealthConnectSource,
    private val notifier: Notifier,
    /** Вес для оценки калорий, как на странице «Здоровье». */
    private val bodyWeightKg: suspend () -> Double,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()

    /** Поставить следующую проверку по настройке или снять, если выключено. */
    fun schedule() {
        val minutes = settings.settings.value.stepsGoalCheckMinutes
        val intent = alarmIntent()
        if (minutes <= 0) {
            alarms.cancel(intent)
            return
        }
        // Неточный будильник: Android сам сдвигает его ради батареи — для поздравления это не важно
        alarms.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + minutes * 60_000L,
            intent,
        )
    }

    suspend fun check() = lock.withLock {
        try {
            if (settings.settings.value.stepsGoalCheckMinutes <= 0) return@withLock
            val today = LocalDate.now()
            val day = today.toEpochDay()
            val allNotified = prefs.getLong(KEY_ALL_NOTIFIED_DAY, -1) == day
            if (allNotified || health.availability != HealthAvailability.AVAILABLE) return@withLock

            val targets = goals.goals.value
            val activity = health.day(today, bodyWeightKg())
            HealthWidget.push(context, activity)
            val reminderSettings = reminders.settings.value
            val now = LocalTime.now()
            val allAction = StepsGoalRules.decide(
                reached = StepsGoalRules.allGoalsMet(activity, targets),
                alreadyNotified = false,
                notificationsEnabled = reminderSettings.enabled,
                quiet = reminderSettings.quiet,
                now = now,
            )
            val stepsAction = StepsGoalRules.decide(
                steps = activity.steps,
                goal = targets.steps,
                alreadyNotified = prefs.getLong(KEY_NOTIFIED_DAY, -1) == day,
                notificationsEnabled = reminderSettings.enabled,
                quiet = reminderSettings.quiet,
                now = now,
            )
            Log.i(TAG, "Шаги: ${activity.steps} из ${targets.steps} → $stepsAction, все цели → $allAction")
            val number = NumberFormat.getIntegerInstance()
            if (allAction != StepsGoalAction.NONE) {
                post(
                    action = allAction,
                    id = NotificationIds.HEALTH_GOALS,
                    title = context.getString(R.string.notify_goals_title),
                    text = context.getString(R.string.notify_goals_text),
                )
                // Все цели включают шаги: второе поздравление уже ни к чему
                prefs.edit {
                    putLong(KEY_ALL_NOTIFIED_DAY, day)
                    putLong(KEY_NOTIFIED_DAY, day)
                }
            } else if (stepsAction != StepsGoalAction.NONE) {
                post(
                    action = stepsAction,
                    id = NotificationIds.STEPS,
                    title = context.getString(R.string.notify_steps_title),
                    text = context.getString(R.string.notify_steps_text, number.format(activity.steps), number.format(targets.steps)),
                )
                prefs.edit { putLong(KEY_NOTIFIED_DAY, day) }
            }
        } catch (e: Exception) {
            // Нет разрешения на чтение в фоне или Health Connect недоступен — попробуем в следующий раз
            Log.w(TAG, "Не удалось проверить шаги", e)
        } finally {
            schedule()
        }
    }

    private fun post(action: StepsGoalAction, id: Int, title: String, text: String) {
        if (action == StepsGoalAction.SKIP) return
        val sound = reminders.settings.value.sound
        notifier.ensureChannels(sound)
        notifier.post(
            Message(
                id = id,
                type = NotificationType.HEALTH,
                title = title,
                text = text,
                target = OpenTarget.Health,
                silent = action == StepsGoalAction.NOTIFY_SILENT,
            ),
            sound,
        )
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_STEPS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val TAG = "StepsGoal"
        const val PREFS = "steps_goal"
        const val KEY_NOTIFIED_DAY = "notified_day"
        const val KEY_ALL_NOTIFIED_DAY = "all_notified_day"
        const val REQUEST_ALARM = 1
    }
}
