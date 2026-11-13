package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceSpeechTest {
    private val tunnel = Tunnel(1, "도솔터널", "primary", 700, listOf(LatLng(36.3, 127.3), LatLng(36.306, 127.3)))

    @Test
    fun routeReadyMentionsTunnelCount() {
        val clear = TestRoutes.straight(1500.0, travelSec = 150.0)
        assertEquals("대전역까지 터널 없는 경로입니다. 1.5킬로미터, 약 3분 걸립니다.", GuidanceSpeech.routeReady("대전역", clear))
        val withTunnels = TestRoutes.straight(800.0, travelSec = 61.0, tunnels = listOf(RouteTunnel("a", 100.0, 0.0, 100.0), RouteTunnel("b", 100.0, 300.0, 400.0)))
        assertEquals("대전역까지 경로에 터널이 2개 있습니다. 800미터, 약 2분 걸립니다.", GuidanceSpeech.routeReady("대전역", withTunnels))
    }

    @Test
    fun driveStarted() {
        assertEquals("안내를 시작합니다. 1.5킬로미터, 약 3분 예상입니다.", GuidanceSpeech.driveStarted(TestRoutes.straight(1500.0, travelSec = 150.0)))
    }

    @Test
    fun everyLevelHasSentence() {
        AvoidanceLevel.entries.forEach { assertTrue(GuidanceSpeech.avoidanceLevel(it).isNotBlank()) }
        assertEquals("모든 터널을 피하는 경로로 안내합니다.", GuidanceSpeech.avoidanceLevel(AvoidanceLevel.FULL))
    }

    @Test
    fun approachingVariants() {
        val onRoute = GuidanceSpeech.tunnelApproaching(tunnel, 300, AvoidanceLevel.FULL, onRoute = true)
        assertTrue(onRoute.endsWith("천천히 호흡하세요."))
        assertTrue(GuidanceSpeech.tunnelApproaching(tunnel, 300, AvoidanceLevel.MEDIUM_OK, false).contains("허용 범위"))
        assertTrue(GuidanceSpeech.tunnelApproaching(tunnel, 300, AvoidanceLevel.SHORT_OK, false).contains("회피 경로를 확인"))
        assertTrue(GuidanceSpeech.tunnelApproaching(tunnel, 300, AvoidanceLevel.FULL, false).contains("회피 경로를 확인"))
        assertEquals(
            "전방 300미터에 도솔터널이 있습니다. 길이 700미터입니다. 천천히 호흡하세요.",
            GuidanceSpeech.tunnelApproaching(tunnel, 300, AvoidanceLevel.NONE, false),
        )
    }

    @Test
    fun passSecondsUsesSpeedOrDefault() {
        assertEquals(42, GuidanceSpeech.tunnelPassSeconds(tunnel, null))
        assertEquals(42, GuidanceSpeech.tunnelPassSeconds(tunnel, 5.0))
        assertEquals(25, GuidanceSpeech.tunnelPassSeconds(tunnel, 100.0))
        val tiny = tunnel.copy(lengthM = 20)
        assertEquals(5, GuidanceSpeech.tunnelPassSeconds(tiny, 100.0))
        assertTrue(GuidanceSpeech.tunnelEntered(tunnel, 100.0).contains("약 25초"))
    }

    @Test
    fun approachOnRouteExplainsBodyReaction() {
        val s = GuidanceSpeech.tunnelApproaching(tunnel, 500, AvoidanceLevel.FULL, onRoute = true)
        assertTrue(s.contains("약 42초 걸립니다"))
        assertTrue(s.contains(GuidanceSpeech.BODY_NOTE))
    }

    @Test
    fun tunnelAheadSaysWhyAndWhatNext() {
        val rt = RouteTunnel("도솔터널", 700.0, 1_000.0, 1_700.0)
        val congested = GuidanceSpeech.tunnelAhead(TunnelAheadAlert(rt, 500.0, 252.0, congested = true), hasDetour = true)
        assertEquals("앞이 막혀 도솔터널 안에 약 5분 머물 수 있습니다. 터널을 피하는 길이 있습니다. 우회할까요? 우회해 줘, 또는 그대로 가자라고 말씀해 주세요.", congested)
        val distressed = GuidanceSpeech.tunnelAhead(TunnelAheadAlert(rt, 500.0, 42.0, congested = false), hasDetour = false)
        assertTrue(distressed.startsWith("조금 전 불안을 알려 주셔서 도솔터널을 다시 확인했습니다."))
        assertTrue(distressed.contains("다른 길이 없습니다"))
        assertFalse(distressed.contains("출구까지 거리"))
    }

    @Test
    fun onlyLongTunnelsPromiseExitDistance() {
        val long = RouteTunnel("긴터널", 3_500.0, 1_000.0, 4_500.0)
        val s = GuidanceSpeech.tunnelAhead(TunnelAheadAlert(long, 500.0, 300.0, congested = false), hasDetour = false)
        assertTrue(s.endsWith("출구까지 거리를 알려 드립니다."))
    }

    @Test
    fun exitedWithAndWithoutChallenge() {
        assertEquals("도솔터널을 빠져나왔습니다. 잘 하셨습니다.", GuidanceSpeech.tunnelExited(tunnel, null))
        assertEquals("도솔터널을 빠져나왔습니다. 잘 하셨습니다. 오늘 3번째 터널입니다.", GuidanceSpeech.tunnelExited(tunnel, 3))
    }
}
