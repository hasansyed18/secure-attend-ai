package com.example.autoattendance.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.autoattendance.ui.theme.ThemeConfig

@Composable
fun ThemeToggle(
    modifier: Modifier = Modifier,
    isDark: Boolean = ThemeConfig.isDarkMode.value ?: false
) {
    val context = LocalContext.current
    val offsetX by animateDpAsState(
        targetValue = if (isDark) 24.dp else 0.dp,
        animationSpec = tween(durationMillis = 250)
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(if (isDark) Color(0xFF322C4A) else Color(0xFFE2E8F0))
            .clickable { ThemeConfig.toggle(context) }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF8B5CF6) else Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDark) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = if (isDark) "Toggle light mode" else "Toggle dark mode",
                modifier = Modifier.size(14.dp),
                tint = if (isDark) Color.White else Color(0xFFF59E0B)
            )
        }
    }
}
