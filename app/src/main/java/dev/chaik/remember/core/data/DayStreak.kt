package dev.chaik.remember.core.data

import java.time.LocalDate

/** Серия дней подряд с записью дня. */
object DayStreak {
    /**
     * Сколько дней подряд до [today] есть запись. Сегодня ещё можно написать, поэтому пустое
     * сегодня серию не обрывает — она обрывается, только если пропущен вчерашний день.
     */
    fun length(written: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in written) today else today.minusDays(1)
        var count = 0
        while (day in written) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
