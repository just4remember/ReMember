package dev.chaik.remember.core.sleep

import dev.chaik.remember.core.sleep.PhoneEvent.Type.SCREEN_OFF
import dev.chaik.remember.core.sleep.PhoneEvent.Type.UNLOCK
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SleepGuesserOptionsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 11, 4)
    private fun y(h: Int, m: Int) = today.minusDays(1).atTime(h, m).atZone(zone).toInstant()
    private fun t(h: Int, m: Int) = today.atTime(h, m).atZone(zone).toInstant()
    private fun ev(at: java.time.Instant, type: PhoneEvent.Type) = PhoneEvent(at, type)

    @Test
    fun `вечер за компьютером — тоже вариант, но выбран настоящий сон`() {
        // 19:00–02:00 телефон лежал (играл на компьютере), спал 02:10–11:00
        val events = listOf(
            ev(y(18, 50), UNLOCK), ev(y(19, 0), SCREEN_OFF),
            ev(t(2, 0), UNLOCK), ev(t(2, 10), SCREEN_OFF),
            ev(t(11, 0), UNLOCK),
        )
        val options = SleepGuesser.options(events, today, zone)
        assertEquals(listOf(SleepGuess(y(19, 0), t(2, 0)), SleepGuess(t(2, 10), t(11, 0))), options.windows)
        assertEquals(setOf(1), options.selected)
    }

    @Test
    fun `сон по частям виден двумя вариантами для склейки`() {
        // Спал до будильника, полчаса посидел и лёг снова
        val events = listOf(
            ev(y(23, 50), UNLOCK), ev(t(0, 0), SCREEN_OFF),
            ev(t(6, 0), UNLOCK), ev(t(6, 30), SCREEN_OFF),
            ev(t(10, 30), UNLOCK),
        )
        val options = SleepGuesser.options(events, today, zone)
        assertEquals(2, options.windows.size)
        assertEquals(t(6, 30), options.windows[1].bed)
        // Посидел полчаса — не «взгляд на часы»: вторую часть отмечают сами
        assertEquals(setOf(0), options.selected)
    }

    @Test
    fun `ночью глянул время — обе части отмечены сразу`() {
        val events = listOf(
            ev(y(23, 20), UNLOCK), ev(y(23, 30), SCREEN_OFF),
            ev(t(3, 0), UNLOCK), ev(t(3, 5), SCREEN_OFF),
            ev(t(8, 0), UNLOCK),
        )
        assertEquals(setOf(0, 1), SleepGuesser.options(events, today, zone).selected)
    }
}
