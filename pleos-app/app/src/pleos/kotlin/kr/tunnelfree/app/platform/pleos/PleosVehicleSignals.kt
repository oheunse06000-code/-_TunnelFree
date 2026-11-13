package kr.tunnelfree.app.platform.pleos

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import kr.tunnelfree.app.domain.OdometerSpeed
import kr.tunnelfree.app.platform.VehicleSignalListener
import kr.tunnelfree.app.platform.VehicleSignals
import kr.tunnelfree.app.platform.android.SensorVehicleSignals

class PleosVehicleSignals(context: Context) : VehicleSignals {

    private val sensors = SensorVehicleSignals(context)
    private val handler = Handler(Looper.getMainLooper())
    private val odometer = OdometerSpeed()
    private var vehicle: Vehicle? = null
    private var listener: VehicleSignalListener? = null

    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)

    private var brakeListener: BrakePedalStatusListener? = null
    private var distanceListener: DistanceDrivenListener? = null
    private var steeringListener: SteeringWheelAngleListener? = null
    private var turnSignalListener: TurnSignalStateListener? = null
    private var beltListener: SeatBeltBuckledStateListener? = null
    private var airbagListener: SeatAirbagDeployedListener? = null

    private val active = HashSet<String>()
    private var firstDistanceLogged = false

    private val decay = Runnable {
        if (odometer.decayIfStale(SystemClock.elapsedRealtime())) listener?.onSpeedEstimate(0.0)
    }

    override val hasBrakeSignal: Boolean get() = SIGNAL_BRAKE in active
    override val hasVehicleSignals: Boolean get() = active.isNotEmpty()

    init {
        try {
            val v = Vehicle(context.applicationContext)
            v.initialize()
            vehicle = v
            Log.i(TAG, "Pleos Vehicle initialized")
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos Vehicle unavailable", e)
            vehicle = null
        }
    }

    override fun start(listener: VehicleSignalListener) {
        this.listener = listener
        sensors.start(listener)
        val v = vehicle ?: return

        register(SIGNAL_BRAKE) {
            val l = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(status: Boolean?) {
                    status?.let { onMain { l -> l.onBrakePedal(it) } }
                }
                override fun onFailed(e: Exception) = failed(SIGNAL_BRAKE, e)
            }
            v.getBrake().registerBrakePedalStatus(l)
            brakeListener = l
        }

        register(SIGNAL_ODOMETER) {
            val l = object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(distance: Float?) {
                    distance?.let { handler.post { onDistance(it) } }
                }
                override fun onFailed(e: Exception) = failed(SIGNAL_ODOMETER, e)
            }
            v.getOdometer().registerDistanceDriven(l)
            distanceListener = l
        }

        register(SIGNAL_STEERING) {
            val l = object : SteeringWheelAngleListener {
                override fun onSteeringWheelAngleUpdated(angle: Float?) {
                    angle?.let { onMain { l -> l.onSteeringAngle(it) } }
                }
                override fun onFailed(e: Exception) = failed(SIGNAL_STEERING, e)
            }
            v.getSteeringWheel().registerSteeringWheelAngle(l)
            steeringListener = l
        }

        register(SIGNAL_TURN) {
            val l = object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                    val state = if (value == null || value == TurnSignalValue.OFF) TURN_OFF else value.name
                    onMain { l -> l.onTurnSignal(state) }
                }
                override fun onFailed(e: Exception) = failed(SIGNAL_TURN, e)
            }
            v.getTurnSignal().registerTurnSignalState(l)
            turnSignalListener = l
        }

        register(SIGNAL_BELT) {
            val l = object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) {
                    buckled?.let { onMain { l -> l.onSeatBelt(it) } }
                }
                override fun onFailed(e: Exception) = failed(SIGNAL_BELT, e)
            }
            v.getSeat().registerSeatBeltBuckledState(driverSeat, l)
            beltListener = l
        }

        register(SIGNAL_AIRBAG) {
            val l = object : SeatAirbagDeployedListener {
                override fun onSeatAirbagDeployUpdated(deployed: Boolean?) {
                    if (deployed == true) onMain { l -> l.onAirbagDeployed() }
                }
                override fun onFailed(e: Exception) = failed(SIGNAL_AIRBAG, e)
            }
            v.getSafety().registerSeatAirbagDeployed(driverSeat, l)
            airbagListener = l
        }
    }

    private fun onMain(block: (VehicleSignalListener) -> Unit) {
        handler.post { listener?.let(block) }
    }

    private inline fun register(name: String, block: () -> Unit) {
        active.add(name)
        try {
            block()
            Log.i(TAG, "registered $name")
        } catch (e: Throwable) {
            active.remove(name)
            Log.w(TAG, "register $name failed", e)
        }
    }

    private fun failed(name: String, e: Exception) {
        Log.w(TAG, "$name listener failed", e)
        handler.post { active.remove(name) }
    }

    private fun onDistance(distanceKm: Float) {
        if (!firstDistanceLogged) {
            firstDistanceLogged = true
            Log.i(TAG, "odometer first value $distanceKm")
        }
        val sample = odometer.update(distanceKm.toDouble(), SystemClock.elapsedRealtime()) ?: return
        listener?.onSpeedEstimate(sample.speedKmh)
        if (sample.hardDeceleration) {
            listener?.onHardDeceleration(sample.decelerationMps2 ?: SensorVehicleSignals.HARD_DECELERATION_MPS2)
        }
        handler.removeCallbacks(decay)
        handler.postDelayed(decay, OdometerSpeed.STALE_MS + DECAY_SLACK_MS)
    }

    override fun stop() {
        sensors.stop()
        handler.removeCallbacksAndMessages(null)
        odometer.reset()
        val v = vehicle ?: return
        unregister { brakeListener?.let { v.getBrake().unregisterBrakePedalStatus(it) } }
        unregister { distanceListener?.let { v.getOdometer().unregisterDistanceDriven(it) } }
        unregister { steeringListener?.let { v.getSteeringWheel().unregisterSteeringWheelAngle(it) } }
        unregister { turnSignalListener?.let { v.getTurnSignal().unregisterTurnSignalState(it) } }
        unregister { beltListener?.let { v.getSeat().unregisterSeatBeltBuckledState(driverSeat, it) } }
        unregister { airbagListener?.let { v.getSafety().unregisterSeatAirbagDeployed(driverSeat, it) } }
        brakeListener = null
        distanceListener = null
        steeringListener = null
        turnSignalListener = null
        beltListener = null
        airbagListener = null
        active.clear()
    }

    private inline fun unregister(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "unregister failed", e)
        }
    }

    override fun release() {
        stop()
        try {
            vehicle?.release()
        } catch (e: Exception) {
            Log.w(TAG, "release failed", e)
        }
        vehicle = null
        sensors.release()
    }

    private companion object {
        const val TAG = "PleosVehicle"
        const val TURN_OFF = "OFF"
        const val DECAY_SLACK_MS = 50L
        const val SIGNAL_BRAKE = "brake"
        const val SIGNAL_ODOMETER = "odometer"
        const val SIGNAL_STEERING = "steering"
        const val SIGNAL_TURN = "turnSignal"
        const val SIGNAL_BELT = "seatBelt"
        const val SIGNAL_AIRBAG = "airbag"
    }
}
