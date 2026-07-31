package com.example.autoattendance.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class SubjectHistory(
    val id: String,
    val name: String,
    val totalClasses: Int,
    val presentCount: Int,
    val records: List<AttendanceTimelineRecord>
) {
    val percentage: Float = if (totalClasses > 0) (presentCount.toFloat() / totalClasses.toFloat() * 100) else 0f
}

data class AttendanceTimelineRecord(
    val sessionId: String,
    val timestamp: Long,
    val isPresent: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHistoryScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val instId = prefs.getString("institutionId", "") ?: ""
    val dept = prefs.getString("department", "") ?: ""
    val sem = prefs.getString("semester", "") ?: ""
    val sec = prefs.getString("section", "") ?: ""
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var selectedSubject by remember { mutableStateOf<SubjectHistory?>(null) }
    var subjectsHistory by remember { mutableStateOf<List<SubjectHistory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(instId, uid) {
        if (instId.isEmpty() || uid.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        
        Log.d("AttendanceHistory", "Fetching history for student: $uid ($dept $sem $sec)")
        val db = FirebaseFirestore.getInstance()
        
        // 1. Fetch ALL sessions for this student's specific class (matches enrollment)
        db.collection("attendance_sessions")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("department", dept)
            .whereEqualTo("semester", sem)
            .whereEqualTo("section", sec)
            .get().addOnSuccessListener { sessionSnap ->
                val allSessions = sessionSnap.documents
                Log.d("AttendanceHistory", "Found ${allSessions.size} total sessions for this student's group")

                // 2. Fetch all subjects for the institution to resolve session subject IDs to names
                db.collection("subjects")
                    .whereEqualTo("institutionId", instId)
                    .get().addOnSuccessListener { subSnap ->
                        val subjectsMap = subSnap.documents.associate { it.id to (it.getString("subjectName") ?: "Unknown Subject") }

                        // 3. Fetch attendance records for this student
                        db.collection("attendance_records")
                            .whereEqualTo("studentId", uid)
                            .get().addOnSuccessListener { recordSnap ->
                                val attendedSessionIds = recordSnap.documents.mapNotNull { it.getString("sessionId") }.toSet()
                                Log.d("AttendanceHistory", "Student attended ${attendedSessionIds.size} sessions total")

                                val historyMap = mutableMapOf<String, MutableList<AttendanceTimelineRecord>>()
                                
                                allSessions.forEach { sDoc ->
                                    val subId = sDoc.getString("subjectId") ?: ""
                                    if (subId.isNotEmpty()) {
                                        val sTime = sDoc.getTimestamp("startTime")?.toDate()?.time ?: 0L
                                        val isPresent = attendedSessionIds.contains(sDoc.id)
                                        
                                        if (!historyMap.containsKey(subId)) historyMap[subId] = mutableListOf()
                                        historyMap[subId]?.add(AttendanceTimelineRecord(sDoc.id, sTime, isPresent))
                                    }
                                }
                                
                                subjectsHistory = historyMap.map { (id, records) ->
                                    SubjectHistory(
                                        id = id,
                                        name = subjectsMap[id] ?: "Unknown",
                                        totalClasses = records.size,
                                        presentCount = records.count { it.isPresent },
                                        records = records.sortedByDescending { it.timestamp }
                                    )
                                }.sortedBy { it.name }
                                
                                Log.d("AttendanceHistory", "History processing complete: ${subjectsHistory.size} subjects")
                                isLoading = false
                            }
                    }
            }.addOnFailureListener { e ->
                Log.e("AttendanceHistory", "Fetch failed", e)
                isLoading = false
            }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (selectedSubject == null) {
        SubjectList(subjectsHistory, onSubjectSelect = { selectedSubject = it })
    } else {
        SubjectDetail(subjectHistory = selectedSubject!!, onBack = { selectedSubject = null })
    }
}

@Composable
fun SubjectList(subjects: List<SubjectHistory>, onSubjectSelect: (SubjectHistory) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Subject-wise History",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (subjects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No attendance history available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(subjects) { history ->
                SubjectCard(history, onClick = { onSubjectSelect(history) })
            }
        }
    }
}

@Composable
fun SubjectCard(history: SubjectHistory, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = history.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = "${String.format(Locale.getDefault(), "%.1f", history.percentage)}% Attendance • ${history.presentCount}/${history.totalClasses} Classes",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (history.percentage < 75) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetail(subjectHistory: SubjectHistory, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text(subjectHistory.name, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(label = "Present", value = subjectHistory.presentCount.toString(), modifier = Modifier.weight(1f))
                StatCard(label = "Total", value = subjectHistory.totalClasses.toString(), modifier = Modifier.weight(1f))
                StatCard(
                    label = "Percent", 
                    value = "${String.format(Locale.getDefault(), "%.1f", subjectHistory.percentage)}%", 
                    modifier = Modifier.weight(1f), 
                    color = if (subjectHistory.percentage < 75) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Attendance Timeline", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(subjectHistory.records) { record ->
                    TimelineItem(record)
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TimelineItem(record: AttendanceTimelineRecord) {
    val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(record.timestamp))
    val time = SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date(record.timestamp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(if (record.isPresent) Color(0xFF10B981) else Color(0xFFEF4444), RoundedCornerShape(5.dp)))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = date, fontWeight = FontWeight.Medium)
                Text(text = time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = if (record.isPresent) "Present" else "Absent", color = if (record.isPresent) Color(0xFF10B981) else Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}
