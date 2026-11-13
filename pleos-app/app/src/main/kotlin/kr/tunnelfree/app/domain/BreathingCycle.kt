package kr.tunnelfree.app.domain

object BreathingCycle {
    const val INHALE_SEC = 4
    const val PERIOD_SEC = 10

    const val FIRST_TEXT = "들이쉬기 ${INHALE_SEC}초"

    fun next(tick: Int): Int = (tick + 1) % PERIOD_SEC

    fun text(tick: Int): String =
        if (tick < INHALE_SEC) "들이쉬기 ${INHALE_SEC - tick}초" else "내쉬기 ${PERIOD_SEC - tick}초"
}
