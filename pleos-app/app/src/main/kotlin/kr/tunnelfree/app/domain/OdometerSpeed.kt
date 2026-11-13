package kr.tunnelfree.app.domain

data class SpeedSample(val speedKmh: Double, val decelerationMps2: Double?, val hardDeceleration: Boolean = false)

class OdometerSpeed {
    private var lastKm: Double? = null
    private var lastAtMs = 0L
    private var speedKmh: Double? = null
    private var speedAtMs = 0L
    private var hardStreak = 0

    fun update(distanceKm: Double, nowMs: Long): SpeedSample? {
        val prevKm = lastKm
        val elapsedMs = nowMs - lastAtMs
        if (prevKm == null || elapsedMs < 0) {
            lastKm = distanceKm
            lastAtMs = nowMs
            return null
        }
        if (elapsedMs < MIN_INTERVAL_MS) return null
        lastKm = distanceKm
        lastAtMs = nowMs

        val speed = ((distanceKm - prevKm) / (elapsedMs / 3_600_000.0)).coerceAtLeast(0.0)
        val prevSpeed = speedKmh
        val spanMs = nowMs - speedAtMs
        val deceleration = if (prevSpeed != null && spanMs > 0) {
            ((prevSpeed - speed) / 3.6) / (spanMs / 1000.0)
        } else {
            null
        }
        val sharp = deceleration != null && deceleration >= IncidentClassifier.HARD_DECELERATION_MPS2
        hardStreak = if (sharp) hardStreak + 1 else 0
        speedKmh = speed
        speedAtMs = nowMs
        val hard = hardStreak >= HARD_SAMPLES || (sharp && spanMs >= LONG_SPAN_MS)
        return SpeedSample(speed, deceleration, hard)
    }

    fun decayIfStale(nowMs: Long): Boolean {
        val speed = speedKmh ?: return false
        if (speed == 0.0 || nowMs - lastAtMs < STALE_MS) return false
        speedKmh = 0.0
        speedAtMs = nowMs
        hardStreak = 0
        return true
    }

    fun reset() {
        lastKm = null
        speedKmh = null
        hardStreak = 0
    }

    companion object {
        const val MIN_INTERVAL_MS = 500L
        const val STALE_MS = 5_000L
        const val HARD_SAMPLES = 2
        const val LONG_SPAN_MS = 2_000L
    }
}
