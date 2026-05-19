package com.resqmesh.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.resqmesh.app.data.local.dao.NodeDao
import com.resqmesh.app.data.local.dao.PacketDao
import com.resqmesh.app.data.local.dao.RelayLogDao
import com.resqmesh.app.data.local.entity.EmergencyPacket
import com.resqmesh.app.data.local.entity.NearbyNode
import com.resqmesh.app.data.local.entity.RelayLog

@Database(
    entities = [EmergencyPacket::class, NearbyNode::class, RelayLog::class],
    version = 2,
    exportSchema = false
)
abstract class ResQMeshDatabase : RoomDatabase() {

    abstract fun packetDao(): PacketDao
    abstract fun nodeDao(): NodeDao
    abstract fun relayLogDao(): RelayLogDao

    companion object {
        @Volatile
        private var INSTANCE: ResQMeshDatabase? = null

        fun getDatabase(context: Context): ResQMeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ResQMeshDatabase::class.java,
                    "resqmesh_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
