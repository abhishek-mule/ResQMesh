package com.resqmesh.app.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.resqmesh.app.data.local.ResQMeshDatabase
import com.resqmesh.app.data.local.entity.EmergencyPacket
import com.resqmesh.app.data.local.entity.NearbyNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CloudSyncService(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    var baseUrl = "http://192.168.1.2:8000"

    companion object {
        private const val TAG = "CloudSyncService"
        private const val SYNC_INTERVAL = 30000L
    }

    fun startPeriodicSync() {
        scope.launch {
            while (true) {
                if (isInternetAvailable()) {
                    syncPendingPackets()
                    syncNearbyNodes()
                }
                delay(SYNC_INTERVAL)
            }
        }
    }

    suspend fun syncNearbyNodes() {
        try {
            val db = ResQMeshDatabase.getDatabase(context)
            val nodes = db.nodeDao().getConnectedNodes()

            if (nodes.isEmpty()) {
                return
            }

            for (node in nodes) {
                val json = JSONObject().apply {
                    put("id", node.deviceId)
                    put("name", node.deviceName)
                    put("type", "relay")
                    put("status", if (node.isConnected) "online" else "offline")
                    put("battery", 50)
                    put("latitude", 0.0)
                    put("longitude", 0.0)
                    put("lastSeen", node.lastSeen)
                }

                val requestBody = json.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("$baseUrl/nodes")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Node synced: ${node.deviceName}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Node sync error", e)
        }
    }

    suspend fun syncPendingPackets() {
        try {
            val db = ResQMeshDatabase.getDatabase(context)
            val pendingPackets = db.packetDao().getPendingPackets()

            if (pendingPackets.isEmpty()) {
                Log.d(TAG, "No pending packets to sync")
                return
            }

            val jsonArray = JSONArray()
            for (packet in pendingPackets) {
                val location = JSONObject().apply {
                    put("lat", packet.latitude)
                    put("lon", packet.longitude)
                    put("accuracy", packet.accuracy)
                }
                val deviceState = JSONObject().apply {
                    put("battery", packet.battery)
                    put("internet", true)
                    put("mesh_enabled", true)
                }
                val json = JSONObject().apply {
                    put("packet_id", packet.packetId)
                    put("source_device", packet.sourceDeviceId)
                    put("type", packet.type)
                    put("risk_level", packet.riskLevel)
                    put("timestamp", packet.timestamp)
                    put("ttl", packet.ttl)
                    put("hop_count", packet.hopCount)
                    put("location", location)
                    put("message", packet.message)
                    put("device_state", deviceState)
                }
                jsonArray.put(json)
            }

            val requestBody = JSONObject().apply {
                put("packets", jsonArray)
                put("device_id", android.os.Build.ID)
            }.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/sync")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    for (packet in pendingPackets) {
                        db.packetDao().markAsSynced(packet.packetId)
                    }
                    Log.d(TAG, "Synced ${pendingPackets.size} packets")
                } else {
                    Log.e(TAG, "Sync failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
        }
    }

    suspend fun sendEmergency(packet: EmergencyPacket) {
        try {
            val location = JSONObject().apply {
                put("lat", packet.latitude)
                put("lon", packet.longitude)
                put("accuracy", packet.accuracy)
            }
            val deviceState = JSONObject().apply {
                put("battery", packet.battery)
                put("internet", true)
                put("mesh_enabled", true)
            }
            val json = JSONObject().apply {
                put("packet_id", packet.packetId)
                put("source_device", packet.sourceDeviceId)
                put("type", packet.type)
                put("risk_level", packet.riskLevel)
                put("timestamp", packet.timestamp)
                put("ttl", packet.ttl)
                put("hop_count", packet.hopCount)
                put("location", location)
                put("message", packet.message)
                put("device_state", deviceState)
            }

            val requestBody = json.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url("$baseUrl/emergency")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(TAG, "Emergency sent: ${packet.packetId}")
                } else {
                    Log.e(TAG, "Emergency send failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Emergency send error", e)
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
