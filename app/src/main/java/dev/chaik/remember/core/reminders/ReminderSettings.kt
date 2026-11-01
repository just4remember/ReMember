package dev.chaik.remember.core.reminders

import java.time.DayOfWeek
import java.time.LocalTime

/** Все настройки напоминаний (страница «Настройки → Напоминания»). */
data class ReminderSettings(
    /** Общий выключатель: false — ни одного уведомления. */
    val enabled: Boolean = true,
    /** Вечером: записать, как прошёл день. Не приходит, если запись за сегодня уже есть. */
    val dayReport: DailyReminder = DailyReminder(enabled = true, time = LocalTime.of(21, 0)),
    /** Утром: записать сон. Не приходит, если сон за сегодня уже записан. */
    val sleep: DailyReminder = DailyReminder(enabled = true, time = LocalTime.of(9, 0)),
    /** Утренняя сводка: события и задачи на сегодня. */
    val summary: DailyReminder = DailyReminder(enabled = false, time = LocalTime.of(8, 0)),
    /** Ра[скобки]: не приходит, если выпуск дня уже решён. */
    val raskobki: DailyReminder = DailyReminder(enabled = false, time = LocalTime.of(12, 0)),
    /** Напоминать о событиях (за сколько — в каждом событии). */
    val events: Boolean = true,
    /** Напоминать о задачах, у которых указаны срок и время. */
    val tasks: Boolean = true,
    val quiet: QuietHours = QuietHours(),
    val sound: SoundMode = SoundMode.SOUND_AND_VIBRATION,
)

/** Напоминание раз в день в [time] по выбранным дням недели. */
data class DailyReminder(
    val enabled: Boolean,
    val time: LocalTime,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
) {
    fun firesOn(day: DayOfWeek) = enabled && day in days
}

/** Часы «Не беспокоить»; [start] позже [end] — значит через полночь (23:00–07:00). */
data class QuietHours(
    val enabled: Boolean = false,
    val start: LocalTime = LocalTime.of(23, 0),
    val end: LocalTime = LocalTime.of(7, 0),
    val mode: QuietMode = QuietMode.POSTPONE,
) {
    fun contains(time: LocalTime): Boolean = when {
        !enabled || start == end -> false
        start < end -> time >= start && time < end
        else -> time >= start || time < end
    }
}

/** Что делать с уведомлением в тихие часы. */
enum class QuietMode {
    /** Прислать, когда тихие часы закончатся. */
    POSTPONE,

    /** Прислать вовремя, но без звука и вибрации. */
    SILENT,

    /** Не присылать совсем. */
    SKIP,
}

/** Как звучат уведомления. Android запоминает это в канале, поэтому при смене каналы пересоздаются. */
enum class SoundMode(val key: String, val sound: Boolean, val vibration: Boolean) {
    SOUND_AND_VIBRATION("sv", sound = true, vibration = true),
    SOUND_ONLY("s", sound = true, vibration = false),
    VIBRATION_ONLY("v", sound = false, vibration = true),
    SILENT("q", sound = false, vibration = false),
}
