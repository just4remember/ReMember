package dev.chaik.remember.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutImportTemplateTest {
    @Test
    fun `скачанный шаблон сам импортируется без ошибок`() {
        val workouts = WorkoutImport.parse(WorkoutImport.TEMPLATE)
        assertEquals(2, workouts.size)
        assertEquals(4, workouts.first().exercises.size)
    }
}
