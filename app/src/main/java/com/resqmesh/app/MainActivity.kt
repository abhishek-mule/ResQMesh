package com.resqmesh.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import com.resqmesh.app.data.local.ResQMeshDatabase
import com.resqmesh.app.data.local.entity.EmergencyPacket
import com.resqmesh.app.data.local.entity.NearbyNode
import com.resqmesh.app.services.BleRelayManager
import com.resqmesh.app.services.DecisionEngine
import com.resqmesh.app.services.EmergencyPacketData
import com.resqmesh.app.services.EmergencyState
import com.resqmesh.app.ui.theme.*
import com.resqmesh.app.utils.LocationHelper
import com.resqmesh.app.utils.LocationResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var decisionEngine: DecisionEngine
    private lateinit var bleRelayManager: BleRelayManager

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.FOREGROUND_SERVICE
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeServices()
        } else {
            Toast.makeText(this, "Permissions required for ResQMesh to function", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        decisionEngine = DecisionEngine(this)
        bleRelayManager = BleRelayManager(this)

        checkPermissions()

        setContent {
            ResQMeshTheme {
                ResQMeshApp()
            }
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            initializeServices()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun initializeServices() {
        decisionEngine.startMonitoring()
        bleRelayManager.startAdvertising()
        bleRelayManager.startScanning()
        bleRelayManager.startGattServer()

        decisionEngine.onEmergencyTriggered = { packetData ->
            handleEmergencyTriggered(packetData)
        }
    }

    private fun handleEmergencyTriggered(packetData: EmergencyPacketData) {
        val scope = CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            try {
                val locationHelper = LocationHelper(this@MainActivity)
                val locationResult = locationHelper.getCurrentLocation()

                val (lat, lon, acc) = when (locationResult) {
                    is LocationResult.Success -> Triple(locationResult.lat, locationResult.lon, locationResult.accuracy)
                    else -> Triple(0.0, 0.0, 0f)
                }

                val packet = EmergencyPacket(
                    sourceDeviceId = bleRelayManager.toString(),
                    riskLevel = packetData.riskLevel,
                    latitude = lat,
                    longitude = lon,
                    accuracy = acc,
                    message = packetData.message,
                    battery = 75,
                    ttl = 8
                )

                bleRelayManager.broadcastPacket(packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        decisionEngine.stopMonitoring()
        bleRelayManager.shutdown()
    }

    @Composable
    fun ResQMeshApp() {
        var emergencyState by remember { mutableStateOf<EmergencyState>(EmergencyState.Normal) }
        var riskScore by remember { mutableStateOf(0f) }
        var nearbyNodes by remember { mutableStateOf<List<NearbyNode>>(emptyList()) }
        var activeEmergencies by remember { mutableStateOf<List<EmergencyPacket>>(emptyList()) }
        var isMeshActive by remember { mutableStateOf(false) }

        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            scope.launch {
                decisionEngine.emergencyState.collectLatest { state ->
                    emergencyState = state
                }
            }
            scope.launch {
                decisionEngine.riskScore.collectLatest { score ->
                    riskScore = score
                }
            }
            scope.launch {
                bleRelayManager.nearbyNodes.collectLatest { nodes ->
                    nearbyNodes = nodes
                }
            }
            scope.launch {
                bleRelayManager.isAdvertising.collectLatest { advertising ->
                    isMeshActive = advertising
                }
            }
            scope.launch {
                val db = ResQMeshDatabase.getDatabase(this@MainActivity)
                db.packetDao().observePassiveEmergencies().collectLatest { packets ->
                    activeEmergencies = packets
                }
            }
        }

        Scaffold(
            topBar = { TopStatusBar(nodeCount = nearbyNodes.size, emergencyCount = activeEmergencies.size, isMeshActive = isMeshActive) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(DarkBackground)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EmergencyButton(
                    emergencyState = emergencyState,
                    riskScore = riskScore,
                    onCancelEmergency = {
                        decisionEngine.cancelObservation()
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                MeshStatusCard(
                    isAdvertising = isMeshActive,
                    isScanning = isMeshActive,
                    nodeCount = nearbyNodes.size
                )

                Spacer(modifier = Modifier.height(16.dp))

                NearbyNodesCard(nodes = nearbyNodes)

                Spacer(modifier = Modifier.height(16.dp))

                if (activeEmergencies.isNotEmpty()) {
                    EmergencyFeedCard(emergencies = activeEmergencies)
                }
            }
        }
    }

    @Composable
    fun TopStatusBar(nodeCount: Int, emergencyCount: Int, isMeshActive: Boolean) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DarkSurface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ResQMesh",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusIndicator(label = "Nodes", value = nodeCount.toString(), color = EmergencyGreen)
                    StatusIndicator(label = "Emergencies", value = emergencyCount.toString(), color = EmergencyRed)
                    StatusIndicator(
                        label = "Mesh",
                        value = if (isMeshActive) "Active" else "Off",
                        color = if (isMeshActive) EmergencyGreen else Color.Gray
                    )
                }
            }
        }
    }

    @Composable
    fun StatusIndicator(label: String, value: String, color: Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, fontSize = 10.sp, color = TextSecondary)
        }
    }

    @Composable
    fun EmergencyButton(
        emergencyState: EmergencyState,
        riskScore: Float,
        onCancelEmergency: () -> Unit
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        val isEmergency = emergencyState is EmergencyState.Emergency
        val isObserving = emergencyState is EmergencyState.Observing

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isEmergency) {
                Text(
                    text = "EMERGENCY ACTIVE",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Risk Score: ${riskScore.toInt()}%",
                    fontSize = 16.sp,
                    color = EmergencyOrange,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            } else if (isObserving) {
                Text(
                    text = "Possible Emergency Detected",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyOrange,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Button(
                    onClick = onCancelEmergency,
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyGreen),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Cancel (30s)")
                }
            }

            Button(
                onClick = {
                    if (!isEmergency) {
                        decisionEngine.resetEmergency()
                    }
                },
                modifier = Modifier
                    .size(160.dp)
                    .scale(if (isEmergency) scale else 1f),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isEmergency -> EmergencyRed
                        isObserving -> EmergencyOrange
                        else -> DarkSurfaceVariant
                    }
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Emergency",
                    modifier = Modifier.size(64.dp),
                    tint = Color.White
                )
            }

            Text(
                text = when {
                    isEmergency -> "Emergency Broadcast Active"
                    isObserving -> "Observing..."
                    else -> "Tap for Emergency"
                },
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    @Composable
    fun MeshStatusCard(isAdvertising: Boolean, isScanning: Boolean, nodeCount: Int) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mesh Network Status",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatusRow(icon = Icons.Default.Wifi, label = "Advertising", active = isAdvertising)
                    StatusRow(icon = Icons.Default.Search, label = "Scanning", active = isScanning)
                    StatusRow(icon = Icons.Default.Devices, label = "Nodes", active = nodeCount > 0, value = nodeCount.toString())
                }
            }
        }
    }

    @Composable
    fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, value: String? = null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) EmergencyGreen else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(text = label, fontSize = 12.sp, color = TextSecondary)
                Text(
                    text = value ?: if (active) "Yes" else "No",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (active) EmergencyGreen else TextSecondary
                )
            }
        }
    }

    @Composable
    fun NearbyNodesCard(nodes: List<NearbyNode>) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Nearby Nodes (${nodes.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (nodes.isEmpty()) {
                    Text(
                        text = "No nodes discovered yet",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    nodes.take(5).forEach { node ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = node.deviceName, fontSize = 14.sp, color = TextPrimary)
                            Text(
                                text = "RSSI: ${node.rssi}",
                                fontSize = 12.sp,
                                color = if (node.rssi > -70) EmergencyGreen else EmergencyOrange
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun EmergencyFeedCard(emergencies: List<EmergencyPacket>) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Emergency Feed",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn {
                    items(emergencies.take(5)) { packet ->
                        EmergencyFeedItem(packet = packet)
                    }
                }
            }
        }
    }

    @Composable
    fun EmergencyFeedItem(packet: EmergencyPacket) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "[${packet.riskLevel}]",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (packet.riskLevel) {
                        "CRITICAL" -> EmergencyRed
                        "HIGH" -> EmergencyOrange
                        else -> Color.Yellow
                    }
                )
                Text(
                    text = packet.message.take(40) + "...",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Hops: ${packet.hopCount}",
                    fontSize = 12.sp,
                    color = EmergencyBlue
                )
                Text(
                    text = "TTL: ${packet.ttl}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
