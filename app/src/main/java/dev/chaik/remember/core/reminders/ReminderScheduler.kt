package dev.chaik.remember.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.chaik.remember.R
import dev.chaik.remember.core.data.DayRepository
import dev.chaik.remember.core.data.EventRepository
import dev.chaik.remember.core.data.TaskRepository
import dev.chaik.remember.core.data.WorkoutRepository
import dev.chaik.remember.core.model.Event
import dev.chaik.remember.core.model.Task
import dev.chaik.remember.core.raskobki.RaskobkiRepository
import dev.chaik.remember.core.raskobki.raskobkiToday
import dev.chaik.remember.feature.calendar.eventsOn
import dev.chaik.remember.feature.calendar.formatDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Рассылка напоминаний. Будильник Android ставится только на ближайшее напоминание;
 * когда он срабатывает, [check] показывает всё, что подошло, и ставит следующий.
 * [check] же вызывается при любом изменении событий, задач и настроек, при запуске
 * приложения и после перезагрузки телефона — так расписание всегда свежее.
 */
class ReminderScheduler(
    private val context: Context,
    private val store: ReminderSettingsStore,
    private val notifier: Notifier,
    private val days: DayRepository,
    private val events: EventRepository,
    private val tasks: TaskRepository,
    private val workouts: WorkoutRepository,
    private val raskobki: RaskobkiRepository,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    suspend fun check() = lock.withLock {
        val zone = ZoneId.systemDefault()
        val nowInstant = Instant.now()
        val now = LocalDateTime.ofInstant(nowInstant, zone)
        val settings = store.settings.value
        notifier.ensureChannels(settings.sound)

        val allEvents = events.observeAll().first()
        val allTasks = tasks.observeAll().first()
        val workout = workouts.active()
        val active = workout?.start?.let { ActiveWorkout(workout.id, workout.date.atTime(it)) }
        // Висящее уведомление тренировки — вместе с остальными: так оно вернётся и после перезагрузки
        if (workout != null && active != null && settings.enabled) {
            notifier.showWorkout(workout.id, workout.number, active.start.atZone(zone).toInstant())
        } else {
            notifier.hideWorkout()
        }

        // Всё, что подошло с прошлой проверки. Старше GRACE не показываем: телефон был выключен
        // или часы перевели — устаревшее «как прошёл день» утром только мешает
        val last = store.lastCheck?.let { LocalDateTime.ofInstant(it, zone) } ?: now
        val due = ReminderPlanner.plan(maxOf(last, now.minus(GRACE)), now, settings, allEvents, allTasks, active)
        for (planned in due) {
            message(planned, now.toLocalDate(), allEvents, allTasks)?.let { notifier.post(it, settings.sound) }
        }
        store.lastCheck = nowInstant

        if (!settings.enabled) {
            setAlarm(null)
            return@withLock
        }
        // Ничего не нашлось на ближайшие дни — всё равно проснёмся и посмотрим ещё раз:
        // у события может быть «предупредить за месяц»
        val next = ReminderPlanner.plan(now, now.plusDays(LOOKAHEAD_DAYS), settings, allEvents, allTasks, active).firstOrNull()?.at
            ?: now.plusDays(LOOKAHEAD_DAYS)
        setAlarm(next.atZone(zone).toInstant())
    }

    /** Проверочное уведомление со страницы настроек. */
    fun test() {
        val settings = store.settings.value
        notifier.ensureChannels(settings.sound)
        notifier.post(
            Message(
                id = ID_TEST,
                type = NotificationType.DAY,
                title = context.getString(R.string.notify_test_title),
                text = context.getString(R.string.notify_test_text),
                target = OpenTarget.Day,
            ),
            settings.sound,
        )
    }

    /** Текст уведомления; null — показывать уже незачем (запись сделана, задача выполнена…). */
    private suspend fun message(planned: PlannedReminder, today: LocalDate, allEvents: List<Event>, allTasks: List<Task>): Message? =
        when (val r = planned.reminder) {
            is Reminder.DayReport -> {
                val entry = days.observe(r.date).first()
                // Перенесённое тихими часами на следующее утро «как прошёл день» уже не к месту
                if (r.date != today || entry != null && (entry.text.isNotBlank() || entry.mood != null)) {
                    null
                } else {
                    Message(
                        id = ID_DAY,
                        type = NotificationType.DAY,
                        title = context.getString(R.string.notify_day_title),
                        text = context.getString(R.string.notify_day_text),
                        target = OpenTarget.Day,
                        silent = planned.silent,
                    )
                }
            }
            is Reminder.Summary -> if (r.date == today) summary(r.date, allEvents, allTasks, planned.silent) else null
            is Reminder.Raskobki -> if (r.date != today || raskobki.progress.value[raskobkiToday()]?.c == true) {
                null
            } else {
                Message(
                    id = NotificationIds.RASKOBKI,
                    type = NotificationType.GAMES,
                    title = context.getString(R.string.notify_raskobki_title),
                    text = context.getString(R.string.notify_raskobki_text),
                    target = OpenTarget.Raskobki,
                    silent = planned.silent,
                )
            }
            is Reminder.EventAlert -> eventMessage(r, planned.silent, today, allEvents)
            is Reminder.WorkoutRunning -> if (workouts.active()?.id != r.workoutId) {
                null
            } else {
                Message(
                    id = "w${r.workoutId}".hashCode(),
                    type = NotificationType.WORKOUT,
                    title = context.getString(R.string.notify_workout_long_title),
                    text = context.getString(R.string.notify_workout_long_text),
                    target = OpenTarget.Workout(r.workoutId, finish = true),
                    silent = planned.silent,
                )
            }
            is Reminder.TaskDue -> {
                val task = allTasks.firstOrNull { it.id == r.taskId }
                val due = task?.dueTime?.let { time -> task.dueDate?.atTime(time) }
                // Задачу могли выполнить или перенести, пока ждали
                if (task == null || task.done || due != r.due) {
                    null
                } else {
                    Message(
                        id = "t${task.id}".hashCode(),
                        type = NotificationType.TASKS,
                        title = task.title,
                        text = context.getString(R.string.notify_task_text, whenLabel(due, today)),
                        target = OpenTarget.Task(task.id),
                        silent = planned.silent,
                    )
                }
            }
        }

    private fun eventMessage(r: Reminder.EventAlert, silent: Boolean, today: LocalDate, allEvents: List<Event>): Message? {
        val event = allEvents.firstOrNull { it.id == r.eventId } ?: return null
        val day = r.occurrence.toLocalDate()
        // Событие могли удалить или перенести, пока ждали
        if (!event.occursOn(day) || event.time != r.occurrence.toLocalTime()) return null
        val title = event.title.ifBlank { context.getString(R.string.event_untitled) }
        val details = buildList {
            add(whenLabel(r.occurrence, today).replaceFirstChar { it.titlecase() })
            event.ageOn(day)?.let { add(context.getString(R.string.notify_turns, years(it))) }
        }.joinToString(" · ")
        return Message(
            id = "e${event.id}:${r.occurrence}".hashCode(),
            type = NotificationType.EVENTS,
            title = if (event.isBirthday) context.getString(R.string.notify_birthday, title) else title,
            text = if (event.description.isBlank()) details else "$details\n${event.description}",
            target = OpenTarget.Calendar(day),
            silent = silent,
        )
    }

    private fun summary(date: LocalDate, allEvents: List<Event>, allTasks: List<Task>, silent: Boolean): Message? {
        val dayEvents = eventsOn(allEvents, date)
        val dayTasks = allTasks.filter { task -> !task.done && task.dueDate?.let { !it.isAfter(date) } == true }
        if (dayEvents.isEmpty() && dayTasks.isEmpty()) return null
        val lines = dayEvents.map { event ->
            val title = event.title.ifBlank { context.getString(R.string.event_untitled) }
            if (event.isBirthday) context.getString(R.string.notify_birthday, title) else "${event.time}  $title"
        } + dayTasks.map { "• ${it.title}" }
        val counts = listOfNotNull(
            dayEvents.size.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.notify_events, it, it) },
            dayTasks.size.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.notify_tasks, it, it) },
        ).joinToString(", ")
        return Message(
            id = ID_SUMMARY,
            type = NotificationType.SUMMARY,
            title = context.getString(R.string.notify_summary_title),
            text = counts,
            target = OpenTarget.Calendar(date),
            lines = lines,
            silent = silent,
        )
    }

    /** «сегодня в 08:00», «завтра в 08:00», «23 октября в 08:00». */
    private fun whenLabel(at: LocalDateTime, today: LocalDate): String {
        val time = at.toLocalTime().toString()
        return when (at.toLocalDate()) {
            today -> context.getString(R.string.notify_today_at, time)
            today.plusDays(1) -> context.getString(R.string.notify_tomorrow_at, time)
            else -> context.getString(R.string.notify_date_at, formatDay(at.toLocalDate(), withYear = at.year != today.year), time)
        }
    }

    private fun years(n: Int) = context.resources.getQuantityString(R.plurals.years, n, n)

    private fun setAlarm(at: Instant?) {
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        when {
            at == null -> alarms.cancel(intent)
            // USE_EXACT_ALARM выдаётся сразу, но на всякий случай — неточный будильник вместо никакого
            alarms.canScheduleExactAlarms() -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), intent)
        }
    }

    private companion object {
        val GRACE: Duration = Duration.ofMinutes(30)
        const val LOOKAHEAD_DAYS = 2L
        const val ID_DAY = NotificationIds.DAY
        const val ID_SUMMARY = NotificationIds.SUMMARY
        const val ID_TEST = NotificationIds.TEST
    }
}
