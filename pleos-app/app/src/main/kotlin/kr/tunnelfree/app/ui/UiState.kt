package kr.tunnelfree.app.ui

import kr.tunnelfree.app.domain.DayCondition
import kr.tunnelfree.app.domain.ParkingAdvice
import kr.tunnelfree.app.domain.ParkingCandidate
import kr.tunnelfree.app.domain.PoiCategory
import kr.tunnelfree.app.domain.RouteAdvice
import kr.tunnelfree.app.domain.SearchHit
import kr.tunnelfree.app.domain.SituationPlan
import kr.tunnelfree.app.domain.TripUrgency
import kr.tunnelfree.app.domain.AvoidanceLevel
import kr.tunnelfree.app.domain.DemoData
import kr.tunnelfree.app.domain.DriveProgress
import kr.tunnelfree.app.domain.IncidentKind
import kr.tunnelfree.app.domain.LatLng
import kr.tunnelfree.app.domain.Maneuver
import kr.tunnelfree.app.domain.MusicState
import kr.tunnelfree.app.domain.Place
import kr.tunnelfree.app.domain.RoadRoute
import kr.tunnelfree.app.domain.Tunnel
import kr.tunnelfree.app.domain.TunnelStatus
import kr.tunnelfree.app.domain.formatDistance
import kr.tunnelfree.app.platform.GeoPoint

sealed class IncidentState {
    data object None : IncidentState()
    data class Checking(val cause: String, val kind: IncidentKind = IncidentKind.PANIC_SUSPECTED) : IncidentState()
    data class SafeStop(val cause: String, val kind: IncidentKind, val steps: List<String>) : IncidentState()
}

data class VehicleTelemetry(
    val connected: Boolean = false,
    val brakePressed: Boolean? = null,
    val lastBrakeAt: Long = 0L,
    val speedKmh: Double? = null,
    val steeringAngle: Float? = null,
    val lastSteeringChangeAt: Long = 0L,
    val turnSignal: String = "OFF",
    val seatBeltBuckled: Boolean? = null,
)

enum class NavScreen { MENU, SEARCH, NEARBY, EMERGENCY, RECENT, OPTIONS, PARKING, STATUS, VOICE }

data class TunnelAheadUi(
    val title: String,
    val message: String,
    val detour: RoadRoute? = null,
)

data class ParkingUi(
    val destinationName: String = "",
    val candidates: List<ParkingCandidate> = emptyList(),
    val advice: ParkingAdvice? = null,
    val note: String = "",
)

data class UiState(
    val platformName: String = "standard",
    val naviAvailable: Boolean = false,
    val avoidanceLevel: AvoidanceLevel = AvoidanceLevel.DEFAULT,
    val roadsReady: Boolean = false,
    val running: Boolean = false,
    val paused: Boolean = false,
    val arrived: Boolean = false,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val breathing: Boolean = false,
    val breathText: String = "",
    val helpOpen: Boolean = false,
    val heard: String = "",
    val journeyStatus: String = "목적지를 선택하세요",
    val destination: Place? = null,
    val via: Place? = null,
    val origin: LatLng = DemoData.START,
    val route: RoadRoute? = null,
    val comparison: RoadRoute? = null,
    val routeAdvice: RouteAdvice? = null,
    val tunnelLimitM: Double = 0.0,
    val dayCondition: DayCondition = DayCondition.NORMAL,
    val urgency: TripUrgency = TripUrgency.NORMAL,
    val hasPassenger: Boolean = false,
    val nightDrive: Boolean = false,
    val ratingCount: Int = 0,
    val pendingRatingM: Double? = null,
    val navScreen: NavScreen = NavScreen.MENU,
    val situation: SituationPlan? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchHit> = emptyList(),
    val nearbyCategory: PoiCategory? = null,
    val recentPlaces: List<Place> = emptyList(),
    val parking: ParkingUi = ParkingUi(),
    val tunnelAhead: TunnelAheadUi? = null,
    val restBeforeTunnel: Place? = null,
    val routing: Boolean = false,
    val progressM: Double = 0.0,
    val simulated: Boolean = true,
    val gps: GeoPoint? = null,
    val guardianPhone: String = "",
    val incident: IncidentState = IncidentState.None,
    val vehicle: VehicleTelemetry = VehicleTelemetry(),
    val tunnelStatus: TunnelStatus = TunnelStatus(),
    val mapTunnels: List<Tunnel> = emptyList(),
    val tunnelDemoRunning: Boolean = false,
    val autoMusic: Boolean = true,
    val musicState: MusicState = MusicState.OFF,
    val musicUserRequested: Boolean = false,
    val musicUserStopped: Boolean = false,
    val musicExitGrace: Boolean = false,
    val challengeMode: Boolean = false,
    val challengeCountToday: Int = 0,
    val challengeLongestM: Int = 0,
) {
    val avoidTunnel: Boolean get() = avoidanceLevel.avoidsAny

    val insideRealTunnel: Boolean get() = tunnelStatus.inside != null

    val effectiveSpeedKmh: Double? get() = DriveProgress.effectiveSpeedKmh(simulated, running, paused, vehicle.speedKmh)

    val position: LatLng
        get() {
            val r = route ?: return origin
            return if (running || arrived) r.pointAt(progressM) else origin
        }

    val heading: Double get() = route?.bearingAt(progressM) ?: 0.0

    val remainingDistance: Double get() = route?.remainingM(progressM) ?: 0.0

    val remainingMinutes: Int get() = route?.remainingMinutes(progressM) ?: 0

    val tunnelCount: Int get() = route?.tunnelsAfter(progressM)?.size ?: 0

    val routeTunnelCount: Int get() = route?.tunnelCount ?: 0

    val routeTunnelSummary: String get() = route?.tunnels?.joinToString(", ") { "${it.name} ${formatDistance(it.lengthM)}" }.orEmpty()

    val nextManeuver: Maneuver? get() = route?.nextManeuver(progressM)
    val nextManeuverDistanceM: Double get() = nextManeuver?.let { (it.atM - progressM).coerceAtLeast(0.0) } ?: 0.0

    val destinationName: String? get() = destination?.name
    val viaName: String? get() = via?.name

    val emergencyActive: Boolean get() = incident !is IncidentState.None || helpOpen

    val comparisonTunnelSummary: String? get() = comparison?.tunnels?.joinToString(", ") { "${it.name} ${formatDistance(it.lengthM)}" }

    val avoidanceExtraMinutes: Int?
        get() {
            val c = comparison ?: return null
            val r = route ?: return null
            return r.minutes - c.minutes
        }
}
