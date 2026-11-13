package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeStopGuideTest {

    private fun all(road: StopRoadType) = SafeStopGuide.spoken(road) + SafeStopGuide.steps(road).joinToString(" ")

    @Test
    fun `터널이 도로 등급보다 우선한다`() {
        assertEquals(StopRoadType.TUNNEL, StopRoadType.of(true, RoadClass.MOTORWAY))
        assertEquals(StopRoadType.TUNNEL, StopRoadType.of(true, null))
    }

    @Test
    fun `도로 등급을 유형으로 나눈다`() {
        for (c in listOf(RoadClass.MOTORWAY, RoadClass.MOTORWAY_LINK, RoadClass.TRUNK, RoadClass.TRUNK_LINK)) {
            assertEquals(c.name, StopRoadType.EXPRESSWAY, StopRoadType.of(false, c))
        }
        for (c in listOf(RoadClass.PRIMARY, RoadClass.SECONDARY, RoadClass.RESIDENTIAL, RoadClass.LIVING_STREET)) {
            assertEquals(c.name, StopRoadType.SURFACE, StopRoadType.of(false, c))
        }
        assertEquals(StopRoadType.UNKNOWN, StopRoadType.of(false, null))
    }

    @Test
    fun `고속 주행 도로는 휴게 지점이 먼저이고 갓길은 최후 수단이다`() {
        val spoken = SafeStopGuide.spoken(StopRoadType.EXPRESSWAY)
        assertTrue(spoken.indexOf("휴게소") in 0 until spoken.indexOf("갓길"))
        val steps = SafeStopGuide.steps(StopRoadType.EXPRESSWAY)
        assertTrue(steps.first().contains("휴게소"))
        assertTrue(steps.any { it.contains("가드레일") && it.contains("119") })
        assertTrue(all(StopRoadType.EXPRESSWAY).contains(SafeStopGuide.EXPRESSWAY_CALL_CENTER))
    }

    @Test
    fun `터널·일반도로·미상 도로는 갓길을 말하지 않는다`() {
        for (road in listOf(StopRoadType.TUNNEL, StopRoadType.SURFACE, StopRoadType.UNKNOWN)) {
            assertFalse(road.name, all(road).contains("갓길"))
        }
    }

    @Test
    fun `터널은 출구까지 이동을 안내한다`() {
        assertTrue(SafeStopGuide.spoken(StopRoadType.TUNNEL).contains("출구"))
        assertTrue(SafeStopGuide.steps(StopRoadType.TUNNEL).any { it.contains("전조등") })
    }

    @Test
    fun `모든 유형은 급제동 금지와 도움 요청을 포함한다`() {
        for (road in StopRoadType.entries) {
            assertTrue(road.name, SafeStopGuide.steps(road).any { it.contains("급제동") })
            assertTrue(road.name, SafeStopGuide.steps(road).last().contains("도와줘"))
        }
    }
}
