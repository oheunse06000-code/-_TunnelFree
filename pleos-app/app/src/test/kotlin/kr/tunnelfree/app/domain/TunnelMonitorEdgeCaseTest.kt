package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelMonitorEdgeCaseTest {

    private val lineA = "1;터널A;primary;600;36.350000,127.400000|36.350000,127.403300|36.350000,127.406700"
    private val lineF = "2;멀리터널;tertiary;200;36.420000,127.500000|36.420000,127.502000"
    private val lineB = "3;터널B;primary;600;36.360000,127.400000|36.360000,127.406700"
    private val lineC = "4;터널C;primary;450;36.350000,127.413000|36.350000,127.418000"
    private val lineD = "5;터널D;primary;600;36.350000,127.409000|36.350000,127.415700"
    private val lineTwin = "6;터널A;primary;600;36.350300,127.406700|36.350300,127.403300|36.350300,127.400000"

    private fun db(vararg lines: String) = TunnelDatabase.parse(lines.joinToString("\n"))
    private fun at(lng: Double, lat: Double = 36.35) = LatLng(lat, lng)

    private val base = db(lineA, lineF)

    @Test
    fun `접근 → 멀어짐 → 다시 접근 → 진입 → 이탈의 이벤트와 상태`() {
        val m = TunnelMonitor(base)
        val a = base.tunnels[0]

        assertTrue(m.update(at(127.389)).isEmpty())
        assertNull(m.status.inside)
        assertEquals(a, m.status.nearest)
        assertFalse(m.status.approaching)
        assertEquals(distanceMeters(at(127.389), a.entry).toInt(), m.status.nearestDistanceM)

        val e1 = m.update(at(127.395))
        val ap = e1.single() as TunnelEvent.Approaching
        assertEquals(a, ap.tunnel)
        assertEquals(distanceMeters(at(127.395), a.entry).toInt(), ap.distanceM)
        assertTrue(m.status.approaching)
        assertEquals(ap.distanceM, m.status.nearestDistanceM)

        assertTrue(m.update(at(127.398)).isEmpty())
        assertTrue(m.status.approaching)
        assertEquals(179.0, m.status.nearestDistanceM!!.toDouble(), 1.0)

        assertTrue(m.update(at(127.397)).isEmpty())
        assertFalse(m.status.approaching)
        assertEquals(a, m.status.nearest)

        assertTrue(m.update(at(127.3985)).isEmpty())
        assertTrue(m.status.approaching)

        val e2 = m.update(at(127.4015))
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Entered(a)), e2)
        assertEquals(a, m.status.inside)
        assertEquals(a, m.status.nearest)
        assertEquals(0, m.status.nearestDistanceM)
        assertFalse(m.status.approaching)

        val e3 = m.update(at(127.409))
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Exited(a)), e3)
        assertNull(m.status.inside)
        assertEquals(a, m.status.nearest)
        assertFalse(m.status.approaching)
        assertEquals(206.0, m.status.nearestDistanceM!!.toDouble(), 2.0)
    }

    @Test
    fun `진입은 중심선 30 m 미만, 이탈은 60 m 초과 (히스테리시스)`() {
        val m = TunnelMonitor(base)
        val ev = m.update(at(127.4033, 36.350314))
        assertTrue(ev.none { it is TunnelEvent.Entered })
        assertNull(m.status.inside)
        assertTrue(m.update(at(127.4035, 36.350225)).single() is TunnelEvent.Entered)
        assertTrue(m.update(at(127.404, 36.35045)).isEmpty())
        assertEquals(0, m.status.nearestDistanceM)
        val out = m.update(at(127.4045, 36.35063))
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Exited(base.tunnels[0])), out)
    }

    @Test
    fun `현재 동작 - 터널 옆 30~60 m 를 나란히 달리는 지상 도로는 접근 안내를 받는다`() {
        val m = TunnelMonitor(base)
        val ev = m.update(at(127.4033, 36.350314))
        assertTrue(ev.single() is TunnelEvent.Approaching)
    }

    @Test
    fun `첫 샘플이 바로 중심선 위면 접근 없이 진입만`() {
        val m = TunnelMonitor(base)
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Entered(base.tunnels[0])), m.update(at(127.403)))
    }

    @Test
    fun `제자리에 서 있으면 approaching 은 false (거리가 줄어야 함)`() {
        val m = TunnelMonitor(base)
        m.update(at(127.395))
        assertTrue(m.status.approaching)
        assertTrue(m.update(at(127.395)).isEmpty())
        assertFalse(m.status.approaching)
    }

    @Test
    fun `현재 동작 - 반경 밖으로 나갔다 돌아와도 같은 터널 접근 안내는 다시 하지 않는다`() {
        val m = TunnelMonitor(base)
        assertEquals(1, m.update(at(127.395)).size)
        assertTrue(m.update(at(127.389)).isEmpty())
        assertTrue(m.update(at(127.395)).isEmpty())
        assertTrue(m.status.approaching)
    }

    @Test
    fun `반경을 줄이면 그 밖에서는 접근 안내 없음`() {
        val m = TunnelMonitor(base, approachRadiusM = 100.0)
        assertTrue(m.update(at(127.398)).isEmpty())
        assertFalse(m.status.approaching)
        assertEquals(1, m.update(at(127.3995)).size)
    }

    @Test
    fun `다른 터널로 바뀌면 새 터널을 다시 안내`() {
        val d = db(lineA, lineB)
        val m = TunnelMonitor(d)
        assertEquals(d.tunnels[0], (m.update(at(127.395)).single() as TunnelEvent.Approaching).tunnel)
        assertEquals(d.tunnels[1], (m.update(at(127.396, 36.36)).single() as TunnelEvent.Approaching).tunnel)
    }

    @Test
    fun `직전 거리 기억은 터널마다 따로라 더 먼 새 터널도 첫 샘플에서 안내한다`() {
        val d = db(lineA, lineB)
        val m = TunnelMonitor(d)
        m.update(at(127.395))
        m.update(at(127.398))
        val ev = m.update(at(127.395, 36.36))
        assertEquals(d.tunnels[1], (ev.single() as TunnelEvent.Approaching).tunnel)
        assertTrue(m.status.approaching)
        assertTrue(m.update(at(127.394, 36.36)).isEmpty())
        assertFalse(m.status.approaching)
    }

    @Test
    fun `GPS 가 터널 안에서 다른 터널 위로 튀면 이탈과 진입이 한 번에`() {
        val m = TunnelMonitor(base)
        m.update(at(127.403))
        val ev = m.update(LatLng(36.42, 127.501))
        assertEquals(listOf(TunnelEvent.Exited(base.tunnels[0]), TunnelEvent.Entered(base.tunnels[1])), ev)
        assertEquals(base.tunnels[1], m.status.inside)
    }

    @Test
    fun `이탈 직후 250 m 밖에 입구가 있는 다음 터널은 같은 갱신에서 접근 안내`() {
        val d = db(lineA, lineC)
        val m = TunnelMonitor(d)
        m.update(at(127.403))
        val ev = m.update(at(127.409))
        assertEquals(2, ev.size)
        assertEquals(TunnelEvent.Exited(d.tunnels[0]), ev[0])
        val ap = ev[1] as TunnelEvent.Approaching
        assertEquals(d.tunnels[1], ap.tunnel)
        assertEquals(358.0, ap.distanceM.toDouble(), 2.0)
    }

    @Test
    fun `출구 250 m 안에 입구가 있어도 같은 방향 연속 터널은 쌍둥이가 아니라 접근·진입을 안내한다`() {
        val d = db(lineA, lineD)
        val tD = d.tunnels[1]
        val m = TunnelMonitor(d)
        m.update(at(127.395))
        m.update(at(127.403))
        val exit = m.update(at(127.4075))
        assertEquals(listOf(TunnelEvent.Exited(d.tunnels[0]), TunnelEvent.Approaching(tD, exit.filterIsInstance<TunnelEvent.Approaching>().single().distanceM)), exit)
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Entered(tD)), m.update(at(127.410)))
    }

    @Test
    fun `반대 방향 보어는 여전히 쌍둥이로 억제한다`() {
        val d = db(lineA, lineTwin)
        val m = TunnelMonitor(d)
        m.update(at(127.395))
        m.update(at(127.403))
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Exited(d.tunnels[0])), m.update(at(127.409)))
        assertTrue(m.update(at(127.408, 36.3503)).isEmpty())
    }

    @Test
    fun `쌍둥이 억제는 출구에서 600 m 넘게 멀어지면 풀린다`() {
        val d = db(lineA, lineTwin)
        val twin = d.tunnels[1]
        val m = TunnelMonitor(d)
        m.update(at(127.395))
        m.update(at(127.403))
        assertTrue(m.update(at(127.409)).single() is TunnelEvent.Exited)
        assertTrue(m.update(at(127.414)).isEmpty())
        val ev = m.update(at(127.410))
        assertEquals(twin, (ev.single() as TunnelEvent.Approaching).tunnel)
    }

    @Test
    fun `방금 나온 터널 자체는 쌍둥이 억제 대상이 아니다`() {
        val m = TunnelMonitor(base)
        m.update(at(127.403))
        m.update(at(127.409))
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Entered(base.tunnels[0])), m.update(at(127.406)))
    }

    @Test
    fun `짧은 터널을 빠져나와도 방금 나온 터널 입구에 대해 접근 안내를 다시 하지 않는다`() {
        val short = db("7;짧은터널;primary;134;36.350000,127.400000|36.350000,127.401500")
        val t = short.tunnels[0]
        val m = TunnelMonitor(short)
        m.update(at(127.395))
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Entered(t)), m.update(at(127.4007)))
        val ev = m.update(at(127.4025))
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Exited(t)), ev)
        assertTrue(m.update(at(127.404)).isEmpty())
        assertFalse(m.status.approaching)
    }

    @Test
    fun `두 터널 중심선이 모두 30 m 안이면 더 가까운 쪽으로 진입`() {
        val d = db(lineA, lineTwin)
        val m = TunnelMonitor(d)
        assertEquals(d.tunnels[1], (m.update(at(127.403, 36.35025)).single() as TunnelEvent.Entered).tunnel)
    }

    @Test
    fun `relevance 로 제외한 터널은 접근·진입하지 않는다`() {
        val m = TunnelMonitor(base)
        m.relevance = { it.id != 1L }
        assertTrue(m.update(at(127.395)).isEmpty())
        assertTrue(m.update(at(127.398)).isEmpty())
        assertTrue(m.update(at(127.403)).isEmpty())
        assertTrue(m.update(at(127.409)).isEmpty())
        assertNull(m.status.inside)
        assertFalse(m.status.approaching)
    }

    @Test
    fun `현재 동작 - 접근 반경 밖 상태의 nearest 는 relevance 를 무시한다`() {
        val m = TunnelMonitor(base)
        m.relevance = { false }
        m.update(at(127.403))
        assertNull(m.status.inside)
        assertEquals(base.tunnels[0], m.status.nearest)
        assertEquals(0, m.status.nearestDistanceM)
    }

    @Test
    fun `relevance 는 일부만 제외 - 나머지 터널은 정상 동작`() {
        val d = db(lineA, lineB)
        val m = TunnelMonitor(d)
        m.relevance = { it.id == 3L }
        assertTrue(m.update(at(127.395)).isEmpty())
        assertEquals(d.tunnels[1], (m.update(at(127.396, 36.36)).single() as TunnelEvent.Approaching).tunnel)
    }

    @Test
    fun `현재 동작 - 통과 중에 relevance 가 바뀌어도 이탈 판정은 그대로 한다`() {
        val m = TunnelMonitor(base)
        m.update(at(127.403))
        m.relevance = { false }
        assertTrue(m.update(at(127.405)).isEmpty())
        assertEquals(base.tunnels[0], m.status.inside)
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Exited(base.tunnels[0])), m.update(at(127.409)))
    }

    @Test
    fun `reset 은 상태를 비우고 이탈 이벤트 없이 처음부터 다시 판정`() {
        val m = TunnelMonitor(base)
        m.update(at(127.395))
        m.update(at(127.403))
        m.reset()
        assertEquals(TunnelStatus(), m.status)
        assertEquals(listOf<TunnelEvent>(TunnelEvent.Entered(base.tunnels[0])), m.update(at(127.404)))
    }

    @Test
    fun `reset 후에는 같은 터널 접근 안내를 다시 한다`() {
        val m = TunnelMonitor(base)
        assertEquals(1, m.update(at(127.395)).size)
        m.reset()
        assertEquals(1, m.update(at(127.395)).size)
    }

    @Test
    fun `reset 은 쌍둥이 억제도 푼다`() {
        val d = db(lineA, lineTwin)
        val m = TunnelMonitor(d)
        m.update(at(127.403))
        m.update(at(127.409))
        m.reset()
        val ev = m.update(at(127.409))
        assertEquals(d.tunnels[1], (ev.single() as TunnelEvent.Approaching).tunnel)
    }

    @Test
    fun `빈 DB 는 이벤트도 nearest 도 없다`() {
        val m = TunnelMonitor(TunnelDatabase.EMPTY)
        assertTrue(m.update(at(127.403)).isEmpty())
        assertEquals(TunnelStatus(), m.status)
    }
}
