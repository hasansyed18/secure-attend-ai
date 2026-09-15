package com.example.autoattendance.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.autoattendance.ui.theme.ThemeConfig

@Composable
fun AetherCircularProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    isDark: Boolean = ThemeConfig.isDarkMode.value ?: isSystemInDarkTheme()
) {
    val trackColor = if (isDark) Color(0xFF3A4250) else Color(0xFFE2E8F0)
    val fillBrush = if (isDark) {
        Brush.linearGradient(listOf(Color(0xFF0EE5FF), Color(0xFF0EE5FF)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF0EE5FF), Color(0xFF4FD1C5)))
    }

    Canvas(modifier = modifier.size(40.dp)) {
        val strokeWidth = 4.dp.toPx()
        
        // Track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // Fill
        drawArc(
            brush = fillBrush,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
