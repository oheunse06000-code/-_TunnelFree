package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadNetworkTest {

    private val text = """
        # 시험용
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

    private val net = RoadNetwork.parse(text)
    private val a = LatLng(36.3000, 127.3000)
    private val c = LatLng(36.3050, 127.3000)
    private val d = LatLng(36.3000, 127.3100)
    private val e = LatLng(36.3100, 127.3000)
    private val b = LatLng(36.3050, 127.3100)
    private val f = LatLng(36.3050, 127.3200)

    @Test
    fun parsesVerticesAndEdges() {
        assertEquals(6, net.vertexCount)
        assertEquals(6, net.edges.size)
        assertEquals("시험터널", net.edges[0].name)
        assertTrue(net.edges[0].isTunnel)
        assertTrue(net.isJunction(1))
        assertTrue(!net.isJunction(4))
    }

    @Test
    fun fullAvoidanceGoesAroundTunnel() {
        val r = net.route(a, d, AvoidanceLevel.FULL)!!
        assertTrue(r.tunnels.isEmpty())
        assertTrue(r.points.contains(c))
        assertEquals(2010.0, r.lengthM, 30.0)
    }

    @Test
    fun noAvoidanceTakesTunnel() {
        val r = net.route(a, d, AvoidanceLevel.NONE)!!
        assertEquals(1, r.tunnels.size)
        assertEquals("시험터널", r.tunnels[0].name)
        assertEquals(900.0, r.tunnels[0].lengthM, 0.1)
        assertEquals(1612.0, r.lengthM, 30.0)
        assertNotNull(r.tunnelAt(500.0))
        assertNull(r.tunnelAt(1500.0))
        assertTrue(r.hasTunnelNear(LatLng(36.3025, 127.3050)))
        assertTrue(!r.hasTunnelNear(c))
        assertTrue(!net.route(a, d, AvoidanceLevel.FULL)!!.hasTunnelNear(LatLng(36.3025, 127.3050)))
    }

    @Test
    fun levelsCompareTunnelLength() {
        assertTrue(net.route(a, d, AvoidanceLevel.SHORT_OK)!!.tunnels.isEmpty())
        assertEquals(1, net.route(a, d, AvoidanceLevel.MEDIUM_OK)!!.tunnels.size)
    }

    @Test
    fun onewayIsRespected() {
        assertNotNull(net.route(b, f, AvoidanceLevel.NONE))
        assertNull(net.route(f, b, AvoidanceLevel.NONE))
    }

    @Test
    fun maneuversAreRightTurnsAtJunctions() {
        val r = net.route(a, d, AvoidanceLevel.FULL)!!
        val turns = r.maneuvers.filter { it.type != TurnType.DEPART && it.type != TurnType.ARRIVE }
        assertEquals(2, turns.size)
        assertEquals(TurnType.RIGHT, turns[0].type)
        assertEquals(556.0, turns[0].atM, 10.0)
        assertEquals("동로", turns[0].roadName)
        assertEquals(TurnType.RIGHT, turns[1].type)
        assertEquals(TurnType.ARRIVE, r.maneuvers.last().type)
        assertTrue(r.nextManeuver(100.0)!!.spoken(456.0).contains("우회전"))
        assertTrue(r.nextManeuver(100.0)!!.spoken(456.0).contains("동로"))
    }

    @Test
    fun pointAtAndProjection() {
        val r = net.route(a, d, AvoidanceLevel.FULL)!!
        assertEquals(a.lat, r.pointAt(0.0).lat, 1e-9)
        assertEquals(d.lng, r.pointAt(r.lengthM).lng, 1e-9)
        val mid = r.pointAt(1000.0)
        assertEquals(36.305, mid.lat, 1e-6)
        val proj = r.project(LatLng(36.3051, 127.3050), fromM = 800.0)
        assertEquals(1004.0, proj.progressM, 40.0)
        assertTrue(proj.offsetM < 30.0)
        assertEquals(0.0, r.bearingAt(200.0), 1.0)
        assertEquals(90.0, r.bearingAt(1000.0), 1.0)
    }

    @Test
    fun roadClassFollowsRouteSegments() {
        val mixed = RoadNetwork.parse(text.replace("1 2 8970 4", "1 2 8970 0"))
        val r = mixed.route(a, d, AvoidanceLevel.FULL)!!
        assertEquals(RoadClass.PRIMARY, r.roadClassAt(100.0))
        assertEquals(RoadClass.MOTORWAY, r.roadClassAt(1000.0))
        assertEquals(RoadClass.PRIMARY, r.roadClassAt(r.lengthM - 10.0))
        assertEquals(3, r.roadSegments.size)
        assertNull(r.roadClassAt(r.lengthM + 100.0))
        assertEquals(r.roadSegments, r.withManeuvers(emptyList()).roadSegments)
    }

    @Test
    fun viaRouteMarksWaypoint() {
        val r = net.route(a, d, AvoidanceLevel.FULL, via = e)!!
        assertNotNull(r.viaM)
        assertEquals(1113.0, r.viaM!!, 20.0)
        assertTrue(r.points.contains(e))
        assertEquals(3123.0, r.lengthM, 40.0)
    }

    @Test
    fun remainingAndEta() {
        val r = net.route(a, d, AvoidanceLevel.FULL)!!
        assertEquals(r.lengthM, r.remainingM(0.0), 1e-9)
        assertEquals(0.0, r.remainingM(r.lengthM + 10), 1e-9)
        assertTrue(r.minutes in 2..4)
        assertEquals(r.minutes, r.remainingMinutes(0.0))
    }

    @Test
    fun emptyNetworkRoutesNull() {
        assertNull(RoadNetwork.EMPTY.route(a, d, AvoidanceLevel.FULL))
        assertEquals("1.2 km", formatDistance(1234.0))
        assertEquals("300 m", formatDistance(296.0))
        assertEquals("300미터", spokenDistance(296.0))
    }
}
