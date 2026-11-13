package kr.tunnelfree.app.domain

import kotlin.math.max

enum class TunnelTier(val label: String) {
    SHORT("짧은 터널"),
    MEDIUM("중간 터널"),
    LONG("긴 터널");

    companion object {
        fun of(dwellSec: Double): TunnelTier = when {
            dwellSec < TunnelTiming.SHORT_MAX_SEC -> SHORT
            dwellSec < TunnelTiming.LONG_MIN_SEC -> MEDIUM
            else -> LONG
        }

        fun ofLength(lengthM: Double): TunnelTier = of(TunnelTiming.dwellSec(lengthM))
    }
}

object TunnelTiming {
    const val PLAN_SPEED_KMH = 60.0
    const val SHORT_MAX_SEC = 60.0
    const val LONG_MIN_SEC = 180.0
    const val MIN_CRAWL_KMH = 5.0

    fun dwellSec(lengthM: Double, speedKmh: Double = PLAN_SPEED_KMH): Double =
        lengthM * 3.6 / max(speedKmh, MIN_CRAWL_KMH)

    fun limitSec(limitM: Double): Double = if (limitM >= Double.MAX_VALUE / 2) Double.MAX_VALUE else dwellSec(limitM)
}

data class TunnelAheadAlert(
    val tunnel: RouteTunnel,
    val distanceM: Double,
    val dwellSec: Double,
    val congested: Boolean,
)

class TunnelAheadCheck {
    private val samples = ArrayDeque<Pair<Long, Double>>()
    private var alertedStartM: Double? = null

    fun reset() {
        samples.clear()
        alertedStartM = null
    }

    fun update(route: RoadRoute, progressM: Double, nowMs: Long, speedKmh: Double?, limitM: Double, distressed: Boolean): TunnelAheadAlert? {
        if (speedKmh != null) samples.addLast(nowMs to speedKmh)
        while (samples.size > 1 && nowMs - samples[1].first >= WINDOW_MS) samples.removeFirst()

        val next = route.tunnels.firstOrNull { it.startM > progressM } ?: return null
        val distance = next.startM - progressM
        if (distance > CHECK_FROM_M || alertedStartM == next.startM) return null

        val avg = averageSpeed(nowMs)
        val congested = avg != null && avg < CONGESTED_KMH
        if (!congested && !distressed) return null
        val dwell = TunnelTiming.dwellSec(next.lengthM, if (congested) avg!! else TunnelTiming.PLAN_SPEED_KMH)
        if (dwell <= TunnelTiming.limitSec(limitM)) return null
        alertedStartM = next.startM
        return TunnelAheadAlert(next, distance, dwell, congested)
    }

    private fun averageSpeed(nowMs: Long): Double? {
        if (samples.isEmpty() || nowMs - samples.first().first < WINDOW_MS) return null
        return samples.map { it.second }.average()
    }

    companion object {
        const val CHECK_FROM_M = 1_000.0
        const val WINDOW_MS = 30_000L
        const val CONGESTED_KMH = 30.0
    }
}

class InTunnelAnnouncer {
    private var tunnelStartM: Double? = null
    private var lastSaidMs = 0L
    private var exitSaid = false

    fun reset() {
        tunnelStartM = null
        exitSaid = false
    }

    fun update(route: RoadRoute, progressM: Double, nowMs: Long, canSpeak: Boolean): String? {
        val t = route.tunnelAt(progressM)
        if (t == null || TunnelTier.ofLength(t.lengthM) != TunnelTier.LONG) {
            if (t == null) tunnelStartM = null
            return null
        }
        if (tunnelStartM != t.startM) {
            tunnelStartM = t.startM
            lastSaidMs = nowMs
            exitSaid = false
        }
        if (!canSpeak) return null
        val remaining = t.endM - progressM
        if (remaining <= EXIT_SOON_M) {
            if (exitSaid) return null
            exitSaid = true
            lastSaidMs = nowMs
            return "곧 출구입니다."
        }
        if (nowMs - lastSaidMs < REPEAT_MS) return null
        lastSaidMs = nowMs
        return "출구까지 ${spokenDistance(remaining)} 남았습니다."
    }

    companion object {
        const val REPEAT_MS = 60_000L
        const val EXIT_SOON_M = 300.0
    }
}

object RestBeforeTunnel {
    const val SEARCH_BEFORE_M = 15_000.0
    const val MAX_OFF_ROUTE_M = 500.0
    private const val STEP_M = 100.0

    fun find(route: RoadRoute, tunnel: RouteTunnel, pois: List<Poi>): Poi? {
        val rests = pois.filter { it.category == PoiCategory.REST_AREA }
        if (rests.isEmpty()) return null
        val from = max(0.0, tunnel.startM - SEARCH_BEFORE_M)
        var d = tunnel.startM
        while (d >= from) {
            val p = route.pointAt(d)
            rests.firstOrNull { distanceMeters(it.latLng, p) <= MAX_OFF_ROUTE_M }?.let { return it }
            d -= STEP_M
        }
        return null
    }
}
