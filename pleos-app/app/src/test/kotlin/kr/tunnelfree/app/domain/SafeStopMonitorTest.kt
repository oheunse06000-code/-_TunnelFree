package kr.tunnelfree.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeStopMonitorTest {

    @Test
    fun stoppedDetection() {
        assertTrue(SafeStopMonitor.isStopped(0.0))
        assertTrue(SafeStopMonitor.isStopped(1.9))
        assertFalse(SafeStopMonitor.isStopped(2.0))
        assertFalse(SafeStopMonitor.isStopped(30.0))
        assertFalse(SafeStopMonitor.isStopped(null))
    }

    @Test
    fun opensHelpAfterFiveSecondsStopped() {
        val m = SafeStopMonitor(startedAtMs = 0)
        assertFalse(m.tick(1_000, 0.0))
        assertFalse(m.tick(5_000, 0.0))
        assertTrue(m.tick(6_000, 0.0))
    }

    @Test
    fun movingResetsStopTimer() {
        val m = SafeStopMonitor(startedAtMs = 0)
        assertFalse(m.tick(1_000, 0.0))
        assertFalse(m.tick(4_000, 0.0))
        assertFalse(m.tick(5_000, 20.0))
        assertFalse(m.tick(6_000, 0.0))
        assertFalse(m.tick(10_000, 0.0))
        assertTrue(m.tick(11_000, 0.0))
    }

    @Test
    fun opensHelpOnResponseTimeoutEvenWhileMoving() {
        val m = SafeStopMonitor(startedAtMs = 100_000)
        assertFalse(m.tick(119_999, 40.0))
        assertTrue(m.tick(120_000, 40.0))
    }
}
