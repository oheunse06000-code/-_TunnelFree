package kr.tunnelfree.app.domain

internal object TestRoutes {
    const val M_PER_DEG_LAT = 111_320.0

    fun straight(
        lengthM: Double,
        maneuvers: List<Maneuver> = emptyList(),
        viaM: Double? = null,
        tunnels: List<RouteTunnel> = emptyList(),
        travelSec: Double = lengthM / 10.0,
    ): RoadRoute {
        val n = (lengthM / 100.0).toInt().coerceAtLeast(1)
        val cum = DoubleArray(n + 1) { i -> lengthM * i / n }
        val pts = cum.map { LatLng(36.3 + it / M_PER_DEG_LAT, 127.3) }
        return RoadRoute(pts, cum, travelSec, tunnels, maneuvers, viaM)
    }

    val network: RoadNetwork by lazy {
        RoadNetwork.parse(
            """
            V 6
            36300000 127300000
            36305000 127300000
            36305000 127310000
            36300000 127310000
            36310000 127300000
            36305000 127320000
            P 0
            E 6
            0 2 10557 4 0 900 0 0 시험터널
            0 1 5566 4 0 0 0 0 북로
            1 2 8970 4 0 0 0 0 동로
            2 3 5566 4 0 0 0 0 남로
            1 4 5566 4 0 0 0 0 북로
            2 5 8970 4 1 0 0 0 일방로
            """.trimIndent()
        )
    }
    val A = LatLng(36.3000, 127.3000)
    val C = LatLng(36.3050, 127.3000)
    val D = LatLng(36.3000, 127.3100)
    val E = LatLng(36.3100, 127.3000)
}
