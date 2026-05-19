package com.resqmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nearby_nodes")
data class NearbyNode(
    @PrimaryKey
    val deviceId: String,

    val deviceName: String,

    val rssi: Int,

    val lastSeen: Long = System.currentTimeMillis(),

    val isConnected: Boolean = false,

    val isGateway: Boolean = false
)
