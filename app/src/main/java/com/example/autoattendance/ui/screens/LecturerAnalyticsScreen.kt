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
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

data class SubjectAnalytic(
    val id: String,
    val name: String,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturerAnalyticsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val instId = prefs.getString("institutionId", "") ?: ""

    var selectedSubject by remember { mutableStateOf<SubjectAnalytic?>(null) }
    var subjects by remember { mutableStateOf<List<SubjectAnalytic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(instId) {
        if (instId.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("subjects")
            .whereEqualTo("institutionId", instId)
            .get()
            .addOnSuccessListener { snapshot ->
                subjects = snapshot.documents.mapNotNull { doc ->
                    SubjectAnalytic(
                        id = doc.id,
                        name = doc.getString("subjectName") ?: "Unknown",
                        dept = doc.getString("department") ?: "",
                        sem = doc.getString("semester") ?: "",
                        sec = doc.getString("section") ?: "",
                        batch = doc.getString("batch") ?: ""
                    )
                }.sortedBy { it.name }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (selectedSubject == null) {
        SubjectSelectionList(subjects, onSelect = { selectedSubject = it })
    } else {
        SubjectAnalyticsDetail(selectedSubject!!, onBack = { selectedSubject = null })
    }
}

@Composable
fun SubjectSelectionList(subjects: List<SubjectAnalytic>, onSelect: (SubjectAnalytic) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Select Subject for Analytics",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (subjects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No subjects found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(subjects) { subject ->
                Card(
                    onClick = { onSelect(subject) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = subject.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = "${subject.dept} • Sem ${subject.sem} • Sec ${subject.sec} • ${subject.batch}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectAnalyticsDetail(subject: SubjectAnalytic, onBack: () -> Unit) {
    var studentStats by remember { mutableStateOf<List<StudentAnalytic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val instId = prefs.getString("institutionId", "") ?: ""

    LaunchedEffect(subject.id) {
        val db = FirebaseFirestore.getInstance()
        
        // 1. Fetch sessions for this subject
        db.collection("attendance_sessions")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("subjectId", subject.id)
            .get()
            .addOnSuccessListener { sessionSnap ->
                val sessionIds = sessionSnap.documents.map { it.id }
                val totalSessions = sessionIds.size

                if (totalSessions == 0) {
                    isLoading = false
                    return@addOnSuccessListener
                }

                // 2. Fetch all records for these sessions (take first 10 sessions for 'in' query demo limitation)
                db.collection("attendance_records")
                    .whereIn("sessionId", sessionIds.take(10))
                    .get()
                    .addOnSuccessListener { recordSnap ->
                        val statsMap = mutableMapOf<String, Pair<String, Int>>() // usn -> (name, count)
                        
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Column {
                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                Text("Attendance Analytics", style = MaterialTheme.typography.labelSmall)
            } },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                if (studentStats.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No student attendance records found.")
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(studentStats) { stat ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = stat.name, fontWeight = FontWeight.Medium)
                                        Text(text = "USN: ${stat.usn}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${stat.percentage}%",
                                            fontWeight = FontWeight.Bold,
                                            color = if (stat.percentage < 75) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                                        )
                                        Text(text = "${stat.attendedCount}/${stat.totalSessions} classes", style = MaterialTheme.typography.labelSmall)
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
