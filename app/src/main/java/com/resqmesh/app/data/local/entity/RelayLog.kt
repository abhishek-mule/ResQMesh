package com.resqmesh.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "relay_logs")
data class RelayLog(
    @PrimaryKey
    val logId: String = UUID.randomUUID().toString(),

    val packetId: String,

    val fromNode: String,

    val toNode: String,

    val timestamp: Long = System.currentTimeMillis(),

    val action: String,

    val success: Boolean
)
