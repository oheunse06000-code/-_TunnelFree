package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverAnnouncerTest {
    private val route = TestRoutes.straight(
        2000.0,
        maneuvers = listOf(
            Maneuver(0.0, TurnType.DEPART, ""),
            Maneuver(1000.0, TurnType.RIGHT, "동로"),
            Maneuver(2000.0, TurnType.ARRIVE, ""),
        ),
    )

    @Test
    fun stages() {
        val a = ManeuverAnnouncer()
        assertEquals(0, a.stageFor(351.0))
        assertEquals(1, a.stageFor(350.0))
        assertEquals(1, a.stageFor(61.0))
        assertEquals(2, a.stageFor(60.0))
        assertEquals(2, a.stageFor(-5.0))
    }

    @Test
    fun farThenNearOncePerManeuver() {
        val a = ManeuverAnnouncer()
        assertNull(a.update(route, 500.0, canSpeak = true))
        val far = a.update(route, 700.0, canSpeak = true)
        assertNotNull(far)
        assertTrue(far!!.contains("300미터 앞에서 우회전"))
        assertNull(a.update(route, 750.0, canSpeak = true))
        val near = a.update(route, 950.0, canSpeak = true)
        assertTrue(near!!.contains("우회전"))
        assertNull(a.update(route, 960.0, canSpeak = true))
    }

    @Test
    fun cannotSpeakDefersAnnouncement() {
        val a = ManeuverAnnouncer()
        assertNull(a.update(route, 700.0, canSpeak = false))
        assertNotNull(a.update(route, 710.0, canSpeak = true))
    }

    @Test
    fun skippingDirectlyToNearOnlyAnnouncesNear() {
        val a = ManeuverAnnouncer()
        assertNotNull(a.update(route, 980.0, canSpeak = true))
        assertNull(a.update(route, 990.0, canSpeak = true))
    }

    @Test
    fun arrivalFarIsSpokenNearIsLeftToArrivalHandler() {
        val a = ManeuverAnnouncer()
        val far = a.update(route, 1700.0, canSpeak = true)
        assertTrue(far!!.startsWith("목적지까지"))
        assertNull(a.update(route, 1950.0, canSpeak = true))
        assertNull(a.update(route, 1960.0, canSpeak = true))
    }

    @Test
    fun arrivalNearConsumedEvenWhenCannotSpeak() {
        val a = ManeuverAnnouncer()
        assertNull(a.update(route, 1950.0, canSpeak = false))
        assertNull(a.update(route, 1960.0, canSpeak = true))
    }

    @Test
    fun resetAllowsAnnouncingAgainOnNewDrive() {
        val a = ManeuverAnnouncer()
        assertNotNull(a.update(route, 700.0, canSpeak = true))
        a.reset()
        assertNotNull(a.update(route, 700.0, canSpeak = true))
    }

    @Test
    fun noManeuversMeansSilence() {
        val a = ManeuverAnnouncer()
        assertNull(a.update(TestRoutes.straight(1000.0), 500.0, canSpeak = true))
    }
}
