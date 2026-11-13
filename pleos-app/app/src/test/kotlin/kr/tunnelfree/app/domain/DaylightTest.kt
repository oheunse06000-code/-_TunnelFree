package kr.tunnelfree.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class DaylightTest {

    private fun kst(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).toInstant(ZoneOffset.ofHours(9)).toEpochMilli()

    @Test
    fun `겨울은 저녁 5시 반이면 이미 밤이다`() {
        assertFalse(Daylight.isNight(kst(2026, 12, 21, 16, 50)))
        assertTrue(Daylight.isNight(kst(2026, 12, 21, 17, 50)))
    }

    @Test
    fun `여름은 저녁 7시 반에도 낮이다`() {
        assertFalse(Daylight.isNight(kst(2026, 6, 21, 19, 30)))
        assertTrue(Daylight.isNight(kst(2026, 6, 21, 20, 20)))
    }

    @Test
    fun `해 뜨는 시각도 계절을 따른다`() {
        assertTrue(Daylight.isNight(kst(2026, 12, 21, 7, 10)))
        assertFalse(Daylight.isNight(kst(2026, 12, 21, 8, 10)))
        assertTrue(Daylight.isNight(kst(2026, 6, 21, 4, 50)))
        assertFalse(Daylight.isNight(kst(2026, 6, 21, 5, 40)))
    }
}
