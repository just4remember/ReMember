package dev.chaik.remember.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WorkoutTemplateTest {
    @Test
    fun `шаблоны стыкуются в порядке выбора`() {
        assertEquals(listOf(3L, 1L, 2L, 4L), stackTemplates(listOf(listOf(3, 1), listOf(2, 4)), emptyList()))
    }

    @Test
    fun `повторы и то, что уже есть в тренировке, не добавляются`() {
        assertEquals(listOf(1L, 2L), stackTemplates(listOf(listOf(1, 5), listOf(5, 2, 1)), listOf(5)))
    }

    @Test
    fun `повторами считаются и уже записанные, и встреченные дважды`() {
        assertEquals(setOf(5L, 1L), repeatedInTemplates(listOf(listOf(1, 5), listOf(1, 2)), listOf(5)))
        assertEquals(emptySet<Long>(), repeatedInTemplates(listOf(listOf(1), listOf(2)), listOf(3)))
    }

    private fun summary(id: Long, vararg names: String) = WorkoutSummary(
        id = id, number = id.toInt(), date = LocalDate.of(2026, 11, 1), start = null, end = null,
        weightBefore = null, weightAfter = null, active = false, imported = false, exerciseNames = names.toList(),
    )

    @Test
    fun `поиск по части названия без учёта регистра`() {
        val list = listOf(summary(1, "Жим лёжа", "Присед"), summary(2, "Бег"))
        assertEquals(listOf(1L), list.withExercise(" жим ").map { it.id })
        assertEquals(listOf(1L, 2L), list.withExercise("").map { it.id })
        assertEquals(emptyList<Long>(), list.withExercise("тяга").map { it.id })
    }
}
