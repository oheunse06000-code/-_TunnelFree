package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoadAssetTest {

    private val net: RoadNetwork by lazy {
        val file = File("src/main/assets/roads_daejeon.txt")
        assertTrue("에셋이 없음: ${file.absolutePath}", file.exists())
        file.bufferedReader().use { RoadNetwork.parse(it) }
    }

    @Test
    fun assetParses() {
        assertTrue(net.vertexCount > 10_000)
        assertTrue(net.edges.size > 20_000)
        assertTrue(net.edges.count { it.isTunnel } > 100)
        assertTrue(net.contains(DemoData.START))
    }

    @Test
    fun demoDestinationsAreReachableWithoutTunnels() {
        for (index in DemoData.destinationIndices) {
            val place = DemoData.places[index]
            val r = net.route(DemoData.START, place.latLng, AvoidanceLevel.FULL)
            assertNotNull("회피 경로 없음: ${place.name}", r)
            assertEquals(0, r!!.tunnelCount)
            assertTrue(r.lengthM in 1_000.0..40_000.0)
            assertTrue(r.maneuvers.first().type == TurnType.DEPART && r.maneuvers.last().type == TurnType.ARRIVE)
        }
    }

    @Test
    fun realRoadsMapToStopRoadTypes() {
        val motorway = net.edges.first { it.roadClass == RoadClass.MOTORWAY && !it.isTunnel && it.lengthM > 500 }
        val fast = net.route(net.vertex(motorway.from), net.vertex(motorway.to), AvoidanceLevel.NONE)!!
        val fastMid = fast.lengthM / 2
        assertEquals(StopRoadType.EXPRESSWAY, StopRoadType.of(fast.tunnelAt(fastMid) != null, fast.roadClassAt(fastMid)))

        val tunnel = net.edges.first { it.isTunnel && it.lengthM > 300 }
        val under = net.route(net.vertex(tunnel.from), net.vertex(tunnel.to), AvoidanceLevel.NONE)!!
        val underMid = under.lengthM / 2
        assertEquals(StopRoadType.TUNNEL, StopRoadType.of(under.tunnelAt(underMid) != null, under.roadClassAt(underMid)))

        val surface = net.route(DemoData.START, DemoData.places[5].latLng, AvoidanceLevel.FULL)!!
        assertEquals(surface.lengthM, surface.roadSegments.last().endM, 1e-6)
    }

    @Test
    fun shortestRouteToStationIsNotLongerThanAvoidingOne() {
        val station = DemoData.places[5].latLng
        val avoid = net.route(DemoData.START, station, AvoidanceLevel.FULL)!!
        val shortest = net.route(DemoData.START, station, AvoidanceLevel.NONE)!!
        assertTrue(shortest.travelSec <= avoid.travelSec + 1e-6)
    }
}
