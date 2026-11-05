package dev.chaik.remember.feature.stats.days

import dev.chaik.remember.core.model.DayEntry
import dev.chaik.remember.core.model.Rating
import dev.chaik.remember.core.model.Tag
import java.time.DayOfWeek
import java.time.LocalDate

/** Серия дней с записью подряд: с какого по какой день. */
data class Streak(val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt() + 1
}

/** Тег за период: в скольких днях стоял и средняя оценка этих дней (в половинках; null — без оценок). */
data class TagStat(val tag: Tag, val days: Int, val averageRating: Double?)

/** День с оценкой — для лучшего и худшего дня. */
data class RatedDay(val date: LocalDate, val rating: Int)

data class DaysSummary(
    /** Оценки по дням, только где выставлены (в половинках звезды). */
    val ratings: Map<LocalDate, Int>,
    /** Средняя оценка в половинках; null — оценок нет. */
    val averageRating: Double?,
    /** Сколько дней периода с записью. */
    val writtenDays: Int,
    val best: RatedDay?,
    val worst: RatedDay?,
    /** Самая длинная серия записей внутри периода. */
    val longestStreak: Streak?,
    /** Сколько дней с каждой оценкой: ключ — целые звёзды (7.5 → 7), 11 — секретная. */
    val distribution: Map<Int, Int>,
    /** Средняя оценка по дням недели (в половинках); дня нет — оценок в этот день недели не было. */
    val byWeekday: Map<DayOfWeek, Double>,
    val weekdaysAverage: Double?,
    val weekendsAverage: Double?,
    /** Теги периода, самые частые — первыми. */
    val tags: List<TagStat>,
)

object DaysStats {
    /**
     * Сводка по дням периода. [entries] — записи дней периода, [written] — дни с текстом или фото
     * (для «записано» и серий), [tags] — теги всех дней.
     */
    fun summary(
        entries: List<DayEntry>,
        written: Set<LocalDate>,
        tags: Map<LocalDate, List<Tag>>,
        range: ClosedRange<LocalDate>,
    ): DaysSummary {
        val ratings = entries.filter { it.date in range }.mapNotNull { e -> e.mood?.takeIf { it > 0 }?.let { e.date to it } }.toMap()
        val inRange = written.filter { it in range }.toSet()
        // При равных оценках — более поздний день: он чаще и вспоминается
        val best = ratings.entries.maxWithOrNull(compareBy({ it.value }, { it.key }))?.let { RatedDay(it.key, it.value) }
        val worst = ratings.entries.minWithOrNull(compareBy<Map.Entry<LocalDate, Int>> { it.value }.thenByDescending { it.key })
            ?.let { RatedDay(it.key, it.value) }
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val dayTags = tags.filterKeys { it in range }
        val tagStats = dayTags.flatMap { (date, list) -> list.distinctBy { it.id }.map { it to date } }
            .groupBy({ it.first.id }, { it })
            .map { (_, pairs) ->
                val dates = pairs.map { it.second }
                val rated = dates.mapNotNull { ratings[it] }
                TagStat(pairs.first().first, dates.size, rated.takeIf { it.isNotEmpty() }?.average())
            }
            .sortedWith(compareByDescending<TagStat> { it.days }.thenBy { it.tag.name.lowercase() })
        return DaysSummary(
            ratings = ratings,
            averageRating = ratings.values.takeIf { it.isNotEmpty() }?.average(),
            writtenDays = inRange.size,
            best = best,
            worst = worst,
            longestStreak = longestStreak(inRange),
            distribution = ratings.values.groupingBy { if (Rating.isSecret(it)) 11 else it / 2 }.eachCount(),
            byWeekday = ratings.entries.groupBy({ it.key.dayOfWeek }, { it.value }).mapValues { (_, v) -> v.average() },
            weekdaysAverage = ratings.filterKeys { it.dayOfWeek !in weekend }.values.takeIf { it.isNotEmpty() }?.average(),
            weekendsAverage = ratings.filterKeys { it.dayOfWeek in weekend }.values.takeIf { it.isNotEmpty() }?.average(),
            tags = tagStats,
        )
    }

    /** Самая длинная серия подряд идущих дат; при равной длине — более поздняя. */
    fun longestStreak(dates: Set<LocalDate>): Streak? {
        var best: Streak? = null
        dates.sorted().forEach { date ->
            if (date.minusDays(1) in dates) return@forEach
            var end = date
            while (end.plusDays(1) in dates) end = end.plusDays(1)
            val run = Streak(date, end)
            if (best == null || run.days >= best!!.days) best = run
        }
        return best
    }
}
