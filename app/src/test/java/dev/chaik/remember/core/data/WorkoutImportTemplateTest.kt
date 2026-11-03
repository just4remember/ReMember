package dev.chaik.remember.core.data

import dev.chaik.remember.core.model.ExerciseKind
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutImportTemplateTest {
    @Test
    fun `скачанный шаблон сам импортируется без ошибок`() {
        val workouts = WorkoutImport.parse(WorkoutImport.TEMPLATE)
        assertEquals(2, workouts.size)
        // В шаблоне по упражнению на каждый вид подходов
        assertEquals(ExerciseKind.entries.toSet(), workouts.first().exercises.map { it.exercise.kind }.toSet())
    }
}
