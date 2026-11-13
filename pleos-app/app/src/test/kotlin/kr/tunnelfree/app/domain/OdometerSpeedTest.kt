package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerSpeedTest {

    @Test
    fun firstValueOnlySetsTheBaseline() {
        assertNull(OdometerSpeed().update(500.0, 0))
    }

    @Test
    fun speedIsDistanceOverTime() {
        val o = OdometerSpeed()
        o.update(500.0, 0)
        val s = o.update(500.1, 6_000)!!
        assertEquals(60.0, s.speedKmh, 1e-6)
        assertNull(s.decelerationMps2)
        assertFalse(s.hardDeceleration)
    }

    @Test
    fun updatesCloserThanHalfASecondAreMergedIntoTheNextOne() {
        val o = OdometerSpeed()
        o.update(500.0, 0)
        assertNull(o.update(500.001, 100))
        val s = o.update(500.01, 600)!!
        assertEquals(60.0, s.speedKmh, 1e-6)
    }

    @Test
    fun decelerationComesFromTwoSpeedSamples() {
        val o = OdometerSpeed()
        o.update(0.0, 0)
        o.update(0.02, 1_000)
        val s = o.update(0.03, 2_000)!!
        assertEquals(36.0, s.speedKmh, 1e-6)
        assertEquals(10.0, s.decelerationMps2!!, 1e-6)
    }

    @Test
    fun oneSharpSampleAloneIsNotAHardStop() {
        val o = OdometerSpeed()
        o.update(0.0, 0)
        o.update(0.02, 1_000)
        assertFalse(o.update(0.03, 2_000)!!.hardDeceleration)
        assertFalse(o.update(0.04, 3_000)!!.hardDeceleration)
    }

    @Test
    fun twoSharpSamplesInARowAreAHardStop() {
        val o = OdometerSpeed()
        o.update(0.0, 0)
        assertEquals(72.0, o.update(0.02, 1_000)!!.speedKmh, 1e-6)
        val first = o.update(0.035, 2_000)!!
        assertEquals(5.0, first.decelerationMps2!!, 1e-6)
        assertFalse(first.hardDeceleration)
        val second = o.update(0.045, 3_000)!!
        assertEquals(5.0, second.decelerationMps2!!, 1e-6)
        assertTrue(second.hardDeceleration)
    }

    @Test
    fun oneSharpSampleOverALongSpanIsAHardStop() {
        val o = OdometerSpeed()
        o.update(0.0, 0)
        assertEquals(90.0, o.update(0.1, 4_000)!!.speedKmh, 1e-6)
        val s = o.update(0.11, 8_000)!!
        assertEquals(9.0, s.speedKmh, 1e-6)
        assertEquals(5.625, s.decelerationMps2!!, 1e-6)
        assertTrue(s.hardDeceleration)
    }

    @Test
    fun aGentleStopOverLongSpansIsNotAHardStop() {
        val o = OdometerSpeed()
        o.update(0.0, 0)
        o.update(0.1, 4_000)
        val s = o.update(0.15, 8_000)!!
        assertEquals(45.0, s.speedKmh, 1e-6)
        assertFalse(s.hardDeceleration)
    }

    @Test
    fun aLateCallbackDoesNotLookLikeBraking() {
        val o = OdometerSpeed()
        var km = 0.0
        var t = 0L
        o.update(km, t)
        var hard = false
        var sharp = false
        for (i in 1..40) {
            t += 100
            km += 100.0 / 36_000.0
            val stamp = if (i == 15) t + 60 else t
            o.update(km, stamp)?.let {
                if (i == 15) sharp = it.decelerationMps2!! >= IncidentClassifier.HARD_DECELERATION_MPS2
                if (it.hardDeceleration) hard = true
            }
        }
        assertTrue(sharp)
        assertFalse(hard)
    }

    @Test
    fun odometerGoingBackwardsNeverGivesNegativeSpeed() {
        val o = OdometerSpeed()
        o.update(10.0, 0)
        assertEquals(0.0, o.update(9.9, 1_000)!!.speedKmh, 0.0)
    }

    @Test
    fun aClockStepBackwardsStartsANewBaseline() {
        val o = OdometerSpeed()
        o.update(1.0, 10_000)
        assertNull(o.update(1.01, 5_000))
        assertEquals(36.0, o.update(1.02, 6_000)!!.speedKmh, 1e-6)
    }

    @Test
    fun speedDropsToZeroWhenTheOdometerStopsChanging() {
        val o = OdometerSpeed()
        o.update(0.0, 0)
        o.update(0.02, 1_000)
        assertFalse(o.decayIfStale(1_000 + OdometerSpeed.STALE_MS - 1))
        assertTrue(o.decayIfStale(1_000 + OdometerSpeed.STALE_MS))
        assertFalse(o.decayIfStale(20_000))
    }

    @Test
    fun nothingToDecayBeforeAnySpeedIsKnown() {
        val o = OdometerSpeed()
        assertFalse(o.decayIfStale(60_000))
        o.update(1.0, 0)
        assertFalse(o.decayIfStale(60_000))
    }

    @Test
    fun resetForgetsTheBaselineAndTheStreak() {
        val o = OdometerSpeed()
        o.update(0.0, 0)
        o.update(0.02, 1_000)
        o.update(0.035, 2_000)
        o.reset()
        assertNull(o.update(5.0, 3_000))
        assertFalse(o.decayIfStale(60_000))
        assertFalse(o.update(5.0, 4_000)!!.hardDeceleration)
    }
}
