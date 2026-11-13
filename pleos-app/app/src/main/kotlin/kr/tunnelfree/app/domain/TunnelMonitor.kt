package kr.tunnelfree.app.domain

import kotlin.math.abs

sealed class TunnelEvent {
    data class Approaching(val tunnel: Tunnel, val distanceM: Int) : TunnelEvent()
    data class Entered(val tunnel: Tunnel) : TunnelEvent()
    data class Exited(val tunnel: Tunnel) : TunnelEvent()
}

data class TunnelStatus(
    val inside: Tunnel? = null,
    val nearest: Tunnel? = null,
    val nearestDistanceM: Int? = null,
    val approaching: Boolean = false,
)

class TunnelMonitor(
    private val db: TunnelDatabase,
    private val approachRadiusM: Double = 500.0,
    private val insideRadiusM: Double = 30.0,
) {
    var status: TunnelStatus = TunnelStatus()
        private set

    var relevance: (Tunnel) -> Boolean = { true }

    private var announcedApproachId: Long? = null
    private var lastEntryDistance: Double? = null
    private var lastEntryTunnelId: Long? = null
    private var recentlyExited: Tunnel? = null

    fun approachRadiusFor(speedKmh: Double?): Double =
        if (speedKmh == null) approachRadiusM
        else maxOf(approachRadiusM, PHYSIO_ONSET_M + speedKmh / 3.6 * APPROACH_SPEECH_SEC)

    fun update(p: LatLng, speedKmh: Double? = null): List<TunnelEvent> {
        val events = ArrayList<TunnelEvent>(2)
        val current = status.inside

        if (current != null) {
            if (current.distanceTo(p) > insideRadiusM * 2) {
                events.add(TunnelEvent.Exited(current))
                status = status.copy(inside = null)
                announcedApproachId = null
                lastEntryDistance = null
                recentlyExited = current
            } else {
                status = status.copy(nearest = current, nearestDistanceM = 0)
                return events
            }
        }

        recentlyExited?.let { if (distanceMeters(p, it.exit) > TWIN_SUPPRESS_M) recentlyExited = null }

        val near = db.nearest(p, insideRadiusM, relevance)
        if (near != null && !isTwinOfExited(near.first)) {
            val t = near.first
            status = TunnelStatus(inside = t, nearest = t, nearestDistanceM = 0)
            events.add(TunnelEvent.Entered(t))
            announcedApproachId = null
            lastEntryDistance = null
            return events
        }

        val approaching = db.entriesWithin(p, approachRadiusFor(speedKmh)).firstOrNull {
            relevance(it.first) && !isTwinOfExited(it.first) && !isPartOfExited(it.first)
        }
        if (approaching != null) {
            val (t, d) = approaching
            val closing = lastEntryDistance?.takeIf { lastEntryTunnelId == t.id }?.let { d < it } ?: true
            lastEntryDistance = d
            lastEntryTunnelId = t.id
            status = TunnelStatus(inside = null, nearest = t, nearestDistanceM = d.toInt(), approaching = closing)
            if (closing && announcedApproachId != t.id) {
                announcedApproachId = t.id
                events.add(TunnelEvent.Approaching(t, d.toInt()))
            }
        } else {
            lastEntryDistance = null
            val far = db.nearest(p)
            status = TunnelStatus(inside = null, nearest = far?.first, nearestDistanceM = far?.second?.toInt())
        }
        return events
    }

    private fun isTwinOfExited(t: Tunnel): Boolean {
        val ex = recentlyExited ?: return false
        if (t.id == ex.id || distanceMeters(t.entry, ex.exit) >= TWIN_ENTRY_M) return false
        val turn = abs(normalizeAngle(bearingDegrees(t.entry, t.exit) - bearingDegrees(ex.entry, ex.exit)))
        return turn > TWIN_MIN_TURN_DEG
    }

    private fun isPartOfExited(t: Tunnel): Boolean {
        val ex = recentlyExited ?: return false
        return t.id == ex.id || (ex.name.isNotEmpty() && t.name == ex.name)
    }

    fun reset() {
        status = TunnelStatus()
        announcedApproachId = null
        lastEntryDistance = null
        recentlyExited = null
    }

    companion object {
        const val PHYSIO_ONSET_M = 250.0
        const val APPROACH_SPEECH_SEC = 12.0
        const val TWIN_ENTRY_M = 250.0
        const val TWIN_SUPPRESS_M = 600.0
        const val TWIN_MIN_TURN_DEG = 90.0
    }
}
