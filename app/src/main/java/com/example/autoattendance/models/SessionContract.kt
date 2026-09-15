package com.example.autoattendance.models

data class SessionContract(
    val sessionId: String,
    val securityToken: String,
    val subjectId: String,
    val subjectCode: String,
    val subjectName: String,
    val lecturerId: String,
    val lecturerName: String,
    val institutionId: String,
    val department: String,
    val semester: String,
    val section: String,
    val batch: String,
    val expiresAt: Long,
    val pinHash: String = ""
)
