package kr.tunnelfree.app.domain

data class DriveStep(val progressM: Double, val reachedVia: Boolean, val arrived: Boolean)

object DriveProgress {
    const val SIM_SPEED_KMH = 60.0
    const val SIM_TICK_MS = 250L
    const val OFF_ROUTE_M = 60.0
    const val ARRIVAL_M = 25.0
    const val GPS_START_NEAR_M = 500.0

    const val SIM_MAX_ELAPSED_MS = 1_000L

    val SIM_STEP_M: Double = stepForElapsed(SIM_TICK_MS)

    fun stepForElapsed(elapsedMs: Long): Double =
        (SIM_SPEED_KMH / 3.6) * (elapsedMs.coerceIn(0L, SIM_MAX_ELAPSED_MS) / 1000.0)

    fun effectiveSpeedKmh(simulated: Boolean, running: Boolean, paused: Boolean, vehicleKmh: Double?): Double? = when {
        !simulated || !running -> vehicleKmh
        paused -> 0.0
        else -> SIM_SPEED_KMH
    }

    fun needsSimulation(hasVehicleGps: Boolean, gps: LatLng?, routeStart: LatLng): Boolean =
        !hasVehicleGps || gps == null || distanceMeters(gps, routeStart) > GPS_START_NEAR_M

    fun simulateStep(route: RoadRoute, beforeM: Double, viaPending: Boolean, stepM: Double = SIM_STEP_M): DriveStep {
        var progress = beforeM + stepM
        var reachedVia = false
        val viaM = route.viaM
        if (viaM != null && viaPending && beforeM < viaM && progress >= viaM) {
            progress = viaM
            reachedVia = true
        }
        val arrived = progress >= route.lengthM
        if (arrived) progress = route.lengthM
        return DriveStep(progress, reachedVia, arrived)
    }

    fun snapToRoute(route: RoadRoute, p: LatLng, progressM: Double): Double? {
        val proj = route.project(p, progressM)
        return if (proj.offsetM < OFF_ROUTE_M) proj.progressM else null
    }

    fun isArrived(route: RoadRoute, progressM: Double): Boolean = route.remainingM(progressM) < ARRIVAL_M
}
