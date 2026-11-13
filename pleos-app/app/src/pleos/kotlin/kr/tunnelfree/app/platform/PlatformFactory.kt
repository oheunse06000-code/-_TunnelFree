package kr.tunnelfree.app.platform

import android.content.Context
import kr.tunnelfree.app.platform.android.AndroidLocationSource
import kr.tunnelfree.app.platform.android.AndroidMusicPlayer
import kr.tunnelfree.app.platform.android.AndroidSpeechInput
import kr.tunnelfree.app.platform.android.AndroidSpeechOutput
import kr.tunnelfree.app.platform.pleos.PleosLocationSource
import kr.tunnelfree.app.platform.pleos.PleosNaviGateway
import kr.tunnelfree.app.platform.pleos.PleosSpeechInput
import kr.tunnelfree.app.platform.pleos.PleosSpeechOutput
import kr.tunnelfree.app.platform.pleos.PleosVehicleSignals

object PlatformFactory : PlatformProvider {
    override fun create(context: Context): PlatformServices {
        val app = context.applicationContext
        return PlatformServices(
            speechOutput = PleosSpeechOutput(app, fallback = AndroidSpeechOutput(app)),
            speechInput = PleosSpeechInput(app, fallback = AndroidSpeechInput(app)),
            locationSource = PleosLocationSource(app, fallback = AndroidLocationSource(app)),
            vehicleSignals = PleosVehicleSignals(app),
            navi = PleosNaviGateway(app),
            music = AndroidMusicPlayer(app),
            platformName = "pleos",
        )
    }
}
