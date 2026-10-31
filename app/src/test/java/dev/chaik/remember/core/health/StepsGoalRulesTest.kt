package dev.chaik.remember.core.health

import dev.chaik.remember.core.reminders.QuietHours
import dev.chaik.remember.core.reminders.QuietMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class StepsGoalRulesTest {
    private val noon = LocalTime.of(12, 0)
    private val night = LocalTime.of(23, 30)
    private fun quiet(mode: QuietMode) = QuietHours(enabled = true, start = LocalTime.of(23, 0), end = LocalTime.of(7, 0), mode = mode)

    private fun decide(steps: Long, notified: Boolean = false, enabled: Boolean = true, quiet: QuietHours = QuietHours(), now: LocalTime = noon) =
        StepsGoalRules.decide(steps, goal = 6000, alreadyNotified = notified, notificationsEnabled = enabled, quiet = quiet, now = now)

    @Test
    fun `поздравляем один раз, когда цель достигнута`() {
        assertEquals(StepsGoalAction.NONE, decide(5999))
        assertEquals(StepsGoalAction.NOTIFY, decide(6000))
        assertEquals(StepsGoalAction.NONE, decide(8000, notified = true))
        assertEquals(StepsGoalAction.NONE, decide(8000, enabled = false))
    }

    @Test
    fun `тихие часы — как у напоминаний`() {
        assertEquals(StepsGoalAction.NONE, decide(7000, quiet = quiet(QuietMode.POSTPONE), now = night))
        assertEquals(StepsGoalAction.NOTIFY_SILENT, decide(7000, quiet = quiet(QuietMode.SILENT), now = night))
        assertEquals(StepsGoalAction.SKIP, decide(7000, quiet = quiet(QuietMode.SKIP), now = night))
        // Вне тихих часов — как обычно
        assertEquals(StepsGoalAction.NOTIFY, decide(7000, quiet = quiet(QuietMode.SKIP), now = noon))
    }

    @Test
    fun `все цели — только когда выполнены все три`() {
        val goals = HealthGoals(steps = 6000, activeMinutes = 90, calories = 500)
        assertEquals(true, StepsGoalRules.allGoalsMet(DayActivity(6000, 90, 500.0), goals))
        assertEquals(false, StepsGoalRules.allGoalsMet(DayActivity(5999, 90, 500.0), goals))
        assertEquals(false, StepsGoalRules.allGoalsMet(DayActivity(6000, 89, 500.0), goals))
        assertEquals(false, StepsGoalRules.allGoalsMet(DayActivity(6000, 90, 499.9), goals))
    }
}
