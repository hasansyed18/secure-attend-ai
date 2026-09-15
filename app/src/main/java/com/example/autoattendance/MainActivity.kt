package com.example.autoattendance

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity
 * ---------------------
 * First screen of the application.
 * User chooses:
 *  - Lecturer Mode
 *  - Student Mode
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.autoattendance.ui.theme.ThemeConfig.load(this)

        // 🔐 Role-based auto routing (only if actually logged in)
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val role = userPrefs.getString("role", null)
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

        if (role != null) {
            if (auth.currentUser != null) {
                Log.d("Routing", "MainActivity: User logged in, Role: $role")
                if (role == "student") {
                    Log.d("Routing", "MainActivity: Routing to StudentActivity")
                    startActivity(Intent(this, StudentActivity::class.java))
                    finish()
                    return
                }
                if (role == "lecturer") {
                    Log.d("Routing", "MainActivity: Routing to LecturerActivity")
                    startActivity(Intent(this, LecturerActivity::class.java))
                    finish()
                    return
                }
            } else {
                // 🚀 Issue Fix: User logged out but role remembered. Go to specific Login.
                Log.d("Routing", "MainActivity: User logged out, but Role remembered: $role")
                if (role == "student") {
                    startActivity(Intent(this, StudentLoginActivity::class.java))
                    finish()
                    return
                }
                if (role == "lecturer") {
                    startActivity(Intent(this, LecturerLoginActivity::class.java))
                    finish()
                    return
                }
            }
        } else {
            Log.d("Routing", "MainActivity: No role found. Showing selection layout.")
        }

        setContentView(R.layout.activity_main)

        // Updated to use View (matches the new card-based LinearLayout design)
        val btnLecturer: View = findViewById(R.id.btnLecturer)
        val btnStudent: View = findViewById(R.id.btnStudent)

        btnLecturer.setOnClickListener {
            startActivity(Intent(this, LecturerLoginActivity::class.java))
        }

        btnStudent.setOnClickListener {
            startActivity(Intent(this, StudentLoginActivity::class.java))
        }
    }
}
