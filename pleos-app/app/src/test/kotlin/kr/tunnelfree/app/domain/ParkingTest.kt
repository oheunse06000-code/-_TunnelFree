package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingTest {

    private val destination = LatLng(36.35, 127.38)
    private val under = ParkingLot("u", "지하 주차장", 36.3503, 127.38, ParkingKind.UNDERGROUND)
    private val surface = ParkingLot("s", "공영 주차장", 36.3525, 127.38, ParkingKind.SURFACE)
    private val far = ParkingLot("f", "먼 주차장", 36.40, 127.38, ParkingKind.SURFACE)
    private val lots = listOf(under, surface, far)

    @Test
    fun `CSV 를 읽고 주석과 깨진 줄을 건너뛴다`() {
        val parsed = ParkingDatabase.parse("# 주석\nw1;둔산 주차장;36.3;127.3;UNDERGROUND;120;yes;public\n깨진줄\n")
        assertEquals(1, parsed.size)
        assertEquals(ParkingKind.UNDERGROUND, parsed[0].kind)
        assertEquals(120, parsed[0].capacity)
        assertEquals("유료", parsed[0].feeInfo)
    }

    @Test
    fun `터널 회피 사용자는 조금 멀어도 지상 주차장이 먼저다`() {
        val ranked = ParkingRanker.rank(lots, destination, ParkingPreference(avoidEnclosed = true))
        assertEquals("s", ranked.first().lot.id)
        assertEquals(2, ranked.size)
    }

    @Test
    fun `제약이 없으면 가까운 곳이 앞선다`() {
        val ranked = ParkingRanker.rank(lots, destination, ParkingPreference(avoidEnclosed = false))
        assertEquals("u", ranked.first().lot.id)
    }

    @Test
    fun `규칙 추천은 닫힌 공간을 알린다`() {
        val onlyUnder = ParkingPreference(avoidEnclosed = true)
        val ranked = ParkingRanker.rank(listOf(under), destination, onlyUnder)
        val advice = ParkingRanker.ruleAdvice(ranked, onlyUnder)
        assertEquals("u", advice?.lotId)
        assertTrue(advice!!.reason.contains("닫힌 공간"))
    }

    @Test
    fun `터널 회피 사용자에게는 이름과 구조가 확인된 곳이 먼저다`() {
        val unknown = ParkingLot("x", "", 36.3502, 127.38, ParkingKind.UNKNOWN)
        val known = ParkingLot("k", "시청 주차장", 36.3512, 127.38, ParkingKind.SURFACE)
        val ranked = ParkingRanker.rank(listOf(unknown, known), destination, ParkingPreference(avoidEnclosed = true))
        assertEquals("k", ranked.first().lot.id)
    }

    @Test
    fun `구조를 모르면 추천 이유에 알린다`() {
        val unknown = ParkingLot("x", "둔산 주차장", 36.3502, 127.38, ParkingKind.UNKNOWN)
        val pref = ParkingPreference(avoidEnclosed = true)
        val advice = ParkingRanker.ruleAdvice(ParkingRanker.rank(listOf(unknown), destination, pref), pref)
        assertTrue(advice!!.reason.contains("지하 여부 확인 필요"))
    }

    @Test
    fun `후보가 없으면 추천도 없다`() {
        assertNull(ParkingRanker.ruleAdvice(emptyList(), ParkingPreference(avoidEnclosed = true)))
    }
}
