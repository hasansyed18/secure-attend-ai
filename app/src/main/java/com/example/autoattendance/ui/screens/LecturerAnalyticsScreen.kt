package com.example.autoattendance.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

data class SubjectAnalytic(
    val id: String,
    val name: String,
    val code: String,
    val dept: String,
    val sem: String,
    val sec: String,
    val batch: String
)

data class StudentAnalytic(
    val usn: String,
    val name: String,
    val attendedCount: Int,
    val totalSessions: Int
) {
    val percentage: Int = if (totalSessions > 0) (attendedCount.toFloat() / totalSessions.toFloat() * 100).toInt() else 0
}

data class SessionRecord(
    val timestamp: Long,
    val isPresent: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturerAnalyticsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val instId = prefs.getString("institutionId", "") ?: ""
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    var selectedSubject by remember { mutableStateOf<SubjectAnalytic?>(null) }
    var selectedStudent by remember { mutableStateOf<StudentAnalytic?>(null) }
    var subjects by remember { mutableStateOf<List<SubjectAnalytic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(instId) {
        if (instId.isEmpty() || currentUid.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        val db = FirebaseFirestore.getInstance()
        db.collection("subjects")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("lecturerId", currentUid)
            .get()
            .addOnSuccessListener { snapshot ->
                subjects = snapshot.documents.mapNotNull { doc ->
                    SubjectAnalytic(
                        id = doc.id,
                        name = doc.getString("subjectName") ?: "Unknown",
                        code = doc.getString("subjectCode") ?: "",
                        dept = doc.getString("department") ?: "",
                        sem = doc.getString("semester") ?: "",
                        sec = doc.getString("section") ?: "",
                        batch = doc.getString("batch") ?: ""
                    )
                }.sortedBy { it.name }
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (selectedSubject == null) {
        SubjectSelectionList(subjects, onSelect = { selectedSubject = it })
    } else if (selectedStudent == null) {
        SubjectAnalyticsDetail(
            selectedSubject!!, 
            onBack = { selectedSubject = null },
            onStudentSelect = { selectedStudent = it }
        )
    } else {
        StudentTimelineDetail(
            subject = selectedSubject!!,
            student = selectedStudent!!,
            onBack = { selectedStudent = null }
        )
    }
}

@Composable
fun SubjectSelectionList(subjects: List<SubjectAnalytic>, onSelect: (SubjectAnalytic) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Text(text = "Course Performance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(text = "Analyze student activity by subject", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(24.dp))

        if (subjects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No subjects registered.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(subjects) { subject ->
                Card(
                    onClick = { onSelect(subject) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "[${subject.code}] ${subject.dept} • Sem ${subject.sem}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
fun SubjectAnalyticsDetail(
    subject: SubjectAnalytic, 
    onBack: () -> Unit,
    onStudentSelect: (StudentAnalytic) -> Unit
) {
    var studentStats by remember { mutableStateOf<List<StudentAnalytic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val instId = prefs.getString("institutionId", "") ?: ""

    LaunchedEffect(subject.id) {
        val db = FirebaseFirestore.getInstance()
        db.collection("attendance_sessions")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("subjectId", subject.id)
            .get()
            .addOnSuccessListener { sessionSnap ->
                val totalSessions = sessionSnap.size()
                if (totalSessions == 0) { isLoading = false; return@addOnSuccessListener }

                db.collection("attendance_records")
                    .whereEqualTo("institutionId", instId)
                    .whereEqualTo("subjectId", subject.id)
                    .get()
                    .addOnSuccessListener { recordSnap ->
                        val statsMap = mutableMapOf<String, Pair<String, Int>>()
                        recordSnap.documents.forEach { doc ->
                            val usn = doc.getString("studentUsn") ?: "---"
                            val name = doc.getString("studentName") ?: "Unknown"
                            val current = statsMap[usn] ?: Pair(name, 0)
                            statsMap[usn] = Pair(name, current.second + 1)
                        }
                        studentStats = statsMap.map { (usn, data) ->
                            StudentAnalytic(usn, data.first, data.second, totalSessions)
                        }.sortedBy { it.name }
                        isLoading = false
                    }
                    .addOnFailureListener { isLoading = false }
            }
            .addOnFailureListener { isLoading = false }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Column {
                Text(subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Enrollment Metrics", style = MaterialTheme.typography.labelSmall)
            } },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(modifier = Modifier.padding(20.dp)) {
                if (studentStats.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No records found.")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(studentStats) { stat ->
                            Card(
                                onClick = { onStudentSelect(stat) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // v3.0 Attendance Ring
                                    AetherCircularProgress(
                                        progress = (stat.percentage.toFloat() / 100f),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stat.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(text = stat.usn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.TrendingUp, 
                                                contentDescription = null, 
                                                modifier = Modifier.size(12.dp),
                                                tint = Color(0xFF0EE5FF)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${stat.percentage}%",
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = if (stat.percentage < 75) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                                            )
                                        }
                                        Text(text = "${stat.attendedCount}/${stat.totalSessions}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentTimelineDetail(
    subject: SubjectAnalytic,
    student: StudentAnalytic,
    onBack: () -> Unit
) {
    var timeline by remember { mutableStateOf<List<SessionRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val instId = prefs.getString("institutionId", "") ?: ""

    LaunchedEffect(student.usn) {
        val db = FirebaseFirestore.getInstance()
        db.collection("attendance_sessions")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("subjectId", subject.id)
            .get()
            .addOnSuccessListener { sessionSnap ->
                val sessions = sessionSnap.documents
                db.collection("attendance_records")
                    .whereEqualTo("institutionId", instId)
                    .whereEqualTo("subjectId", subject.id)
                    .whereEqualTo("studentUsn", student.usn)
                    .get()
                    .addOnSuccessListener { recordSnap ->
                        val attendedSessionIds = recordSnap.documents.mapNotNull { it.getString("sessionId") }.toSet()
                        timeline = sessions.map { sDoc ->
                            SessionRecord(
                                timestamp = sDoc.getTimestamp("startTime")?.toDate()?.time ?: 0L,
                                isPresent = attendedSessionIds.contains(sDoc.id)
                            )
                        }.sortedByDescending { it.timestamp }
                        isLoading = false
                    }
                    .addOnFailureListener { isLoading = false }
            }
            .addOnFailureListener { isLoading = false }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Column {
                Text(student.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Chronological Activity", style = MaterialTheme.typography.labelSmall)
            } },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "Attendance Timeline", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(timeline) { record ->
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
}
