package com.example.autoattendance.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf

object ThemeConfig {
    var isDarkMode = mutableStateOf<Boolean?>(null)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        if (prefs.contains("dark_mode")) {
            val dark = prefs.getBoolean("dark_mode", false)
            isDarkMode.value = dark
            applyTheme(dark)
        }
    }

    fun toggle(context: Context) {
        val current = isDarkMode.value ?: false
        val next = !current
        isDarkMode.value = next
        context.getSharedPreferences("Settings", Context.MODE_PRIVATE)
            .edit().putBoolean("dark_mode", next).apply()
        applyTheme(next)
    }

    private fun applyTheme(dark: Boolean) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (dark) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
