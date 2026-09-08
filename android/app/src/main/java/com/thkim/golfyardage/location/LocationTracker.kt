package com.thkim.golfyardage.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.thkim.golfyardage.data.model.GeoPoint

data class GpsFix(
    val point: GeoPoint,
    val accuracyMeters: Float,
    /** GPS 고도(m). 스마트폰 GPS 고도는 오차가 크므로(±10m 내외) 참고용으로만 사용한다. */
    val altitudeMeters: Double?
)

class LocationTracker(context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun start(onFix: (GpsFix) -> Unit) {
        stop()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val altitude = if (location.hasAltitude()) location.altitude else null
                onFix(
                    GpsFix(
                        point = GeoPoint(location.latitude, location.longitude),
                        accuracyMeters = location.accuracy,
                        altitudeMeters = altitude
                    )
                )
            }
        }
        callback = cb
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }
}
