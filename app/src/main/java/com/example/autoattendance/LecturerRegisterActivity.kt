package com.example.autoattendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.autoattendance.models.LecturerProfile
import com.example.autoattendance.ui.theme.AutoAttendanceTheme
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LecturerRegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var autoState: AutoCompleteTextView
    private lateinit var autoCity: AutoCompleteTextView
    private lateinit var autoInstitution: AutoCompleteTextView
    private lateinit var progressBar: ProgressBar

    private var selectedStateId: String? = null
    private var selectedCityId: String? = null
    private var selectedInstitutionId: String? = null

    private val stateMap = mutableMapOf<String, String>() // name -> id
    private val cityMap = mutableMapOf<String, String>() // name -> id
    private val instMap = mutableMapOf<String, String>() // name -> id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.autoattendance.ui.theme.ThemeConfig.load(this)
        setContentView(R.layout.activity_lecturer_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupThemeToggle()
        initViews()
        setupDropdowns()
        fetchStates()

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            registerLecturer()
        }

        findViewById<TextView>(R.id.tvRequestInstitution).setOnClickListener {
            showRequestInstitutionDialog()
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

    private fun initViews() {
        autoState = findViewById(R.id.autoCompleteState)
        autoCity = findViewById(R.id.autoCompleteCity)
        autoInstitution = findViewById(R.id.autoCompleteInstitution)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupDropdowns() {
        autoState.setOnItemClickListener { parent, _, position, _ ->
            val stateName = parent.getItemAtPosition(position) as String
            selectedStateId = stateMap[stateName]
            Log.d("Registration", "Selected state: $stateName, ID: $selectedStateId")
            autoCity.setText("")
            autoInstitution.setText("")
            if (selectedStateId != null) fetchCities(selectedStateId!!)
        }

        autoCity.setOnItemClickListener { parent, _, position, _ ->
            val cityName = parent.getItemAtPosition(position) as String
            selectedCityId = cityMap[cityName]
            Log.d("Registration", "Selected city: $cityName, ID: $selectedCityId")
            autoInstitution.setText("")
            if (selectedStateId != null && selectedCityId != null) fetchInstitutions(selectedStateId!!, selectedCityId!!)
        }

        autoInstitution.setOnItemClickListener { parent, _, position, _ ->
            val instName = parent.getItemAtPosition(position) as String
            selectedInstitutionId = instMap[instName]
            Log.d("Registration", "Selected institution: $instName, ID: $selectedInstitutionId")
        }
    }

    private fun fetchStates() {
        firestore.collection("states").get().addOnSuccessListener { snapshot ->
            stateMap.clear()
            snapshot.documents.forEach { doc ->
                val name = doc.getString("name")
                val id = doc.getString("stateId")
                if (name != null && id != null) stateMap[name] = id
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, stateMap.keys.toList())
            autoState.setAdapter(adapter)
        }
    }

    private fun fetchCities(stateId: String) {
        firestore.collection("cities")
            .whereEqualTo("stateId", stateId)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d("Registration", "fetchCities: ${snapshot.size()} cities returned for state $stateId")
                cityMap.clear()
                snapshot.documents.forEach { doc ->
                    val name = doc.getString("name")
                    val id = doc.getString("cityId")
                    if (name != null && id != null) cityMap[name] = id
                }
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, cityMap.keys.toList())
                autoCity.setAdapter(adapter)
            }
            .addOnFailureListener { e ->
                Log.e("Registration", "fetchCities failed", e)
                Toast.makeText(this, "Failed to load cities", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchInstitutions(stateId: String, cityId: String) {
        firestore.collection("institutions")
            .whereEqualTo("stateId", stateId)
            .whereEqualTo("cityId", cityId)
            .whereEqualTo("status", "verified")
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d("Registration", "fetchInstitutions: ${snapshot.size()} institutions returned for state $stateId, city $cityId")
                instMap.clear()
                snapshot.documents.forEach { doc ->
                    val name = doc.getString("name")
                    val id = doc.getString("institutionId")
                    if (name != null && id != null) instMap[name] = id
                }
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, instMap.keys.toList())
                autoInstitution.setAdapter(adapter)
            }
            .addOnFailureListener { e ->
                Log.e("Registration", "fetchInstitutions failed", e)
                Toast.makeText(this, "Failed to load institutions", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRequestInstitutionDialog() {
        if (selectedStateId == null || selectedCityId == null) {
            Toast.makeText(this, "Please select state and city first", Toast.LENGTH_SHORT).show()
            return
        }

        val etInput = EditText(this)
        etInput.hint = "Institution Name"
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Request New Institution")
            .setView(etInput)
            .setPositiveButton("Submit") { dialog, which ->
                val name = etInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    requestInstitution(name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestInstitution(name: String) {
        val request = mapOf(
            "institutionName" to name,
            "stateId" to selectedStateId,
            "cityId" to selectedCityId,
            "status" to "pending",
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        firestore.collection("institution_requests").add(request).addOnSuccessListener {
            Toast.makeText(this, "Request submitted. Admin will verify soon.", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerLecturer() {
        val name = findViewById<TextInputEditText>(R.id.etName).text.toString().trim()
        val email = findViewById<TextInputEditText>(R.id.etEmail).text.toString().trim()
        val password = findViewById<TextInputEditText>(R.id.etPassword).text.toString().trim()

        Log.d("Registration", "registerLecturer: Starting for $email")

        if (name.isEmpty() || email.isEmpty() || password.length < 6 || 
            selectedInstitutionId == null) {
            Log.d("Registration", "registerLecturer: Validation failed.")
            Toast.makeText(this, "Please fill all details correctly", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        findViewById<Button>(R.id.btnRegister).isEnabled = false

        Log.d("Registration", "registerLecturer: Calling Auth createUser")
        auth.createUserWithEmailAndPassword(email, password).addOnSuccessListener { authResult ->
            val uid = authResult.user?.uid ?: ""
            Log.d("Registration", "registerLecturer: Auth Success, UID: $uid")
            
            val profile = LecturerProfile(
                uid = uid,
                role = "lecturer",
                name = name,
                email = email,
                stateId = selectedStateId!!,
                cityId = selectedCityId!!,
                institutionId = selectedInstitutionId!!,
                createdAt = System.currentTimeMillis()
            )

            Log.d("Registration", "registerLecturer: Writing to Firestore")
            firestore.collection("users").document(uid).set(profile)
                .addOnSuccessListener {
                    // 📧 Send Email Verification
                    auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { verifyTask ->
                        progressBar.visibility = android.view.View.GONE
                        if (verifyTask.isSuccessful) {
                            Log.d("Registration", "Verification email sent to $email")
                            Toast.makeText(this, "Verification email sent. Please verify before logging in.", Toast.LENGTH_LONG).show()
                        } else {
                            Log.e("Registration", "Failed to send verification email", verifyTask.exception)
                            Toast.makeText(this, "Account created, but failed to send verification email.", Toast.LENGTH_SHORT).show()
                        }
                        
                        // Sign out after registration
                        auth.signOut()
                        getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().clear().apply()

                        // Redirect to Login
                        val intent = Intent(this, LecturerLoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Registration", "registerLecturer: Firestore Failure", e)
                    progressBar.visibility = View.GONE
                    findViewById<Button>(R.id.btnRegister).isEnabled = true
                    Toast.makeText(this, "Database Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }.addOnFailureListener { e ->
            Log.e("Registration", "registerLecturer: Auth Failure", e)
            progressBar.visibility = View.GONE
            findViewById<Button>(R.id.btnRegister).isEnabled = true
            Toast.makeText(this, "Registration Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
