package kr.tunnelfree.app.domain

class SafeStopMonitor(private val startedAtMs: Long) {
    private var stoppedSinceMs = 0L

    fun tick(nowMs: Long, speedKmh: Double?): Boolean {
        if (isStopped(speedKmh)) {
            if (stoppedSinceMs == 0L) stoppedSinceMs = nowMs
        } else {
            stoppedSinceMs = 0L
        }
        val stoppedLongEnough = stoppedSinceMs != 0L && nowMs - stoppedSinceMs >= SafeStopGuide.STOPPED_CONFIRM_MS
        return stoppedLongEnough || nowMs - startedAtMs >= SafeStopGuide.RESPONSE_TIMEOUT_MS
    }

    companion object {
        const val STOPPED_SPEED_KMH = 2.0

        fun isStopped(speedKmh: Double?): Boolean = speedKmh?.let { it < STOPPED_SPEED_KMH } ?: false
    }
}
