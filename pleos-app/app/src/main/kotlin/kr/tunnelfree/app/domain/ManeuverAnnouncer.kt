package kr.tunnelfree.app.domain

class ManeuverAnnouncer(
    private val farM: Double = FAR_M,
    private val nearM: Double = NEAR_M,
) {
    private var announcedAt = -1.0
    private var announcedStage = 0

    fun reset() {
        announcedAt = -1.0
    }

    fun update(route: RoadRoute, progressM: Double, canSpeak: Boolean): String? {
        val m = route.nextManeuver(progressM) ?: return null
        if (m.atM != announcedAt) {
            announcedAt = m.atM
            announcedStage = 0
        }
        val d = m.atM - progressM
        val stage = stageFor(d)
        if (stage <= announcedStage) return null
        if (m.type == TurnType.ARRIVE && stage == 2) {
            announcedStage = stage
            return null
        }
        if (!canSpeak) return null
        announcedStage = stage
        return m.spoken(d)
    }

    fun stageFor(d: Double): Int = when {
        d <= nearM -> 2
        d <= farM -> 1
        else -> 0
    }

    companion object {
        const val FAR_M = 350.0
        const val NEAR_M = 60.0
    }
}
