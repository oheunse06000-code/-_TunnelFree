package kr.tunnelfree.app.domain

import java.util.Locale

data class VehicleContext(
    val decelerationMps2: Double = 0.0,
    val brakePressedAgoMs: Long? = null,
    val steeringChangedAgoMs: Long? = null,
    val turnSignalOn: Boolean = false,
    val airbagDeployed: Boolean = false,
    val seatBeltBuckled: Boolean? = null,
    val speedKmh: Double? = null,
    val hasVehicleSignals: Boolean = false,
)

enum class IncidentKind { NONE, CRASH, EXIT_ATTEMPT, TRAFFIC_LIKELY, PANIC_SUSPECTED }

data class IncidentAssessment(
    val kind: IncidentKind,
    val cause: String,
    val askDriver: Boolean,
    val openHelpImmediately: Boolean,
)

object IncidentClassifier {
    const val HARD_DECELERATION_MPS2 = 4.5
    const val RECENT_INPUT_MS = 3_000L
    const val MOVING_SPEED_KMH = 5.0

    private val none = IncidentAssessment(IncidentKind.NONE, "", askDriver = false, openHelpImmediately = false)

    fun assess(ctx: VehicleContext): IncidentAssessment {
        if (ctx.airbagDeployed) {
            return IncidentAssessment(IncidentKind.CRASH, "에어백 전개 감지 · 사고 가능성", askDriver = false, openHelpImmediately = true)
        }

        val moving = ctx.speedKmh?.let { it > MOVING_SPEED_KMH } ?: false
        if (ctx.seatBeltBuckled == false && moving) {
            return IncidentAssessment(IncidentKind.EXIT_ATTEMPT, "주행 중 안전벨트 해제 · 하차 시도 가능성", askDriver = false, openHelpImmediately = true)
        }

        if (ctx.decelerationMps2 < HARD_DECELERATION_MPS2) return none

        val decelText = String.format(Locale.ROOT, "급감속 %.1f m/s²", ctx.decelerationMps2)
        val brakeRecent = ctx.brakePressedAgoMs?.let { it in 0..RECENT_INPUT_MS } ?: false
        val steeringRecent = ctx.steeringChangedAgoMs?.let { it in 0..RECENT_INPUT_MS } ?: false

        if (brakeRecent || ctx.turnSignalOn || steeringRecent) {
            val evidence = buildList {
                if (brakeRecent) add("브레이크 조작")
                if (ctx.turnSignalOn) add("방향지시등")
                if (steeringRecent) add("조향 변화")
            }.joinToString("·")
            return IncidentAssessment(
                IncidentKind.TRAFFIC_LIKELY,
                "$decelText · $evidence 감지 · 교통 상황일 가능성",
                askDriver = true,
                openHelpImmediately = false,
            )
        }

        val detail = if (ctx.hasVehicleSignals) "브레이크·조향 입력 없음 · 상태 확인 필요" else "차량 신호 없음 · 상태 확인 필요"
        return IncidentAssessment(IncidentKind.PANIC_SUSPECTED, "$decelText · $detail", askDriver = true, openHelpImmediately = false)
    }
}
