package com.example.autoattendance.ui.screens

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
import com.example.autoattendance.models.GamificationProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun HallOfFameScreen() {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val db = FirebaseFirestore.getInstance()
    
    var profile by remember { mutableStateOf<GamificationProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("gamification").document(uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    profile = snapshot.toObject(GamificationProfile::class.java)
                }
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // --- 🏆 Section 1: Student Summary ---
            HeaderSection(profile ?: GamificationProfile())

            Column(modifier = Modifier.padding(20.dp)) {
                // --- 📊 Section 2: Next Badge Progress ---
                BadgeProgressSection(profile?.totalPoints ?: 0)

                Spacer(modifier = Modifier.height(32.dp))

                // --- ✅ Section 3: Earned Badges ---
                AchievementSection(title = "Earned Achievements", badges = profile?.achievementBadges ?: emptyList(), isLocked = false)

                Spacer(modifier = Modifier.height(32.dp))

                // --- 🔒 Section 4: Locked Badges ---
                val allAchievements = listOf("Perfect Week", "Iron Will", "Unbreakable", "Attendance Titan", "Comeback King", "Consistency Champion")
                val lockedBadges = allAchievements.filterNot { profile?.achievementBadges?.contains(it) ?: false }
                AchievementSection(title = "Ongoing Quests", badges = lockedBadges, isLocked = true, currentStreak = profile?.currentStreak ?: 0)
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun HeaderSection(profile: GamificationProfile) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF8B5CF6), // brandPurple
                        Color(0xFF10B981)  // brandGreen
                    )
                )
            )
            .padding(top = 40.dp, bottom = 40.dp, start = 24.dp, end = 24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // Badge Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = profile.currentBadge.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(icon = Icons.Default.Whatshot, label = "Streak", value = "${profile.currentStreak}")
                StatItem(icon = Icons.Default.Star, label = "Points", value = "${profile.totalPoints}")
                StatItem(
                    icon = Icons.Default.EmojiEvents, 
                    label = "Rank", 
                    value = if (profile.isTop25) "#${profile.rank}" else "Locked"
                )
            }
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(text = value, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun BadgeProgressSection(points: Long) {
    val thresholds = listOf(500, 1500, 3000, 6000)
    val nextThreshold = thresholds.find { it > points } ?: 6000
    val progress = points.toFloat() / nextThreshold.toFloat()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "NEXT BADGE PROGRESS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "$points Points", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(text = "${nextThreshold - points} to Next Tier", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AchievementSection(title: String, badges: List<String>, isLocked: Boolean, currentStreak: Int = 0) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.5.sp
    )
    Spacer(modifier = Modifier.height(12.dp))
    
    if (badges.isEmpty()) {
        Text("No badges in this category yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        badges.forEach { badge ->
            AchievementCard(name = badge, isLocked = isLocked, currentStreak = currentStreak)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun AchievementCard(name: String, isLocked: Boolean, currentStreak: Int) {
    val requirementText = when(name) {
        "Perfect Week" -> "7-Day Streak"
        "Iron Will" -> "30-Day Streak"
        "Unbreakable" -> "60-Day Streak"
        "Attendance Titan" -> "100-Day Streak"
        "Comeback King" -> "Recover after losing 30+ day streak"
        "Consistency Champion" -> "90%+ Attendance for semester"
        else -> "Hidden Requirement"
    }

    val progress = when(name) {
        "Perfect Week" -> currentStreak.toFloat() / 7f
        "Iron Will" -> currentStreak.toFloat() / 30f
        "Unbreakable" -> currentStreak.toFloat() / 60f
        "Attendance Titan" -> currentStreak.toFloat() / 100f
        else -> 0f
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isLocked) MaterialTheme.colorScheme.outline else Color(0xFF10B981),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name, 
                    fontWeight = FontWeight.Bold, 
                    color = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                Text(text = requirementText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                if (isLocked && progress > 0f && progress < 1f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}
