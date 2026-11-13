package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveProgressTest {

    @Test
    fun simulatesUnlessVehicleGpsIsNearRouteStart() {
        val start = LatLng(36.35, 127.38)
        val near = LatLng(36.351, 127.38)
        val california = LatLng(37.42, -122.08)
        assertTrue(DriveProgress.needsSimulation(hasVehicleGps = false, gps = near, routeStart = start))
        assertTrue(DriveProgress.needsSimulation(hasVehicleGps = true, gps = null, routeStart = start))
        assertTrue(DriveProgress.needsSimulation(hasVehicleGps = true, gps = california, routeStart = start))
        assertFalse(DriveProgress.needsSimulation(hasVehicleGps = true, gps = near, routeStart = start))
    }

    @Test
    fun stepIs60KmhInRealTimeFor250Ms() {
        assertEquals(60.0 / 3.6 * 0.25, DriveProgress.SIM_STEP_M, 1e-9)
        val s = DriveProgress.simulateStep(TestRoutes.straight(1000.0), 100.0, viaPending = false)
        assertEquals(100.0 + DriveProgress.SIM_STEP_M, s.progressM, 1e-9)
        assertFalse(s.reachedVia)
        assertFalse(s.arrived)
    }

    @Test
    fun stepFollowsRealElapsedTimeAndCapsLongStalls() {
        assertEquals(60.0 / 3.6 * 0.4, DriveProgress.stepForElapsed(400), 1e-9)
        assertEquals(60.0 / 3.6, DriveProgress.stepForElapsed(5_000), 1e-9)
        assertEquals(0.0, DriveProgress.stepForElapsed(-10), 0.0)
    }

    @Test
    fun simulatedDriveReportsSimSpeedInsteadOfParkedVehicleSignal() {
        assertEquals(60.0, DriveProgress.effectiveSpeedKmh(simulated = true, running = true, paused = false, vehicleKmh = 0.0)!!, 0.0)
        assertEquals(0.0, DriveProgress.effectiveSpeedKmh(simulated = true, running = true, paused = true, vehicleKmh = 0.0)!!, 0.0)
        assertEquals(42.0, DriveProgress.effectiveSpeedKmh(simulated = false, running = true, paused = false, vehicleKmh = 42.0)!!, 0.0)
        assertNull(DriveProgress.effectiveSpeedKmh(simulated = true, running = false, paused = false, vehicleKmh = null))
    }

    @Test
    fun stopsExactlyAtViaPoint() {
        val r = TestRoutes.straight(1000.0, viaM = 400.0)
        val s = DriveProgress.simulateStep(r, 390.0, viaPending = true, stepM = 30.0)
        assertEquals(400.0, s.progressM, 0.0)
        assertTrue(s.reachedVia)
        assertFalse(s.arrived)
    }

    @Test
    fun viaIgnoredWhenAlreadyPassedOrNotPending() {
        val r = TestRoutes.straight(1000.0, viaM = 400.0)
        assertFalse(DriveProgress.simulateStep(r, 390.0, viaPending = false, stepM = 30.0).reachedVia)
        val resumed = DriveProgress.simulateStep(r, 400.0, viaPending = true, stepM = 30.0)
        assertFalse(resumed.reachedVia)
        assertEquals(430.0, resumed.progressM, 0.0)
    }

    @Test
    fun clampsToEndAndArrives() {
        val r = TestRoutes.straight(1000.0)
        val s = DriveProgress.simulateStep(r, 990.0, viaPending = false, stepM = 30.0)
        assertEquals(1000.0, s.progressM, 0.0)
        assertTrue(s.arrived)
    }

    @Test
    fun viaAtEndIsBothReachedAndArrived() {
        val r = TestRoutes.straight(1000.0, viaM = 1000.0)
        val s = DriveProgress.simulateStep(r, 990.0, viaPending = true, stepM = 30.0)
        assertTrue(s.reachedVia)
        assertTrue(s.arrived)
        assertEquals(1000.0, s.progressM, 0.0)
    }

    @Test
    fun snapToRouteWithinTolerance() {
        val r = TestRoutes.straight(1000.0)
        val p = LatLng(36.3 + 500.0 / TestRoutes.M_PER_DEG_LAT, 127.3 + 30.0 / (TestRoutes.M_PER_DEG_LAT * Math.cos(Math.toRadians(36.3045))))
        assertEquals(500.0, DriveProgress.snapToRoute(r, p, 450.0)!!, 2.0)
    }

    @Test
    fun offRouteReturnsNull() {
        val r = TestRoutes.straight(1000.0)
        val p = LatLng(36.3 + 500.0 / TestRoutes.M_PER_DEG_LAT, 127.3 + 100.0 / (TestRoutes.M_PER_DEG_LAT * Math.cos(Math.toRadians(36.3045))))
        assertNull(DriveProgress.snapToRoute(r, p, 450.0))
    }

    @Test
    fun arrivalThreshold() {
        val r = TestRoutes.straight(1000.0)
        assertFalse(DriveProgress.isArrived(r, 975.0))
        assertTrue(DriveProgress.isArrived(r, 975.1))
        assertTrue(DriveProgress.isArrived(r, 1200.0))
    }
}
