package com.example.autoattendance

import android.content.Intent
import android.os.Bundle
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
        
        // 🔐 Role-based auto routing
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val role = userPrefs.getString("role", null)

        if (role == "student") {
            startActivity(Intent(this, StudentActivity::class.java))
            finish()
            return
        }

        if (role == "lecturer") {
            startActivity(Intent(this, LecturerActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        // Updated to use View (matches the new card-based LinearLayout design)
        val btnLecturer: View = findViewById(R.id.btnLecturer)
        val btnStudent: View = findViewById(R.id.btnStudent)

        btnLecturer.setOnClickListener {
            startActivity(Intent(this, LecturerActivity::class.java))
        }

        btnStudent.setOnClickListener {
            startActivity(Intent(this, StudentLoginActivity::class.java))
        }
    }
}
