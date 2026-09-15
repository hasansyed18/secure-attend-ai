package com.example.autoattendance.models

data class GamificationProfile(
    val uid: String = "",
    val totalPoints: Long = 0,
    val currentStreak: Int = 0,
    val highestStreak: Int = 0,
    val currentBadge: String = "Rookie",
    val rank: Int = 0,
    val isTop25: Boolean = false,
    val achievementBadges: List<String> = emptyList(),
    val lastAttendanceDate: Long = 0,
    val perfectDays: Int = 0,
    val institutionId: String = "",
    val department: String = "",
    val semester: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
