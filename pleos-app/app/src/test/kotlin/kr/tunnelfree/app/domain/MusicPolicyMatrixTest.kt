package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPolicyMatrixTest {

    private fun ctx(bits: Int) = MusicContext(
        autoPlayOnTunnel = bits and 1 != 0,
        insideTunnel = bits and 2 != 0,
        exitGraceActive = bits and 4 != 0,
        distress = bits and 8 != 0,
        userRequested = bits and 16 != 0,
        userStoppedThisSession = bits and 32 != 0,
        emergencyActive = bits and 64 != 0,
        voiceActive = bits and 128 != 0,
        naviPromptActive = bits and 256 != 0,
    )

    private fun expected(c: MusicContext): MusicState {
        if (c.emergencyActive) return MusicState.OFF
        val trigger = c.insideTunnel || c.exitGraceActive || c.distress
        val reason = when {
            c.userRequested -> true
            c.userStoppedThisSession -> false
            !c.autoPlayOnTunnel -> false
            else -> trigger
        }
        if (!reason) return MusicState.OFF
        if (c.naviPromptActive || c.voiceActive) return MusicState.DUCKED
        return MusicState.PLAYING
    }

    @Test
    fun `512가지 조합 전부 우선순위 사다리와 같다`() {
        val counts = HashMap<MusicState, Int>()
        for (bits in 0 until 512) {
            val c = ctx(bits)
            val actual = MusicPolicy.decide(c)
            assertEquals("조합 $c", expected(c), actual)
            counts[actual] = (counts[actual] ?: 0) + 1
        }
        assertEquals(39, counts[MusicState.PLAYING])
        assertEquals(117, counts[MusicState.DUCKED])
        assertEquals(356, counts[MusicState.OFF])
    }

    @Test
    fun `응급은 모든 다른 조건을 이긴다`() {
        val all = MusicContext(
            insideTunnel = true, exitGraceActive = true, distress = true, userRequested = true,
            emergencyActive = true, voiceActive = true, naviPromptActive = true,
        )
        assertEquals(MusicState.OFF, MusicPolicy.decide(all))
    }

    @Test
    fun `덕킹은 재생할 이유가 있을 때만 - 이유 없으면 음성 안내 중에도 OFF`() {
        assertEquals(MusicState.OFF, MusicPolicy.decide(MusicContext(voiceActive = true)))
        assertEquals(MusicState.OFF, MusicPolicy.decide(MusicContext(naviPromptActive = true, insideTunnel = true, userStoppedThisSession = true)))
        assertEquals(MusicState.DUCKED, MusicPolicy.decide(MusicContext(userRequested = true, voiceActive = true, naviPromptActive = true)))
    }

    @Test
    fun `사용자 요청은 자동 재생 설정 OFF·사용자 정지 기록과 무관하게 재생`() {
        assertEquals(MusicState.PLAYING, MusicPolicy.decide(MusicContext(autoPlayOnTunnel = false, userRequested = true)))
        assertEquals(MusicState.PLAYING, MusicPolicy.decide(MusicContext(autoPlayOnTunnel = false, userStoppedThisSession = true, userRequested = true)))
    }

    @Test
    fun `사용자 정지는 터널·유예·불안 세 트리거 모두를 막는다`() {
        for (c in listOf(
            MusicContext(insideTunnel = true, userStoppedThisSession = true),
            MusicContext(exitGraceActive = true, userStoppedThisSession = true),
            MusicContext(distress = true, userStoppedThisSession = true),
        )) assertEquals(MusicState.OFF, MusicPolicy.decide(c))
    }

    @Test
    fun `현재 동작 - 자동 재생 설정 OFF 면 불안 상태(호흡 안내)에서도 자동 재생 안 함`() {
        assertEquals(MusicState.OFF, MusicPolicy.decide(MusicContext(autoPlayOnTunnel = false, distress = true)))
        assertEquals(MusicState.OFF, MusicPolicy.decide(MusicContext(autoPlayOnTunnel = false, exitGraceActive = true)))
    }

    @Test
    fun `상수 값`() {
        assertEquals(30_000L, MusicPolicy.EXIT_GRACE_MS)
        assertEquals(0.3f, MusicPolicy.DUCK_VOLUME, 0f)
    }
}
