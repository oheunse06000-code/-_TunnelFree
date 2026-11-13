package kr.tunnelfree.app.domain

import java.util.Arrays
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class RouteTunnel(val name: String, val lengthM: Double, val startM: Double, val endM: Double)

enum class TurnType(val label: String) {
    DEPART("출발"), STRAIGHT("직진"),
    SLIGHT_LEFT("좌측 방향"), SLIGHT_RIGHT("우측 방향"),
    LEFT("좌회전"), RIGHT("우회전"),
    SHARP_LEFT("급좌회전"), SHARP_RIGHT("급우회전"),
    U_TURN("유턴"), ARRIVE("목적지 도착");

    companion object {
        fun fromAngle(deg: Double): TurnType = when {
            abs(deg) < 20 -> STRAIGHT
            abs(deg) < 60 -> if (deg > 0) SLIGHT_RIGHT else SLIGHT_LEFT
            abs(deg) < 135 -> if (deg > 0) RIGHT else LEFT
            abs(deg) < 165 -> if (deg > 0) SHARP_RIGHT else SHARP_LEFT
            else -> U_TURN
        }
    }
}

data class Maneuver(val atM: Double, val type: TurnType, val roadName: String) {
    val label: String get() = type.label

    fun spoken(distanceM: Double): String = when (type) {
        TurnType.ARRIVE -> "목적지까지 ${spokenDistance(distanceM)} 남았습니다."
        TurnType.DEPART -> "안내를 시작합니다."
        else -> {
            val where = if (distanceM < 40) "" else "${spokenDistance(distanceM)} 앞에서 "
            val toward = if (roadName.isEmpty()) "" else " $roadName 방면입니다."
            "$where${type.label}입니다.$toward"
        }
    }
}

data class RoadSegment(val startM: Double, val endM: Double, val roadClass: RoadClass)

data class Projection(val progressM: Double, val offsetM: Double)

class RoadRoute(
    val points: List<LatLng>,
    val cumulativeM: DoubleArray,
    val travelSec: Double,
    val tunnels: List<RouteTunnel>,
    val maneuvers: List<Maneuver>,
    val viaM: Double?,
    val roadSegments: List<RoadSegment> = emptyList(),
) {
    val lengthM: Double get() = cumulativeM.last()
    val minutes: Int get() = ceil(travelSec / 60).toInt()
    val tunnelCount: Int get() = tunnels.size
    val bounds: Bounds by lazy { Bounds.of(points) }
    val start: LatLng get() = points.first()
    val end: LatLng get() = points.last()

    fun withManeuvers(list: List<Maneuver>): RoadRoute = RoadRoute(points, cumulativeM, travelSec, tunnels, list, viaM, roadSegments)

    fun remainingM(progressM: Double): Double = (lengthM - progressM).coerceAtLeast(0.0)

    fun remainingSec(progressM: Double): Double =
        if (lengthM <= 0) 0.0 else travelSec * remainingM(progressM) / lengthM

    fun remainingMinutes(progressM: Double): Int = ceil(remainingSec(progressM) / 60).toInt()

    fun tunnelsAfter(progressM: Double): List<RouteTunnel> = tunnels.filter { it.endM > progressM }

    fun tunnelAt(progressM: Double): RouteTunnel? = tunnels.firstOrNull { progressM >= it.startM && progressM <= it.endM }

    fun roadClassAt(d: Double): RoadClass? =
        roadSegments.firstOrNull { d >= it.startM && d <= it.endM }?.roadClass

    fun hasTunnelNear(p: LatLng, toleranceM: Double = 60.0, marginM: Double = 150.0): Boolean {
        if (tunnels.isEmpty()) return false
        val proj = project(p, 0.0, lengthM + 1.0, 1.0)
        if (proj.offsetM > toleranceM) return false
        return tunnels.any { proj.progressM >= it.startM - marginM && proj.progressM <= it.endM + marginM }
    }

    fun nextManeuver(progressM: Double): Maneuver? =
        maneuvers.firstOrNull { it.type != TurnType.DEPART && it.atM >= progressM - 1.0 }

    fun pointAt(d: Double): LatLng {
        val dd = d.coerceIn(0.0, lengthM)
        var idx = Arrays.binarySearch(cumulativeM, dd)
        if (idx >= 0) return points[idx]
        idx = -idx - 2
        if (idx < 0) return points[0]
        if (idx >= points.size - 1) return points.last()
        val segLen = cumulativeM[idx + 1] - cumulativeM[idx]
        val t = if (segLen <= 0) 0.0 else (dd - cumulativeM[idx]) / segLen
        val a = points[idx]; val b = points[idx + 1]
        return LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
    }

    fun bearingAt(d: Double, spanM: Double = 10.0): Double =
        bearingDegrees(pointAt(d - spanM), pointAt(d + spanM))

    fun bearingBefore(d: Double, spanM: Double = 20.0): Double = bearingDegrees(pointAt(d - spanM), pointAt(d))

    fun bearingAfter(d: Double, spanM: Double = 20.0): Double = bearingDegrees(pointAt(d), pointAt(d + spanM))

    fun project(p: LatLng, fromM: Double = 0.0, aheadM: Double = 800.0, behindM: Double = 80.0): Projection {
        val lo = fromM - behindM
        val hi = fromM + aheadM
        val kLat = 111_320.0
        val kLng = 111_320.0 * cos(Math.toRadians(p.lat))
        var best = Projection(fromM, Double.MAX_VALUE)
        for (i in 0 until points.size - 1) {
            if (cumulativeM[i + 1] < lo || cumulativeM[i] > hi) continue
            val a = points[i]; val b = points[i + 1]
            val ax = (a.lng - p.lng) * kLng; val ay = (a.lat - p.lat) * kLat
            val bx = (b.lng - p.lng) * kLng; val by = (b.lat - p.lat) * kLat
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + t * dx; val cy = ay + t * dy
            val off = sqrt(cx * cx + cy * cy)
            if (off < best.offsetM) {
                best = Projection(cumulativeM[i] + (cumulativeM[i + 1] - cumulativeM[i]) * t, off)
            }
        }
        return best
    }
}

fun bearingDegrees(a: LatLng, b: LatLng): Double {
    val dy = b.lat - a.lat
    val dx = (b.lng - a.lng) * cos(Math.toRadians((a.lat + b.lat) / 2))
    return (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
}

fun normalizeAngle(deg: Double): Double = ((deg + 540.0) % 360.0) - 180.0

fun formatDistance(m: Double): String =
    if (m >= 1000) String.format(Locale.ROOT, "%.1f km", m / 1000) else "${((m / 10).roundToInt() * 10)} m"

fun spokenDistance(m: Double): String =
    if (m >= 1000) String.format(Locale.ROOT, "%.1f킬로미터", m / 1000) else "${((m / 10).roundToInt() * 10)}미터"
