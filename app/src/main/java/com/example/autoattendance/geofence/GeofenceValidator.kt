package com.example.autoattendance.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

object GeofenceValidator {

    data class ValidationResult(
        val verdict: ClassroomBoundaryManager.Verdict,
        val score: ClassroomBoundaryManager.PresenceScore,
        val studentLocation: GeoPoint,
        val boundary: ClassroomBoundary
    )

    fun validate(
        context: Context,
        subjectId: String,
        sessionId: String,
        studentUid: String,
        rssi: Int,
        isPacketValid: Boolean,
        isTimestampValid: Boolean,
        onResult: (ValidationResult) -> Unit,
        onNoBoundary: () -> Unit,
        onLocationError: (String) -> Unit = {}
    ) {
        getStudentLocation(context,
            onLocation = { location ->
                val studentPoint = GeoPoint(location.latitude, location.longitude)

                ClassroomBoundaryManager.fetchBoundary(subjectId) { boundary ->
                    if (boundary == null || boundary.corners.size < 3) {
                        onNoBoundary()
                        return@fetchBoundary
                    }

                    val score = ClassroomBoundaryManager.calculatePresenceScore(
                        studentLocation = studentPoint,
                        boundary = boundary,
                        rssi = rssi,
                        isPacketValid = isPacketValid,
                        isTimestampValid = isTimestampValid
                    )

                    ClassroomBoundaryManager.logValidationEvent(
                        sessionId = sessionId,
                        studentUid = studentUid,
                        score = score,
                        studentLocation = studentPoint,
                        rssi = rssi
                    )

                    onResult(
                        ValidationResult(
                            verdict = score.verdict,
                            score = score,
                            studentLocation = studentPoint,
                            boundary = boundary
                        )
                    )
                }
            },
            onError = { errorMsg ->
                onLocationError(errorMsg)
            }
        )
    }

    private fun getStudentLocation(
        context: Context,
        onLocation: (Location) -> Unit,
        onError: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onError("Location permission not granted")
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(context)

        try {
            client.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null && (System.currentTimeMillis() - lastLoc.time < 60000)) {
                    onLocation(lastLoc)
                    return@addOnSuccessListener
                }
                requestFreshLocation(client, onLocation, onError)
            }.addOnFailureListener {
                requestFreshLocation(client, onLocation, onError)
            }
        } catch (e: SecurityException) {
            onError("Location permission denied")
        }
    }

    private fun requestFreshLocation(
        client: FusedLocationProviderClient,
        onLocation: (Location) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMaxUpdates(1)
            .build()

        var delivered = false
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (delivered) return
                delivered = true
                val loc = result.lastLocation
                if (loc != null) {
                    onLocation(loc)
                } else {
                    onError("Could not get GPS location")
                }
                client.removeLocationUpdates(this)
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                if (!delivered) {
                    delivered = true
                    client.removeLocationUpdates(callback)
                    onError("GPS timeout")
                }
            }, 10000L)
        } catch (e: SecurityException) {
            onError("Location permission denied")
        }
    }
}
