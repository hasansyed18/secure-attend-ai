package com.example.autoattendance.models

data class LecturerProfile(
    val uid: String = "",
    val role: String = "lecturer",
    val name: String = "",
    val email: String = "",
    val stateId: String = "",
    val cityId: String = "",
    val institutionId: String = "",
    val department: String = "",
    val createdAt: Long = 0L
)
