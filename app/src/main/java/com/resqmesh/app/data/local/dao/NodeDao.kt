package com.resqmesh.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resqmesh.app.data.local.entity.NearbyNode
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: NearbyNode)

    @Update
    suspend fun updateNode(node: NearbyNode)

    @Query("SELECT * FROM nearby_nodes ORDER BY lastSeen DESC")
    fun observeAllNodes(): Flow<List<NearbyNode>>

    @Query("SELECT * FROM nearby_nodes WHERE isConnected = 1 ORDER BY rssi DESC")
    suspend fun getConnectedNodes(): List<NearbyNode>

    @Query("SELECT * FROM nearby_nodes WHERE deviceId = :deviceId")
    suspend fun getNodeById(deviceId: String): NearbyNode?

    @Query("DELETE FROM nearby_nodes WHERE lastSeen < :cutoffTime")
    suspend fun deleteStaleNodes(cutoffTime: Long)

    @Query("SELECT COUNT(*) FROM nearby_nodes WHERE lastSeen > :cutoffTime")
    fun observeActiveNodeCount(cutoffTime: Long = System.currentTimeMillis() - 60000): Flow<Int>

    @Query("UPDATE nearby_nodes SET isConnected = :connected WHERE deviceId = :deviceId")
    suspend fun updateConnectionStatus(deviceId: String, connected: Boolean)
}
