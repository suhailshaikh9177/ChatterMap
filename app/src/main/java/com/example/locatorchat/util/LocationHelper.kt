package com.example.locatorchat.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

object LocationHelper {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationCallback = object : LocationCallback() {}

    fun initialize(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }

    // NEW FUNCTION: Gets the current location once.
    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        context: Context,
        onSuccess: (location: Location) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    onSuccess(location)
                } else {
                    onFailure(Exception("Failed to get location. Location is null."))
                }
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context) {
        Log.d("LocationDebug", "startLocationUpdates called.")
        stopLocationUpdates()

        val locationRequest = LocationRequest.create().apply {
            interval = 60_000
            fastestInterval = 30_000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                Log.d("LocationDebug", "onLocationResult: Got location -> Lat: ${location.latitude}, Lon: ${location.longitude}")

                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                val db = FirebaseFirestore.getInstance()

                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val address = addresses?.firstOrNull()

                    if (address != null) {
                        val locationData = hashMapOf<String, Any>(
                            "city" to (address.locality ?: ""),
                            "state" to (address.adminArea ?: ""),
                            "country" to (address.countryName ?: ""),
                            "latitude" to location.latitude,
                            "longitude" to location.longitude
                        )
                        db.collection("users").document(uid).update(locationData)
                            .addOnSuccessListener { Log.d("LocationDebug", "Firestore update SUCCESS for user $uid") }
                            .addOnFailureListener { e -> Log.d("LocationDebug", "Firestore update FAILED for user $uid: ${e.message}") }
                    }
                } catch (e: Exception) {
                    Log.e("LocationDebug", "An error occurred in location callback: ${e.message}")
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    fun stopLocationUpdates() {
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("LocationDebug", "stopLocationUpdates called.")
        }
    }
}