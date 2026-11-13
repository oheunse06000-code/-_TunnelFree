package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentClassifierTest {

    @Test
    fun `에어백 전개는 질문 없이 즉시 도움`() {
        val a = IncidentClassifier.assess(VehicleContext(airbagDeployed = true))
        assertEquals(IncidentKind.CRASH, a.kind)
        assertTrue(a.openHelpImmediately)
        assertFalse(a.askDriver)
    }

    @Test
    fun `주행 중 벨트 해제는 하차 시도로 즉시 도움`() {
        val a = IncidentClassifier.assess(VehicleContext(seatBeltBuckled = false, speedKmh = 40.0))
        assertEquals(IncidentKind.EXIT_ATTEMPT, a.kind)
        assertTrue(a.openHelpImmediately)
    }

    @Test
    fun `정차 중 벨트 해제는 이벤트 아님`() {
        val a = IncidentClassifier.assess(VehicleContext(seatBeltBuckled = false, speedKmh = 0.0))
        assertEquals(IncidentKind.NONE, a.kind)
    }

    @Test
    fun `약한 감속은 이벤트 아님`() {
        assertEquals(IncidentKind.NONE, IncidentClassifier.assess(VehicleContext(decelerationMps2 = 2.0)).kind)
    }

    @Test
    fun `브레이크 직후 급감속은 교통 상황`() {
        val a = IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0, brakePressedAgoMs = 500, hasVehicleSignals = true))
        assertEquals(IncidentKind.TRAFFIC_LIKELY, a.kind)
        assertTrue(a.askDriver)
        assertFalse(a.openHelpImmediately)
        assertTrue(a.cause.contains("브레이크"))
    }

    @Test
    fun `방향지시등이나 조향 변화도 교통 상황 근거`() {
        assertEquals(IncidentKind.TRAFFIC_LIKELY, IncidentClassifier.assess(VehicleContext(decelerationMps2 = 5.0, turnSignalOn = true)).kind)
        assertEquals(IncidentKind.TRAFFIC_LIKELY, IncidentClassifier.assess(VehicleContext(decelerationMps2 = 5.0, steeringChangedAgoMs = 1000)).kind)
    }

    @Test
    fun `오래된 브레이크 입력은 근거가 아님`() {
        val a = IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0, brakePressedAgoMs = 10_000, hasVehicleSignals = true))
        assertEquals(IncidentKind.PANIC_SUSPECTED, a.kind)
        assertTrue(a.cause.contains("입력 없음"))
    }

    @Test
    fun `차량 신호 없는 환경에서는 신호 없음을 표시`() {
        val a = IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0))
        assertEquals(IncidentKind.PANIC_SUSPECTED, a.kind)
        assertTrue(a.askDriver)
        assertTrue(a.cause.contains("차량 신호 없음"))
    }

    @Test
    fun `급감속 임계값 4_5 는 이벤트에 포함`() {
        assertEquals(IncidentKind.NONE, IncidentClassifier.assess(VehicleContext(decelerationMps2 = 4.49)).kind)
        assertEquals(IncidentKind.PANIC_SUSPECTED, IncidentClassifier.assess(VehicleContext(decelerationMps2 = IncidentClassifier.HARD_DECELERATION_MPS2)).kind)
    }

    @Test
    fun `기본값 컨텍스트는 이벤트 없음`() {
        val a = IncidentClassifier.assess(VehicleContext())
        assertEquals(IncidentKind.NONE, a.kind)
        assertEquals("", a.cause)
        assertFalse(a.askDriver)
        assertFalse(a.openHelpImmediately)
    }

    @Test
    fun `브레이크 입력 경과시간 경계 - 0 과 3000 ms 는 최근, 3001 ms 와 음수는 아님`() {
        fun kind(ago: Long) = IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0, brakePressedAgoMs = ago)).kind
        assertEquals(IncidentKind.TRAFFIC_LIKELY, kind(0))
        assertEquals(IncidentKind.TRAFFIC_LIKELY, kind(IncidentClassifier.RECENT_INPUT_MS))
        assertEquals(IncidentKind.PANIC_SUSPECTED, kind(IncidentClassifier.RECENT_INPUT_MS + 1))
        assertEquals(IncidentKind.PANIC_SUSPECTED, kind(-1))
    }

    @Test
    fun `조향 입력 경과시간 경계`() {
        fun kind(ago: Long) = IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0, steeringChangedAgoMs = ago)).kind
        assertEquals(IncidentKind.TRAFFIC_LIKELY, kind(3_000))
        assertEquals(IncidentKind.PANIC_SUSPECTED, kind(3_001))
    }

    @Test
    fun `근거가 여럿이면 모두 원인에 나열`() {
        val a = IncidentClassifier.assess(
            VehicleContext(decelerationMps2 = 7.0, brakePressedAgoMs = 100, turnSignalOn = true, steeringChangedAgoMs = 100)
        )
        assertEquals(IncidentKind.TRAFFIC_LIKELY, a.kind)
        assertTrue(a.cause.contains("브레이크 조작·방향지시등·조향 변화"))
        assertTrue(a.cause.startsWith("급감속 "))
        assertTrue(a.cause.contains("m/s²"))
    }

    @Test
    fun `에어백은 벨트 해제·급감속보다 우선`() {
        val a = IncidentClassifier.assess(
            VehicleContext(airbagDeployed = true, seatBeltBuckled = false, speedKmh = 60.0, decelerationMps2 = 9.0, brakePressedAgoMs = 0)
        )
        assertEquals(IncidentKind.CRASH, a.kind)
        assertEquals(IncidentKind.CRASH, IncidentClassifier.assess(VehicleContext(airbagDeployed = true, speedKmh = 0.0)).kind)
    }

    @Test
    fun `주행 중 벨트 해제는 급감속보다 우선`() {
        val a = IncidentClassifier.assess(
            VehicleContext(seatBeltBuckled = false, speedKmh = 40.0, decelerationMps2 = 8.0, brakePressedAgoMs = 0)
        )
        assertEquals(IncidentKind.EXIT_ATTEMPT, a.kind)
        assertFalse(a.askDriver)
    }

    @Test
    fun `주행 판정 속도 경계 - 5 km_h 는 정차, 초과는 주행`() {
        assertEquals(IncidentKind.NONE, IncidentClassifier.assess(VehicleContext(seatBeltBuckled = false, speedKmh = IncidentClassifier.MOVING_SPEED_KMH)).kind)
        assertEquals(IncidentKind.EXIT_ATTEMPT, IncidentClassifier.assess(VehicleContext(seatBeltBuckled = false, speedKmh = 5.1)).kind)
    }

    @Test
    fun `속도를 모르면 주행 중으로 보지 않는다`() {
        assertEquals(IncidentKind.NONE, IncidentClassifier.assess(VehicleContext(seatBeltBuckled = false)).kind)
    }

    @Test
    fun `벨트 해제는 속도로만 주행 여부를 가린다`() {
        assertEquals(IncidentKind.EXIT_ATTEMPT, IncidentClassifier.assess(VehicleContext(seatBeltBuckled = false, speedKmh = 30.0)).kind)
        assertEquals(IncidentKind.NONE, IncidentClassifier.assess(VehicleContext(seatBeltBuckled = false, speedKmh = 0.0)).kind)
    }

    @Test
    fun `벨트 신호 없음이나 체결 상태는 하차 시도가 아님`() {
        assertEquals(IncidentKind.NONE, IncidentClassifier.assess(VehicleContext(seatBeltBuckled = null, speedKmh = 80.0)).kind)
        assertEquals(IncidentKind.NONE, IncidentClassifier.assess(VehicleContext(seatBeltBuckled = true, speedKmh = 80.0)).kind)
    }

    @Test
    fun `저속이어도 급감속 값이 크면 판정은 진행된다`() {
        assertEquals(IncidentKind.PANIC_SUSPECTED, IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0)).kind)
        assertEquals(IncidentKind.PANIC_SUSPECTED, IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0, speedKmh = 0.0)).kind)
    }

    @Test
    fun `차량 신호 연결 여부에 따라 원인 문구가 다르다`() {
        val withSignals = IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0, hasVehicleSignals = true))
        assertTrue(withSignals.cause.contains("브레이크·조향 입력 없음"))
        val without = IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0, hasVehicleSignals = false))
        assertTrue(without.cause.contains("차량 신호 없음"))
        assertFalse(without.openHelpImmediately)
    }
}
