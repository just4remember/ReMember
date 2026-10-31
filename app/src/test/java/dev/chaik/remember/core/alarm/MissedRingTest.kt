package dev.chaik.remember.core.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class MissedRingTest {
    private val at = 1_000_000_000L
    private val alarm = Alarm(id = 1, hour = 7, minute = 0, ringAt = at)

    @Test
    fun `срок ещё не прошёл`() {
        assertEquals(MissedRing.NONE, alarm.missedRing(at - 1, margin = 0))
        assertEquals(MissedRing.NONE, alarm.missedRing(at, margin = 0))
    }

    @Test
    fun `телефон включился после срока — звоним сразу`() {
        assertEquals(MissedRing.RING_NOW, alarm.missedRing(at + 1, margin = 0))
        assertEquals(MissedRing.RING_NOW, alarm.missedRing(at + MISSED_GRACE_MS, margin = 0))
    }

    @Test
    fun `слишком поздно — не звоним`() {
        assertEquals(MissedRing.TOO_LATE, alarm.missedRing(at + MISSED_GRACE_MS + 1, margin = 0))
    }

    @Test
    fun `при обычном запуске системный будильник может сработать сам`() {
        assertEquals(MissedRing.NONE, alarm.missedRing(at + 30_000, margin = 60_000))
        assertEquals(MissedRing.RING_NOW, alarm.missedRing(at + 90_000, margin = 60_000))
    }

    @Test
    fun `выключенный и ещё не ставившийся будильник не пропущен`() {
        assertEquals(MissedRing.NONE, alarm.copy(enabled = false).missedRing(at + 1, margin = 0))
        assertEquals(MissedRing.NONE, alarm.copy(ringAt = null).missedRing(at + 1, margin = 0))
    }
}
