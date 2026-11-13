package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class KoreanTest {

    @Test
    fun picksTheObjectParticleFromTheLastSyllable() {
        assertEquals("도솔터널을", Korean.withObject("도솔터널"))
        assertEquals("대솔밭휴게소를", Korean.withObject("대솔밭휴게소"))
        assertEquals("대전역을", Korean.withObject("대전역"))
    }

    @Test
    fun skipsTrailingBracketsAndReadsDigits() {
        assertEquals("휴게시설 (시연)을", Korean.withObject("휴게시설 (시연)"))
        assertEquals("주차장 2를", Korean.withObject("주차장 2"))
        assertEquals("주차장 3을", Korean.withObject("주차장 3"))
    }
}
