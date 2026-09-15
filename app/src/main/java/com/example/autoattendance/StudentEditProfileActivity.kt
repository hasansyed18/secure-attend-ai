package com.example.autoattendance

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentEditProfileActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var currentUsn: String = ""
    private var instId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_edit_profile)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUsn = prefs.getString("usn", "") ?: ""
        instId = prefs.getString("institutionId", "") ?: ""

        val tvFixedInfo = findViewById<TextView>(R.id.tvFixedInfo)
        val etName = findViewById<TextInputEditText>(R.id.etEditName)
        val spinnerBranch = findViewById<Spinner>(R.id.spinnerEditBranch)
        val spinnerSemester = findViewById<Spinner>(R.id.spinnerEditSemester)
        val etSection = findViewById<TextInputEditText>(R.id.etEditSection)
        val btnUpdate = findViewById<Button>(R.id.btnUpdateProfile)

        // Setup Spinners
        val branches = arrayOf("CSE", "AI", "EEE", "ECE", "Mechanical", "Civil")
        spinnerBranch.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, branches)
        val semesters = arrayOf("1", "2", "3", "4", "5", "6", "7", "8")
        spinnerSemester.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, semesters)

        // Load current data from SharedPreferences
        val name = prefs.getString("name", "")
        val dept = prefs.getString("department", "CSE")
        val sem = prefs.getString("semester", "1")
        val sec = prefs.getString("section", "")
        val inst = prefs.getString("institution", "")
        val batch = prefs.getInt("batch", 0).toString()

        etName.setText(name)
        etSection.setText(sec)
        spinnerBranch.setSelection(branches.indexOf(dept).coerceAtLeast(0))
        spinnerSemester.setSelection(semesters.indexOf(sem).coerceAtLeast(0))

        // 🔒 NON-EDITABLE DISPLAY: USN, Batch, Institution, State, City
        tvFixedInfo.text = """
            USN: $currentUsn
            Batch Year: $batch
            Institution: $inst
        """.trimIndent()

        btnUpdate.setOnClickListener {
            val updatedName = etName.text.toString().trim()
            val updatedDept = spinnerBranch.selectedItem.toString()
            val updatedSem = spinnerSemester.selectedItem.toString()
            val updatedSec = etSection.text.toString().trim().uppercase()

            if (updatedName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            updateProfileInFirestore(updatedName, updatedDept, updatedSem, updatedSec)
        }
    }

    private fun updateProfileInFirestore(name: String, dept: String, sem: String, sec: String) {
        val uid = auth.currentUser?.uid ?: return
        
        val updates = mapOf(
            "name" to name,
            "department" to dept,
            "semester" to sem,
            "section" to sec
        )

        val batch = firestore.batch()
        
        // 1. Update primary profile
        val userRef = firestore.collection("users").document(uid)
        batch.update(userRef, updates)

        // 2. Update Student Directory entry (if USN and InstId are available)
        if (currentUsn.isNotEmpty() && instId.isNotEmpty()) {
            val directoryId = "${instId}_${currentUsn.uppercase()}"
            val directoryRef = firestore.collection("student_directory").document(directoryId)
            batch.update(directoryRef, updates)
        }

        batch.commit().addOnSuccessListener {
            // Update local storage
            val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("name", name)
                .putString("department", dept)
                .putString("semester", sem)
                .putString("section", sec)
                .apply()

            Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Update Failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
