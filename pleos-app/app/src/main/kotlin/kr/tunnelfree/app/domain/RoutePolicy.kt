package kr.tunnelfree.app.domain

import kotlin.math.max
import kotlin.math.min

enum class DayCondition(val label: String, val limitFactor: Double) {
    GOOD("좋음", 1.0),
    NORMAL("보통", 1.0),
    HARD("힘듦", 0.5),
}

enum class TripUrgency(val label: String) {
    NORMAL("여유 있음"),
    TIME_LIMITED("시간 제한"),
    MEDICAL("응급 이송"),
}

data class TunnelExposure(val count: Int, val longestM: Double, val totalM: Double, val longestName: String = "") {
    val longestSec: Int get() = TunnelTiming.dwellSec(longestM).toInt()
    val totalSec: Int get() = TunnelTiming.dwellSec(totalM).toInt()
    val longestTier: TunnelTier get() = TunnelTier.ofLength(longestM)
    val hasTunnel: Boolean get() = count > 0

    companion object {
        val NONE = TunnelExposure(0, 0.0, 0.0)

        fun of(route: RoadRoute?): TunnelExposure {
            val tunnels = route?.tunnels.orEmpty()
            if (tunnels.isEmpty()) return NONE
            val longest = tunnels.maxBy { it.lengthM }
            return TunnelExposure(tunnels.size, longest.lengthM, tunnels.sumOf { it.lengthM }, longest.name)
        }
    }
}

data class TunnelRating(val lengthM: Double, val distress: Int, val accompanied: Boolean = false)

object TunnelTolerance {
    const val HIGH_DISTRESS = 7
    const val LOW_DISTRESS = 3
    const val RAISE_AFTER_COUNT = 2
    const val STEP_DOWN_RATIO = 0.7
    const val STEP_UP_RATIO = 1.5

    fun personalLimitM(level: AvoidanceLevel, ratings: List<TunnelRating>, accompanied: Boolean = false): Double {
        if (!level.avoidsAny) return Double.MAX_VALUE
        val alone = adjust(level.maxTunnelLengthM, ratings.filter { !it.accompanied })
        return if (accompanied) adjust(alone, ratings.filter { it.accompanied }) else alone
    }

    private fun adjust(start: Double, ratings: List<TunnelRating>): Double {
        var limit = start
        var comfortable = 0
        for (rating in ratings) {
            val hard = rating.distress >= HIGH_DISTRESS
            val easy = rating.distress <= LOW_DISTRESS && rating.lengthM >= limit
            when {
                hard && rating.lengthM <= limit * 1.5 -> {
                    limit = min(limit, rating.lengthM * STEP_DOWN_RATIO)
                    comfortable = 0
                }
                easy -> {
                    comfortable += 1
                    if (comfortable >= RAISE_AFTER_COUNT) {
                        limit = max(limit, rating.lengthM * STEP_UP_RATIO)
                        comfortable = 0
                    }
                }
            }
        }
        return limit
    }
}

data class RouteContext(
    val level: AvoidanceLevel,
    val ratings: List<TunnelRating> = emptyList(),
    val condition: DayCondition = DayCondition.NORMAL,
    val urgency: TripUrgency = TripUrgency.NORMAL,
    val night: Boolean = false,
    val hasPassenger: Boolean = false,
    val recentDistress: Boolean = false,
) {
    val effectiveLimitM: Double
        get() {
            val base = TunnelTolerance.personalLimitM(level, ratings, hasPassenger)
            if (base == Double.MAX_VALUE) return base
            val bodyFactor = min(condition.limitFactor, if (recentDistress) RoutePolicy.DISTRESS_FACTOR else 1.0)
            val lightFactor = if (night) RoutePolicy.NIGHT_FACTOR else 1.0
            return base * bodyFactor * lightFactor
        }
}

enum class RouteVerdict(val label: String) {
    NO_CHOICE("경로 차이 없음"),
    MUST_AVOID("터널 꼭 피하기"),
    MUST_PASS("터널 꼭 지나가기"),
    CHOICE("직접 선택"),
}

enum class RouteChoice { SHORTEST, AVOIDING }

data class RouteAdvice(
    val verdict: RouteVerdict,
    val recommended: RouteChoice,
    val headline: String,
    val reasons: List<String>,
    val shortest: TunnelExposure,
    val avoiding: TunnelExposure,
    val extraMinutes: Int,
)

