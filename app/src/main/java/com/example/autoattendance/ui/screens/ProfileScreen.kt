package com.example.autoattendance.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            .padding(20.dp)
    ) {
        // ✏️ Editable Section
        Text(
            text = "Edit Profile",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Manage your active account details",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.LightGray,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Department Dropdown
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
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = deptExpanded,
                onDismissRequest = { deptExpanded = false }
            ) {
                branches.forEach { branch ->
                    DropdownMenuItem(
                        text = { Text(branch, color = Color.Black) },
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
                // Semester Dropdown
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
                        leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = semExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.LightGray,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = semExpanded,
                        onDismissRequest = { semExpanded = false }
                    ) {
                        semesters.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("Sem $s", color = Color.Black) },
                                onClick = {
                                    sem = s
                                    semExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Section Dropdown
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
                        leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = secExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.LightGray,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = secExpanded,
                        onDismissRequest = { secExpanded = false }
                    ) {
                        sections.forEach { s ->
                            DropdownMenuItem(
                                text = { Text("Sec $s", color = Color.Black) },
                                onClick = {
                                    sec = s
                                    secExpanded = false
                                }
                            )
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
                val updates = mutableMapOf<String, Any>(
                    "name" to name.trim(),
                    "department" to dept
                )
                if (role == "student") {
                    updates["semester"] = sem
                    updates["section"] = sec
                }

                firestore.collection("users").document(uid).update(updates)
                    .addOnSuccessListener {
                        isUpdating = false
                        val editor = prefs.edit()
                            .putString("name", name.trim())
                            .putString("department", dept)
                        if (role == "student") {
                            editor.putString("semester", sem)
                            editor.putString("section", sec)
                        }
                        editor.apply()
                        Toast.makeText(context, "Profile Updated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        isUpdating = false
                        Toast.makeText(context, "Update Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isUpdating
        ) {
            if (isUpdating) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text(text = "Save Changes", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // 🔒 Read-only Section
        Text(
            text = "Registration Details",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                InfoRow(icon = Icons.Default.Badge, label = "Role", value = role.uppercase())
                if (role == "student") {
                    InfoRow(icon = Icons.Default.Badge, label = "USN", value = currentUsn)
                }
                InfoRow(icon = Icons.Default.Email, label = "Email", value = email)
                InfoRow(icon = Icons.Default.School, label = "Institution", value = inst)
                if (role == "student") {
                    InfoRow(icon = Icons.Default.DateRange, label = "Batch Year", value = batch.toString())
                }
                InfoRow(icon = Icons.Default.LocationOn, label = "Location", value = "$city, $state")
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 🔒 Security Note
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Note: Core registration details are fixed for security purposes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
