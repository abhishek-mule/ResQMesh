package com.resqmesh.app.services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.resqmesh.app.data.local.ResQMeshDatabase
import com.resqmesh.app.data.local.entity.EmergencyPacket
import com.resqmesh.app.data.local.entity.NearbyNode
import com.resqmesh.app.data.local.entity.RelayLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.UUID

class BleRelayManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private var gattServer: BluetoothGattServer? = null
    private val connectedGatts = mutableMapOf<String, BluetoothGatt>()

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _nearbyNodes = MutableStateFlow<List<NearbyNode>>(emptyList())
    val nearbyNodes: StateFlow<List<NearbyNode>> = _nearbyNodes.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val seenPackets = mutableSetOf<String>()

    var onPacketReceived: ((EmergencyPacket) -> Unit)? = null

    companion object {
        private const val TAG = "BleRelayManager"

        val RESQ_MESH_SERVICE_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
        val PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A5E-0000-1000-8000-00805F9B34FB")
        val DEVICE_ID_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002A5D-0000-1000-8000-00805F9B34FB")

        private const val STALE_NODE_TIMEOUT = 60000L
    }

    private val deviceId: String = "DEVICE_${android.os.Build.MODEL.replace(" ", "_")}_${android.os.Build.ID}"

    fun startAdvertising() {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "Bluetooth permissions not granted")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(RESQ_MESH_SERVICE_UUID))
            .build()

        bluetoothAdapter?.bluetoothLeAdvertiser?.startAdvertising(settings, data, object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                _isAdvertising.value = true
                Log.d(TAG, "BLE Advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "BLE Advertising failed: $errorCode")
            }
        })
    }

    fun stopAdvertising() {
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(object : AdvertiseCallback() {})
        _isAdvertising.value = false
        Log.d(TAG, "BLE Advertising stopped")
    }

    fun startScanning() {
        if (!checkBluetoothPermissions()) {
            Log.e(TAG, "Bluetooth permissions not granted")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(RESQ_MESH_SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(filters, settings, scanCallback)
        _isScanning.value = true
        Log.d(TAG, "BLE Scanning started")
    }

    fun stopScanning() {
        bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
        Log.d(TAG, "BLE Scanning stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi

            if (result.scanRecord?.serviceUuids?.contains(ParcelUuid(RESQ_MESH_SERVICE_UUID)) == true) {
                val nodeId = device.address

                scope.launch {
                    val db = ResQMeshDatabase.getDatabase(context)
                    val node = NearbyNode(
                        deviceId = nodeId,
                        deviceName = device.name ?: "Unknown",
                        rssi = rssi,
                        lastSeen = System.currentTimeMillis()
                    )
                    db.nodeDao().insertNode(node)

                    val currentNodes = _nearbyNodes.value.toMutableList()
                    val existingIndex = currentNodes.indexOfFirst { it.deviceId == nodeId }
                    if (existingIndex >= 0) {
                        currentNodes[existingIndex] = node
                    } else {
                        currentNodes.add(node)
                    }
                    _nearbyNodes.value = currentNodes

                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        if (connectedGatts.containsKey(device.address)) return

        val gatt = device.connectGatt(context, false, gattCallback)
        connectedGatts[device.address] = gatt
        Log.d(TAG, "Connecting to: ${device.address}")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceId = gatt.device.address

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to: $deviceId")
                    scope.launch {
                        ResQMeshDatabase.getDatabase(context).nodeDao()
                            .updateConnectionStatus(deviceId, true)
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from: $deviceId")
                    connectedGatts.remove(deviceId)
                    scope.launch {
                        ResQMeshDatabase.getDatabase(context).nodeDao()
                            .updateConnectionStatus(deviceId, false)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(RESQ_MESH_SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(PACKET_CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        gatt.readCharacteristic(characteristic)
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val packetJson = String(value, Charset.forName("UTF-8"))
                Log.d(TAG, "Received packet: $packetJson")
            }
        }
    }

    fun startGattServer() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(RESQ_MESH_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val deviceIdChar = BluetoothGattCharacteristic(
            DEVICE_ID_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        deviceIdChar.setValue(deviceId)

        val packetChar = BluetoothGattCharacteristic(
            PACKET_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        service.addCharacteristic(deviceIdChar)
        service.addCharacteristic(packetChar)

        gattServer?.addService(service)
        Log.d(TAG, "GATT Server started")
    }

    fun stopGattServer() {
        gattServer?.close()
        gattServer = null
        Log.d(TAG, "GATT Server stopped")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            when (characteristic.uuid) {
                DEVICE_ID_CHARACTERISTIC_UUID -> {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, deviceId.toByteArray())
                }
                PACKET_CHARACTERISTIC_UUID -> {
                    scope.launch {
                        val db = ResQMeshDatabase.getDatabase(context)
                        val pendingPackets = db.packetDao().getPendingPackets()
                        if (pendingPackets.isNotEmpty()) {
                            val packet = pendingPackets.first()
                            val json = serializePacket(packet)
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, json.toByteArray())
                        } else {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, ByteArray(0))
                        }
                    }
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid == PACKET_CHARACTERISTIC_UUID) {
                val packetJson = String(value, Charset.forName("UTF-8"))
                Log.d(TAG, "Packet received via GATT: $packetJson")

                scope.launch {
                    val packet = deserializePacket(packetJson)
                    if (packet != null) {
                        relayPacket(packet, device.address)
                    }
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    suspend fun broadcastPacket(packet: EmergencyPacket) {
        if (seenPackets.contains(packet.packetId)) {
            Log.d(TAG, "Packet already seen, skipping: ${packet.packetId}")
            return
        }

        seenPackets.add(packet.packetId)

        val db = ResQMeshDatabase.getDatabase(context)
        db.packetDao().insertPacket(packet)

        val connectedNodes = db.nodeDao().getConnectedNodes()
        val packetJson = serializePacket(packet)

        for (node in connectedNodes) {
            val gatt = connectedGatts[node.deviceId]
            if (gatt != null) {
                val service = gatt.getService(RESQ_MESH_SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(PACKET_CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        characteristic.setValue(packetJson)
                        gatt.writeCharacteristic(characteristic)

                        val relayLog = RelayLog(
                            packetId = packet.packetId,
                            fromNode = deviceId,
                            toNode = node.deviceId,
                            action = "RELAY",
                            success = true
                        )
                        db.relayLogDao().insertRelayLog(relayLog)

                        Log.d(TAG, "Packet relayed to: ${node.deviceId}")
                    }
                }
            }
        }
    }

    private suspend fun relayPacket(packet: EmergencyPacket, fromNode: String) {
        if (seenPackets.contains(packet.packetId)) {
            Log.d(TAG, "Duplicate packet dropped: ${packet.packetId}")
            return
        }

        seenPackets.add(packet.packetId)

        val db = ResQMeshDatabase.getDatabase(context)

        val relayedPacket = packet.copy(
            hopCount = packet.hopCount + 1,
            ttl = packet.ttl - 1,
            prevHop = deviceId
        )

        if (relayedPacket.ttl <= 0) {
            Log.d(TAG, "Packet TTL expired: ${packet.packetId}")
            return
        }

        db.packetDao().insertPacket(relayedPacket)

        val relayLog = RelayLog(
            packetId = packet.packetId,
            fromNode = fromNode,
            toNode = deviceId,
            action = "RECEIVED",
            success = true
        )
        db.relayLogDao().insertRelayLog(relayLog)

        onPacketReceived?.invoke(relayedPacket)

        val nextHopNodes = db.nodeDao().getConnectedNodes().filter { it.deviceId != fromNode }
        val packetJson = serializePacket(relayedPacket)

        for (node in nextHopNodes) {
            val gatt = connectedGatts[node.deviceId]
            if (gatt != null) {
                val service = gatt.getService(RESQ_MESH_SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(PACKET_CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        characteristic.setValue(packetJson)
                        gatt.writeCharacteristic(characteristic)

                        val outLog = RelayLog(
                            packetId = packet.packetId,
                            fromNode = deviceId,
                            toNode = node.deviceId,
                            action = "FORWARDED",
                            success = true
                        )
                        db.relayLogDao().insertRelayLog(outLog)
                    }
                }
            }
        }
    }

    fun cleanupStaleNodes() {
        scope.launch {
            val db = ResQMeshDatabase.getDatabase(context)
            val cutoffTime = System.currentTimeMillis() - STALE_NODE_TIMEOUT
            db.nodeDao().deleteStaleNodes(cutoffTime)
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    }

    private fun serializePacket(packet: EmergencyPacket): String {
        return "{\"packet_id\":\"${packet.packetId}\"," +
                "\"source_device\":\"${packet.sourceDeviceId}\"," +
                "\"type\":\"${packet.type}\"," +
                "\"risk_level\":\"${packet.riskLevel}\"," +
                "\"timestamp\":${packet.timestamp}," +
                "\"ttl\":${packet.ttl}," +
                "\"hop_count\":${packet.hopCount}," +
                "\"latitude\":${packet.latitude}," +
                "\"longitude\":${packet.longitude}," +
                "\"accuracy\":${packet.accuracy}," +
                "\"message\":\"${packet.message}\"," +
                "\"battery\":${packet.battery}," +
                "\"status\":\"${packet.status}\"}"
    }

    private fun deserializePacket(json: String): EmergencyPacket? {
        return try {
            val obj = org.json.JSONObject(json)
            EmergencyPacket(
                packetId = obj.optString("packet_id", java.util.UUID.randomUUID().toString()),
                sourceDeviceId = obj.optString("source_device", "UNKNOWN"),
                type = obj.optString("type", "PASSIVE_EMERGENCY"),
                riskLevel = obj.optString("risk_level", "MEDIUM"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                ttl = obj.optInt("ttl", 8),
                hopCount = obj.optInt("hop_count", 0),
                latitude = obj.optDouble("latitude", 0.0),
                longitude = obj.optDouble("longitude", 0.0),
                accuracy = obj.optDouble("accuracy", 0.0).toFloat(),
                message = obj.optString("message", ""),
                battery = obj.optInt("battery", 0),
                status = obj.optString("status", "RELAYED")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize packet", e)
            null
        }
    }

    fun shutdown() {
        stopAdvertising()
        stopScanning()
        stopGattServer()
        connectedGatts.values.forEach { it.close() }
        connectedGatts.clear()
    }
}
