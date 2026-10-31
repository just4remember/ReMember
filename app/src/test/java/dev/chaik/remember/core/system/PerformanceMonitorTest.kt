package dev.chaik.remember.core.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformanceMonitorTest {
    @Test
    fun `процессор считается от всех ядер`() {
        assertEquals(12.5, PerformanceMonitor.cpuPercent(cpuMs = 2000, wallMs = 2000, cores = 8)!!, 0.001)
        assertEquals(0.0, PerformanceMonitor.cpuPercent(cpuMs = 0, wallMs = 2000, cores = 8)!!, 0.001)
        assertNull(PerformanceMonitor.cpuPercent(cpuMs = 10, wallMs = 0, cores = 8))
    }

    @Test
    fun `ток приводится к миллиамперам без знака`() {
        assertEquals(420, PerformanceMonitor.batteryMilliamps(-420_000))
        assertEquals(420, PerformanceMonitor.batteryMilliamps(420))
        assertEquals(1500, PerformanceMonitor.batteryMilliamps(1_500_000))
        assertNull(PerformanceMonitor.batteryMilliamps(Int.MIN_VALUE))
        assertNull(PerformanceMonitor.batteryMilliamps(0))
    }
}
