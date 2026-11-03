package dev.chaik.remember.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceSetTest {
    @Test
    fun `новые виды подходов выглядят понятно`() {
        assertEquals("5 км × 28:00", WorkoutSet(distance = 5.0, seconds = 28 * 60).format(ExerciseKind.DISTANCE_TIME, "км/ч", "км"))
        assertEquals("10 × 0:45", WorkoutSet(weight = 10.0, seconds = 45).format(ExerciseKind.WEIGHT_TIME))
        assertEquals("1,5 км", WorkoutSet(distance = 1.5).format(ExerciseKind.DISTANCE, "км/ч", "км"))
    }
}
