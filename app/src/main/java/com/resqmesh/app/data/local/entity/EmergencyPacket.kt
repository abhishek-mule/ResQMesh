package com.resqmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "emergency_packets")
data class EmergencyPacket(
    @PrimaryKey
    val packetId: String = UUID.randomUUID().toString(),

    val sourceDeviceId: String,

    val type: String = "PASSIVE_EMERGENCY",

    val riskLevel: String,           // HIGH, MEDIUM, LOW

    val timestamp: Long = System.currentTimeMillis(),

    val ttl: Int = 8,                // Time To Live
    val hopCount: Int = 0,
    val prevHop: String? = null,

    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,

    val message: String,

    val battery: Int,

    val status: String = "PENDING"   // PENDING, RELAYED, SYNCED
)