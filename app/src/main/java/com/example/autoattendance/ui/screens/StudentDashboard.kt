package com.example.autoattendance.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
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
import com.example.autoattendance.AttendanceHistoryActivity
import com.example.autoattendance.StudentEditProfileActivity

@Composable
fun StudentDashboard(
    userName: String,
    userUsn: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Ready to Scan") }
    var isAttendanceMarked by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FE))
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // Profile Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
                    Text(text = initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }

                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(text = userName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(text = "USN: $userUsn", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }

                IconButton(onClick = { context.startActivity(Intent(context, StudentEditProfileActivity::class.java)) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = Color.White)
                }
            }
        }

        // Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isAttendanceMarked) Icons.Default.CheckCircle else Icons.Default.Bluetooth,
                    contentDescription = "Status Icon",
                    modifier = Modifier.size(64.dp),
                    tint = if (isAttendanceMarked) Color(0xFF10B981) else MaterialTheme.colorScheme.secondary
                )

                Text(
                    text = if (isAttendanceMarked) "✅ Attendance Marked" else statusText,
                    modifier = Modifier.padding(top = 16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )

                Text(
                    text = "Make sure Bluetooth and GPS are enabled",
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                Button(
                    onClick = { 
                        // logic
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isAttendanceMarked
                ) {
                    Text(text = "Mark Attendance")
                }
            }
        }

        // Action Buttons
        TextButton(
            onClick = { context.startActivity(Intent(context, AttendanceHistoryActivity::class.java)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(56.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = "Attendance History")
            }
        }
    }
}
