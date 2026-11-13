package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FormatLocaleTest {

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val old = Locale.getDefault()
        Locale.setDefault(locale)
        try { return block() } finally { Locale.setDefault(old) }
    }

    @Test
    fun distancesUseDotInAnyLocale() = withLocale(Locale.GERMANY) {
        assertEquals("1.5 km", formatDistance(1500.0))
        assertEquals("1.5킬로미터", spokenDistance(1500.0))
        assertEquals("300 m", formatDistance(304.0))
        assertEquals("300미터", spokenDistance(296.0))
    }

    @Test
    fun incidentCauseUsesDotInAnyLocale() = withLocale(Locale.GERMANY) {
        val a = IncidentClassifier.assess(VehicleContext(decelerationMps2 = 6.0, hasVehicleSignals = true, speedKmh = 40.0))
        assertTrue(a.cause, a.cause.contains("6.0"))
    }
}
