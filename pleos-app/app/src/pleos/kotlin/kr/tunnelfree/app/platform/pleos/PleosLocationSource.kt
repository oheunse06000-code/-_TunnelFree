package kr.tunnelfree.app.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import kr.tunnelfree.app.platform.GeoPoint
import kr.tunnelfree.app.platform.GeoZone
import kr.tunnelfree.app.platform.LocationSource

class PleosLocationSource(
    context: Context,
    private val fallback: LocationSource,
) : LocationSource {

    private var fused: FusedLocation? = null
    private var listener: FusedLocationListener? = null
    private var usingPleos = false
    private val alertIds = HashMap<String, String>()

    init {
        try {
            val f = FusedLocation(context.applicationContext)
            if (f.isSupported()) {
                f.initialize()
                fused = f
                Log.i(TAG, "Pleos FusedLocation initialized")
            } else {
                Log.w(TAG, "Pleos FusedLocation not supported on this image")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos FusedLocation unavailable, falling back to LocationManager", e)
            fused = null
        }
    }

    override fun start(onLocation: (GeoPoint) -> Unit) {
        val f = fused
        if (f == null) {
            usingPleos = false
            fallback.start(onLocation)
            return
        }
        try {
            val l = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    location ?: return
                    onLocation(GeoPoint(location.latitude, location.longitude))
                }
                override fun onError(exception: Exception) {
                    Log.w(TAG, "FusedLocation error", exception)
                }
            }
            listener = l
            f.registerFusedLocationCallback(l)
            usingPleos = true
        } catch (e: Throwable) {
            Log.w(TAG, "register failed, using fallback", e)
            usingPleos = false
            fallback.start(onLocation)
        }
    }

    override fun watchZones(zones: List<GeoZone>, onTransition: (zoneId: String, entered: Boolean) -> Unit) {
        val f = fused ?: return
        clearZones()
        for (z in zones) {
            try {
                val req = ProximityAlertRequest(z.lat, z.lng, z.radiusM, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT))
                f.addProximityAlert(req, object : ProximityAlertListener {
                    override fun onRegistered(alertId: String) { alertIds[z.id] = alertId }
                    override fun onTransition(event: ProximityAlertEvent) {
                        onTransition(z.id, event.transition == ProximityTransition.ENTER)
                    }
                    override fun onError(exception: Exception) { Log.w(TAG, "proximity alert ${z.id} error", exception) }
                })
            } catch (e: Throwable) {
                Log.w(TAG, "addProximityAlert failed for ${z.id}", e)
            }
        }
    }

    private fun clearZones() {
        val f = fused ?: return
        for (id in alertIds.values) {
            try { f.removeProximityAlert(id) } catch (_: Throwable) {}
        }
        alertIds.clear()
    }

    override fun stop() {
        if (usingPleos) {
            try { listener?.let { fused?.unregisterFusedLocationCallback(it) } } catch (_: Throwable) {}
            listener = null
            clearZones()
        }
        fallback.stop()
    }

    override fun release() {
        stop()
        try { fused?.release() } catch (_: Throwable) {}
        fused = null
        fallback.release()
    }

    private companion object { const val TAG = "PleosLocation" }
}
