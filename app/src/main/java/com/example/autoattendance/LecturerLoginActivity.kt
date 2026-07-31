package com.example.autoattendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LecturerLoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lecturer_login)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginLecturer(email, password)
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, LecturerRegisterActivity::class.java))
        }
    }

    private fun loginLecturer(email: String, pass: String) {
        auth.signInWithEmailAndPassword(email, pass).addOnSuccessListener {
            val uid = auth.currentUser?.uid ?: ""
            
            firestore.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Toast.makeText(this, "Lecturer profile not found", Toast.LENGTH_LONG).show()
                    auth.signOut()
                    return@addOnSuccessListener
                }

                val role = snapshot.getString("role")
                if (role != "lecturer" && role != "institutionHead") {
                    Toast.makeText(this, "Access Denied: Invalid role", Toast.LENGTH_SHORT).show()
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
        }.addOnFailureListener {
            Toast.makeText(this, "Login Failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
