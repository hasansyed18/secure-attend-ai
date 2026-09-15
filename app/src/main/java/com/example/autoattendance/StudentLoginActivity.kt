package com.example.autoattendance

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.autoattendance.ui.theme.AutoAttendanceTheme
import com.example.autoattendance.ui.theme.ThemeConfig
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class StudentLoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.autoattendance.ui.theme.ThemeConfig.load(this)
        setContentView(R.layout.activity_student_login)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        credentialManager = CredentialManager.create(this)

        val etEmail = findViewById<TextInputEditText>(R.id.etLoginEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etLoginPassword)
        val btnLogin = findViewById<Button>(R.id.btnLoginStudent)
        val tvRegister = findViewById<TextView>(R.id.tvGoToRegister)
        val progressBar = findViewById<ProgressBar>(R.id.pbLogin)

        setupThemeToggle()

        // 🚀 Issue: Email Suggestions Appearing Too Early
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            etEmail.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            etEmail.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    etEmail.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                }
            }
        }

        tryRetrieveCredentials(etEmail, etPassword, btnLogin, progressBar)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            progressBar.visibility = View.VISIBLE
            loginUser(email, password, btnLogin, progressBar)
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, StudentProfileActivity::class.java))
            finish()
        }

        findViewById<TextView>(R.id.tvResendVerification).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address above", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this, "Password reset link sent to $email", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter email to receive reset link", Toast.LENGTH_SHORT).show()
            } else {
                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Password reset link sent to $email", Toast.LENGTH_LONG).show()
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

    private fun tryRetrieveCredentials(etEmail: TextInputEditText, etPassword: TextInputEditText, btn: Button, pb: ProgressBar) {
        val getPasswordOption = GetPasswordOption()
        val getCredRequest = GetCredentialRequest(listOf(getPasswordOption))

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@StudentLoginActivity, getCredRequest)
                val credential = result.credential
                if (credential is androidx.credentials.PasswordCredential) {
                    etEmail.setText(credential.id)
                    etPassword.setText(credential.password)
                    btn.isEnabled = false
                    pb.visibility = View.VISIBLE
                    loginUser(credential.id, credential.password, btn, pb)
                }
            } catch (e: GetCredentialException) {
                Log.d("Auth", "No saved credentials found")
            }
        }
    }

    private fun loginUser(email: String, pass: String, btn: Button, pb: ProgressBar) {
        val currentDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser
                user?.reload()?.addOnCompleteListener {
                    if (user.isEmailVerified) {
                        val uid = user.uid
                        firestore.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                            if (!snapshot.exists()) {
                                Toast.makeText(this, "Profile not found", Toast.LENGTH_LONG).show()
                                btn.isEnabled = true
                                pb.visibility = View.GONE
                                auth.signOut()
                                return@addOnSuccessListener
                            }

                            val role = snapshot.getString("role")
                            if (role != "student") {
                                Toast.makeText(this, "Invalid account type", Toast.LENGTH_SHORT).show()
                                btn.isEnabled = true
                                pb.visibility = View.GONE
                                auth.signOut()
                                return@addOnSuccessListener
                            }

                            val registeredDeviceId = snapshot.getString("deviceId")
                            if (registeredDeviceId != null && registeredDeviceId != currentDeviceId) {
                                btn.isEnabled = true
                                pb.visibility = View.GONE
                                auth.signOut()
                                Toast.makeText(this, "Access Denied: Different device!", Toast.LENGTH_LONG).show()
                                return@addOnSuccessListener
                            }

                            saveLocalPrefs(snapshot)
                            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, StudentActivity::class.java))
                            finish()
                        }
                    } else {
                        Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show()
                        btn.isEnabled = true
                        pb.visibility = View.GONE
                        user.sendEmailVerification()
                        auth.signOut()
                    }
                }
            } else {
                btn.isEnabled = true
                pb.visibility = View.GONE
                Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
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

    private fun saveLocalPrefs(snap: com.google.firebase.firestore.DocumentSnapshot) {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        userPrefs.edit()
            .putString("role", "student")
            .putString("uid", snap.id)
            .putString("name", snap.getString("name"))
            .putString("email", snap.getString("email"))
            .putString("usn", snap.getString("usn"))
            .putString("state", snap.getString("stateId"))
            .putString("city", snap.getString("cityId"))
            .putString("institutionId", snap.getString("institutionId"))
            .putString("institution", snap.getString("institutionId"))
            .putString("department", snap.getString("department"))
            .putString("semester", snap.getString("semester"))
            .putString("section", snap.getString("section"))
            .putInt("batch", snap.getLong("batch")?.toInt() ?: 0)
            .putString("deviceId", snap.getString("deviceId"))
            .apply()
    }
}
