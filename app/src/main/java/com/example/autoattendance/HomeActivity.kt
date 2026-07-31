package com.example.autoattendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.autoattendance.ui.Screen
import com.example.autoattendance.ui.AllScreens
import com.example.autoattendance.ui.components.AppDrawer
import com.example.autoattendance.ui.screens.AttendanceHistoryScreen
import com.example.autoattendance.ui.screens.LecturerAnalyticsScreen
import com.example.autoattendance.ui.screens.ProfileScreen
import com.example.autoattendance.ui.theme.AutoAttendanceTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val name = userPrefs.getString("name", "User") ?: "User"
            val email = userPrefs.getString("email", "") ?: ""
            val role = userPrefs.getString("role", "student") ?: "student"
            
            AutoAttendanceTheme {
                MainScreen(userName = name, userEmail = email, userRole = role)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(userName: String, userEmail: String, userRole: String) {
        val context = LocalContext.current
        val userPrefs = remember { context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) } as android.content.SharedPreferences
        val navController = rememberNavController()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Profile.route

        // Handle navigation from StudentActivity drawer
        LaunchedEffect(intent) {
            intent.getStringExtra("TARGET_SCREEN")?.let { target ->
                navController.navigate(target) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
                intent.removeExtra("TARGET_SCREEN")
            }
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawer(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        scope.launch { drawerState.close() }
                        if (screen == Screen.Logout) {
                            logout()
                        } else if (screen == Screen.Dashboard) {
                            val role = userPrefs.getString("role", null)
                            if (role == "student") {
                                startActivity(Intent(this@HomeActivity, StudentActivity::class.java))
                            } else if (role == "lecturer") {
                                startActivity(Intent(this@HomeActivity, LecturerActivity::class.java))
                            }
                            finish()
                        } else {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    userName = userName,
                    userEmail = userEmail,
                    userRole = userRole
                )
            }
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { 
                            val screen = AllScreens.find { it.route == currentRoute }
                            val title = if (userRole == "lecturer") {
                                when (screen) {
                                    Screen.UpcomingClasses -> "Schedule Classes"
                                    Screen.AttendanceHistory -> "Attendance Analytics"
                                    else -> screen?.title ?: "SecureAttend"
                                }
                            } else {
                                screen?.title ?: "SecureAttend"
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge
                            ) 
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    NavHost(navController = navController, startDestination = Screen.Profile.route) {
                        composable(Screen.Profile.route) { ProfileScreen() }
                        composable(Screen.AttendanceHistory.route) { 
                            if (userRole == "lecturer") {
                                LecturerAnalyticsScreen()
                            } else {
                                AttendanceHistoryScreen()
                            }
                        }
                        composable(Screen.Achievements.route) { PlaceholderScreen("Achievements") }
                        composable(Screen.UpcomingClasses.route) { PlaceholderScreen("Upcoming Classes") }
                        composable(Screen.Notifications.route) { PlaceholderScreen("Notifications") }
                        composable(Screen.Settings.route) { PlaceholderScreen("Settings") }
                        composable(Screen.HelpFeedback.route) { PlaceholderScreen("Help & Feedback") }
                        composable(Screen.About.route) { PlaceholderScreen("About SecureAttend") }
                    }
                }
            }
        }
    }

    @Composable
    fun PlaceholderScreen(name: String) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(text = "$name Screen", style = MaterialTheme.typography.headlineMedium)
        }
    }

    private fun logout() {
        FirebaseAuth.getInstance().signOut()
        getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
