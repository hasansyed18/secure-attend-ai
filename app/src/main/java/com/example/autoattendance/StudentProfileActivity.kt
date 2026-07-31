package com.example.autoattendance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialException
import androidx.lifecycle.lifecycleScope
import com.example.autoattendance.models.StudentProfile
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class StudentProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var credentialManager: CredentialManager

    private lateinit var autoState: AutoCompleteTextView
    private lateinit var autoCity: AutoCompleteTextView
    private lateinit var autoInstitution: AutoCompleteTextView
    private lateinit var autoBranch: AutoCompleteTextView
    private lateinit var autoSemester: AutoCompleteTextView
    private lateinit var progressBar: ProgressBar

    private var selectedStateId: String? = null
    private var selectedCityId: String? = null
    private var selectedInstitutionId: String? = null

    private val stateMap = mutableMapOf<String, String>() // name -> id
    private val cityMap = mutableMapOf<String, String>() // name -> id
    private val instMap = mutableMapOf<String, String>() // name -> id

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        credentialManager = CredentialManager.create(this)

        initViews()
        setupDropdowns()
        fetchStates()

        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            validateAndRegister()
        }

        findViewById<TextView>(R.id.tvRequestInstitution).setOnClickListener {
            showRequestInstitutionDialog()
        }

        findViewById<TextView>(R.id.tvBackToLogin).setOnClickListener { finish() }
    }

    private fun initViews() {
        autoState = findViewById(R.id.autoCompleteState)
        autoCity = findViewById(R.id.autoCompleteCity)
        autoInstitution = findViewById(R.id.autoCompleteInstitution)
        autoBranch = findViewById(R.id.autoCompleteBranch)
        autoSemester = findViewById(R.id.autoCompleteSemester)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupDropdowns() {
        val branches = arrayOf("CSE", "AI", "EEE", "ECE", "Mechanical", "Civil")
        autoBranch.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, branches))

        val semesters = arrayOf("1", "2", "3", "4", "5", "6", "7", "8")
        autoSemester.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, semesters))

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

    private fun validateAndRegister() {
        val email = findViewById<TextInputEditText>(R.id.etEmail).text.toString().trim()
        val password = findViewById<TextInputEditText>(R.id.etPassword).text.toString()
        val confirm = findViewById<TextInputEditText>(R.id.etConfirmPassword).text.toString()
        val name = findViewById<TextInputEditText>(R.id.etName).text.toString().trim()
        val usn = findViewById<TextInputEditText>(R.id.etUsn).text.toString().trim().uppercase()
        val dept = autoBranch.text.toString()
        val sem = autoSemester.text.toString()
        val sec = findViewById<TextInputEditText>(R.id.etSection).text.toString().trim().uppercase()
        val batch = findViewById<TextInputEditText>(R.id.etBatchYear).text.toString().trim()

        if (email.isEmpty() || password.isEmpty() || name.isEmpty() || usn.isEmpty() || 
            selectedInstitutionId == null || dept.isEmpty() || sem.isEmpty() || sec.isEmpty() || batch.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirm) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = android.view.View.VISIBLE
        findViewById<Button>(R.id.btnSaveProfile).isEnabled = false

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = task.result?.user?.uid ?: ""
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                
                val profile = StudentProfile(
                    uid = uid,
                    role = "student",
                    name = name,
                    usn = usn,
                    email = email,
                    stateId = selectedStateId!!,
                    cityId = selectedCityId!!,
                    institutionId = selectedInstitutionId!!,
                    department = dept,
                    semester = sem,
                    section = sec,
                    batch = batch.toIntOrNull() ?: 0,
                    deviceId = deviceId,
                    createdAt = System.currentTimeMillis()
                )

                Log.d("Registration", "Writing student to Firestore: users/$uid")

                firestore.collection("users").document(uid).set(profile)
                    .addOnSuccessListener {
                        progressBar.visibility = android.view.View.GONE
                        saveLocalPrefs(profile)
                        saveCredentials(email, password)
                        Toast.makeText(this, "Welcome to SecureAttend!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, StudentActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = android.view.View.GONE
                        findViewById<Button>(R.id.btnSaveProfile).isEnabled = true
                        Log.e("Registration", "Firestore error", e)
                        Toast.makeText(this, "Firestore Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                progressBar.visibility = android.view.View.GONE
                findViewById<Button>(R.id.btnSaveProfile).isEnabled = true
                Log.e("Registration", "Auth error", task.exception)
                Toast.makeText(this, "Registration Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveCredentials(email: String, pass: String) {
        lifecycleScope.launch {
            try {
                val createPasswordRequest = CreatePasswordRequest(email, pass)
                credentialManager.createCredential(this@StudentProfileActivity, createPasswordRequest)
            } catch (e: CreateCredentialException) {
                Log.e("Auth", "Failed to save credential: ${e.message}")
            }
        }
    }

    private fun saveLocalPrefs(p: StudentProfile) {
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("role", "student")
            .putString("uid", p.uid)
            .putString("name", p.name)
            .putString("email", p.email)
            .putString("usn", p.usn)
            .putString("state", p.stateId)
            .putString("city", p.cityId)
            .putString("institutionId", p.institutionId)
            .putString("institution", p.institutionId) // for UI display
            .putString("department", p.department)
            .putString("semester", p.semester)
            .putString("section", p.section)
            .putInt("batch", p.batch)
            .apply()
    }
}