object RoutePolicy {
    const val NIGHT_FACTOR = 0.7
    const val DISTRESS_FACTOR = 0.5
    const val DISTRESS_WINDOW_MS = 30 * 60_000L
    const val MEDICAL_EXTRA_MIN = 5
    const val TIME_LIMITED_EXTRA_MIN = 15

    fun decide(ctx: RouteContext, shortest: RoadRoute?, avoiding: RoadRoute?): RouteAdvice {
        val shortExposure = TunnelExposure.of(shortest)
        val avoidExposure = TunnelExposure.of(avoiding)
        val extra = if (shortest != null && avoiding != null) max(0, avoiding.minutes - shortest.minutes) else 0
        fun advice(verdict: RouteVerdict, choice: RouteChoice, headline: String, vararg reasons: String) =
            RouteAdvice(verdict, choice, headline, reasons.toList(), shortExposure, avoidExposure, extra)

        if (!shortExposure.hasTunnel) {
            return advice(RouteVerdict.NO_CHOICE, RouteChoice.SHORTEST, "터널 없는 경로입니다", "최단 경로에 터널이 없습니다.")
        }
        if (avoiding == null) {
            return advice(
                RouteVerdict.MUST_PASS, RouteChoice.SHORTEST, "터널을 지나야 하는 경로입니다",
                "오늘 한도로 터널을 피하는 길을 찾지 못했습니다.",
                "${longestLabel(shortExposure)}은 약 ${shortExposure.longestSec}초 걸립니다.",
            )
        }
        if (ctx.urgency == TripUrgency.MEDICAL) {
            return if (extra <= MEDICAL_EXTRA_MIN) {
                advice(RouteVerdict.MUST_AVOID, RouteChoice.AVOIDING, "회피 경로로도 시간 차이가 작습니다", "응급 이송이지만 추가 시간이 ${extra}분입니다.")
            } else {
                advice(RouteVerdict.MUST_PASS, RouteChoice.SHORTEST, "응급 이송은 터널을 지나갑니다", "회피하면 ${extra}분 늦어집니다.")
            }
        }
        val overLimit = shortExposure.longestM > ctx.effectiveLimitM
        if (overLimit) {
            if (ctx.urgency == TripUrgency.TIME_LIMITED && extra > TIME_LIMITED_EXTRA_MIN) {
                return advice(
                    RouteVerdict.CHOICE, RouteChoice.AVOIDING, "회피하면 ${extra}분 더 걸립니다",
                    "터널이 오늘 한도보다 깁니다.", "시간 조건을 함께 보고 직접 골라 주세요.",
                )
            }
            return advice(
                RouteVerdict.MUST_AVOID, RouteChoice.AVOIDING,
                if (extra == 0) "터널 없이도 시간이 같습니다" else "${extra}분 더 걸려도 터널을 피합니다",
                if (ctx.effectiveLimitM <= 0.0) "모든 터널을 피하는 설정입니다."
                else "가장 긴 터널이 ${formatDistance(shortExposure.longestM)}로 오늘 한도 ${formatDistance(ctx.effectiveLimitM)}보다 깁니다.",
            )
        }
        if (avoidExposure == shortExposure) {
            return advice(
                RouteVerdict.NO_CHOICE, RouteChoice.SHORTEST, "오늘 한도 안의 터널만 지나갑니다",
                "가장 긴 터널은 약 ${shortExposure.longestSec}초 걸립니다.",
            )
        }
        if (extra == 0) {
            return advice(
                RouteVerdict.CHOICE, RouteChoice.AVOIDING, "터널을 줄여도 시간이 같습니다",
                "터널이 ${shortExposure.count}개에서 ${avoidExposure.count}개로 줄어듭니다.",
            )
        }
        return advice(
            RouteVerdict.CHOICE, RouteChoice.SHORTEST, "오늘 한도 안의 터널입니다",
            "가장 긴 터널은 약 ${shortExposure.longestSec}초 걸립니다.",
            "회피하면 ${extra}분 더 걸립니다. 편한 쪽을 고르세요.",
        )
    }

    private fun longestLabel(e: TunnelExposure): String =
        "가장 긴 ${e.longestName.ifBlank { "터널" }}(${e.longestTier.label})"
}
