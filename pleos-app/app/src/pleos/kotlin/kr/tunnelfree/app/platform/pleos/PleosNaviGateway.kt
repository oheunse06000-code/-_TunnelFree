package kr.tunnelfree.app.platform.pleos

import android.content.Context
import android.util.Log
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import kr.tunnelfree.app.domain.Place
import kr.tunnelfree.app.platform.NaviGateway

class PleosNaviGateway(context: Context) : NaviGateway {

    private var navi: NaviHelper? = null

    private val events = object : NaviHelperEventListener {
        override fun onRoutingCompleted() { Log.i(TAG, "routing completed") }
        override fun onRouteOptionChanged(option: RouteOption) { Log.i(TAG, "route option -> $option") }
        override fun onDestinationChanged() { Log.i(TAG, "destination changed") }
        override fun onError(code: NaviErrorCode) { Log.w(TAG, "navi error $code") }
    }

    override val isAvailable: Boolean get() = navi != null

    init {
        try {
            val n = NaviHelper(context.applicationContext)
            n.initialize()
            n.addListener(events)
            navi = n
            Log.i(TAG, "Pleos NaviHelper initialized")
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos NaviHelper unavailable", e)
            navi = null
        }
    }

    override fun requestRoute(destination: Place) {
        val n = navi ?: return
        try {
            n.requestRoute(
                RouteInfo(
                    latitude = destination.lat,
                    longitude = destination.lng,
                    displayLatitude = null,
                    displayLongitude = null,
                    poiName = destination.name,
                    poiId = "",
                    address = "",
                    poiSubId = "0",
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "requestRoute failed", e)
        }
    }

    override fun addWaypoint(place: Place) {
        val n = navi ?: return
        try {
            n.addWaypoint(
                RequestWaypointInfo(
                    latitude = place.lat,
                    longitude = place.lng,
                    poiName = place.name,
                    waypointIndex = WaypointIndex.FIRST,
                    poiId = "",
                    address = "",
                    poiSubId = "0",
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "addWaypoint failed", e)
        }
    }

    override fun removeWaypoint() {
        try { navi?.removeWaypoint(WaypointIndex.FIRST) } catch (e: Exception) { Log.w(TAG, "removeWaypoint failed", e) }
    }

    override fun cancelRoute() {
        try { navi?.cancelRoute() } catch (e: Exception) { Log.w(TAG, "cancelRoute failed", e) }
    }

    override fun release() {
        try {
            navi?.removeListener(events)
            navi?.release()
        } catch (e: Exception) {
            Log.w(TAG, "release failed", e)
        }
        navi = null
    }

    private companion object { const val TAG = "PleosNavi" }
}
