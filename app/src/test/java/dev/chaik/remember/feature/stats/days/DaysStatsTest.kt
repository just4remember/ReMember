package dev.chaik.remember.feature.stats.days

import dev.chaik.remember.core.model.DayEntry
import dev.chaik.remember.core.model.Rating
import dev.chaik.remember.core.model.Tag
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DaysStatsTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val range = monday..monday.plusDays(6)

    private fun day(offset: Long, mood: Int?) = DayEntry(date = monday.plusDays(offset), mood = mood)

    @Test
    fun averagesBestWorstAndDistribution() {
        val entries = listOf(day(0, 14), day(1, 15), day(2, 4), day(5, Rating.SECRET), day(6, null))
        val summary = DaysStats.summary(entries, emptySet(), emptyMap(), range)
        assertEquals((14 + 15 + 4 + 22) / 4.0, summary.averageRating!!, 0.001)
        assertEquals(monday.plusDays(5), summary.best!!.date)
        assertEquals(monday.plusDays(2), summary.worst!!.date)
        assertEquals(mapOf(7 to 2, 2 to 1, 11 to 1), summary.distribution)
        assertEquals(14.0, summary.byWeekday[DayOfWeek.MONDAY]!!, 0.001)
        assertEquals(22.0, summary.weekendsAverage!!, 0.001)
    }

    @Test
    fun equalRatingsPreferTheLaterDay() {
        val summary = DaysStats.summary(listOf(day(0, 10), day(3, 10)), emptySet(), emptyMap(), range)
        assertEquals(monday.plusDays(3), summary.best!!.date)
        assertEquals(monday.plusDays(3), summary.worst!!.date)
    }

    @Test
    fun longestStreakInsidePeriod() {
        val written = setOf(0L, 1L, 3L, 4L, 5L).map { monday.plusDays(it) }.toSet() + monday.minusDays(1)
        val summary = DaysStats.summary(emptyList(), written, emptyMap(), range)
        assertEquals(5, summary.writtenDays)
        assertEquals(Streak(monday.plusDays(3), monday.plusDays(5)), summary.longestStreak)
        assertNull(summary.averageRating)
    }

    @Test
    fun tagsCountDaysAndAverageRatings() {
        val sport = Tag(1, "спорт")
        val coffee = Tag(2, "кофе")
        val tags = mapOf(
            monday to listOf(sport, coffee),
            monday.plusDays(1) to listOf(sport),
            monday.plusDays(10) to listOf(coffee),
        )
        val summary = DaysStats.summary(listOf(day(0, 16), day(1, 12)), emptySet(), tags, range)
        assertEquals(listOf("спорт", "кофе"), summary.tags.map { it.tag.name })
        assertEquals(2, summary.tags[0].days)
        assertEquals(14.0, summary.tags[0].averageRating!!, 0.001)
        assertEquals(1, summary.tags[1].days)
    }
}
