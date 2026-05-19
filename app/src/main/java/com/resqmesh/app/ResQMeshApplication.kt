package com.resqmesh.app

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.resqmesh.app.services.PassiveEmergencyService

class ResQMeshApplication : Application() {

    companion object {
        private const val TAG = "ResQMeshApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ResQMesh Application Started")

        startPassiveEmergencyService()
    }

    private fun startPassiveEmergencyService() {
        val serviceIntent = Intent(this, PassiveEmergencyService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        Log.d(TAG, "PassiveEmergencyService started")
    }
}
