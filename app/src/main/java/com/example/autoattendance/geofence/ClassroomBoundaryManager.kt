package com.example.autoattendance.geofence

import com.google.firebase.database.FirebaseDatabase
import kotlin.math.*

/**
 * ClassroomBoundaryManager
 *
 * Handles storing, retrieving, and validating GPS-based polygon boundaries.
 * Uses the Ray Casting Algorithm for point-in-polygon detection.
 */
data class GeoPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

data class ClassroomBoundary(
    val subjectId: String = "",
    val classroomName: String = "",
    val corners: List<GeoPoint> = emptyList(),
    val centerLat: Double = 0.0,
    val centerLng: Double = 0.0,
    val radiusMeters: Double = 0.0,   // auto-calculated from corners
    val createdBy: String = "",       // lecturer UID
    val createdAt: Long = 0L,
    val isActive: Boolean = true
)

object ClassroomBoundaryManager {

    private val db = FirebaseDatabase.getInstance().reference

    // SECTION 1: POINT-IN-POLYGON (Ray Casting)
    fun isPointInPolygon(point: GeoPoint, corners: List<GeoPoint>): Boolean {
        if (corners.size < 3) return false

        var crossings = 0
        val n = corners.size

        for (i in 0 until n) {
            val a = corners[i]
            val b = corners[(i + 1) % n]

            if (rayIntersectsEdge(point, a, b)) {
                crossings++
            }
        }

        return crossings % 2 == 1
    }

    private fun rayIntersectsEdge(point: GeoPoint, a: GeoPoint, b: GeoPoint): Boolean {
        val minLat = min(a.latitude, b.latitude)
        val maxLat = max(a.latitude, b.latitude)

        if (point.latitude < minLat || point.latitude >= maxLat) return false

        val intersectLng = a.longitude +
                (point.latitude - a.latitude) *
                (b.longitude - a.longitude) /
                (b.latitude - a.latitude)

        return point.longitude < intersectLng
    }

    // SECTION 2: DISTANCE CALCULATION (Haversine)
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)

        val a = sin(dLat / 2).pow(2) +
                cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    fun calculateCenter(corners: List<GeoPoint>): GeoPoint {
        val avgLat = corners.map { it.latitude }.average()
        val avgLng = corners.map { it.longitude }.average()
        return GeoPoint(avgLat, avgLng)
    }

    fun calculateRadius(center: GeoPoint, corners: List<GeoPoint>): Double {
        if (corners.isEmpty()) return 0.0
        return corners.maxOf { distanceMeters(center, it) }
    }

    // SECTION 3: CONFIDENCE SCORING
    data class PresenceScore(
        val totalScore: Int,
        val gpsScore: Int,
        val rssiScore: Int,
        val packetScore: Int,
        val verdict: Verdict,
        val distanceFromBoundary: Double,
        val details: String
    )

    enum class Verdict {
        PRESENT,   // score >= 70
        SUSPECT,   // score 40-69
        REJECTED   // score < 40
    }

    fun calculatePresenceScore(
        studentLocation: GeoPoint,
        boundary: ClassroomBoundary,
        rssi: Int,
        isPacketValid: Boolean,
        isTimestampValid: Boolean
    ): PresenceScore {

        val center = GeoPoint(boundary.centerLat, boundary.centerLng)
        val distFromCenter = distanceMeters(studentLocation, center)
        val isInsidePolygon = isPointInPolygon(studentLocation, boundary.corners)

        val gpsScore = when {
            isInsidePolygon -> 40
            distFromCenter <= boundary.radiusMeters + 5 -> 20
            distFromCenter <= boundary.radiusMeters + 15 -> 10
            else -> 0
        }

        val distFromBoundary = distFromCenter - boundary.radiusMeters

        val rssiScore = when {
            rssi >= -65 -> 40
            rssi >= -70 -> 35
            rssi >= -75 -> 25
            rssi >= -80 -> 15
            rssi >= -85 -> 5
            else -> 0
        }

        val packetScore = if (isPacketValid && isTimestampValid) 20 else if (isPacketValid) 10 else 0

        val total = gpsScore + rssiScore + packetScore

        val verdict = when {
            total >= 70 -> Verdict.PRESENT
            total >= 40 -> Verdict.SUSPECT
            else -> Verdict.REJECTED
        }

        val details = "GPS: $gpsScore/40 | RSSI: $rssiScore/40 | Packet: $packetScore/20 | Total: $total/100"

        return PresenceScore(total, gpsScore, rssiScore, packetScore, verdict, distFromBoundary, details)
    }

    // SECTION 4: FIREBASE STORAGE
    fun saveBoundary(boundary: ClassroomBoundary, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.child("boundaries").child(boundary.subjectId).setValue(boundary)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun fetchBoundary(subjectId: String, onResult: (ClassroomBoundary?) -> Unit) {
        db.child("boundaries").child(subjectId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    onResult(null)
                } else {
                    onResult(snapshot.getValue(ClassroomBoundary::class.java))
                }
            }
            .addOnFailureListener { onResult(null) }
    }

    fun logValidationEvent(sessionId: String, studentUid: String, score: PresenceScore, studentLocation: GeoPoint, rssi: Int) {
        val logEntry = mapOf(
            "studentUid" to studentUid,
            "sessionId" to sessionId,
            "timestamp" to System.currentTimeMillis(),
            "gpsLat" to studentLocation.latitude,
            "gpsLng" to studentLocation.longitude,
            "rssi" to rssi,
            "totalScore" to score.totalScore,
            "gpsScore" to score.gpsScore,
            "rssiScore" to score.rssiScore,
            "packetScore" to score.packetScore,
            "verdict" to score.verdict.name,
            "details" to score.details
        )

        db.child("validationLogs").child(sessionId).child(studentUid).setValue(logEntry)
    }
}
