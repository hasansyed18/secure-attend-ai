package com.example.autoattendance.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.autoattendance.ui.components.AetherCircularProgress
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
        
        db.collection("attendance_sessions")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("department", dept)
            .whereEqualTo("semester", sem)
            .whereEqualTo("section", sec)
            .get().addOnSuccessListener { sessionSnap ->
                val allSessions = sessionSnap.documents
                
                db.collection("subjects").whereEqualTo("institutionId", instId).get().addOnSuccessListener { subSnap ->
                    val subjectsMap = subSnap.documents.associate { it.id to (it.getString("subjectName") ?: "Unknown Subject") }
                    
                    db.collection("attendance_records").whereEqualTo("studentId", uid).get().addOnSuccessListener { recordSnap ->
                        val attendedSessionIds = recordSnap.documents.mapNotNull { it.getString("sessionId") }.toSet()
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
                        isLoading = false
                    }
                }
            }.addOnFailureListener {
                isLoading = false
            }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
            .padding(20.dp)
    ) {
        Text(
            text = "Academic History",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Track your attendance progress per subject",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        if (subjects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No records found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(subjects) { history ->
                Card(
                    onClick = { onSubjectSelect(history) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        AetherCircularProgress(
                            progress = (history.percentage / 100f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = history.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${String.format(Locale.getDefault(), "%.1f", history.percentage)}% • ${history.presentCount}/${history.totalClasses} classes",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (history.percentage < 75) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetail(subjectHistory: SubjectHistory, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(subjectHistory.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HistoryStatCard(label = "Present", value = subjectHistory.presentCount.toString(), modifier = Modifier.weight(1f))
                HistoryStatCard(label = "Total", value = subjectHistory.totalClasses.toString(), modifier = Modifier.weight(1f))
                HistoryStatCard(
                    label = "Score", 
                    value = "${String.format(Locale.getDefault(), "%.1f", subjectHistory.percentage)}%", 
                    modifier = Modifier.weight(1f), 
                    color = if (subjectHistory.percentage < 75) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(text = "Attendance Timeline", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(subjectHistory.records) { record ->
                    val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(record.timestamp))
                    val time = SimpleDateFormat("hh:mm aa", Locale.getDefault()).format(Date(record.timestamp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(if (record.isPresent) Color(0xFF10B981) else MaterialTheme.colorScheme.error, CircleShape))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = date, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = if (record.isPresent) "PRESENT" else "ABSENT", 
                                color = if (record.isPresent) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryStatCard(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
        }
    }
}
