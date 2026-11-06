package dev.chaik.remember.feature.stats.sleep

import dev.chaik.remember.core.model.SleepEntry
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Норма сна для «недосыпа» и линии на графике, минут. */
const val SLEEP_NORM_MINUTES = 8 * 60

/**
 * Время отбоя и подъёма — в минутах от полуночи дня пробуждения: отбой накануне вечером
 * отрицательный (23:30 → −30), так 23:30 и 00:30 стоят рядом. Шкала не привязана к ночи:
 * сон днём (08:00–16:00) — просто 480…960, а сон с вечера накануне — от минус часов.
 */
/** Один сон: дата пробуждения, отбой и подъём в минутах от полуночи дня пробуждения. */
data class Night(
    val date: LocalDate,
    val bedMinute: Int,
    val wakeMinute: Int,
    /** Качество в половинках звезды из 5; null — не указано. */
    val quality: Int?,
) {
    val durationMinutes: Int get() = wakeMinute - bedMinute

    /** Выходная ночь — проснулся в субботу или воскресенье. */
    val weekend: Boolean get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
}

/** Средние по набору ночей; null — ночей нет. */
data class SleepAverages(
    val nights: Int,
    val durationMinutes: Int,
    val bedMinute: Int,
    val wakeMinute: Int,
    /** Насколько в среднем отбой гуляет от своего среднего, минут (стандартное отклонение). */
    val bedSpreadMinutes: Int,
)

data class SleepSummary(
    val nights: List<Night>,
    val all: SleepAverages?,
    val weekdays: SleepAverages?,
    val weekends: SleepAverages?,
    /** Сколько минут не доспал до нормы за период — только ночи короче нормы. */
    val deficitMinutes: Int,
    /** Среднее качество в половинках; null — нигде не указано. */
    val averageQuality: Double?,
)

object SleepStats {
    /** Ночи из записей сна: только с отбоем и подъёмом, длительностью от 0 до 20 часов. */
    fun nights(entries: List<SleepEntry>, zone: ZoneId = ZoneId.systemDefault()): List<Night> = entries.mapNotNull { entry ->
        val bed = entry.bedTime ?: return@mapNotNull null
        val wake = entry.wakeTime ?: return@mapNotNull null
        val minutes = Duration.between(bed, wake).toMinutes()
        if (minutes <= 0 || minutes > 20 * 60) return@mapNotNull null
        // Шкала — от полуночи дня пробуждения
        val axisStart = entry.date.atStartOfDay(zone).toInstant()
        val bedMinute = Duration.between(axisStart, bed).toMinutes().toInt()
        Night(entry.date, bedMinute, bedMinute + minutes.toInt(), entry.quality)
    }.sortedBy { it.date }

    fun summary(entries: List<SleepEntry>, zone: ZoneId = ZoneId.systemDefault()): SleepSummary {
        val nights = nights(entries, zone)
        val qualities = entries.mapNotNull { it.quality?.takeIf { q -> q > 0 } }
        return SleepSummary(
            nights = nights,
            all = averages(nights),
            weekdays = averages(nights.filterNot { it.weekend }),
            weekends = averages(nights.filter { it.weekend }),
            deficitMinutes = nights.sumOf { (SLEEP_NORM_MINUTES - it.durationMinutes).coerceAtLeast(0) },
            averageQuality = qualities.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    fun averages(nights: List<Night>): SleepAverages? {
        if (nights.isEmpty()) return null
        val bed = nights.map { it.bedMinute.toDouble() }
        val bedMean = bed.average()
        val spread = sqrt(bed.sumOf { (it - bedMean) * (it - bedMean) } / bed.size)
        return SleepAverages(
            nights = nights.size,
            durationMinutes = nights.map { it.durationMinutes }.average().roundToInt(),
            bedMinute = bedMean.roundToInt(),
            wakeMinute = nights.map { it.wakeMinute }.average().roundToInt(),
            bedSpreadMinutes = spread.roundToInt(),
        )
    }

    /** Минуты от полуночи дня пробуждения (бывают и отрицательные) → время суток «23:40». */
    fun clock(axisMinute: Int): String {
        val minuteOfDay = Math.floorMod(axisMinute, 24 * 60)
        return "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
    }
}

/**
 * Период статистики — календарный: неделя, месяц, квартал (3 месяца), год; или всё время.
 * [range] с [offset] = 0 — текущий (неделя с её первого дня, месяц с 1-го числа и т. д.),
 * 1 — предыдущий и так далее.
 */
enum class StatsPeriod {
    WEEK,
    MONTH,
    QUARTER,
    YEAR,

    /** С первой записи до сегодня; листать некуда. */
    ALL,
    ;

    /** Даты периода включительно. [first] — самая ранняя запись, начало «всего времени». */
    fun range(today: LocalDate, offset: Int, first: LocalDate? = null, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): ClosedRange<LocalDate> {
        val back = offset.toLong()
        return when (this) {
            WEEK -> today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).minusWeeks(back).let { it..it.plusDays(6) }
            MONTH -> today.withDayOfMonth(1).minusMonths(back).let { it..it.plusMonths(1).minusDays(1) }
            QUARTER -> LocalDate.of(today.year, (today.monthValue - 1) / 3 * 3 + 1, 1).minusMonths(3 * back)
                .let { it..it.plusMonths(3).minusDays(1) }
            YEAR -> LocalDate.of(today.year - offset, 1, 1).let { it..it.plusYears(1).minusDays(1) }
            ALL -> (first?.takeIf { it.isBefore(today) } ?: today)..today
        }
    }
}
