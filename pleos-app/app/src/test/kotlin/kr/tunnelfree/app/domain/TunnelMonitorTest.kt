package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelMonitorTest {

    private val csv = """
        # test
        1;테스트터널;primary;600;36.350000,127.400000|36.350000,127.403300|36.350000,127.406700
        2;멀리터널;tertiary;200;36.420000,127.500000|36.420000,127.502000
    """.trimIndent()
    private val db = TunnelDatabase.parse(csv)

    @Test
    fun `CSV 파싱`() {
        assertEquals(2, db.tunnels.size)
        val t = db.tunnels[0]
        assertEquals("테스트터널", t.name)
        assertEquals(600, t.lengthM)
        assertEquals(3, t.points.size)
        assertEquals(36.35, t.entry.lat, 1e-9)
    }

    @Test
    fun `거리 계산은 대략 맞다`() {
        val a = LatLng(36.35, 127.40)
        val b = LatLng(36.35, 127.41)
        val d = distanceMeters(a, b)
        assertTrue("d=$d", d in 880.0..915.0)
    }

    @Test
    fun `접근 → 진입 → 이탈 이벤트 순서`() {
        val m = TunnelMonitor(db)
        assertTrue(m.update(LatLng(36.35, 127.389)).isEmpty())
        val e1 = m.update(LatLng(36.35, 127.395))
        assertEquals(1, e1.size)
        val ap = e1[0] as TunnelEvent.Approaching
        assertEquals("테스트터널", ap.tunnel.name)
        assertTrue(ap.distanceM in 400..500)
        assertTrue(m.update(LatLng(36.35, 127.398)).isEmpty())
        val e2 = m.update(LatLng(36.35, 127.4015))
        assertEquals(1, e2.size)
        assertTrue(e2[0] is TunnelEvent.Entered)
        assertNotNull(m.status.inside)
        assertTrue(m.update(LatLng(36.35, 127.404)).isEmpty())
        val e3 = m.update(LatLng(36.35, 127.409)).filterIsInstance<TunnelEvent.Exited>()
        assertEquals(1, e3.size)
        assertNull(m.status.inside)
    }

    @Test
    fun `입구에서 멀어지는 중이면 접근 안내하지 않음`() {
        val m = TunnelMonitor(db)
        m.update(LatLng(36.35, 127.3975))
        m.reset()
        m.update(LatLng(36.35, 127.3975))
        val away = m.update(LatLng(36.35, 127.3960))
        assertTrue(away.filterIsInstance<TunnelEvent.Approaching>().size <= 1)
    }

    @Test
    fun `이탈 직후 반대 방향 쌍둥이 터널은 접근으로 보지 않는다`() {
        val twinCsv = csv + "\n3;테스트터널;primary;600;36.350300,127.406700|36.350300,127.403300|36.350300,127.400000"
        val m = TunnelMonitor(TunnelDatabase.parse(twinCsv))
        m.update(LatLng(36.35, 127.395))
        m.update(LatLng(36.35, 127.4015))
        m.update(LatLng(36.35, 127.404))
        val afterExit = m.update(LatLng(36.35, 127.409))
        assertTrue(afterExit.any { it is TunnelEvent.Exited })
        assertTrue(afterExit.none { it is TunnelEvent.Approaching })
        val further = m.update(LatLng(36.35, 127.4105))
        assertTrue(further.none { it is TunnelEvent.Approaching })
        assertNull(m.status.inside)
    }

    @Test
    fun `가장 가까운 터널과 범위 조회`() {
        val near = db.nearest(LatLng(36.351, 127.402))
        assertEquals("테스트터널", near!!.first.name)
        assertTrue(near.second < 150)
        assertEquals(1, db.within(36.34, 36.36, 127.39, 127.41).size)
        assertEquals(0, db.entriesWithin(LatLng(36.30, 127.30), 1000.0).size)
    }
}
