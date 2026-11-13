package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BreathingCycleTest {

    @Test
    fun tenSecondCycleTexts() {
        var tick = 0
        val texts = mutableListOf(BreathingCycle.FIRST_TEXT)
        repeat(10) {
            tick = BreathingCycle.next(tick)
            texts.add(BreathingCycle.text(tick))
        }
        assertEquals(
            listOf(
                "들이쉬기 4초", "들이쉬기 3초", "들이쉬기 2초", "들이쉬기 1초",
                "내쉬기 6초", "내쉬기 5초", "내쉬기 4초", "내쉬기 3초", "내쉬기 2초", "내쉬기 1초",
                "들이쉬기 4초",
            ),
            texts,
        )
    }

    @Test
    fun firstTextMatchesTickZero() {
        assertEquals(BreathingCycle.text(0), BreathingCycle.FIRST_TEXT)
        assertEquals(0, BreathingCycle.next(9))
    }
}
