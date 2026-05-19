package com.resqmesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resqmesh.app.data.local.entity.RelayLog
import kotlinx.coroutines.flow.Flow

@Dao
interface RelayLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelayLog(log: RelayLog)

    @Query("SELECT * FROM relay_logs WHERE packetId = :packetId ORDER BY timestamp ASC")
    suspend fun getRelayChainForPacket(packetId: String): List<RelayLog>

    @Query("SELECT * FROM relay_logs ORDER BY timestamp DESC LIMIT 50")
    fun observeRecentRelayLogs(): Flow<List<RelayLog>>

    @Query("DELETE FROM relay_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOldLogs(cutoffTime: Long)
}
