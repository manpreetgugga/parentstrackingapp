package com.parentstracker.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.parentstracker.app.util.Perms
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One-shot location fixes, battery status and street addresses (Android's Geocoder, no API key needed). */
class LocationTracker(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    data class Battery(val level: Int?, val charging: Boolean)

    fun hasLocationPermission() = Perms.hasLocation(context)

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            val token = CancellationTokenSource()
            withTimeoutOrNull(20_000) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).await()
            } ?: fused.lastLocation.await()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't get current location", e)
            null
        }
    }

    fun batteryStatus(): Battery {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return Battery(null, false)
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return Battery(level.takeIf { it in 0..100 }, manager.isCharging)
    }

    suspend fun reverseGeocode(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            withTimeoutOrNull(10_000) {
                val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                cont.resume(null)
                            }
                        })
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                    }
                }
                address?.let(::format)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocoding failed", e)
            null
        }
    }

    private fun format(address: Address): String? {
        address.getAddressLine(0)?.takeIf { it.isNotBlank() }?.let { return it }
        return listOfNotNull(address.subLocality ?: address.thoroughfare, address.locality, address.adminArea)
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "LocationTracker"
    }
}
