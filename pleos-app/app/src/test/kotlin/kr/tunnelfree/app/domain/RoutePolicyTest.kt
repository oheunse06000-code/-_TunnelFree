package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePolicyTest {

    private fun route(tunnelM: Double?, seconds: Double): RoadRoute {
        val tunnels = tunnelM?.let { listOf(RouteTunnel("시험터널", it, 100.0, 100.0 + it)) }.orEmpty()
        return RoadRoute(listOf(LatLng(36.0, 127.0), LatLng(36.1, 127.0)), doubleArrayOf(0.0, 11_000.0), seconds, tunnels, emptyList(), null)
    }

    private val ctx = RouteContext(AvoidanceLevel.MEDIUM_OK)

    @Test
    fun `최단 경로에 터널이 없으면 차이가 없다`() {
        val a = RoutePolicy.decide(ctx, route(null, 600.0), null)
        assertEquals(RouteVerdict.NO_CHOICE, a.verdict)
    }

    @Test
    fun `회피 길이 없으면 꼭 지나간다`() {
        val a = RoutePolicy.decide(ctx, route(2_000.0, 600.0), null)
        assertEquals(RouteVerdict.MUST_PASS, a.verdict)
        assertEquals(RouteChoice.SHORTEST, a.recommended)
    }

    @Test
    fun `한도보다 긴 터널은 돌아가더라도 피한다`() {
        val a = RoutePolicy.decide(ctx, route(2_000.0, 600.0), route(null, 1_800.0))
        assertEquals(RouteVerdict.MUST_AVOID, a.verdict)
        assertEquals(RouteChoice.AVOIDING, a.recommended)
        assertEquals(20, a.extraMinutes)
    }

    @Test
    fun `한도 안의 터널은 운전자가 선택한다`() {
        val a = RoutePolicy.decide(ctx, route(500.0, 600.0), route(null, 900.0))
        assertEquals(RouteVerdict.CHOICE, a.verdict)
        assertEquals(RouteChoice.SHORTEST, a.recommended)
    }

    @Test
    fun `모든 터널 회피는 0 m 한도라고 말하지 않는다`() {
        val a = RoutePolicy.decide(RouteContext(AvoidanceLevel.FULL), route(170.0, 600.0), route(null, 600.0))
        assertEquals("터널 없이도 시간이 같습니다", a.headline)
        assertTrue(a.reasons.single().contains("모든 터널"))
    }

    @Test
    fun `두 경로의 터널이 같으면 비교하지 않는다`() {
        val a = RoutePolicy.decide(ctx, route(500.0, 600.0), route(500.0, 600.0))
        assertEquals(RouteVerdict.NO_CHOICE, a.verdict)
        assertFalse(a.reasons.any { it.contains("더 걸립니다") })
    }

    @Test
    fun `시간이 같으면 터널이 적은 경로를 권한다`() {
        val a = RoutePolicy.decide(ctx, route(500.0, 600.0), route(null, 600.0))
        assertEquals(RouteChoice.AVOIDING, a.recommended)
    }

    @Test
    fun `힘든 날과 방금 불안은 같은 몸 상태라 두 번 깎지 않는다`() {
        val both = RouteContext(AvoidanceLevel.MEDIUM_OK, condition = DayCondition.HARD, recentDistress = true)
        assertEquals(500.0, both.effectiveLimitM, 0.1)
    }

    @Test
    fun `힘든 날과 야간은 한도를 낮춘다`() {
        val hardNight = ctx.copy(condition = DayCondition.HARD, night = true)
        assertEquals(350.0, hardNight.effectiveLimitM, 0.1)
        val a = RoutePolicy.decide(hardNight, route(500.0, 600.0), route(null, 900.0))
        assertEquals(RouteVerdict.MUST_AVOID, a.verdict)
    }

    @Test
    fun `응급 이송은 5분 안쪽에서만 회피한다`() {
        val medical = ctx.copy(urgency = TripUrgency.MEDICAL)
        assertEquals(RouteVerdict.MUST_AVOID, RoutePolicy.decide(medical, route(2_000.0, 600.0), route(null, 840.0)).verdict)
        assertEquals(RouteVerdict.MUST_PASS, RoutePolicy.decide(medical, route(2_000.0, 600.0), route(null, 1_800.0)).verdict)
    }

    @Test
    fun `시간 제한에서 긴 우회는 운전자가 고른다`() {
        val limited = ctx.copy(urgency = TripUrgency.TIME_LIMITED)
        assertEquals(RouteVerdict.CHOICE, RoutePolicy.decide(limited, route(2_000.0, 600.0), route(null, 1_800.0)).verdict)
    }

    @Test
    fun `높은 힘듦은 한 번에 한도를 낮춘다`() {
        val limit = TunnelTolerance.personalLimitM(AvoidanceLevel.MEDIUM_OK, listOf(TunnelRating(800.0, 8)))
        assertEquals(560.0, limit, 0.1)
    }

    @Test
    fun `낮은 힘듦이 반복될 때만 한도를 올린다`() {
        val once = listOf(TunnelRating(1_000.0, 2))
        assertEquals(1_000.0, TunnelTolerance.personalLimitM(AvoidanceLevel.MEDIUM_OK, once), 0.1)
        val twice = once + TunnelRating(1_000.0, 3)
        assertEquals(1_500.0, TunnelTolerance.personalLimitM(AvoidanceLevel.MEDIUM_OK, twice), 0.1)
    }

    @Test
    fun `회피 안 함은 한도가 없다`() {
        assertFalse(RouteContext(AvoidanceLevel.NONE).effectiveLimitM < 1e9)
    }

    @Test
    fun `노출 시간은 시속 60 km 체류 시간으로 계산한다`() {
        val e = TunnelExposure.of(route(1_400.0, 600.0))
        assertEquals(84, e.longestSec)
        assertEquals(TunnelTier.MEDIUM, e.longestTier)
        assertEquals("시험터널", e.longestName)
        assertTrue(e.hasTunnel)
    }

    @Test
    fun `3 km 허용은 긴 등급 터널부터 피한다`() {
        val ctx3 = RouteContext(AvoidanceLevel.LONG_OK)
        assertEquals(3_000.0, ctx3.effectiveLimitM, 0.1)
        assertEquals(RouteVerdict.MUST_AVOID, RoutePolicy.decide(ctx3, route(3_500.0, 600.0), route(null, 700.0)).verdict)
        assertEquals(RouteVerdict.CHOICE, RoutePolicy.decide(ctx3, route(2_500.0, 600.0), route(null, 700.0)).verdict)
    }

    @Test
    fun `꼭 지나가기는 가장 긴 터널 이름과 등급을 알린다`() {
        val a = RoutePolicy.decide(ctx, route(3_600.0, 600.0), null)
        assertTrue(a.reasons.any { it.contains("가장 긴 시험터널(긴 터널)은 약 216초") })
    }

    @Test
    fun `동행 평가가 없으면 동행 한도는 혼자 한도와 같다`() {
        val alone = listOf(TunnelRating(800.0, 8))
        assertEquals(
            TunnelTolerance.personalLimitM(AvoidanceLevel.MEDIUM_OK, alone),
            TunnelTolerance.personalLimitM(AvoidanceLevel.MEDIUM_OK, alone, accompanied = true), 0.1,
        )
        assertEquals(1_000.0, RouteContext(AvoidanceLevel.MEDIUM_OK, hasPassenger = true).effectiveLimitM, 0.1)
    }

    @Test
    fun `동행 평가는 동행 한도만 바꾼다`() {
        val ratings = listOf(TunnelRating(1_000.0, 2, accompanied = true), TunnelRating(1_000.0, 1, accompanied = true))
        assertEquals(1_000.0, TunnelTolerance.personalLimitM(AvoidanceLevel.MEDIUM_OK, ratings), 0.1)
        assertEquals(1_500.0, TunnelTolerance.personalLimitM(AvoidanceLevel.MEDIUM_OK, ratings, accompanied = true), 0.1)
    }

    @Test
    fun `혼자 힘들었던 평가는 동행 한도의 출발점도 낮춘다`() {
        val ratings = listOf(TunnelRating(800.0, 9))
        assertEquals(560.0, TunnelTolerance.personalLimitM(AvoidanceLevel.MEDIUM_OK, ratings, accompanied = true), 0.1)
    }

    @Test
    fun `불안 뒤에는 한도를 절반으로 본다`() {
        assertEquals(500.0, RouteContext(AvoidanceLevel.MEDIUM_OK, recentDistress = true).effectiveLimitM, 0.1)
    }
}
