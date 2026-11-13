package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeRecordTest {

    @Test
    fun countsWithinSameDay() {
        val r = ChallengeRecord("2026-11-05", 2, 500).afterPassing("2026-11-05", 300)
        assertEquals(ChallengeRecord("2026-11-05", 3, 500), r)
    }

    @Test
    fun newDayRestartsCountButKeepsLongest() {
        val old = ChallengeRecord("2026-11-04", 7, 900)
        assertEquals(0, old.countOn("2026-11-05"))
        assertEquals(ChallengeRecord("2026-11-05", 1, 900), old.afterPassing("2026-11-05", 200))
    }

    @Test
    fun longestUpdates() {
        assertEquals(1200, ChallengeRecord("", 0, 0).afterPassing("2026-11-05", 1200).longestM)
        assertEquals(7, ChallengeRecord("2026-11-05", 7, 0).countOn("2026-11-05"))
    }
}
