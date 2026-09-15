package com.example.autoattendance

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * AuditLogger
 * Centralized service for security and activity tracking in SecureAttend.
 * All writes are fire-and-forget to ensure zero impact on user experience.
 */
object AuditLogger {
    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "AuditLogger"

    fun log(
        action: String,
        userId: String,
        userRole: String,
        institutionId: String,
        sessionId: String? = null,
        studentId: String? = null,
        subjectId: String? = null,
        reason: String? = null,
        details: Map<String, Any>? = null
    ) {
        val logData = mutableMapOf<String, Any>(
            "timestamp" to Timestamp.now(),
            "action" to action,
            "userId" to userId,
            "userRole" to userRole,
            "institutionId" to institutionId
        )

        // Optional Fields
        sessionId?.let { logData["sessionId"] = it }
        studentId?.let { logData["studentId"] = it }
        subjectId?.let { logData["subjectId"] = it }
        reason?.let { logData["reason"] = it }
        details?.let { logData["details"] = it }

        db.collection("audit_logs").add(logData)
            .addOnSuccessListener {
                Log.d(TAG, "Audit: $action recorded for user $userId")
            }
            .addOnFailureListener { e ->
                // Fire-and-forget: failure must not block the app.
                // We log to Logcat for visibility during development.
                Log.e(TAG, "Failed to write audit log: ${e.message}", e)
                // In production, consider reporting to Crashlytics here.
            }
    }
}
