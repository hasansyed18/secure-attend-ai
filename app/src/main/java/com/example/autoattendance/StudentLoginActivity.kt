package com.example.autoattendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
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
        setContentView(R.layout.activity_student_login)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        credentialManager = CredentialManager.create(this)

        val etEmail = findViewById<TextInputEditText>(R.id.etLoginEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etLoginPassword)
        val btnLogin = findViewById<Button>(R.id.btnLoginStudent)
        val tvRegister = findViewById<TextView>(R.id.tvGoToRegister)

        tryRetrieveCredentials(etEmail, etPassword)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(email, password)
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, StudentProfileActivity::class.java))
            finish()
        }
    }

    private fun tryRetrieveCredentials(etEmail: TextInputEditText, etPassword: TextInputEditText) {
        val getPasswordOption = GetPasswordOption()
        val getCredRequest = GetCredentialRequest(listOf(getPasswordOption))

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@StudentLoginActivity, getCredRequest)
                val credential = result.credential
                if (credential is androidx.credentials.PasswordCredential) {
                    etEmail.setText(credential.id)
                    etPassword.setText(credential.password)
                    loginUser(credential.id, credential.password)
                }
            } catch (e: GetCredentialException) {
                Log.d("Auth", "No saved credentials found")
            }
        }
    }

    private fun loginUser(email: String, pass: String) {
        val currentDeviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        auth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = auth.currentUser?.uid ?: ""
                
                // Fetch from flat users collection
                firestore.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Profile not found", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        return@addOnSuccessListener
                    }

                    val role = snapshot.getString("role")
                    if (role != "student") {
                        Toast.makeText(this, "Invalid account type", Toast.LENGTH_SHORT).show()
                        auth.signOut()
                        return@addOnSuccessListener
                    }

                    val registeredDeviceId = snapshot.getString("deviceId")
                    if (registeredDeviceId != null && registeredDeviceId != currentDeviceId) {
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
                Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
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
