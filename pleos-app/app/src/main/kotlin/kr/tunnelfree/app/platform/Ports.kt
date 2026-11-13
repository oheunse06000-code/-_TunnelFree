package kr.tunnelfree.app.platform

import android.content.Context
import kr.tunnelfree.app.domain.Place

data class GeoPoint(val lat: Double, val lng: Double)

interface SpeechOutput {
    fun speak(text: String, onDone: (() -> Unit)? = null)
    fun stop()
    fun release()
}

interface SpeechInput {
    val isListening: Boolean
    fun start(onResult: (String) -> Unit, onEnd: () -> Unit, onError: (String) -> Unit, onReady: () -> Unit = {})
    fun stop()
    fun release()
}

data class GeoZone(val id: String, val lat: Double, val lng: Double, val radiusM: Float)

interface LocationSource {
    fun start(onLocation: (GeoPoint) -> Unit)
    fun watchZones(zones: List<GeoZone>, onTransition: (zoneId: String, entered: Boolean) -> Unit) {}
    fun stop()
    fun release()
}

interface VehicleSignalListener {
    fun onHardDeceleration(deceleration: Double) {}
    fun onBrakePedal(pressed: Boolean) {}
    fun onSpeedEstimate(kmh: Double) {}
    fun onSteeringAngle(degrees: Float) {}
    fun onTurnSignal(state: String) {}
    fun onSeatBelt(buckled: Boolean) {}
    fun onAirbagDeployed() {}
}

interface VehicleSignals {
    val hasBrakeSignal: Boolean
    val hasVehicleSignals: Boolean
    fun start(listener: VehicleSignalListener)
    fun stop()
    fun release()
}

interface MusicPlayer {
    val isPlaying: Boolean
    fun play()
    fun setVolume(volume: Float)
    fun pause()
    fun release()
}

interface NaviGateway {
    val isAvailable: Boolean
    fun requestRoute(destination: Place)
    fun addWaypoint(place: Place)
    fun removeWaypoint()
    fun cancelRoute()
    fun release()
}

class PlatformServices(
    val speechOutput: SpeechOutput,
    val speechInput: SpeechInput,
    val locationSource: LocationSource,
    val vehicleSignals: VehicleSignals,
    val navi: NaviGateway,
    val music: MusicPlayer,
    val platformName: String,
) {
    fun release() {
        speechInput.release()
        speechOutput.release()
        locationSource.release()
        vehicleSignals.release()
        navi.release()
        music.release()
    }
}

fun interface PlatformProvider {
    fun create(context: Context): PlatformServices
}
