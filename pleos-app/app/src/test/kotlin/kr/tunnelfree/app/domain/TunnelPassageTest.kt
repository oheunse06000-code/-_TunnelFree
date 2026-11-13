package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelPassageTest {

    @Test
    fun `체류 시간은 시속 60 km 기준이다`() {
        assertEquals(60.0, TunnelTiming.dwellSec(1_000.0), 0.01)
        assertEquals(180.0, TunnelTiming.dwellSec(3_000.0), 0.01)
        assertEquals(360.0, TunnelTiming.dwellSec(1_000.0, 10.0), 0.01)
        assertEquals(720.0, TunnelTiming.dwellSec(1_000.0, 0.0), 0.01)
    }

    @Test
    fun `등급 경계는 60초와 180초다`() {
        assertEquals(TunnelTier.SHORT, TunnelTier.ofLength(999.0))
        assertEquals(TunnelTier.MEDIUM, TunnelTier.ofLength(1_000.0))
        assertEquals(TunnelTier.MEDIUM, TunnelTier.ofLength(2_999.0))
        assertEquals(TunnelTier.LONG, TunnelTier.ofLength(3_000.0))
    }

    @Test
    fun `한도 초 변환은 제한 없음을 유지한다`() {
        assertEquals(18.0, TunnelTiming.limitSec(300.0), 0.01)
        assertEquals(Double.MAX_VALUE, TunnelTiming.limitSec(Double.MAX_VALUE), 0.0)
    }

    private val tunnelRoute = TestRoutes.straight(5_000.0, tunnels = listOf(RouteTunnel("시험터널", 600.0, 3_000.0, 3_600.0)))

    private fun feed(check: TunnelAheadCheck, progress: Double, speed: Double?, limitM: Double, distressed: Boolean = false): TunnelAheadAlert? {
        var last: TunnelAheadAlert? = null
        for (t in 0..30) last = check.update(tunnelRoute, progress, t * 1_000L, speed, limitM, distressed) ?: last
        return last
    }

    @Test
    fun `입구 1 km 안 정체면 체류 시간으로 다시 판단한다`() {
        val alert = feed(TunnelAheadCheck(), progress = 2_500.0, speed = 10.0, limitM = 1_000.0)
        assertNotNull(alert)
        assertTrue(alert!!.congested)
        assertEquals(216.0, alert.dwellSec, 0.5)
        assertEquals(500.0, alert.distanceM, 0.01)
    }

    @Test
    fun `정체가 없고 불안도 없으면 알리지 않는다`() {
        assertNull(feed(TunnelAheadCheck(), progress = 2_500.0, speed = 50.0, limitM = 0.0))
    }

    @Test
    fun `입구에서 1 km 보다 멀면 알리지 않는다`() {
        assertNull(feed(TunnelAheadCheck(), progress = 1_500.0, speed = 10.0, limitM = 0.0))
    }

    @Test
    fun `30초 표본이 쌓이기 전에는 정체로 보지 않는다`() {
        val check = TunnelAheadCheck()
        for (t in 0..20) assertNull(check.update(tunnelRoute, 2_500.0, t * 1_000L, 10.0, 0.0, false))
    }

    @Test
    fun `정체여도 한도 안이면 알리지 않는다`() {
        assertNull(feed(TunnelAheadCheck(), progress = 2_500.0, speed = 15.0, limitM = 3_000.0))
    }

    @Test
    fun `불안 뒤에는 정체가 없어도 낮아진 한도로 본다`() {
        val alert = feed(TunnelAheadCheck(), progress = 2_500.0, speed = 50.0, limitM = 500.0, distressed = true)
        assertNotNull(alert)
        assertFalse(alert!!.congested)
        assertEquals(36.0, alert.dwellSec, 0.5)
    }

    @Test
    fun `같은 터널은 한 번만 알린다`() {
        val check = TunnelAheadCheck()
        assertNotNull(feed(check, progress = 2_500.0, speed = 10.0, limitM = 0.0))
        for (t in 31..60) assertNull(check.update(tunnelRoute, 2_600.0, t * 1_000L, 10.0, 0.0, false))
    }

    private val longRoute = TestRoutes.straight(6_000.0, tunnels = listOf(RouteTunnel("긴터널", 4_000.0, 1_000.0, 5_000.0)))

    @Test
    fun `긴 터널 안에서는 60초마다 남은 거리를 알린다`() {
        val a = InTunnelAnnouncer()
        assertNull(a.update(longRoute, 1_100.0, 0L, canSpeak = true))
        assertNull(a.update(longRoute, 1_500.0, 30_000L, canSpeak = true))
        assertEquals("출구까지 2.0킬로미터 남았습니다.", a.update(longRoute, 3_000.0, 60_000L, canSpeak = true))
        assertNull(a.update(longRoute, 3_200.0, 90_000L, canSpeak = true))
    }

    @Test
    fun `출구 300 m 전에 한 번 곧 출구를 알린다`() {
        val a = InTunnelAnnouncer()
        a.update(longRoute, 1_100.0, 0L, canSpeak = true)
        assertEquals("곧 출구입니다.", a.update(longRoute, 4_750.0, 10_000L, canSpeak = true))
        assertNull(a.update(longRoute, 4_900.0, 80_000L, canSpeak = true))
    }

    @Test
    fun `짧고 중간인 터널 안에서는 따로 말하지 않는다`() {
        val a = InTunnelAnnouncer()
        val medium = TestRoutes.straight(4_000.0, tunnels = listOf(RouteTunnel("중간", 2_000.0, 1_000.0, 3_000.0)))
        a.update(medium, 1_100.0, 0L, canSpeak = true)
        assertNull(a.update(medium, 2_000.0, 70_000L, canSpeak = true))
        assertNull(a.update(medium, 2_900.0, 140_000L, canSpeak = true))
    }

    @Test
    fun `말할 수 없을 때는 미뤘다가 다음 틱에 말한다`() {
        val a = InTunnelAnnouncer()
        a.update(longRoute, 1_100.0, 0L, canSpeak = true)
        assertNull(a.update(longRoute, 3_000.0, 60_000L, canSpeak = false))
        assertEquals("출구까지 1.9킬로미터 남았습니다.", a.update(longRoute, 3_100.0, 61_000L, canSpeak = true))
    }

    private fun poiAt(progressM: Double, offsetLngDeg: Double = 0.0, category: PoiCategory = PoiCategory.REST_AREA) =
        Poi("p$progressM", "쉼터$progressM", 36.3 + progressM / TestRoutes.M_PER_DEG_LAT, 127.3 + offsetLngDeg, category)

    @Test
    fun `터널 입구에 가장 가까운 앞쪽 쉼터를 고른다`() {
        val tunnel = RouteTunnel("긴터널", 4_000.0, 10_000.0, 14_000.0)
        val route = TestRoutes.straight(16_000.0, tunnels = listOf(tunnel))
        val pois = listOf(poiAt(2_000.0), poiAt(8_000.0), poiAt(15_000.0), poiAt(9_000.0, category = PoiCategory.FUEL))
        assertEquals("쉼터8000.0", RestBeforeTunnel.find(route, tunnel, pois)?.name)
    }

    @Test
    fun `경로에서 먼 쉼터와 터널 뒤 쉼터는 고르지 않는다`() {
        val tunnel = RouteTunnel("긴터널", 4_000.0, 10_000.0, 14_000.0)
        val route = TestRoutes.straight(16_000.0, tunnels = listOf(tunnel))
        val pois = listOf(poiAt(8_000.0, offsetLngDeg = 0.01), poiAt(15_000.0))
        assertNull(RestBeforeTunnel.find(route, tunnel, pois))
    }
}
