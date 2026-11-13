package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadNetworkEdgeCaseTest {

    private val baseText = """
        V 6
        36300000 127300000
        36305000 127300000
        36305000 127310000
        36300000 127310000
        36310000 127300000
        36305000 127320000
        P 0
        E 6
        0 2 10557 4 0 900 0 0 시험터널
        0 1 5566 4 0 0 0 0 북로
        1 2 8970 4 0 0 0 0 동로
        2 3 5566 4 0 0 0 0 남로
        1 4 5566 4 0 0 0 0 북로
        2 5 8970 4 1 0 0 0 일방로
    """.trimIndent()
    private val base = RoadNetwork.parse(baseText)
    private val a = LatLng(36.3000, 127.3000)
    private val d = LatLng(36.3000, 127.3100)
    private val e = LatLng(36.3100, 127.3000)
    private val f = LatLng(36.3050, 127.3200)

    private fun tunnelOnly(tunnelLengthM: String): RoadNetwork = RoadNetwork.parse(
        """
        V 2
        36300000 127300000
        36310000 127300000
        P 0
        E 1
        0 1 11132 4 0 $tunnelLengthM 0 0 단일터널
        """.trimIndent()
    )
    private val t0 = LatLng(36.300, 127.300)
    private val t1 = LatLng(36.310, 127.300)

    @Test
    fun `EMPTY 도로망은 비어 있고 어떤 경로도 null`() {
        val empty = RoadNetwork.EMPTY
        assertTrue(empty.isEmpty)
        assertEquals(0, empty.vertexCount)
        assertEquals(-1, empty.nearestVertex(a))
        assertEquals(Bounds(0.0, 0.0, 0.0, 0.0), empty.bounds)
        for (level in AvoidanceLevel.entries) {
            assertNull(empty.route(a, d, level))
            assertNull(empty.route(a, d, level, via = e))
        }
    }

    @Test
    fun `빈 문자열·주석만 있는 텍스트는 빈 도로망`() {
        assertTrue(RoadNetwork.parse("").isEmpty)
        val onlyComments = RoadNetwork.parse("# 주석\n# 또 주석\n")
        assertTrue(onlyComments.isEmpty)
        assertEquals(0, onlyComments.vertexCount)
    }

    @Test
    fun `정점만 있고 간선이 없으면 isEmpty 이고 경로 null`() {
        val net = RoadNetwork.parse("V 2\n36300000 127300000\n36310000 127300000\nE 0\n")
        assertTrue(net.isEmpty)
        assertEquals(2, net.vertexCount)
        assertEquals(1, net.nearestVertex(t1))
        assertNull(net.route(t0, t1, AvoidanceLevel.NONE))
    }

    @Test
    fun `서로 끊긴 두 구역 사이에는 경로가 없다`() {
        val net = RoadNetwork.parse(
            """
            V 4
            36300000 127300000
            36305000 127300000
            36300000 127330000
            36305000 127330000
            P 0
            E 2
            0 1 5566 4 0 0 0 0 서쪽길
            2 3 5566 4 0 0 0 0 동쪽길
            """.trimIndent()
        )
        val w0 = LatLng(36.300, 127.300); val w1 = LatLng(36.305, 127.300)
        val e0 = LatLng(36.300, 127.330); val e1 = LatLng(36.305, 127.330)
        for (level in AvoidanceLevel.entries) {
            assertNull(net.route(w0, e1, level))
            assertNull(net.route(e0, w1, level))
        }
        assertNotNull(net.route(w0, w1, AvoidanceLevel.FULL))
        assertNotNull(net.route(e0, e1, AvoidanceLevel.FULL))
        assertNull(net.route(w0, w1, AvoidanceLevel.NONE, via = e0))
    }

    @Test
    fun `터널로만 이어진 경로는 FULL 에서 null, NONE 에서 찾는다`() {
        val net = tunnelOnly("500")
        assertNull(net.route(t0, t1, AvoidanceLevel.FULL))
        val r = net.route(t0, t1, AvoidanceLevel.NONE)!!
        assertEquals(1, r.tunnelCount)
        assertEquals("단일터널", r.tunnels[0].name)
        assertEquals(500.0, r.tunnels[0].lengthM, 1e-9)
        assertEquals(0.0, r.tunnels[0].startM, 1e-9)
        assertEquals(r.lengthM, r.tunnels[0].endM, 1e-9)
    }

    @Test
    fun `SHORT_OK 는 300 m 까지 허용 (경계 포함)`() {
        assertNotNull(tunnelOnly("300").route(t0, t1, AvoidanceLevel.SHORT_OK))
        assertNull(tunnelOnly("301").route(t0, t1, AvoidanceLevel.SHORT_OK))
        assertNull(tunnelOnly("300.5").route(t0, t1, AvoidanceLevel.SHORT_OK))
        assertNotNull(tunnelOnly("301").route(t0, t1, AvoidanceLevel.MEDIUM_OK))
    }

    @Test
    fun `MEDIUM_OK 는 1000 m 까지 허용 (경계 포함)`() {
        assertNotNull(tunnelOnly("1000").route(t0, t1, AvoidanceLevel.MEDIUM_OK))
        assertNull(tunnelOnly("1001").route(t0, t1, AvoidanceLevel.MEDIUM_OK))
        assertNull(tunnelOnly("1001").route(t0, t1, AvoidanceLevel.SHORT_OK))
        assertNotNull(tunnelOnly("1001").route(t0, t1, AvoidanceLevel.NONE))
        assertNotNull(tunnelOnly("50000").route(t0, t1, AvoidanceLevel.NONE))
    }

    @Test
    fun `FULL 은 아주 짧은 터널도 피한다`() {
        assertNull(tunnelOnly("1").route(t0, t1, AvoidanceLevel.FULL))
        val plain = tunnelOnly("0")
        assertFalse(plain.edges[0].isTunnel)
        assertTrue(plain.route(t0, t1, AvoidanceLevel.FULL)!!.tunnels.isEmpty())
    }

    @Test
    fun `AvoidanceLevel 경계값`() {
        assertFalse(AvoidanceLevel.FULL.shouldAvoid(0.0))
        assertTrue(AvoidanceLevel.FULL.shouldAvoid(0.1))
        assertFalse(AvoidanceLevel.SHORT_OK.shouldAvoid(300.0))
        assertTrue(AvoidanceLevel.SHORT_OK.shouldAvoid(300.01))
        assertFalse(AvoidanceLevel.MEDIUM_OK.shouldAvoid(1000.0))
        assertTrue(AvoidanceLevel.MEDIUM_OK.shouldAvoid(1000.01))
        assertFalse(AvoidanceLevel.NONE.shouldAvoid(1e9))
        assertFalse(AvoidanceLevel.NONE.avoidsAny)
        assertTrue(AvoidanceLevel.FULL.avoidsAny)
        assertEquals(AvoidanceLevel.FULL, AvoidanceLevel.fromName(null))
        assertEquals(AvoidanceLevel.FULL, AvoidanceLevel.fromName("없는값"))
        assertEquals(AvoidanceLevel.MEDIUM_OK, AvoidanceLevel.fromName("MEDIUM_OK"))
    }

    @Test
    fun `같은 이름의 연속 터널 간선은 터널 하나로 합친다`() {
        val net = RoadNetwork.parse(
            """
            V 3
            36300000 127300000
            36305000 127300000
            36310000 127300000
            P 0
            E 2
            0 1 5566 4 0 1113 0 0 긴터널
            1 2 5566 4 0 1113 0 0 긴터널
            """.trimIndent()
        )
        val r = net.route(t0, t1, AvoidanceLevel.NONE)!!
        assertEquals(1, r.tunnelCount)
        assertEquals(1113.0, r.tunnels[0].lengthM, 1e-9)
        assertEquals(0.0, r.tunnels[0].startM, 1e-9)
        assertEquals(r.lengthM, r.tunnels[0].endM, 1e-9)
        assertNull(net.route(t0, t1, AvoidanceLevel.MEDIUM_OK))
    }

    @Test
    fun `비용이 같은 두 경로는 둘 중 하나를 결정적으로 고른다`() {
        val net = RoadNetwork.parse(
            """
            V 4
            36300000 127300000
            36310000 127300000
            36300000 127312000
            36310000 127312000
            P 0
            E 4
            0 1 11000 4 0 0 0 0 -
            1 3 11000 4 0 0 0 0 -
            0 2 11000 4 0 0 0 0 -
            2 3 11000 4 0 0 0 0 -
            """.trimIndent()
        )
        val from = LatLng(36.300, 127.300)
        val to = LatLng(36.310, 127.312)
        val r1 = net.route(from, to, AvoidanceLevel.FULL)!!
        val r2 = net.route(from, to, AvoidanceLevel.FULL)!!
        assertEquals(3, r1.points.size)
        assertEquals(from, r1.start)
        assertEquals(to, r1.end)
        val mid = r1.points[1]
        assertTrue(mid == net.vertex(1) || mid == net.vertex(2))
        assertEquals(2200.0 / (55.0 / 3.6), r1.travelSec, 1e-6)
        assertEquals(r1.points, r2.points)
        assertEquals(r1.travelSec, r2.travelSec, 0.0)
    }

    @Test
    fun `출발과 도착이 같으면 경로 null (길이 0 경로는 만들지 않음)`() {
        for (level in AvoidanceLevel.entries) {
            assertNull(base.route(a, a, level))
        }
        assertNull(base.route(a, LatLng(36.30009, 127.3000), AvoidanceLevel.NONE))
    }

    @Test
    fun `경유지가 출발지와 같으면 viaM 은 0`() {
        val r = base.route(a, d, AvoidanceLevel.FULL, via = a)!!
        assertEquals(0.0, r.viaM!!, 1e-9)
        val direct = base.route(a, d, AvoidanceLevel.FULL)!!
        assertEquals(direct.lengthM, r.lengthM, 1e-6)
        assertEquals(direct.points, r.points)
    }

    @Test
    fun `경유지가 도착지와 같으면 viaM 은 전체 길이`() {
        val r = base.route(a, d, AvoidanceLevel.FULL, via = d)!!
        assertEquals(r.lengthM, r.viaM!!, 1e-6)
        assertEquals(TurnType.ARRIVE, r.maneuvers.last().type)
    }

    @Test
    fun `경유지 이후 구간에 길이 없으면 전체가 null`() {
        assertNotNull(base.route(a, f, AvoidanceLevel.NONE))
        assertNull(base.route(a, d, AvoidanceLevel.NONE, via = f))
    }

    @Test
    fun `경유지 없이 구한 경로는 viaM 이 null`() {
        assertNull(base.route(a, d, AvoidanceLevel.NONE)!!.viaM)
    }

    @Test
    fun `경유지로 가는 구간도 회피 강도를 지킨다`() {
        val net = tunnelOnly("500")
        assertNull(net.route(t0, t0, AvoidanceLevel.FULL, via = t1))
        val r = net.route(t0, t0, AvoidanceLevel.NONE, via = t1)!!
        assertEquals(2, r.tunnelCount)
        assertEquals(0.0, r.tunnels[0].startM, 1e-9)
        assertEquals(r.lengthM / 2, r.tunnels[0].endM, 1e-6)
        assertEquals(r.lengthM / 2, r.tunnels[1].startM, 1e-6)
        assertEquals(r.lengthM, r.tunnels[1].endM, 1e-6)
        assertEquals(r.lengthM / 2, r.viaM!!, 1e-6)
    }
}
