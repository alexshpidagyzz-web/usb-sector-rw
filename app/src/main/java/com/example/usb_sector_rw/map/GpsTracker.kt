// Файл: app/src/main/java/com/example/usb_sector_rw/map/GpsTracker.kt
package com.example.usb_sector_rw.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*

/**
 * Простой GPS трекер для получения местоположения
 */
class GpsTracker(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null

    private var onLocationUpdateListener: ((Location) -> Unit)? = null

    /**
     * Начать отслеживание местоположения
     */
    @SuppressLint("MissingPermission")
    fun startTracking(
        intervalMs: Long = 5000,
        fastestIntervalMs: Long = 2000,
        priority: Int = LocationRequest.PRIORITY_HIGH_ACCURACY,
        onLocationUpdate: (Location) -> Unit
    ) {
        stopTracking()

        onLocationUpdateListener = onLocationUpdate

        val locationRequest = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(fastestIntervalMs)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    onLocationUpdateListener?.invoke(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback as LocationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * Остановить отслеживание
     */
    fun stopTracking() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
        onLocationUpdateListener = null
    }

    /**
     * Получить последнее известное местоположение
     */
    @SuppressLint("MissingPermission")
    fun getLastLocation(callback: (Location?) -> Unit) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                callback(location)
            }
            .addOnFailureListener { e ->
                callback(null)
            }
    }

    /**
     * Проверить, активно ли отслеживание
     */
    fun isTracking(): Boolean = locationCallback != null

    /**
     * Очистка ресурсов
     */
    fun cleanup() {
        stopTracking()
    }
}