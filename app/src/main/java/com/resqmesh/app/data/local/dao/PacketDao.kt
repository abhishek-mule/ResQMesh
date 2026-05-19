package com.resqmesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resqmesh.app.data.local.entity.EmergencyPacket
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: EmergencyPacket)

    @Query("SELECT * FROM emergency_packets WHERE status IN ('PENDING', 'RELAYED') ORDER BY timestamp DESC")
    suspend fun getPendingPackets(): List<EmergencyPacket>

    @Query("SELECT * FROM emergency_packets WHERE status IN ('PENDING', 'RELAYED') ORDER BY timestamp DESC")
    fun observePendingPackets(): Flow<List<EmergencyPacket>>

    @Query("SELECT * FROM emergency_packets ORDER BY timestamp DESC")
    suspend fun getAllPackets(): List<EmergencyPacket>

    @Query("SELECT * FROM emergency_packets ORDER BY timestamp DESC")
    fun observeAllPackets(): Flow<List<EmergencyPacket>>

    @Query("UPDATE emergency_packets SET status = 'SYNCED' WHERE packetId = :packetId")
    suspend fun markAsSynced(packetId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM emergency_packets WHERE packetId = :packetId)")
    suspend fun isPacketExists(packetId: String): Boolean

    @Query("DELETE FROM emergency_packets WHERE status = 'SYNCED'")
    suspend fun deleteSyncedPackets()

    @Query("SELECT * FROM emergency_packets WHERE type = 'PASSIVE_EMERGENCY' ORDER BY timestamp DESC")
    fun observePassiveEmergencies(): Flow<List<EmergencyPacket>>

    @Query("SELECT COUNT(*) FROM emergency_packets WHERE status IN ('PENDING', 'RELAYED')")
    fun observePendingCount(): Flow<Int>
}