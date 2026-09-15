package com.example.autoattendance

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autoattendance.ui.theme.AutoAttendanceTheme
import com.example.autoattendance.ui.theme.ThemeConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LecturerLoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeConfig.load(this)
        setContentView(R.layout.activity_lecturer_login)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)
        
        setupThemeToggle()
        
        // Use an indeterminate progress bar if available in layout, or create one
        // Note: activity_lecturer_login.xml doesn't have a pbLogin. Let's add one or use button state.
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            etEmail.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            etEmail.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) etEmail.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            }
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Authenticating..."
            loginLecturer(email, password, btnLogin)
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, LecturerRegisterActivity::class.java))
        }

        findViewById<TextView>(R.id.tvResendVerification).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter email above", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Attempting login will trigger verification resend.", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter email to receive reset link", Toast.LENGTH_SHORT).show()
            } else {
                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Reset link sent to $email", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<TextView>(R.id.tvChangeRole).setOnClickListener {
            val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            prefs.edit().remove("role").apply()
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun setupThemeToggle() {
        val toggleView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.themeToggleCompose)
        toggleView.setContent {
            AutoAttendanceTheme {
                com.example.autoattendance.ui.components.ThemeToggle()
            }
        }
    }

    private fun loginLecturer(email: String, pass: String, btn: Button) {
        auth.signInWithEmailAndPassword(email, pass).addOnSuccessListener {
            val user = auth.currentUser
            user?.reload()?.addOnCompleteListener {
                if (user?.isEmailVerified == true) {
                    val uid = user.uid
                    
                    firestore.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                        if (!snapshot.exists()) {
                            Toast.makeText(this, "Lecturer profile not found", Toast.LENGTH_LONG).show()
                            btn.isEnabled = true
                            btn.text = "Login"
                            auth.signOut()
                            return@addOnSuccessListener
                        }

                        val role = snapshot.getString("role")
                        if (role != "lecturer" && role != "institutionHead") {
                            Toast.makeText(this, "Access Denied: Invalid role", Toast.LENGTH_SHORT).show()
                            btn.isEnabled = true
                            btn.text = "Login"
                            auth.signOut()
                            return@addOnSuccessListener
                        }

                        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                        userPrefs.edit()
                            .putString("role", role)
                            .putString("uid", uid)
                            .putString("name", snapshot.getString("name"))
                            .putString("email", snapshot.getString("email"))
                            .putString("state", snapshot.getString("stateId"))
                            .putString("city", snapshot.getString("cityId"))
                            .putString("institutionId", snapshot.getString("institutionId"))
                            .putString("institution", snapshot.getString("institutionId"))
                            .apply()

                        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LecturerActivity::class.java))
                        finish()
                    }
                } else {
                    Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                    btn.isEnabled = true
                    btn.text = "Login"
                    user?.sendEmailVerification()
                    auth.signOut()
                }
            }
        }.addOnFailureListener {
            btn.isEnabled = true
            btn.text = "Login"
            Toast.makeText(this, "Login Failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
