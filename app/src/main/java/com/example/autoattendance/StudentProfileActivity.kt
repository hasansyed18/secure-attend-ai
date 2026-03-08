package com.example.autoattendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase

class StudentProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        val etName = findViewById<EditText>(R.id.etName)
        val etUsn = findViewById<EditText>(R.id.etUsn)
        val spinnerBranch = findViewById<Spinner>(R.id.spinnerBranch)
        val spinnerSemester = findViewById<Spinner>(R.id.spinnerSemester)
        val etSection = findViewById<EditText>(R.id.etSection)
        val btnSave = findViewById<Button>(R.id.btnSaveProfile)

        val branches = arrayOf("CSE", "AI", "EEE", "ECE", "Mechanical", "Civil")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, branches)
        spinnerBranch.adapter = adapter

        val semesters = arrayOf(
            "SEM-1", "SEM-2", "SEM-3", "SEM-4", "SEM-5", "SEM-6", "SEM-7", "SEM-8"
        )
        val semAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, semesters)
        spinnerSemester.adapter = semAdapter

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val usn = etUsn.text.toString().trim().uppercase()
            val selectedBranch = spinnerBranch.selectedItem.toString()
            val selectedSemester = spinnerSemester.selectedItem.toString()
            val selectedSection = etSection.text.toString().trim()

            if (name.isEmpty() || usn.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidUSN(usn, selectedBranch)) {
                return@setOnClickListener
            }

            saveProfile(name, usn, selectedBranch, selectedSemester, selectedSection)
        }
    }

    private fun isValidUSN(usn: String, branch: String): Boolean {
        if (usn.length != 10) {
            Toast.makeText(this, "Invalid USN: Must be 10 characters", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!usn.startsWith("3LA") && !usn.startsWith("3VN")) {
            Toast.makeText(this, "Invalid USN: Must start with College Code '3LA' or '3VN'", Toast.LENGTH_SHORT).show()
            return false
        }
        val branchCodeMap = mapOf(
            "CSE" to "CS", "AI" to "AI", "EEE" to "EE", "ECE" to "EC", "Mechanical" to "ME", "Civil" to "CV"
        )
        val expectedCode = branchCodeMap[branch]
        val actualCode = usn.substring(5, 7)
        if (expectedCode != actualCode) {
            Toast.makeText(this, "USN doesn't match selected branch ($branch should be $expectedCode)", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun saveProfile(name: String, usn: String, branch: String, semester: String, section: String) {
        val database = FirebaseDatabase.getInstance()
        val studentRef = database.reference.child("students").child(usn)

        // Capture unique Device ID
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        studentRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                Toast.makeText(this, "This USN is already registered!", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            val studentData = mapOf(
                "name" to name,
                "usn" to usn,
                "department" to branch,
                "semester" to semester,
                "section" to section,
                "deviceId" to deviceId, // 🔐 Bind Device ID here
                "createdAt" to System.currentTimeMillis()
            )

            studentRef.setValue(studentData).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    userPrefs.edit()
                        .putString("role", "student")
                        .putString("name", name)
                        .putString("usn", usn)
                        .putString("department", branch)
                        .putString("semester", semester)
                        .putString("section", section)
                        .putString("deviceId", deviceId)
                        .apply()

                    Toast.makeText(this, "Profile Registered & Bound to this Device!", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, StudentActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Database error: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
