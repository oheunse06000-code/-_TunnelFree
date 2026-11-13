package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RealAssetRoutingTest {

    companion object {
        private val net: RoadNetwork by lazy {
            val file = File("src/main/assets/roads_daejeon.txt")
            assertTrue("에셋이 없음: ${file.absolutePath}", file.exists())
            file.bufferedReader().use { RoadNetwork.parse(it) }
        }
        private val tunnelDb: TunnelDatabase by lazy {
            val file = File("src/main/assets/tunnels_daejeon.csv")
            assertTrue("에셋이 없음: ${file.absolutePath}", file.exists())
            TunnelDatabase.parse(file.readText())
        }
        private const val SNAP_M = 400.0
        private val destinations get() = DemoData.destinationIndices.map { DemoData.places[it] }
    }

    @Test
    fun `터널 CSV 는 주석 1줄 + 터널 137개, 버려지는 줄 없음`() {
        val lines = File("src/main/assets/tunnels_daejeon.csv").readLines()
        val dataLines = lines.count { it.isNotBlank() && !it.trimStart().startsWith("#") }
        assertEquals(137, dataLines)
        assertEquals(dataLines, tunnelDb.tunnels.size)
    }

    @Test
    fun `모든 터널은 점 2개 이상·양의 길이·고유 id`() {
        val ts = tunnelDb.tunnels
        assertTrue(ts.all { it.points.size >= 2 })
        assertTrue(ts.all { it.lengthM > 0 })
        assertEquals(61, ts.minOf { it.lengthM })
        assertEquals(2695, ts.maxOf { it.lengthM })
        assertEquals(ts.size, ts.map { it.id }.toSet().size)
        assertTrue(ts.all { it.name.isNotBlank() })
        assertEquals(
            setOf("motorway", "trunk", "primary", "primary_link", "secondary", "tertiary"),
            ts.map { it.highway }.toSet(),
        )
    }

    @Test
    fun `터널은 헤더의 대전 범위(경계에 걸친 way 는 약간 넘침) 안에 있고 도로망 범위에도 들어간다`() {
        val all = tunnelDb.tunnels.flatMap { it.points }
        assertTrue(all.any { it.lat > 36.48 })
        for (t in tunnelDb.tunnels) for (p in t.points) {
            assertTrue("${t.name} $p", p.lat in 36.195..36.485 && p.lng in 127.245..127.565)
            assertTrue("${t.name} $p", net.contains(p))
        }
    }

    @Test
    fun `모든 시연 목적지는 FULL 과 NONE 모두 경로가 있다`() {
        for (place in destinations) for (level in listOf(AvoidanceLevel.FULL, AvoidanceLevel.NONE)) {
            val r = net.route(DemoData.START, place.latLng, level)
            assertNotNull("${place.name} $level", r)
            r!!
            assertTrue(r.lengthM in 1_000.0..40_000.0)
            assertEquals(TurnType.DEPART, r.maneuvers.first().type)
            assertEquals(TurnType.ARRIVE, r.maneuvers.last().type)
            assertTrue("${place.name} 시작 ${distanceMeters(r.start, DemoData.START)}", distanceMeters(r.start, DemoData.START) <= SNAP_M)
            assertTrue("${place.name} 끝 ${distanceMeters(r.end, place.latLng)}", distanceMeters(r.end, place.latLng) <= SNAP_M)
        }
    }

    @Test
    fun `FULL 경로에는 길이가 0 보다 긴 터널이 하나도 없다`() {
        for (place in destinations) {
            val r = net.route(DemoData.START, place.latLng, AvoidanceLevel.FULL)!!
            assertEquals(place.name, 0, r.tunnels.count { it.lengthM > 0 })
            assertEquals(place.name, 0, r.tunnelCount)
        }
    }

    @Test
    fun `회피 강도별 허용 길이를 넘는 터널은 경로에 없다`() {
        for (place in destinations) for (level in AvoidanceLevel.entries) {
            val r = net.route(DemoData.START, place.latLng, level)!!
            assertTrue("${place.name} $level ${r.tunnels}", r.tunnels.none { level.shouldAvoid(it.lengthM) })
        }
    }

    @Test
    fun `회피 안 함 경로는 회피 경로보다 느리지 않다`() {
        for (place in destinations) {
            val full = net.route(DemoData.START, place.latLng, AvoidanceLevel.FULL)!!
            val none = net.route(DemoData.START, place.latLng, AvoidanceLevel.NONE)!!
            assertTrue(place.name, none.travelSec <= full.travelSec + 1e-6)
        }
    }

    @Test
    fun `현재 데이터 - 한밭수목원 최단 경로만 지하차도 3곳을 지나고 나머지 목적지는 최단 경로도 터널 없음`() {
        val counts = destinations.associate { it.name to net.route(DemoData.START, it.latLng, AvoidanceLevel.NONE)!!.tunnels.map { t -> t.name } }
        assertEquals(listOf("만년지하차도", "갑천지하차도", "문예지하차도"), counts["한밭수목원"])
        assertEquals(emptyList<String>(), counts["대전시청"])
        assertEquals(emptyList<String>(), counts["대전역"])
        assertEquals(emptyList<String>(), counts["유성온천역"])
        val place = DemoData.places[6]
        val full = net.route(DemoData.START, place.latLng, AvoidanceLevel.FULL)!!
        val none = net.route(DemoData.START, place.latLng, AvoidanceLevel.NONE)!!
        assertTrue(full.lengthM > none.lengthM)
        assertTrue(full.travelSec > none.travelSec)
    }

    @Test
    fun `졸음쉼터·휴게시설 경유 FULL 경로가 모든 목적지에 나오고 경유 지점이 경유지 근처`() {
        for (viaIdx in listOf(DemoData.REST_STOP_NODE, DemoData.REST_AREA_NODE)) {
            val via = DemoData.places[viaIdx]
            for (place in destinations) {
                val r = net.route(DemoData.START, place.latLng, AvoidanceLevel.FULL, via = via.latLng)
                assertNotNull("${via.name} → ${place.name}", r)
                r!!
                assertEquals(0, r.tunnelCount)
                val viaM = r.viaM!!
                assertTrue("viaM=$viaM", viaM > 0 && viaM < r.lengthM)
                val d = distanceMeters(r.pointAt(viaM), via.latLng)
                assertTrue("${via.name} 경유 지점 거리 $d", d <= SNAP_M)
            }
        }
    }

    @Test
    fun `현재 동작 - 경유지까지 첫 구간은 목적지와 무관하게 같다 (구간별 탐색)`() {
        for (viaIdx in listOf(DemoData.REST_STOP_NODE, DemoData.REST_AREA_NODE)) {
            val via = DemoData.places[viaIdx].latLng
            val viaMs = destinations.map { net.route(DemoData.START, it.latLng, AvoidanceLevel.FULL, via = via)!!.viaM!! }
            assertEquals(1, viaMs.map { Math.round(it * 1000) }.toSet().size)
        }
    }

    private fun drive(route: RoadRoute, monitor: TunnelMonitor, stepM: Double = 20.0): List<TunnelEvent> {
        val events = ArrayList<TunnelEvent>()
        var d = 0.0
        while (d <= route.lengthM) {
            events += monitor.update(route.pointAt(d))
            d += stepM
        }
        events += monitor.update(route.end)
        return events
    }

    private val appTunnelDb: TunnelDatabase by lazy { tunnelDb.mergeSegments() }

    private fun monitorFor(route: RoadRoute) = TunnelMonitor(appTunnelDb).apply {
        relevance = { t -> route.hasTunnelNear(t.entry) || route.hasTunnelNear(t.exit) }
    }

    @Test
    fun `FULL 경로를 경로 relevance 로 달리면 터널 이벤트가 하나도 없다`() {
        for (place in destinations) {
            val r = net.route(DemoData.START, place.latLng, AvoidanceLevel.FULL)!!
            assertEquals(place.name, emptyList<TunnelEvent>(), drive(r, monitorFor(r)))
        }
    }

    @Test
    fun `현재 동작 - relevance 없이 FULL 경로를 달리면 지하차도 위를 지나는 곳에서 진입 오판이 난다`() {
        val place = DemoData.places[6]
        val r = net.route(DemoData.START, place.latLng, AvoidanceLevel.FULL)!!
        val ev = drive(r, TunnelMonitor(appTunnelDb))
        assertTrue(ev.any { it is TunnelEvent.Entered })
    }

    @Test
    fun `한밭수목원 최단 경로 - 지하차도마다 접근·진입·이탈이 순서대로 1번씩`() {
        val place = DemoData.places[6]
        val r = net.route(DemoData.START, place.latLng, AvoidanceLevel.NONE)!!
        val ev = drive(r, monitorFor(r))
        val entered = ev.filterIsInstance<TunnelEvent.Entered>().map { it.tunnel.name }
        val exited = ev.filterIsInstance<TunnelEvent.Exited>().map { it.tunnel.name }
        assertEquals(r.tunnels.map { it.name }, entered)
        assertEquals(entered, exited)
        assertEquals(entered, ev.filterIsInstance<TunnelEvent.Approaching>().map { it.tunnel.name })
        for (name in entered) {
            val firstAp = ev.indexOfFirst { it is TunnelEvent.Approaching && it.tunnel.name == name }
            val en = ev.indexOfFirst { it is TunnelEvent.Entered && it.tunnel.name == name }
            val ex = ev.indexOfFirst { it is TunnelEvent.Exited && it.tunnel.name == name }
            assertTrue(name, firstAp in 0 until en && en < ex)
        }
    }

    @Test
    fun `지하차도를 빠져나온 직후 방금 나온 지하차도에 대해 접근 안내를 다시 하지 않는다`() {
        val place = DemoData.places[6]
        val r = net.route(DemoData.START, place.latLng, AvoidanceLevel.NONE)!!
        val ev = drive(r, monitorFor(r))
        for (name in r.tunnels.map { it.name }) {
            val ex = ev.indexOfFirst { it is TunnelEvent.Exited && it.tunnel.name == name }
            val after = ev.drop(ex + 1)
            assertTrue("$name 이탈 뒤 $after", after.none { it is TunnelEvent.Approaching && it.tunnel.name == name })
        }
    }
}
