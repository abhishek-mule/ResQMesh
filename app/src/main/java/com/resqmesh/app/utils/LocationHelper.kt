package com.resqmesh.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): LocationResult {
        if (ActivityCompat.checkSelfPermission(
                context, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return LocationResult.Error("Location Permission Not Granted")
        }

        return try {
            suspendCancellableCoroutine { continuation ->
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(
                            LocationResult.Success(
                                lat = location.latitude,
                                lon = location.longitude,
                                accuracy = location.accuracy
                            )
                        )
                    } else {
                        continuation.resume(LocationResult.Error("Location not available"))
                    }
                }.addOnFailureListener {
                    continuation.resume(LocationResult.Error(it.message ?: "Failed to get location"))
                }
            }
        } catch (e: Exception) {
            LocationResult.Error(e.message ?: "Unknown error")
        }
    }
}

sealed class LocationResult {
    data class Success(val lat: Double, val lon: Double, val accuracy: Float) : LocationResult()
    data class Error(val message: String) : LocationResult()
}