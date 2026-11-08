package dev.chaik.remember.core.data

import java.time.LocalDate

/** Пауза серии: дни с [start] по [end] включительно серию не обрывают и в неё не засчитываются. */
data class StreakPause(val start: LocalDate, val end: LocalDate) {
    operator fun contains(day: LocalDate): Boolean = !day.isBefore(start) && !day.isAfter(end)
}

/** Серия для огонька: длина и последний день идущей паузы (null — паузы нет). */
data class StreakInfo(val days: Int, val pausedUntil: LocalDate?)

/** Правила серии: паузы и запасной день — один пропуск в неделю серию не обрывает. */
data class StreakRules(val pauses: List<StreakPause> = emptyList(), val spareDay: Boolean = false)

/** Серия дней подряд с записью дня. */
object DayStreak {
    /** Запасной день прощает пропуск, если прошлый прощённый пропуск был не ближе стольких дней. */
    const val SPARE_EVERY_DAYS = 7

    /** Сколько дней ищем вперёд, до какого дня доживёт серия: пауза может быть длинной. */
    private const val LOOKAHEAD_DAYS = 400

    /**
     * Сколько дней с записью в серии на [today]. Сегодня ещё можно написать, поэтому пустое сегодня
     * серию не обрывает. Дни паузы пропускаются, запасной день прощает один пропуск в неделю.
     */
    fun length(written: Set<LocalDate>, today: LocalDate, rules: StreakRules = StreakRules()): Int {
        val earliest = written.minOrNull() ?: return 0
        var day = if (today in written) today else today.minusDays(1)
        var count = 0
        var lastSpare: LocalDate? = null
        while (!day.isBefore(earliest)) {
            when {
                day in written -> count++
                rules.pauses.any { day in it } -> Unit
                rules.spareDay && (lastSpare == null || lastSpare.toEpochDay() - day.toEpochDay() >= SPARE_EVERY_DAYS) -> lastSpare = day
                else -> break
            }
            day = day.minusDays(1)
        }
        return count
    }

    /** Последний день, на который серия ещё жива, если больше ничего не писать; null — серии нет. */
    fun aliveThrough(written: Set<LocalDate>, today: LocalDate, rules: StreakRules = StreakRules()): LocalDate? {
        if (length(written, today, rules) == 0) return null
        var day = today
        repeat(LOOKAHEAD_DAYS) {
            val next = day.plusDays(1)
            if (length(written, next, rules) == 0) return day
            day = next
        }
        return day
    }

    /** Пауза, которая идёт в [today]; null — паузы нет. */
    fun activePause(rules: StreakRules, today: LocalDate): StreakPause? = rules.pauses.firstOrNull { today in it }
}

/** Поставить паузу с [today] по [end]; идущая пауза просто получает новый конец. */
fun List<StreakPause>.withPause(today: LocalDate, end: LocalDate): List<StreakPause> {
    val active = firstOrNull { today in it }
    val next = if (active != null) map { if (it === active) it.copy(end = end) else it } else this + StreakPause(today, end)
    return next.sortedBy { it.start }
}

/** Снять идущую паузу: прошедшие дни паузы остаются под защитой, сегодня — уже нет. */
fun List<StreakPause>.withoutPause(today: LocalDate): List<StreakPause> {
    val active = firstOrNull { today in it } ?: return this
    return if (active.start.isBefore(today)) map { if (it === active) it.copy(end = today.minusDays(1)) else it } else this - active
}
