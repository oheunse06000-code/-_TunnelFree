package kr.tunnelfree.app.platform

import android.content.Context
import kr.tunnelfree.app.platform.android.AndroidLocationSource
import kr.tunnelfree.app.platform.android.AndroidMusicPlayer
import kr.tunnelfree.app.platform.android.AndroidSpeechInput
import kr.tunnelfree.app.platform.android.AndroidSpeechOutput
import kr.tunnelfree.app.platform.android.NoopNaviGateway
import kr.tunnelfree.app.platform.android.SensorVehicleSignals

object PlatformFactory : PlatformProvider {
    override fun create(context: Context): PlatformServices {
        val app = context.applicationContext
        return PlatformServices(
            speechOutput = AndroidSpeechOutput(app),
            speechInput = AndroidSpeechInput(app),
            locationSource = AndroidLocationSource(app),
            vehicleSignals = SensorVehicleSignals(app),
            navi = NoopNaviGateway(),
            music = AndroidMusicPlayer(app),
            platformName = "standard",
        )
    }
}
