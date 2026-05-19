package com.resqmesh.app.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DecisionEngine(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var lastImpactTime = 0L
    private var isObserving = false
    private var observationStartTime = 0L

    private val _emergencyState = MutableStateFlow<EmergencyState>(EmergencyState.Normal)
    val emergencyState: StateFlow<EmergencyState> = _emergencyState.asStateFlow()

    private val _riskScore = MutableStateFlow(0f)
    val riskScore: StateFlow<Float> = _riskScore.asStateFlow()

    var onEmergencyTriggered: ((EmergencyPacketData) -> Unit)? = null

    private val observationWindow = 45000L
    private val impactDebounce = 10000L
    private val riskThreshold = 60f

    companion object {
        private const val TAG = "DecisionEngine"
        const val IMPACT_WEIGHT = 40f
        const val INACTIVITY_WEIGHT = 0.5f
        const val NO_RESPONSE_WEIGHT = 0.3f
    }

    fun startMonitoring() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Accelerometer monitoring started")
        }
    }

    fun stopMonitoring() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Accelerometer monitoring stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                val smv = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                if (smv < 3.0f || smv > 25.0f) {
                    handleImpactDetected(smv)
                }

                if (isObserving) {
                    updateRiskScoreForInactivity()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun handleImpactDetected(impactForce: Float) {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastImpactTime < impactDebounce) return

        lastImpactTime = currentTime
        isObserving = true
        observationStartTime = currentTime

        _emergencyState.value = EmergencyState.Observing
        _riskScore.value = IMPACT_WEIGHT

        Log.d(TAG, "Impact detected! Force: $impactForce, Observation started")

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isObserving) {
                evaluateEmergency()
            }
        }, observationWindow)
    }

    private fun updateRiskScoreForInactivity() {
        val inactivityDuration = System.currentTimeMillis() - observationStartTime
        val inactivityScore = (inactivityDuration / 1000f) * INACTIVITY_WEIGHT
        val totalScore = IMPACT_WEIGHT + inactivityScore

        _riskScore.value = totalScore.coerceAtMost(100f)

        if (totalScore > riskThreshold) {
            _emergencyState.value = EmergencyState.HighRisk
        }
    }

    private fun evaluateEmergency() {
        val currentScore = _riskScore.value

        if (currentScore > riskThreshold) {
            val riskLevel = when {
                currentScore > 80f -> "CRITICAL"
                currentScore > 60f -> "HIGH"
                else -> "MEDIUM"
            }

            _emergencyState.value = EmergencyState.Emergency(riskLevel)

            val packetData = EmergencyPacketData(
                riskLevel = riskLevel,
                message = "Passive Emergency: Impact + Inactivity Detected",
                riskScore = currentScore
            )

            onEmergencyTriggered?.invoke(packetData)
            Log.d(TAG, "EMERGENCY TRIGGERED: $riskLevel (Score: $currentScore)")
        } else {
            _emergencyState.value = EmergencyState.Normal
            Log.d(TAG, "Observation ended. No emergency. Score: $currentScore")
        }

        isObserving = false
    }

    fun resetEmergency() {
        _emergencyState.value = EmergencyState.Normal
        _riskScore.value = 0f
        isObserving = false
        Log.d(TAG, "Emergency reset")
    }

    fun cancelObservation() {
        isObserving = false
        _emergencyState.value = EmergencyState.Normal
        _riskScore.value = 0f
        Log.d(TAG, "Observation cancelled by user")
    }
}

sealed class EmergencyState {
    object Normal : EmergencyState()
    object Observing : EmergencyState()
    object HighRisk : EmergencyState()
    data class Emergency(val riskLevel: String) : EmergencyState()
}

data class EmergencyPacketData(
    val riskLevel: String,
    val message: String,
    val riskScore: Float
)
