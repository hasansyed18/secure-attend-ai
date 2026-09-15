package com.example.autoattendance.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autoattendance.ui.components.ThemeToggle
import com.example.autoattendance.ui.theme.ThemeConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
    val firestore = FirebaseFirestore.getInstance()
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    val role = prefs.getString("role", "student") ?: "student"
    val currentUsn = prefs.getString("usn", "---") ?: "---"
    val inst = prefs.getString("institution", "---") ?: "---"
    val batch = prefs.getInt("batch", 0)
    val state = prefs.getString("state", "---") ?: "---"
    val city = prefs.getString("city", "---") ?: "---"
    val email = prefs.getString("email", "---") ?: "---"

    var name by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    var dept by remember { mutableStateOf(prefs.getString("department", "CSE") ?: "CSE") }
    var sem by remember { mutableStateOf(prefs.getString("semester", "1") ?: "1") }
    var sec by remember { mutableStateOf(prefs.getString("section", "A") ?: "A") }

    var isUpdating by remember { mutableStateOf(false) }

    val branches = listOf("CSE", "AI", "EEE", "ECE", "Mechanical", "Civil")
    val semesters = (1..8).map { it.toString() }
    val sections = listOf("A", "B", "C", "D", "E")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        // --- 🚀 v4.0 Professional Header ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6),
                            Color(0xFF10B981)
                        )
                    )
                )
                .padding(top = 40.dp, bottom = 40.dp, start = 24.dp, end = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.firstOrNull()?.toString()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.headlineLarge,
                                color = Color(0xFF8B5CF6),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // Theme Toggle in Header for consistency
                ThemeToggle()
            }
        }

        Column(modifier = Modifier.padding(24.dp)) {
            // --- ✏️ Academic Profile ---
            SectionTitle("Academic Details")
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            var deptExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = deptExpanded,
                onExpandedChange = { deptExpanded = !deptExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = dept,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Department") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deptExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = deptExpanded,
                    onDismissRequest = { deptExpanded = false }
                ) {
                    branches.forEach { branch ->
                        DropdownMenuItem(
                            text = { Text(branch) },
                            onClick = {
                                dept = branch
                                deptExpanded = false
                            }
                        )
                    }
                }
            }

            if (role == "student") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    var semExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = semExpanded,
                        onExpandedChange = { semExpanded = !semExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = "Sem $sem",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Semester") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = semExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = semExpanded,
                            onDismissRequest = { semExpanded = false }
                        ) {
                            semesters.forEach { s ->
                                DropdownMenuItem(text = { Text("Sem $s") }, onClick = { sem = s; semExpanded = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    var secExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = secExpanded,
                        onExpandedChange = { secExpanded = !secExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = "Sec $sec",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Section") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = secExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = secExpanded,
                            onDismissRequest = { secExpanded = false }
                        ) {
                            sections.forEach { s ->
                                DropdownMenuItem(text = { Text("Sec $s") }, onClick = { sec = s; secExpanded = false })
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isUpdating = true
                    val updates = mutableMapOf<String, Any>("name" to name.trim(), "department" to dept)
                    if (role == "student") { updates["semester"] = sem; updates["section"] = sec }

                    firestore.collection("users").document(uid).update(updates)
                        .addOnSuccessListener {
                            isUpdating = false
                            val editor = prefs.edit().putString("name", name.trim()).putString("department", dept)
                            if (role == "student") { editor.putString("semester", sem); editor.putString("section", sec) }
                            editor.apply()
                            Toast.makeText(context, "Profile Synchronized!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            isUpdating = false
                            Toast.makeText(context, "Update Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isUpdating
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(text = "Update Information", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // --- 🔒 Secure Details ---
            SectionTitle("Registration Identity")
            SettingsCard {
                Column {
                    if (role == "student") {
                        InfoRow(icon = Icons.Default.Badge, label = "University USN", value = currentUsn)
                    }
                    InfoRow(icon = Icons.Default.School, label = "Enrolled Institution", value = inst)
                    if (role == "student") {
                        InfoRow(icon = Icons.Default.DateRange, label = "Batch Admission", value = batch.toString())
                    }
                    InfoRow(icon = Icons.Default.LocationOn, label = "Campus Location", value = "$city, $state")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}
