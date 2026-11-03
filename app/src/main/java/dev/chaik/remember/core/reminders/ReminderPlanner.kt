package dev.chaik.remember.core.reminders

import dev.chaik.remember.core.model.Event
import dev.chaik.remember.core.model.Task
import java.time.LocalDate
import java.time.LocalDateTime

/** Что напомнить. Само содержание (и нужно ли вообще) уточняется в момент показа. */
sealed interface Reminder {
    data class DayReport(val date: LocalDate) : Reminder
    data class Summary(val date: LocalDate) : Reminder
    data class Raskobki(val date: LocalDate) : Reminder
    data class EventAlert(val eventId: Long, val occurrence: LocalDateTime) : Reminder
    data class TaskDue(val taskId: Long, val due: LocalDateTime) : Reminder
    data class WorkoutRunning(val workoutId: Long) : Reminder
}

/** Идущая тренировка: через [WORKOUT_REMINDER_HOURS] после начала спросим, не пора ли закончить. */
data class ActiveWorkout(val id: Long, val start: LocalDateTime)

const val WORKOUT_REMINDER_HOURS = 2L

/** Напоминание, приведённое к тихим часам: [at] — когда показать, [silent] — без звука. */
data class PlannedReminder(val at: LocalDateTime, val reminder: Reminder, val silent: Boolean = false)

/**
 * Расписание напоминаний — чистая функция без Android, чтобы её было легко проверить.
 * Возвращает всё, что нужно показать в промежутке (from, to], по порядку.
 */
object ReminderPlanner {
    fun plan(
        from: LocalDateTime,
        to: LocalDateTime,
        settings: ReminderSettings,
        events: List<Event>,
        tasks: List<Task>,
        activeWorkout: ActiveWorkout? = null,
    ): List<PlannedReminder> {
        if (!settings.enabled || !to.isAfter(from)) return emptyList()
        val raw = mutableListOf<Pair<LocalDateTime, Reminder>>()

        // Тихие часы могут перенести вчерашнее позднее напоминание на это утро,
        // поэтому дни перебираем с запасом в сутки назад
        val firstDay = from.toLocalDate().minusDays(1)
        val days = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(to.toLocalDate()) }.toList()
        for (day in days) {
            if (settings.dayReport.firesOn(day.dayOfWeek)) raw += day.atTime(settings.dayReport.time) to Reminder.DayReport(day)
            if (settings.summary.firesOn(day.dayOfWeek)) raw += day.atTime(settings.summary.time) to Reminder.Summary(day)
            if (settings.raskobki.firesOn(day.dayOfWeek)) raw += day.atTime(settings.raskobki.time) to Reminder.Raskobki(day)
        }

        if (settings.tasks) {
            for (task in tasks) {
                if (task.done) continue
                val due = task.dueDate?.atTime(task.dueTime ?: continue) ?: continue
                raw += due to Reminder.TaskDue(task.id, due)
            }
        }

        if (settings.events) {
            for (event in events) {
                val before = event.remindMinutes?.toLong() ?: continue
                // Напомнить в промежутке — значит событие в том же промежутке, сдвинутом на «за сколько»
                val firstOccurrence = firstDay.atStartOfDay().plusMinutes(before).toLocalDate()
                val lastOccurrence = to.plusMinutes(before).toLocalDate()
                var day = firstOccurrence
                while (!day.isAfter(lastOccurrence)) {
                    if (event.occursOn(day)) {
                        val occurrence = day.atTime(event.time)
                        raw += occurrence.minusMinutes(before) to Reminder.EventAlert(event.id, occurrence)
                    }
                    day = day.plusDays(1)
                }
            }
        }

        // Напоминание о незакрытой тренировке не зависит от настроек отдельных видов
        activeWorkout?.let { raw += it.start.plusHours(WORKOUT_REMINDER_HOURS) to Reminder.WorkoutRunning(it.id) }

        return raw
            .mapNotNull { (at, reminder) -> applyQuiet(at, reminder, settings.quiet) }
            .filter { it.at.isAfter(from) && !it.at.isAfter(to) }
            .sortedBy { it.at }
    }

    private fun applyQuiet(at: LocalDateTime, reminder: Reminder, quiet: QuietHours): PlannedReminder? {
        if (!quiet.contains(at.toLocalTime())) return PlannedReminder(at, reminder)
        return when (quiet.mode) {
            QuietMode.SILENT -> PlannedReminder(at, reminder, silent = true)
            QuietMode.SKIP -> null
            QuietMode.POSTPONE -> {
                // Конец тихих часов: сегодня, если сейчас утренняя часть, иначе завтра
                val endToday = at.toLocalDate().atTime(quiet.end)
                PlannedReminder(if (endToday.isAfter(at)) endToday else endToday.plusDays(1), reminder)
            }
        }
    }
}
