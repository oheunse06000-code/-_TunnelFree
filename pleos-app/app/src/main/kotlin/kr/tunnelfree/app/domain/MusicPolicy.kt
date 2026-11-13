package kr.tunnelfree.app.domain

enum class MusicState { OFF, PLAYING, DUCKED }

data class MusicContext(
    val autoPlayOnTunnel: Boolean = true,
    val insideTunnel: Boolean = false,
    val exitGraceActive: Boolean = false,
    val distress: Boolean = false,
    val userRequested: Boolean = false,
    val userStoppedThisSession: Boolean = false,
    val emergencyActive: Boolean = false,
    val voiceActive: Boolean = false,
    val naviPromptActive: Boolean = false,
)

object MusicPolicy {
    const val EXIT_GRACE_MS = 30_000L
    const val DUCK_VOLUME = 0.3f

    fun decide(ctx: MusicContext): MusicState {
        if (ctx.emergencyActive) return MusicState.OFF

        val autoWanted = ctx.autoPlayOnTunnel &&
            !ctx.userStoppedThisSession &&
            (ctx.insideTunnel || ctx.exitGraceActive || ctx.distress)
        val wanted = ctx.userRequested || autoWanted
        if (!wanted) return MusicState.OFF

        return if (ctx.voiceActive || ctx.naviPromptActive) MusicState.DUCKED else MusicState.PLAYING
    }
}
