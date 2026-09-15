package com.example.autoattendance.domain.usecase

import android.util.Log
import com.example.autoattendance.models.GamificationProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.util.Calendar

object GamificationManager {
    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "GamificationManager"

    suspend fun onAttendanceMarked(
        uid: String, 
        institutionId: String, 
        department: String, 
        semester: String,
        sessionId: String
    ) {
        try {
            val docRef = db.collection("gamification").document(uid)
            val snapshot = docRef.get().await()
            
            val currentProfile = if (snapshot.exists()) {
                snapshot.toObject(GamificationProfile::class.java)!!
            } else {
                GamificationProfile(
                    uid = uid, 
                    institutionId = institutionId, 
                    department = department, 
                    semester = semester
                )
            }

            // 1. Calculate Streak
            val newStreak = calculateNewStreak(uid, currentProfile, institutionId, department, semester, sessionId)
            
            // 2. Update Points
            val newPoints = currentProfile.totalPoints + 10
            
            // 3. Update Badge
            val newBadge = calculateBadge(newPoints)
            
            // 4. Update Highest Streak
            val newHighestStreak = if (newStreak > currentProfile.highestStreak) newStreak else currentProfile.highestStreak
            
            // 5. Check Achievements
            val newAchievements = checkAchievements(currentProfile, newStreak, newPoints)

            val updates = mutableMapOf<String, Any>(
                "totalPoints" to newPoints,
                "currentStreak" to newStreak,
                "highestStreak" to newHighestStreak,
                "currentBadge" to newBadge,
                "achievementBadges" to newAchievements,
                "lastAttendanceDate" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis(),
                "institutionId" to institutionId,
                "department" to department,
                "semester" to semester
            )

            docRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            Log.d(TAG, "Gamification updated for $uid: Points=$newPoints, Streak=$newStreak")
            
            // 6. Recalculate Rank (Background task ideally, but we'll do a quick check here)
            updateRankStatus(uid, institutionId, department, semester)

        } catch (e: Exception) {
            Log.e(TAG, "Error updating gamification", e)
        }
    }

    private suspend fun calculateNewStreak(
        uid: String, 
        profile: GamificationProfile,
        instId: String,
        dept: String,
        sem: String,
        currentSessionId: String
    ): Int {
        try {
            // Fetch the current session details
            val sessionDoc = db.collection("attendance_sessions").document(currentSessionId).get().await()
            val currentSessionTime = sessionDoc.getTimestamp("startTime")?.toDate()?.time ?: System.currentTimeMillis()
            
            // If this is the student's first ever attendance, start streak at 1
            if (profile.lastAttendanceDate == 0L) {
                Log.d(TAG, "First attendance for user $uid. Starting streak at 1.")
                return 1
            }

            // Find if there were any sessions between last attendance and now
            val missedSessionsQuery = db.collection("attendance_sessions")
                .whereEqualTo("institutionId", instId)
                .whereEqualTo("department", dept)
                .whereEqualTo("semester", sem)
                .whereGreaterThan("startTime", com.google.firebase.Timestamp(java.util.Date(profile.lastAttendanceDate + 1000))) // +1s to skip the last one
                .whereLessThan("startTime", com.google.firebase.Timestamp(java.util.Date(currentSessionTime - 1000))) // -1s to skip current
                .get().await()

            // If missedSessionsQuery is not empty, streak is broken
            return if (missedSessionsQuery.isEmpty) {
                val s = profile.currentStreak + 1
                Log.d(TAG, "No sessions missed since ${profile.lastAttendanceDate}. Streak: $s")
                s
            } else {
                Log.d(TAG, "User missed ${missedSessionsQuery.size()} sessions. Streak reset to 1.")
                1 // Start new streak
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating streak for $uid", e)
            return profile.currentStreak + 1 // Fallback: don't break streak on query error
        }
    }

    private fun calculateBadge(points: Long): String {
        return when {
            points >= 6000 -> "Legend"
            points >= 3001 -> "Elite Scholar"
            points >= 1501 -> "Streak Master"
            points >= 501 -> "Pathfinder"
            else -> "Rookie"
        }
    }

    private fun checkAchievements(profile: GamificationProfile, streak: Int, points: Long): List<String> {
        val badges = profile.achievementBadges.toMutableList()
        
        if (streak >= 7 && !badges.contains("Perfect Week")) badges.add("Perfect Week")
        if (streak >= 30 && !badges.contains("Iron Will")) badges.add("Iron Will")
        if (streak >= 60 && !badges.contains("Unbreakable")) badges.add("Unbreakable")
        if (streak >= 100 && !badges.contains("Attendance Titan")) badges.add("Attendance Titan")
        
        // Comeback King: Recover after losing 30+ day streak
        // If current streak is 1 and highest was >= 30, we could potentially flag this, 
        // but the rule says "Recover after losing". 
        // Let's say if they reach 7 again after losing 30.
        if (profile.highestStreak >= 30 && profile.currentStreak == 0 && streak == 1) {
            if (!badges.contains("Comeback King")) badges.add("Comeback King")
        }

        return badges
    }

    private suspend fun updateRankStatus(uid: String, instId: String, dept: String, sem: String) {
        // Query top 25 students in the same class
        val top25 = db.collection("gamification")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("department", dept)
            .whereEqualTo("semester", sem)
            .orderBy("totalPoints", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(25)
            .get().await()

        var myRank = 0
        var isTop25 = false
        
        top25.documents.forEachIndexed { index, doc ->
            if (doc.id == uid) {
                myRank = index + 1
                isTop25 = true
            }
        }

        db.collection("gamification").document(uid).update(
            "rank", myRank,
            "isTop25", isTop25
        ).await()
    }
}
