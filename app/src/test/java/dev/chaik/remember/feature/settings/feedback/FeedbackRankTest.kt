package dev.chaik.remember.feature.settings.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackRankTest {
    @Test
    fun `ошибка стоит трёх предложений`() {
        assertEquals(FeedbackCounts(bugs = 0, ideas = 3).points, FeedbackCounts(bugs = 1, ideas = 0).points)
    }

    @Test
    fun `уровни по очкам`() {
        assertEquals(FeedbackRank.NEWCOMER, FeedbackRank.of(0))
        assertEquals(FeedbackRank.BRONZE, FeedbackRank.of(1))
        assertEquals(FeedbackRank.BRONZE, FeedbackRank.of(9))
        assertEquals(FeedbackRank.SILVER, FeedbackRank.of(10))
        assertEquals(FeedbackRank.GOLD, FeedbackRank.of(25))
        assertEquals(FeedbackRank.LEGEND, FeedbackRank.of(60))
        assertEquals(FeedbackRank.LEGEND, FeedbackRank.of(1000))
    }

    @Test
    fun `пороги растут`() {
        FeedbackRank.entries.zipWithNext().forEach { (a, b) -> assertTrue(a.fromPoints < b.fromPoints) }
    }
}
