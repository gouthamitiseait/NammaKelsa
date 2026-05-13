package com.nammakelsa.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.nammakelsa.model.Worker
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.*

/**
 * LocationUtils.kt
 *
 * Wraps Android FusedLocationProvider API.
 * PRD Section 4.5: Distance calculated using worker's saved location
 * and customer's current GPS location.
 * PRD Section 4.5: Results sorted by distance (nearest first).
 *
 * Also handles fallback to manual city/area entry per PRD Section 13 (Risks).
 */
object LocationUtils {

    private const val TAG = "LocationUtils"

    /**
     * GPS permission check helper.
     * @return true if ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION is granted
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get the device's current location using FusedLocationProvider.
     * PRD: Uses Android FusedLocationProviderClient.
     *
     * @param context Activity or Application context
     * @return Location? — null if permission denied or location unavailable
     */
    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted")
            return null
        }

        val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { continuation ->
            try {
                val cancellationToken = CancellationTokenSource()

                // Request high-accuracy current location
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationToken.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "Location acquired: ${location.latitude}, ${location.longitude}")
                        continuation.resume(location)
                    } else {
                        Log.w(TAG, "Location is null — using last known location")
                        // Fallback to last known location
                        fusedClient.lastLocation
                            .addOnSuccessListener { lastLocation ->
                                continuation.resume(lastLocation)
                            }
                            .addOnFailureListener {
                                continuation.resume(null)
                            }
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get current location", e)
                    continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    cancellationToken.cancel()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when accessing location", e)
                continuation.resume(null)
            }
        }
    }

    /**
     * Calculate straight-line distance between two GPS coordinates.
     * Uses the Haversine formula for accuracy on Earth's surface.
     *
     * PRD Section 4.5: Distance in km between customer and worker location.
     *
     * @param lat1 Customer latitude
     * @param lon1 Customer longitude
     * @param lat2 Worker latitude
     * @param lon2 Worker longitude
     * @return Distance in kilometres (km)
     */
    fun calculateDistanceKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadiusKm = 6371.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusKm * c
    }

    /**
     * Format a distance in km to a user-friendly string.
     * @param distanceKm Distance in kilometres
     * @return "0.8 km away" or "850 m away"
     */
    fun formatDistance(distanceKm: Double): String {
        return if (distanceKm < 1.0) {
            "${(distanceKm * 1000).toInt()} m away"
        } else {
            "${"%.1f".format(distanceKm)} km away"
        }
    }

    /**
     * Sort a list of workers by distance from the customer's location.
     * PRD Section 4.5: Results sorted by distance (nearest first) by default.
     *
     * Worker latitude/longitude are approximated from their saved location string
     * using a simple city-to-coordinate lookup. In production, store actual
     * lat/lon in Firestore when worker sets up profile.
     *
     * @param workers List of available workers
     * @param customerLat Customer's current latitude
     * @param customerLon Customer's current longitude
     * @return Workers sorted from nearest to farthest
     */
    fun sortWorkersByDistance(
        workers: List<Worker>,
        customerLat: Double,
        customerLon: Double
    ): List<Pair<Worker, Double>> {
        return workers
            .map { worker ->
                val workerCoords = getCityCoordinates(worker.location)
                val distance = if (workerCoords != null) {
                    calculateDistanceKm(
                        customerLat, customerLon,
                        workerCoords.first, workerCoords.second
                    )
                } else {
                    Double.MAX_VALUE // Unknown location → push to end
                }
                Pair(worker, distance)
            }
            .sortedBy { it.second }
    }

    /**
     * Fallback city coordinate lookup.
     * PRD Section 13 (Risks): GPS unavailable on test device — allow manual entry fallback.
     *
     * In production: Store worker's actual lat/lon in Firestore during profile setup
     * using FusedLocationProvider when worker sets their location.
     *
     * @param locationString Worker's saved location string e.g. "Koramangala, Bangalore"
     * @return Pair<Double, Double>? — (latitude, longitude) or null if unknown
     */
    fun getCityCoordinates(locationString: String): Pair<Double, Double>? {
        val lower = locationString.lowercase()
        return when {
            // Bangalore areas
            "koramangala" in lower -> Pair(12.9352, 77.6245)
            "indiranagar" in lower -> Pair(12.9784, 77.6408)
            "whitefield" in lower  -> Pair(12.9698, 77.7500)
            "hsr layout" in lower  -> Pair(12.9116, 77.6474)
            "jayanagar" in lower   -> Pair(12.9308, 77.5838)
            "bangalore" in lower || "bengaluru" in lower -> Pair(12.9716, 77.5946)
            // Chennai areas
            "t nagar" in lower || "tnagar" in lower -> Pair(13.0418, 80.2341)
            "velachery" in lower   -> Pair(12.9815, 80.2180)
            "mylapore" in lower    -> Pair(13.0368, 80.2676)
            "anna nagar" in lower  -> Pair(13.0850, 80.2101)
            "chennai" in lower     -> Pair(13.0827, 80.2707)
            // Other cities
            "mysuru" in lower || "mysore" in lower -> Pair(12.2958, 76.6394)
            "salem" in lower       -> Pair(11.6643, 78.1460)
            "coimbatore" in lower  -> Pair(11.0168, 76.9558)
            "hyderabad" in lower   -> Pair(17.3850, 78.4867)
            "pune" in lower        -> Pair(18.5204, 73.8567)
            "mumbai" in lower      -> Pair(19.0760, 72.8777)
            else -> null
        }
    }

    /**
     * Check if location services are enabled on device.
     * Used to show appropriate UI prompts.
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }
}
