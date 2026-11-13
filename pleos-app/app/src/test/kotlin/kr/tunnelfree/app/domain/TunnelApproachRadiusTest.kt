package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelApproachRadiusTest {

    private val monitor = TunnelMonitor(TunnelDatabase(emptyList()))

    @Test
    fun `도심 속도에서는 500미터로 충분하다`() {
        assertEquals(500.0, monitor.approachRadiusFor(null), 0.1)
        assertEquals(500.0, monitor.approachRadiusFor(60.0), 0.1)
    }

    @Test
    fun `빠를수록 안내가 입구 250미터 전에 끝나도록 앞당긴다`() {
        val v100 = monitor.approachRadiusFor(100.0)
        assertEquals(250.0 + 100.0 / 3.6 * 12.0, v100, 0.1)
        assertEquals(250.0, v100 - 100.0 / 3.6 * TunnelMonitor.APPROACH_SPEECH_SEC, 0.1)
    }
}
