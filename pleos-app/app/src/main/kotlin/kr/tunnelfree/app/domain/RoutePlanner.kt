package kr.tunnelfree.app.domain

import kotlin.math.abs

enum class RouteFailure {
    TOO_CLOSE,
    AVOIDANCE_BLOCKS,
    UNREACHABLE,
}

data class RoutePlan(val route: RoadRoute?, val comparison: RoadRoute?, val failure: RouteFailure? = null)

object RoutePlanner {

    const val COMPARISON_MIN_DIFF_M = 1.0

    const val TOO_CLOSE_M = 100.0

    fun plan(roads: RoadNetwork, from: LatLng, to: LatLng, level: AvoidanceLevel, via: LatLng? = null): RoutePlan {
        val route = roads.route(from, to, level, via)
        val alt = if (level.avoidsAny) roads.route(from, to, AvoidanceLevel.NONE, via) else null
        val failure = if (route != null) null else failureOf(from, to, alt)
        return RoutePlan(route, comparisonOf(route, alt), failure)
    }

    fun failureOf(from: LatLng, to: LatLng, tunnelAllowedRoute: RoadRoute?): RouteFailure = when {
        distanceMeters(from, to) < TOO_CLOSE_M -> RouteFailure.TOO_CLOSE
        tunnelAllowedRoute != null -> RouteFailure.AVOIDANCE_BLOCKS
        else -> RouteFailure.UNREACHABLE
    }

    fun comparisonOf(route: RoadRoute?, alt: RoadRoute?): RoadRoute? =
        alt?.takeIf { route == null || it.tunnelCount != route.tunnelCount || abs(it.lengthM - route.lengthM) > COMPARISON_MIN_DIFF_M }

    fun origin(gps: LatLng?, roads: RoadNetwork, fallback: LatLng = DemoData.START): LatLng =
        if (gps != null && roads.contains(gps)) gps else fallback

    fun extrapolate(from: LatLng, to: LatLng, meters: Double): LatLng {
        val len = distanceMeters(from, to).coerceAtLeast(1.0)
        val k = meters / len
        return LatLng(to.lat + (to.lat - from.lat) * k, to.lng + (to.lng - from.lng) * k)
    }

    fun tunnelPassEndpoints(tunnel: Tunnel, beforeM: Double, afterM: Double): Pair<LatLng, LatLng> {
        val pts = tunnel.points
        require(pts.size >= 2) { "터널 폴리라인 점이 2개 미만입니다: ${tunnel.id}" }
        val origin = extrapolate(pts[1], pts[0], beforeM)
        val end = extrapolate(pts[pts.size - 2], pts[pts.size - 1], afterM)
        return origin to end
    }

    fun demoTunnel(db: TunnelDatabase, places: List<Place>, marginDeg: Double = 0.005): Tunnel? {
        if (places.isEmpty()) return db.tunnels.firstOrNull()
        val lats = places.map { it.lat }; val lngs = places.map { it.lng }
        val nearby = db.within(lats.min() - marginDeg, lats.max() + marginDeg, lngs.min() - marginDeg, lngs.max() + marginDeg)
        return nearby.maxByOrNull { it.lengthM } ?: db.tunnels.firstOrNull()
    }
}
