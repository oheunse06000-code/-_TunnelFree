package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {
    private val net = TestRoutes.network

    @Test
    fun fullAvoidanceReturnsDetourAndTunnelComparison() {
        val plan = RoutePlanner.plan(net, TestRoutes.A, TestRoutes.D, AvoidanceLevel.FULL)
        val route = plan.route!!
        val comparison = plan.comparison!!
        assertEquals(0, route.tunnelCount)
        assertEquals(1, comparison.tunnelCount)
        assertTrue(comparison.lengthM < route.lengthM)
    }

    @Test
    fun noAvoidanceHasNoComparison() {
        val plan = RoutePlanner.plan(net, TestRoutes.A, TestRoutes.D, AvoidanceLevel.NONE)
        assertEquals(1, plan.route!!.tunnelCount)
        assertNull(plan.comparison)
    }

    @Test
    fun comparisonDroppedWhenSameAsAvoidingRoute() {
        val plan = RoutePlanner.plan(net, TestRoutes.C, TestRoutes.E, AvoidanceLevel.FULL)
        assertNotNull(plan.route)
        assertNull(plan.comparison)
    }

    @Test
    fun mediumLevelAllowsTunnelSoNoComparison() {
        val plan = RoutePlanner.plan(net, TestRoutes.A, TestRoutes.D, AvoidanceLevel.MEDIUM_OK)
        assertEquals(1, plan.route!!.tunnelCount)
        assertNull(plan.comparison)
    }

    @Test
    fun emptyNetworkYieldsNothing() {
        val plan = RoutePlanner.plan(RoadNetwork.EMPTY, TestRoutes.A, TestRoutes.D, AvoidanceLevel.FULL)
        assertNull(plan.route)
        assertNull(plan.comparison)
    }

    @Test
    fun comparisonOfRules() {
        val r1000 = TestRoutes.straight(1000.0)
        val r1000b = TestRoutes.straight(1000.5)
        val r1200 = TestRoutes.straight(1200.0)
        val withTunnel = TestRoutes.straight(1000.0, tunnels = listOf(RouteTunnel("t", 100.0, 200.0, 300.0)))
        assertNull(RoutePlanner.comparisonOf(r1000, null))
        assertSame(r1000, RoutePlanner.comparisonOf(null, r1000))
        assertNull(RoutePlanner.comparisonOf(r1000, r1000b))
        assertSame(r1200, RoutePlanner.comparisonOf(r1000, r1200))
        assertSame(withTunnel, RoutePlanner.comparisonOf(r1000, withTunnel))
    }

    @Test
    fun originUsesGpsOnlyInsideNetwork() {
        val inside = LatLng(36.302, 127.305)
        val outside = LatLng(37.5, 127.0)
        val fallback = LatLng(1.0, 2.0)
        assertEquals(inside, RoutePlanner.origin(inside, net, fallback))
        assertEquals(fallback, RoutePlanner.origin(outside, net, fallback))
        assertEquals(fallback, RoutePlanner.origin(null, net, fallback))
        assertEquals(DemoData.START, RoutePlanner.origin(null, net))
    }

    @Test
    fun extrapolateContinuesInSameDirection() {
        val from = LatLng(36.300, 127.3)
        val to = LatLng(36.301, 127.3)
        val p = RoutePlanner.extrapolate(from, to, 500.0)
        assertEquals(127.3, p.lng, 1e-12)
        assertEquals(500.0, distanceMeters(to, p), 1.0)
        assertTrue(p.lat > to.lat)
        val same = RoutePlanner.extrapolate(to, to, 100.0)
        assertEquals(to, same)
    }

    @Test
    fun tunnelPassEndpointsLieOutsideTunnel() {
        val t = Tunnel(1, "시험터널", "primary", 220, listOf(LatLng(36.300, 127.3), LatLng(36.301, 127.3), LatLng(36.302, 127.3)))
        val (origin, end) = RoutePlanner.tunnelPassEndpoints(t, 1000.0, 600.0)
        assertEquals(1000.0, distanceMeters(t.entry, origin), 1.0)
        assertTrue(origin.lat < t.entry.lat)
        assertEquals(600.0, distanceMeters(t.exit, end), 1.0)
        assertTrue(end.lat > t.exit.lat)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tunnelPassEndpointsRequireTwoPoints() {
        RoutePlanner.tunnelPassEndpoints(Tunnel(1, "x", "primary", 10, listOf(LatLng(36.3, 127.3))), 10.0, 10.0)
    }

    @Test
    fun demoTunnelPicksLongestNearbyThenFallsBack() {
        val near = Place("p", 36.30, 127.30)
        val short = Tunnel(1, "짧은", "primary", 100, listOf(LatLng(36.300, 127.300), LatLng(36.301, 127.300)))
        val long = Tunnel(2, "긴", "primary", 900, listOf(LatLng(36.301, 127.301), LatLng(36.302, 127.301)))
        val far = Tunnel(3, "먼", "primary", 5000, listOf(LatLng(35.0, 128.0), LatLng(35.01, 128.0)))
        val db = TunnelDatabase(listOf(far, short, long))
        assertEquals(long, RoutePlanner.demoTunnel(db, listOf(near)))
        assertEquals(far, RoutePlanner.demoTunnel(TunnelDatabase(listOf(far)), listOf(near)))
        assertNull(RoutePlanner.demoTunnel(TunnelDatabase.EMPTY, listOf(near)))
    }

    @Test
    fun successfulPlanHasNoFailure() {
        assertNull(RoutePlanner.plan(net, TestRoutes.A, TestRoutes.D, AvoidanceLevel.FULL).failure)
    }

    @Test
    fun sameStartAndEndIsTooClose() {
        val plan = RoutePlanner.plan(net, TestRoutes.A, TestRoutes.A, AvoidanceLevel.FULL)
        assertNull(plan.route)
        assertEquals(RouteFailure.TOO_CLOSE, plan.failure)
    }

    @Test
    fun tunnelOnlyPathIsBlockedByAvoidance() {
        val tunnelOnly = RoadNetwork.parse(
            """
            V 2
            36300000 127300000
            36300000 127310000
            P 0
            E 1
            0 1 9000 4 0 900 0 0 외길터널
            """.trimIndent()
        )
        val plan = RoutePlanner.plan(tunnelOnly, TestRoutes.A, TestRoutes.D, AvoidanceLevel.FULL)
        assertNull(plan.route)
        assertEquals(RouteFailure.AVOIDANCE_BLOCKS, plan.failure)
        assertEquals(1, plan.comparison!!.tunnelCount)
    }

    @Test
    fun onewayAgainstIsUnreachable() {
        val f = LatLng(36.3050, 127.3200)
        val b = LatLng(36.3050, 127.3100)
        val plan = RoutePlanner.plan(net, f, b, AvoidanceLevel.NONE)
        assertNull(plan.route)
        assertEquals(RouteFailure.UNREACHABLE, plan.failure)
    }

    @Test
    fun failureOfOrder() {
        val near = LatLng(36.3005, 127.3)
        assertEquals(RouteFailure.TOO_CLOSE, RoutePlanner.failureOf(TestRoutes.A, near, TestRoutes.straight(10.0)))
        assertEquals(RouteFailure.AVOIDANCE_BLOCKS, RoutePlanner.failureOf(TestRoutes.A, TestRoutes.D, TestRoutes.straight(10.0)))
        assertEquals(RouteFailure.UNREACHABLE, RoutePlanner.failureOf(TestRoutes.A, TestRoutes.D, null))
    }
}
