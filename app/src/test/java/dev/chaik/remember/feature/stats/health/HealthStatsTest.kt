package dev.chaik.remember.feature.stats.health

import dev.chaik.remember.core.health.DayActivity
import dev.chaik.remember.core.health.HealthGoals
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthStatsTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val goals = HealthGoals(steps = 6000, activeMinutes = 60, calories = 300)

    @Test
    fun averagesSkipEmptyDaysAndCountGoals() {
        val days = mapOf(
            monday to DayActivity(8000, 70, 350.0),
            monday.plusDays(1) to DayActivity(4000, 30, 150.0),
            monday.plusDays(2) to DayActivity(0, 0, 0.0),
            monday.plusDays(5) to DayActivity(12000, 50, 500.0),
        )
        val summary = HealthStats.summary(days, goals)
        assertEquals(3, summary.days.size)
        assertEquals(8000, summary.averageSteps)
        assertEquals(50, summary.averageMinutes)
        assertEquals(2, summary.stepGoalDays)
        assertEquals(1, summary.allGoalsDays)
        assertEquals(monday.plusDays(5) to 12000L, summary.record)
        assertEquals(6000L, summary.weekdaysAverage)
        assertEquals(12000L, summary.weekendsAverage)
        assertEquals(8000L, summary.byWeekday[DayOfWeek.MONDAY])
    }

    @Test
    fun emptyPeriod() {
        val summary = HealthStats.summary(emptyMap(), goals)
        assertNull(summary.record)
        assertEquals(0, summary.averageSteps)
        assertNull(summary.weekendsAverage)
    }

    @Test
    fun heatmapLevelsFollowTheGoal() {
        assertEquals(0, HealthStats.level(0, 6000))
        assertEquals(1, HealthStats.level(2999, 6000))
        assertEquals(2, HealthStats.level(3000, 6000))
        assertEquals(3, HealthStats.level(6000, 6000))
        assertEquals(4, HealthStats.level(9000, 6000))
    }
}
