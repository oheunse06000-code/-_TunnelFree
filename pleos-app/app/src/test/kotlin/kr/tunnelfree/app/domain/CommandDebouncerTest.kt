package kr.tunnelfree.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDebouncerTest {

    @Test
    fun suppressesSameTextWithinWindow() {
        val d = CommandDebouncer()
        assertTrue(d.accept("안내 시작", 0))
        assertFalse(d.accept("안내시작", 1_999))
        assertTrue(d.accept("안내 시작", 4_000))
    }

    @Test
    fun windowIsMeasuredFromLastAccepted() {
        val d = CommandDebouncer()
        assertTrue(d.accept("도와줘", 0))
        assertFalse(d.accept("도와줘", 1_500))
        assertTrue(d.accept("도와줘", 2_000))
    }

    @Test
    fun differentTextPassesImmediately() {
        val d = CommandDebouncer()
        assertTrue(d.accept("음악 틀어 줘", 0))
        assertTrue(d.accept("음악 꺼 줘", 10))
        assertTrue(d.accept("음악 틀어 줘", 20))
    }

    @Test
    fun blankIsIgnoredAndDoesNotResetState() {
        val d = CommandDebouncer()
        assertTrue(d.accept("안내 종료", 0))
        assertFalse(d.accept("   ", 100))
        assertFalse(d.accept("", 200))
        assertFalse(d.accept("안내 종료", 300))
    }
}
