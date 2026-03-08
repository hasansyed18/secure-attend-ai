package com.example.autoattendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class StudentLoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_login)

        val etUsn = findViewById<EditText>(R.id.etLoginUsn)
        val btnLogin = findViewById<Button>(R.id.btnLoginStudent)
        val tvRegister = findViewById<TextView>(R.id.tvGoToRegister)

        btnLogin.setOnClickListener {
            val usn = etUsn.text.toString().trim().uppercase()
            if (usn.isEmpty()) {
                Toast.makeText(this, "Please enter USN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginStudent(usn)
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, StudentProfileActivity::class.java))
            finish()
        }
    }

    private fun loginStudent(usn: String) {
        val database = FirebaseDatabase.getInstance()
        val studentRef = database.reference.child("students").child(usn)
        
        // Capture current device ID
        val currentDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        studentRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Toast.makeText(this, "USN not found. Please register.", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            // 🔐 DEVICE ID BINDING CHECK
            val registeredDeviceId = snapshot.child("deviceId").getValue(String::class.java)
            
            if (registeredDeviceId != null && registeredDeviceId != currentDeviceId) {
                Toast.makeText(this, "Access Denied: This USN is registered to another phone!", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            // Login successful
            val name = snapshot.child("name").getValue(String::class.java) ?: "Unknown"
            val dept = snapshot.child("department").getValue(String::class.java) ?: ""
            val sem = snapshot.child("semester").getValue(String::class.java) ?: ""
            val section = snapshot.child("section").getValue(String::class.java) ?: ""

            // Save locally
            val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            userPrefs.edit()
                .putString("role", "student")
                .putString("name", name)
                .putString("usn", usn)
                .putString("department", dept)
                .putString("semester", sem)
                .putString("section", section)
                .putString("deviceId", currentDeviceId)
                .apply()

            Toast.makeText(this, "Welcome $name!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, StudentActivity::class.java))
            finish()

        }.addOnFailureListener {
            Toast.makeText(this, "Database error: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
