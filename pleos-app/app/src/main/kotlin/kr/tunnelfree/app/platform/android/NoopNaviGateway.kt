package kr.tunnelfree.app.platform.android

import kr.tunnelfree.app.domain.Place
import kr.tunnelfree.app.platform.NaviGateway

class NoopNaviGateway : NaviGateway {
    override val isAvailable: Boolean = false
    override fun requestRoute(destination: Place) {}
    override fun addWaypoint(place: Place) {}
    override fun removeWaypoint() {}
    override fun cancelRoute() {}
    override fun release() {}
}
