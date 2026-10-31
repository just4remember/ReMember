package dev.chaik.remember.feature.today

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TodaySessionTest {
    @Test
    fun `утром — сон, в остальное время — день`() {
        assertEquals(TodayMode.Sleep, TodaySession.defaultFor(LocalTime.of(4, 0)))
        assertEquals(TodayMode.Sleep, TodaySession.defaultFor(LocalTime.of(11, 59)))
        assertEquals(TodayMode.Day, TodaySession.defaultFor(LocalTime.of(12, 0)))
        assertEquals(TodayMode.Day, TodaySession.defaultFor(LocalTime.of(2, 30)))
    }

    @Test
    fun `выбранный режим держится, пока не было долгой паузы`() {
        var time = LocalTime.of(8, 0)
        val session = TodaySession(clock = { time })
        assertEquals(TodayMode.Sleep, session.mode)

        session.mode = TodayMode.Money
        // Короткая отлучка — режим тот же
        session.onBackground(now = 0)
        session.onForeground(now = 5 * 60_000)
        assertEquals(TodayMode.Money, session.mode)

        // Вернулись днём через несколько часов — новое открытие, режим по времени
        time = LocalTime.of(14, 0)
        session.onBackground(now = 1_000_000)
        session.onForeground(now = 1_000_000 + TodaySession.NEW_OPENING_MILLIS)
        assertEquals(TodayMode.Day, session.mode)
    }

    @Test
    fun `без выбора по времени — при открытии всегда день`() {
        var auto = false
        val session = TodaySession(clock = { LocalTime.of(8, 0) }, autoMode = { auto })
        assertEquals(TodayMode.Day, session.mode)

        session.mode = TodayMode.Sleep
        session.onBackground(now = 1)
        session.onForeground(now = 1 + TodaySession.NEW_OPENING_MILLIS)
        assertEquals(TodayMode.Day, session.mode)

        // Включили обратно — утром снова сон
        auto = true
        session.onBackground(now = 1)
        session.onForeground(now = 1 + TodaySession.NEW_OPENING_MILLIS)
        assertEquals(TodayMode.Sleep, session.mode)
    }

    @Test
    fun `сон уже записан — утром открывается день`() {
        var filled = false
        val session = TodaySession(clock = { LocalTime.of(8, 0) }, sleepFilled = { filled })
        assertEquals(TodayMode.Sleep, session.mode)
        session.onBackground(now = 1_000)
        filled = true
        session.onForeground(now = 1_000 + TodaySession.NEW_OPENING_MILLIS)
        assertEquals(TodayMode.Day, session.mode)
    }
}
