package dev.chaik.remember.feature.stats.finance

import dev.chaik.remember.core.model.MoneyTransaction
import dev.chaik.remember.core.model.MoneyType
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinanceStatsTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun expense(offset: Long, amount: Long, category: Long? = 1) =
        MoneyTransaction(date = monday.plusDays(offset), type = MoneyType.EXPENSE, amountMinor = amount, categoryId = category)

    private fun income(offset: Long, amount: Long) =
        MoneyTransaction(date = monday.plusDays(offset), type = MoneyType.INCOME, amountMinor = amount, categoryId = 9)

    @Test
    fun summaryCountsAllElapsedDays() {
        val range = monday..monday.plusDays(6)
        val today = monday.plusDays(3)
        val list = listOf(expense(0, 1000), expense(1, 3000, 2), expense(3, 500), income(2, 10000), expense(5, 99999))
        val s = FinanceStats.summary(list, range, today)
        assertEquals(104499, s.expenseMinor)
        assertEquals(10000, s.incomeMinor)
        // Прошло 4 дня (пн–чт), будущая трата в субботу в среднее за день всё равно входит по сумме,
        // но делится на прошедшие дни
        assertEquals((1000 + 3000 + 500) / 4L, s.dailyExpenseMinor)
        assertEquals(99999, s.biggestExpense!!.amountMinor)
        assertEquals(listOf(1L, 2L), s.expenseCategories.map { it.categoryId })
        assertEquals(1000L, s.byWeekday[DayOfWeek.MONDAY])
        assertNull(s.weekendsDailyMinor)
    }

    @Test
    fun flowFillsEmptyStepsAndGroupsByWeek() {
        val range = monday..monday.plusDays(20)
        val points = FinanceStats.flow(listOf(expense(0, 100), expense(8, 200), income(9, 50)), range, FlowStep.WEEK, DayOfWeek.MONDAY)
        assertEquals(3, points.size)
        assertEquals(100, points[0].expenseMinor)
        assertEquals(200, points[1].expenseMinor)
        assertEquals(-150, points[1].balanceMinor)
        assertEquals(0, points[2].expenseMinor)
        assertEquals(FlowStep.DAY, FinanceStats.stepFor(monday..monday.plusDays(30)))
        assertEquals(FlowStep.WEEK, FinanceStats.stepFor(monday..monday.plusDays(60)))
        assertEquals(FlowStep.MONTH, FinanceStats.stepFor(monday..monday.plusDays(364)))
    }

    @Test
    fun cumulativeAddsUpToToday() {
        val start = LocalDate.of(2026, 11, 1)
        val list = listOf(
            MoneyTransaction(date = start, type = MoneyType.EXPENSE, amountMinor = 100),
            MoneyTransaction(date = start.plusDays(2), type = MoneyType.EXPENSE, amountMinor = 50),
            MoneyTransaction(date = start.plusDays(2), type = MoneyType.INCOME, amountMinor = 999),
            MoneyTransaction(date = start.plusDays(10), type = MoneyType.EXPENSE, amountMinor = 7),
        )
        assertEquals(listOf(100L, 100L, 150L, 150L), FinanceStats.cumulative(list, start, start.plusDays(3)))
        assertEquals(30, FinanceStats.cumulative(list, start, start.plusMonths(2)).size)
    }

    @Test
    fun changeAndLevels() {
        assertEquals(-12, FinanceStats.change(88, 100))
        assertEquals(50, FinanceStats.change(150, 100))
        assertNull(FinanceStats.change(10, 0))
        val thresholds = FinanceStats.quartiles(listOf(0, 100, 200, 300, 400, 500))
        assertEquals(0, FinanceStats.level(0, thresholds))
        assertEquals(1, FinanceStats.level(100, thresholds))
        assertEquals(4, FinanceStats.level(500, thresholds))
    }
}
