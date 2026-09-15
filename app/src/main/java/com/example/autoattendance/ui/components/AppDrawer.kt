package com.example.autoattendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autoattendance.ui.Screen
import com.example.autoattendance.ui.AllScreens
import com.example.autoattendance.ui.theme.ThemeConfig

@Composable
fun AppDrawer(
    currentRoute: String,
    onNavigate: (Screen) -> Unit,
    userName: String = "User",
    userEmail: String = "user@example.com",
    userRole: String = "student",
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = ThemeConfig.isDarkMode.value ?: isSystemInDarkTheme()
) {
    ModalDrawerSheet(
        modifier = modifier.width(300.dp),
        drawerContainerColor = if (isDarkMode) Color(0xFF1C1830) else Color(0xFFFFFFFF),
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        // --- v4.0 Professional Header ---
        val headerGradient = if (isDarkMode) {
            Brush.verticalGradient(listOf(Color(0xFF261E47), Color(0xFF1C1830)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFEDE9FE), Color(0xFFF8F9FE)))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerGradient)
                .padding(top = 60.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
        ) {
            Column {
                // Avatar with Brand Color
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFF10B981))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName.firstOrNull()?.toString()?.uppercase() ?: "?",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Surface(
                    modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val badgeColor = if (isDarkMode) MaterialTheme.colorScheme.primary else Color(0xFF5B21B6)
                    Text(
                        text = userRole.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Drawer Items
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            AllScreens.forEach { screen ->
                if (userRole == "lecturer" && screen == Screen.HallOfFame) return@forEach
                
                val displayTitle = when {
                    userRole == "lecturer" && screen == Screen.UpcomingClasses -> "Schedule Classes"
                    userRole == "lecturer" && screen == Screen.AttendanceHistory -> "Attendance Analytics"
                    else -> screen.title
                }

                val isSelected = currentRoute == screen.route
                
                // v4.0 Professional Active Item
                val itemBg = if (isSelected) {
                    if (isDarkMode) Color(0xFF8B5CF6).copy(alpha = 0.15f) else Color(0xFFEDE9FE)
                } else {
                    Color.Transparent
                }
                
                val itemIconColor = if (isSelected) {
                    if (isDarkMode) Color(0xFFA78BFA) else Color(0xFF8B5CF6)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                NavigationDrawerItem(
                    label = { 
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ) 
                    },
                    selected = isSelected,
                    onClick = { onNavigate(screen) },
                    icon = { 
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = displayTitle,
                            modifier = Modifier.size(22.dp)
                        ) 
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = itemBg,
                        selectedIconColor = itemIconColor,
                        selectedTextColor = itemIconColor,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
        
        Text(
            text = "SecureAttend PRO v4.0",
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}
