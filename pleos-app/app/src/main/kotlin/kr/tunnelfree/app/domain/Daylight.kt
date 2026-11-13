package kr.tunnelfree.app.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Daylight {
    const val SUN_SET_ALTITUDE_DEG = -0.833
    const val DAEJEON_LAT = 36.35
    const val DAEJEON_LNG = 127.38

    private const val MS_PER_DAY = 86_400_000.0
    private const val UNIX_EPOCH_J2000_DAYS = -10_957.5

    fun sunAltitudeDeg(lat: Double, lng: Double, epochMs: Long): Double {
        val d = epochMs / MS_PER_DAY + UNIX_EPOCH_J2000_DAYS
        val g = Math.toRadians(357.529 + 0.98560028 * d)
        val q = 280.459 + 0.98564736 * d
        val eclipticLng = Math.toRadians(q + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val obliquity = Math.toRadians(23.439 - 0.00000036 * d)
        val rightAscension = atan2(cos(obliquity) * sin(eclipticLng), cos(eclipticLng))
        val declination = asin(sin(obliquity) * sin(eclipticLng))
        val siderealDeg = 280.46061837 + 360.98564736629 * d
        val hourAngle = Math.toRadians(siderealDeg + lng) - rightAscension
        val latRad = Math.toRadians(lat)
        val sinAltitude = sin(latRad) * sin(declination) + cos(latRad) * cos(declination) * cos(hourAngle)
        return Math.toDegrees(asin(sinAltitude))
    }

    fun isNight(epochMs: Long, lat: Double = DAEJEON_LAT, lng: Double = DAEJEON_LNG): Boolean =
        sunAltitudeDeg(lat, lng, epochMs) < SUN_SET_ALTITUDE_DEG
}
