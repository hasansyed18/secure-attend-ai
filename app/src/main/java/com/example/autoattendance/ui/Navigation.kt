package com.example.autoattendance.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object AttendanceHistory : Screen("history", "Attendance History", Icons.Default.History)
    object HallOfFame : Screen("hall_of_fame", "Hall of Fame", Icons.Default.EmojiEvents)
    object UpcomingClasses : Screen("upcoming", "Upcoming Classes", Icons.Default.Event)
    object Notifications : Screen("notifications", "Notifications", Icons.Default.Notifications)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object HelpFeedback : Screen("help", "Help & Feedback", Icons.Default.Help)
    object About : Screen("about", "About SecureAttend", Icons.Default.Info)
    object Logout : Screen("logout", "Logout", Icons.Default.ExitToApp)
}

val AllScreens = listOf(
    Screen.Dashboard,
    Screen.Profile,
    Screen.AttendanceHistory,
    Screen.HallOfFame,
    Screen.UpcomingClasses,
    Screen.Notifications,
    Screen.Settings,
    Screen.HelpFeedback,
    Screen.About,
    Screen.Logout
)
