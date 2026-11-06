package dev.chaik.remember.feature.stats.finance

import dev.chaik.remember.core.model.MoneyTransaction
import dev.chaik.remember.core.model.MoneyType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Шаг графика доходов и расходов: на длинных периодах дни слились бы в кашу. */
enum class FlowStep { DAY, WEEK, MONTH }

/** Доходы и расходы за шаг графика (день, неделю или месяц). */
data class FlowPoint(val start: LocalDate, val end: LocalDate, val incomeMinor: Long, val expenseMinor: Long) {
    val balanceMinor: Long get() = incomeMinor - expenseMinor
}

/** Категория за период: сумма и сколько было операций. [categoryId] = null — без категории. */
data class CategoryShare(val categoryId: Long?, val amountMinor: Long, val count: Int)

data class FinanceSummary(
    val expenseMinor: Long,
    val incomeMinor: Long,
    /** Средний расход за день — по всем прошедшим дням периода, не только с тратами. */
    val dailyExpenseMinor: Long,
    val biggestExpense: MoneyTransaction?,
    val expenseCategories: List<CategoryShare>,
    val incomeCategories: List<CategoryShare>,
    /** Средний расход по дням недели — тоже по всем прошедшим таким дням. */
    val byWeekday: Map<DayOfWeek, Long>,
    val weekdaysDailyMinor: Long?,
    val weekendsDailyMinor: Long?,
) {
    val balanceMinor: Long get() = incomeMinor - expenseMinor
}

object FinanceStats {
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /** Дни периода, которые уже наступили. */
    fun elapsedDays(range: ClosedRange<LocalDate>, today: LocalDate): List<LocalDate> {
        val end = minOf(range.endInclusive, today)
        if (end.isBefore(range.start)) return emptyList()
        return (0..ChronoUnit.DAYS.between(range.start, end)).map { range.start.plusDays(it) }
    }

    fun summary(transactions: List<MoneyTransaction>, range: ClosedRange<LocalDate>, today: LocalDate): FinanceSummary {
        val inRange = transactions.filter { it.date in range }
        val expenses = inRange.filter { it.type == MoneyType.EXPENSE }
        val incomes = inRange.filter { it.type == MoneyType.INCOME }
        val days = elapsedDays(range, today)
        val spentByDay = expenses.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        fun dailyAverage(list: List<LocalDate>): Long? = if (list.isEmpty()) null else list.sumOf { spentByDay[it] ?: 0 } / list.size
        return FinanceSummary(
            expenseMinor = expenses.sumOf { it.amountMinor },
            incomeMinor = incomes.sumOf { it.amountMinor },
            dailyExpenseMinor = dailyAverage(days) ?: 0,
            biggestExpense = expenses.maxWithOrNull(compareBy({ it.amountMinor }, { it.date })),
            expenseCategories = shares(expenses),
            incomeCategories = shares(incomes),
            byWeekday = days.groupBy { it.dayOfWeek }.mapValues { (_, list) -> dailyAverage(list) ?: 0 },
            weekdaysDailyMinor = dailyAverage(days.filter { it.dayOfWeek !in weekend }),
            weekendsDailyMinor = dailyAverage(days.filter { it.dayOfWeek in weekend }),
        )
    }

    /** Категории по сумме, самые крупные первыми. */
    private fun shares(list: List<MoneyTransaction>): List<CategoryShare> =
        list.groupBy { it.categoryId }
            .map { (id, items) -> CategoryShare(id, items.sumOf { it.amountMinor }, items.size) }
            .sortedByDescending { it.amountMinor }

    /** До месяца — по дням, до трёх месяцев — по неделям, дальше — по месяцам. */
    fun stepFor(range: ClosedRange<LocalDate>): FlowStep {
        val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
        return when {
            days <= 31 -> FlowStep.DAY
            days <= 92 -> FlowStep.WEEK
            else -> FlowStep.MONTH
        }
    }

    /** Доходы и расходы по шагам, покрывая весь период — и шаги без операций (с нулями). */
    fun flow(transactions: List<MoneyTransaction>, range: ClosedRange<LocalDate>, step: FlowStep, firstDayOfWeek: DayOfWeek): List<FlowPoint> {
        fun bucketStart(date: LocalDate): LocalDate = when (step) {
            FlowStep.DAY -> date
            FlowStep.WEEK -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            FlowStep.MONTH -> date.withDayOfMonth(1)
        }
        fun next(date: LocalDate): LocalDate = when (step) {
            FlowStep.DAY -> date.plusDays(1)
            FlowStep.WEEK -> date.plusWeeks(1)
            FlowStep.MONTH -> date.plusMonths(1)
        }
        val grouped = transactions.filter { it.date in range }.groupBy { bucketStart(it.date) }
        val points = ArrayList<FlowPoint>()
        var start = bucketStart(range.start)
        while (!start.isAfter(range.endInclusive)) {
            val items = grouped[start].orEmpty()
            points += FlowPoint(
                start = maxOf(start, range.start),
                end = minOf(next(start).minusDays(1), range.endInclusive),
                incomeMinor = items.filter { it.type == MoneyType.INCOME }.sumOf { it.amountMinor },
                expenseMinor = items.filter { it.type == MoneyType.EXPENSE }.sumOf { it.amountMinor },
            )
            start = next(start)
        }
        return points
    }

    /**
     * Накопленные расходы по дням месяца: [i] — сколько потрачено с 1-го по (i+1)-е число.
     * [upTo] — последний учитываемый день (сегодня для текущего месяца).
     */
    fun cumulative(transactions: List<MoneyTransaction>, monthStart: LocalDate, upTo: LocalDate): List<Long> {
        val monthEnd = monthStart.plusMonths(1).minusDays(1)
        val last = minOf(upTo, monthEnd)
        if (last.isBefore(monthStart)) return emptyList()
        val spent = transactions.filter { it.type == MoneyType.EXPENSE && it.date >= monthStart && it.date <= last }
            .groupBy { it.date.dayOfMonth }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        var total = 0L
        return (1..last.dayOfMonth).map { day ->
            total += spent[day] ?: 0
            total
        }
    }

    /** Изменение к прошлому периоду в процентах; null — в прошлом периоде трат не было. */
    fun change(current: Long, previous: Long): Int? =
        if (previous <= 0) null else ((current - previous) * 100.0 / previous).let { Math.round(it).toInt() }

    /**
     * Ступени тепловой карты расходов (1–4) по четвертям трат года: так карта говорит что-то
     * при любом уровне расходов. Возвращает пороги — верхние границы первых трёх ступеней.
     */
    fun quartiles(amounts: Collection<Long>): List<Long> {
        val sorted = amounts.filter { it > 0 }.sorted()
        if (sorted.isEmpty()) return listOf(0, 0, 0)
        return listOf(0.25, 0.5, 0.75).map { q -> sorted[((sorted.size - 1) * q).toInt()] }
    }

    fun level(amount: Long, thresholds: List<Long>): Int = when {
        amount <= 0 -> 0
        amount <= thresholds[0] -> 1
        amount <= thresholds[1] -> 2
        amount <= thresholds[2] -> 3
        else -> 4
    }
}
