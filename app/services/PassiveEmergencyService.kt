package com.resqmesh.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.resqmesh.app.data.local.ResQMeshDatabase
import com.resqmesh.app.data.local.entity.EmergencyPacket
import com.resqmesh.app.utils.LocationHelper
import com.resqmesh.app.utils.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class PassiveEmergencyService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var lastImpactTime = 0L
    private var isObserving = false
    private val observationWindow = 45000L // 45 seconds

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "resqmesh_emergency_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                // Calculate Signal Magnitude Vector (SMV)
                val smv = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                // Impact Detection (Free fall or hard impact)
                if (smv < 3.0f || smv > 25.0f) {   // Threshold tunable hai
                    handleImpactDetected()
                }
            }
        }
    }

    private fun handleImpactDetected() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastImpactTime < 10000) return // Debounce (10 sec)

        lastImpactTime = currentTime
        isObserving = true

        Log.d("PassiveEmergency", "Impact Detected! Starting Observation Window")

        // Observation Window ke baad check karenge (Handler ya WorkManager se better hai, yahan simple rakh rahe)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isObserving) {
                triggerPassiveEmergency()
            }
        }, observationWindow)
    }

    private fun triggerPassiveEmergency() {
        serviceScope.launch {
            try {
                val db = ResQMeshDatabase.getDatabase(this@PassiveEmergencyService)
                val dao = db.packetDao()

                // Get Location
                val locationHelper = LocationHelper(this@PassiveEmergencyService)
                val locationResult = locationHelper.getCurrentLocation()

                val (lat, lon, acc) = when (locationResult) {
                    is LocationResult.Success -> Triple(locationResult.lat, locationResult.lon, locationResult.accuracy)
                    else -> Triple(0.0, 0.0, 0f)
                }

                val packet = EmergencyPacket(
                    sourceDeviceId = "DEVICE_" + android.os.Build.MODEL.replace(" ", "_"),
                    riskLevel = "HIGH",
                    latitude = lat,
                    longitude = lon,
                    accuracy = acc,
                    message = "Passive Emergency: Possible Fall/Impact Detected",
                    battery = 75,           // TODO: Real battery level later
                    ttl = 8
                )

                dao.insertPacket(packet)
                Log.d("PassiveEmergency", "✅ Emergency Packet Saved with Location -> ${packet.packetId}")
                
            } catch (e: Exception) {
                Log.e("PassiveEmergency", "Error creating packet", e)
            }
        }

        isObserving = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ResQMesh Emergency Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors for passive emergencies"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ResQMesh Active")
            .setContentText("Passive emergency detection running...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert) // Badal sakte ho
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}