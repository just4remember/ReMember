package dev.chaik.remember.core.sleep

import dev.chaik.remember.core.sleep.PhoneEvent.Type.SCREEN_OFF
import dev.chaik.remember.core.sleep.PhoneEvent.Type.UNLOCK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SleepGuesserTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 27)

    /** «вчера 23:10» / «сегодня 7:45». */
    private fun at(day: LocalDate, hh: Int, mm: Int) = day.atTime(hh, mm).toInstant(zone)
    private fun y(hh: Int, mm: Int) = at(today.minusDays(1), hh, mm)
    private fun t(hh: Int, mm: Int) = at(today, hh, mm)
    private fun ev(at: java.time.Instant, type: PhoneEvent.Type) = PhoneEvent(at, type)

    @Test
    fun `обычная ночь — отбой и подъём`() {
        val events = listOf(
            ev(y(22, 0), UNLOCK), ev(y(23, 40), SCREEN_OFF),
            ev(t(7, 45), UNLOCK), ev(t(7, 50), SCREEN_OFF),
        )
        assertEquals(SleepGuess(y(23, 40), t(7, 45)), SleepGuesser.guess(events, today, zone))
    }

    @Test
    fun `ночью глянул время — сон не прерывается`() {
        val events = listOf(
            ev(y(23, 0), UNLOCK), ev(y(23, 30), SCREEN_OFF),
            ev(t(3, 10), UNLOCK), ev(t(3, 12), SCREEN_OFF),
            ev(t(8, 0), UNLOCK),
        )
        assertEquals(SleepGuess(y(23, 30), t(8, 0)), SleepGuesser.guess(events, today, zone))
    }

    @Test
    fun `вечерняя пауза к сну не приклеивается`() {
        val events = listOf(
            ev(y(21, 0), UNLOCK), ev(y(21, 10), SCREEN_OFF),
            // Полчаса не трогал, потом ещё посидел пять минут и лёг
            ev(y(21, 40), UNLOCK), ev(y(21, 45), SCREEN_OFF),
            ev(t(6, 30), UNLOCK),
        )
        assertEquals(SleepGuess(y(21, 45), t(6, 30)), SleepGuesser.guess(events, today, zone))
    }

    @Test
    fun `долго сидел ночью — это подъём, а не взгляд на часы`() {
        val events = listOf(
            ev(y(23, 0), UNLOCK), ev(y(23, 30), SCREEN_OFF),
            ev(t(4, 0), UNLOCK), ev(t(5, 0), SCREEN_OFF),
            ev(t(7, 0), UNLOCK),
        )
        assertEquals(SleepGuess(y(23, 30), t(4, 0)), SleepGuesser.guess(events, today, zone))
    }

    @Test
    fun `экран от уведомления без разблокировки перерыв не прерывает`() {
        val events = listOf(
            ev(y(23, 50), UNLOCK), ev(t(0, 20), SCREEN_OFF),
            // Пришло сообщение: экран загорелся и погас, телефон не разблокировали
            ev(t(2, 0), SCREEN_OFF),
            ev(t(8, 5), UNLOCK),
        )
        assertEquals(SleepGuess(t(0, 20), t(8, 5)), SleepGuesser.guess(events, today, zone))
    }

    @Test
    fun `днём тоже сон — для тех, кто работает ночью`() {
        val events = listOf(
            ev(t(7, 0), UNLOCK), ev(t(8, 10), SCREEN_OFF),
            ev(t(16, 30), UNLOCK), ev(t(16, 40), SCREEN_OFF),
        )
        assertEquals(SleepGuess(t(8, 10), t(16, 30)), SleepGuesser.guess(events, today, zone))
        // Лёг вечером накануне после смены и проспал до утра — тоже сон этого дня
        val evening = listOf(ev(y(17, 0), UNLOCK), ev(y(18, 30), SCREEN_OFF), ev(t(2, 0), UNLOCK))
        assertEquals(SleepGuess(y(18, 30), t(2, 0)), SleepGuesser.guess(evening, today, zone))
    }

    @Test
    fun `короткий перерыв — не сон, и сон, закончившийся вчера, — не сегодняшний`() {
        assertNull(SleepGuesser.guess(listOf(ev(t(1, 0), SCREEN_OFF), ev(t(2, 0), UNLOCK)), today, zone))
        val yesterdayNap = listOf(ev(y(7, 0), UNLOCK), ev(y(9, 0), SCREEN_OFF), ev(y(13, 0), UNLOCK))
        assertNull(SleepGuesser.guess(yesterdayNap, today, zone))
        // Ещё не встал (нет разблокировки после перерыва) — подъёма нет
        assertNull(SleepGuesser.guess(listOf(ev(y(23, 0), UNLOCK), ev(y(23, 30), SCREEN_OFF)), today, zone))
    }
}
