package com.example.autoattendance.models

data class StudentProfile(
    val uid: String = "",
    val role: String = "student",
    val name: String = "",
    val usn: String = "",
    val email: String = "",
    val stateId: String = "",
    val cityId: String = "",
    val institutionId: String = "",
    val department: String = "",
    val semester: String = "",
    val section: String = "",
    val batch: Int = 0,
    val deviceId: String = "",
    val createdAt: Long = 0L
)
