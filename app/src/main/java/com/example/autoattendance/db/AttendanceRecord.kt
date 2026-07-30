package com.example.autoattendance.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_records",
    indices = [Index(value = ["sessionId", "usn"], unique = true)]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sessionId: String,
    val subjectName: String,
    val usn: String,
    val studentName: String,
    val rssi: Int,
    val timestamp: Long,
    val isSynced: Boolean = false
)
