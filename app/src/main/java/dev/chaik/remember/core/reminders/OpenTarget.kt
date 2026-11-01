package dev.chaik.remember.core.reminders

import android.content.Intent
import java.time.LocalDate

/** Куда открыть приложение по тапу на уведомление. */
sealed interface OpenTarget {
    data object Day : OpenTarget

    /** Запись дня за [date] (например, из галереи фото). */
    data class DayAt(val date: LocalDate) : OpenTarget
    data object Sleep : OpenTarget
    data class Calendar(val date: LocalDate) : OpenTarget

    /** Страница «Здоровье» (цель по шагам выполнена). */
    data object Health : OpenTarget
    data class Task(val taskId: Long) : OpenTarget

    /** Страница «Время» (будильник в шторке, идущий таймер или секундомер). */
    data object Time : OpenTarget

    /** Главный экран Ра[скобок]. */
    data object Raskobki : OpenTarget

    /** [finish] — сразу с формой «закончить тренировку». */
    data class Workout(val workoutId: Long, val finish: Boolean = false) : OpenTarget

    companion object {
        private const val EXTRA = "dev.chaik.remember.OPEN"

        fun put(intent: Intent, target: OpenTarget): Intent = intent.putExtra(
            EXTRA,
            when (target) {
                Day -> "day"
                is DayAt -> "dayat:${target.date.toEpochDay()}"
                Sleep -> "sleep"
                Health -> "health"
                Time -> "time"
                Raskobki -> "raskobki"
                is Calendar -> "calendar:${target.date.toEpochDay()}"
                is Task -> "task:${target.taskId}"
                is Workout -> (if (target.finish) "finish:" else "workout:") + target.workoutId
            },
        )

        fun from(intent: Intent?): OpenTarget? {
            val value = intent?.getStringExtra(EXTRA) ?: return null
            val arg = value.substringAfter(':', "").toLongOrNull()
            return when {
                value == "day" -> Day
                value.startsWith("dayat:") && arg != null -> DayAt(LocalDate.ofEpochDay(arg))
                value == "sleep" -> Sleep
                value == "health" -> Health
                value == "time" -> Time
                value == "raskobki" -> Raskobki
                value.startsWith("calendar:") && arg != null -> Calendar(LocalDate.ofEpochDay(arg))
                value.startsWith("task:") && arg != null -> Task(arg)
                value.startsWith("workout:") && arg != null -> Workout(arg)
                value.startsWith("finish:") && arg != null -> Workout(arg, finish = true)
                else -> null
            }
        }
    }
}
