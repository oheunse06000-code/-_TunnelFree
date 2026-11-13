package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicPolicyTest {

    @Test
    fun `터널 진입 시 자동 재생`() {
        assertEquals(MusicState.PLAYING, MusicPolicy.decide(MusicContext(insideTunnel = true)))
    }

    @Test
    fun `자동 재생 꺼져 있으면 터널에서도 재생 안 함`() {
        assertEquals(MusicState.OFF, MusicPolicy.decide(MusicContext(autoPlayOnTunnel = false, insideTunnel = true)))
    }

    @Test
    fun `응급 흐름이 가장 우선 - 어떤 경우에도 정지`() {
        assertEquals(MusicState.OFF, MusicPolicy.decide(MusicContext(insideTunnel = true, userRequested = true, emergencyActive = true)))
    }

    @Test
    fun `음성 안내 중에는 덕킹`() {
        assertEquals(MusicState.DUCKED, MusicPolicy.decide(MusicContext(insideTunnel = true, voiceActive = true)))
        assertEquals(MusicState.DUCKED, MusicPolicy.decide(MusicContext(insideTunnel = true, naviPromptActive = true)))
    }

    @Test
    fun `사용자가 끈 뒤에는 자동 재생하지 않지만 사용자 요청은 켠다`() {
        assertEquals(MusicState.OFF, MusicPolicy.decide(MusicContext(insideTunnel = true, userStoppedThisSession = true)))
        assertEquals(MusicState.PLAYING, MusicPolicy.decide(MusicContext(insideTunnel = true, userStoppedThisSession = true, userRequested = true)))
    }

    @Test
    fun `이탈 유예와 불안 상태도 재생 조건`() {
        assertEquals(MusicState.PLAYING, MusicPolicy.decide(MusicContext(exitGraceActive = true)))
        assertEquals(MusicState.PLAYING, MusicPolicy.decide(MusicContext(distress = true)))
        assertEquals(MusicState.OFF, MusicPolicy.decide(MusicContext()))
    }
}
