package kr.tunnelfree.app.platform.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kr.tunnelfree.app.platform.VehicleSignalListener
import kr.tunnelfree.app.platform.VehicleSignals
import kotlin.math.sqrt

class SensorVehicleSignals(context: Context) : VehicleSignals {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var sensorListener: SensorEventListener? = null

    override val hasBrakeSignal: Boolean = false
    override val hasVehicleSignals: Boolean = false

    override fun start(listener: VehicleSignalListener) {
        stop()
        val s = sensor ?: return
        val l = object : SensorEventListener {
            private var streak = 0
            private var lastReportAt = 0L

            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x * x + y * y + z * z).toDouble())
                if (magnitude >= HARD_DECELERATION_MPS2) streak++ else streak = 0
                val now = System.currentTimeMillis()
                if (streak >= MIN_SAMPLES && now - lastReportAt > COOLDOWN_MS) {
                    lastReportAt = now
                    streak = 0
                    listener.onHardDeceleration(magnitude)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorListener = l
        manager.registerListener(l, s, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun stop() {
        sensorListener?.let { manager.unregisterListener(it) }
        sensorListener = null
    }

    override fun release() = stop()

    companion object {
        const val HARD_DECELERATION_MPS2 = 4.5
        const val MIN_SAMPLES = 3
        const val COOLDOWN_MS = 15_000L
    }
}
