package com.example.autoattendance

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.autoattendance.ui.Screen
import com.example.autoattendance.ui.components.AppDrawer
import com.example.autoattendance.ui.theme.AutoAttendanceTheme
import com.example.autoattendance.ui.theme.ThemeConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class LecturerActivity : AppCompatActivity() {

    private companion object {
        const val PREFS_NAME = "LecturerSession"
        const val KEY_SESSION_ID = "active_session_id"
        const val KEY_SUBJECT_ID = "active_subject_id"
        const val KEY_SUBJECT_NAME = "active_subject_name"
    }

    private lateinit var tvSystemStatus: TextView
    private lateinit var statusDot: View
    private lateinit var tvPresentCountLarge: TextView
    private lateinit var tvAttendanceRate: TextView
    private lateinit var tvSuspiciousCount: TextView
    private lateinit var tvActiveSubjectName: TextView
    private lateinit var tvActiveSessionDetails: TextView
    private lateinit var tvLiveIndicator: TextView
    private lateinit var tvSessionPin: TextView
    
    private lateinit var tvWelcome: TextView
    private lateinit var lvStudentList: ListView
    private lateinit var btnStartClass: Button
    private lateinit var btnStopClass: Button
    private lateinit var btnAddSubject: Button
    private lateinit var btnExportCsv: Button
    private lateinit var btnMenu: ImageView
    private lateinit var cardManualAttendance: View
    private lateinit var etSearchUsn: EditText
    private lateinit var btnMarkManual: Button
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerComposeView: ComposeView

    private lateinit var customAdapter: StudentListAdapter
    private val detectionList = mutableListOf<StudentDetection>()

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    
    private var institutionId: String? = null
    private var currentSessionId: String? = null
    private var sessionListener: ListenerRegistration? = null
    
    data class StudentDetection(
        val usn: String,
        val studentName: String,
        val rssi: Int,
        val timestamp: Long,
        val isSuspect: Boolean
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) checkBluetoothAndStart()
        else Toast.makeText(this, "Permissions required.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeConfig.load(this)
        Log.d("Routing", "LecturerActivity: onCreate started")
        if (auth.currentUser == null) {
            Log.d("Routing", "LecturerActivity: No user logged in, routing to LecturerLoginActivity")
            startActivity(Intent(this, LecturerLoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_lecturer)
        
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        institutionId = userPrefs.getString("institutionId", null)
        Log.d("Routing", "LecturerActivity: institutionId: $institutionId")
        
        setupThemeToggle()
        initViews()
        refreshProfileUI(userPrefs)
        setupDrawer(userPrefs)
        restoreSession()
    }

    private fun setupThemeToggle() {
        val toggleView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.themeToggleCompose)
        toggleView.setContent {
            AutoAttendanceTheme {
                com.example.autoattendance.ui.components.ThemeToggle()
            }
        }
    }

    private fun refreshProfileUI(prefs: SharedPreferences) {
        val name = prefs.getString("name", "Lecturer") ?: "Lecturer"
        tvWelcome.text = "Welcome back, $name"
    }

    override fun onResume() {
        super.onResume()
        refreshProfileUI(getSharedPreferences("UserPrefs", MODE_PRIVATE))
    }

    private fun setupDrawer(prefs: SharedPreferences) {
        val name = prefs.getString("name", "Lecturer") ?: "Lecturer"
        val email = prefs.getString("email", "") ?: ""
        val role = prefs.getString("role", "lecturer") ?: "lecturer"
        val isDark = ThemeConfig.isDarkMode.value ?: false

        drawerComposeView.setContent {
            AutoAttendanceTheme {
                AppDrawer(
                    currentRoute = Screen.Dashboard.route,
                    onNavigate = { screen ->
                        drawerLayout.closeDrawer(GravityCompat.START)
                        handleNavigation(screen)
                    },
                    userName = name,
                    userEmail = email,
                    userRole = role,
                    isDarkMode = isDark
                )
            }
        }
    }

    private fun handleNavigation(screen: Screen) {
        when (screen) {
            Screen.Dashboard -> { /* Already here */ }
            Screen.Logout -> confirmLogout()
            else -> {
                val intent = Intent(this, HomeActivity::class.java).apply {
                    putExtra("TARGET_SCREEN", screen.route)
                }
                startActivity(intent)
            }
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this).setTitle("Logout").setMessage("Logout from SecureAttend?").setPositiveButton("Logout") { _, _ -> 
            auth.signOut()
            val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val role = prefs.getString("role", "lecturer")
            prefs.edit().clear().apply()
            
            getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit()
                .putString("role", role)
                .apply()

            startActivity(Intent(this, LecturerLoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish() 
        }.setNegativeButton("Cancel", null).show()
    }

    private fun initViews() {
        tvSystemStatus = findViewById(R.id.tvSystemStatus); statusDot = findViewById(R.id.statusDot)
        tvPresentCountLarge = findViewById(R.id.tvPresentCountLarge); tvAttendanceRate = findViewById(R.id.tvAttendanceRate)
        tvSuspiciousCount = findViewById(R.id.tvSuspiciousCount); tvActiveSubjectName = findViewById(R.id.tvActiveSubjectName)
        tvActiveSessionDetails = findViewById(R.id.tvActiveSessionDetails); tvLiveIndicator = findViewById(R.id.tvLiveIndicator)
        tvSessionPin = findViewById(R.id.tvSessionPin)
        tvWelcome = findViewById(R.id.tvWelcome)
        lvStudentList = findViewById(R.id.lvStudentList); btnStartClass = findViewById(R.id.btnStartClass)
        btnStopClass = findViewById(R.id.btnStopClass); btnAddSubject = findViewById(R.id.btnAddSubject)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        btnMenu = findViewById(R.id.btnMenu)
        cardManualAttendance = findViewById(R.id.cardManualAttendance)
        etSearchUsn = findViewById(R.id.etSearchUsn)
        btnMarkManual = findViewById(R.id.btnMarkManual)
        drawerLayout = findViewById(R.id.drawerLayout)
        drawerComposeView = findViewById(R.id.drawerComposeView)
        
        customAdapter = StudentListAdapter(this, detectionList); lvStudentList.adapter = customAdapter
        
        btnStartClass.setOnClickListener { startClassProcess() }
        btnStopClass.setOnClickListener { stopClassSession() }
        btnAddSubject.setOnClickListener { openAddSubjectDialog() }
        btnExportCsv.setOnClickListener { exportAttendanceToCsv() }
        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        btnMarkManual.setOnClickListener { markAttendanceManually() }
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        findViewById<View>(android.R.id.content).alpha = 0f
        findViewById<View>(android.R.id.content).animate().alpha(1f).setDuration(500).start()
    }

    private fun startClassProcess() {
        val required = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) checkBluetoothAndStart() else requestPermissionLauncher.launch(missing.toTypedArray())
    }

    private fun checkBluetoothAndStart() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager.adapter?.isEnabled != true) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }
        showSubjectSelectionDialog()
    }

    private fun activateClassSession(subject: Subject, subjectId: String) {
        val instId = institutionId ?: return
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val lecturerName = userPrefs.getString("name", "Lecturer") ?: "Lecturer"
        
        val sessionId = "S${System.currentTimeMillis()}"
        val securityToken = UUID.randomUUID().toString().substring(0, 6).uppercase()
        val sessionPin = (1000..9999).random().toString()
        currentSessionId = sessionId
        
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_SUBJECT_ID, subjectId)
            .putString(KEY_SUBJECT_NAME, subject.subjectName)
            .putString("active_pin", sessionPin)
            .apply()
        
        tvActiveSubjectName.text = subject.subjectName
        tvActiveSessionDetails.text = "[${subject.subjectCode}] ${subject.department} • Sem ${subject.semester}"

        val startTime = com.google.firebase.Timestamp.now()
        val expiresAt = com.google.firebase.Timestamp(Date(startTime.toDate().time + 900000))

        val sessionData = hashMapOf<String, Any>(
            "sessionId" to sessionId,
            "securityToken" to securityToken,
            "pin" to sessionPin,
            "institutionId" to instId,
            "subjectId" to subjectId,
            "subjectCode" to subject.subjectCode,
            "subjectName" to subject.subjectName,
            "department" to subject.department,
            "semester" to subject.semester,
            "section" to subject.section,
            "batch" to subject.batch,
            "lecturerId" to (auth.currentUser?.uid ?: ""), 
            "lecturerName" to lecturerName,
            "startTime" to startTime,
            "expiresAt" to expiresAt,
            "status" to "active"
        )

        firestore.collection("attendance_sessions").document(sessionId).set(sessionData)
            .addOnSuccessListener {
                AuditLogger.log("SESSION_CREATED", auth.currentUser?.uid ?: "unknown", "lecturer", instId, sessionId, null, subjectId)
                
                val intent = Intent(this, BleAdvertisingService::class.java).apply { 
                    putExtra("SESSION_ID", sessionId)
                    putExtra("SECURITY_TOKEN", securityToken)
                }
                ContextCompat.startForegroundService(this, intent)
                
                updateUiActive(isActive = true)
                listenForAttendance(sessionId)
            }
            .addOnFailureListener { e ->
                Log.e("LecturerActivity", "Failed to create session", e)
                Toast.makeText(this, "Failed to start session: ${e.message}", Toast.LENGTH_LONG).show()
                btnStartClass.isEnabled = true
            }
    }

    private fun stopClassSession() {
        val sessionId = currentSessionId ?: return
        stopService(Intent(this, BleAdvertisingService::class.java))
        sessionListener?.remove()
        sessionListener = null
        
        firestore.collection("attendance_sessions").document(sessionId).update("status", "completed", "endTime", com.google.firebase.Timestamp.now())

        AuditLogger.log("SESSION_ENDED", auth.currentUser?.uid ?: "unknown", "lecturer", institutionId ?: "unknown", sessionId)

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
        currentSessionId = null; updateUiActive(false); btnExportCsv.visibility = View.VISIBLE
    }

    private fun listenForAttendance(sessionId: String) {
        sessionListener = firestore.collection("attendance_records")
            .whereEqualTo("sessionId", sessionId)
            .whereEqualTo("institutionId", institutionId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                customAdapter.clear()
                var suspectCount = 0
                val tempList = mutableListOf<StudentDetection>()
                snapshot?.documents?.forEach { doc ->
                    val usn = doc.getString("studentUsn") ?: "---"
                    val name = doc.getString("studentName") ?: "Unknown"
                    val rssi = doc.getLong("rssi")?.toInt() ?: 0
                    val ts = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                    val status = doc.getString("status") ?: "present"
                    val isSuspect = status == "suspect"
                    if (isSuspect) suspectCount++
                    tempList.add(StudentDetection(usn, name, rssi, ts, isSuspect))
                }
                tempList.sortByDescending { it.timestamp }
                customAdapter.addAll(tempList)
                customAdapter.notifyDataSetChanged()
                
                tvPresentCountLarge.text = "${tempList.size}"
                tvSuspiciousCount.text = "$suspectCount"
                tvAttendanceRate.text = "${tempList.size} Present"
            }
    }

    private fun updateUiActive(isActive: Boolean) {
        if (isActive) {
            tvSystemStatus.text = "System Active"
            statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.status_success)
            btnStartClass.visibility = View.GONE; btnStopClass.visibility = View.VISIBLE; tvLiveIndicator.visibility = View.VISIBLE
            tvSessionPin.visibility = View.VISIBLE
            val pin = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString("active_pin", "----")
            tvSessionPin.text = "PIN: $pin"
            cardManualAttendance.visibility = View.VISIBLE
        } else {
            tvSystemStatus.text = "System Inactive"; statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
            btnStartClass.visibility = View.VISIBLE; btnStopClass.visibility = View.GONE; tvLiveIndicator.visibility = View.GONE
            tvSessionPin.visibility = View.GONE
            cardManualAttendance.visibility = View.GONE
        }
    }

    private fun markAttendanceManually() {
        val usn = etSearchUsn.text.toString().trim().uppercase()
        val sessionId = currentSessionId ?: return
        val instId = institutionId ?: return
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val subjectId = prefs.getString(KEY_SUBJECT_ID, "") ?: ""

        if (usn.isEmpty()) {
            Toast.makeText(this, "Enter student USN", Toast.LENGTH_SHORT).show()
            return
        }

        btnMarkManual.isEnabled = false
        val directoryId = "${instId}_${usn}"
        
        firestore.collection("student_directory").document(directoryId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Student not found", Toast.LENGTH_LONG).show()
                    btnMarkManual.isEnabled = true
                    return@addOnSuccessListener
                }

                val name = doc.getString("name") ?: "Unknown"
                val uid = doc.getString("uid") ?: ""

                firestore.collection("attendance_records")
                    .whereEqualTo("sessionId", sessionId)
                    .whereEqualTo("studentId", uid)
                    .whereEqualTo("institutionId", instId)
                    .get()
                    .addOnSuccessListener { dupSnap ->
                        if (!dupSnap.isEmpty) {
                            Toast.makeText(this, "Attendance already marked", Toast.LENGTH_SHORT).show()
                            btnMarkManual.isEnabled = true
                            return@addOnSuccessListener
                        }
                        showManualConfirmDialog(name, usn, uid, sessionId, subjectId, instId)
                    }
            }
            .addOnFailureListener { btnMarkManual.isEnabled = true }
    }

    private fun showManualConfirmDialog(name: String, usn: String, uid: String, sessionId: String, subjectId: String, instId: String) {
        AlertDialog.Builder(this)
            .setTitle("Mark Manual Attendance")
            .setMessage("Mark attendance for $name ($usn)?")
            .setPositiveButton("Confirm") { _, _ ->
                val record = mapOf(
                    "sessionId" to sessionId,
                    "subjectId" to subjectId,
                    "institutionId" to instId,
                    "studentId" to uid,
                    "studentName" to name,
                    "studentUsn" to usn,
                    "status" to "present",
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "rssi" to 0,
                    "gpsValidated" to false,
                    "manualMark" to true
                )
                firestore.collection("attendance_records").add(record)
                    .addOnSuccessListener {
                        AuditLogger.log("ATTENDANCE_MARKED", auth.currentUser?.uid ?: "unknown", "lecturer", instId, sessionId, uid, subjectId, "MANUAL_BY_LECTURER")
                        Toast.makeText(this, "Manual attendance marked!", Toast.LENGTH_SHORT).show()
                        etSearchUsn.setText("")
                        btnMarkManual.isEnabled = true
                    }
            }
            .setNegativeButton("Cancel") { _, _ -> btnMarkManual.isEnabled = true }
            .show()
    }

    private fun restoreSession() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedId = prefs.getString(KEY_SESSION_ID, null) ?: return
        currentSessionId = savedId
        val subName = prefs.getString(KEY_SUBJECT_NAME, "Active Session") ?: "Active Session"
        tvActiveSubjectName.text = subName
        updateUiActive(true)
        listenForAttendance(savedId)
    }

    private fun exportAttendanceToCsv() {
        if (detectionList.isEmpty()) return
        val csvContent = StringBuilder("Student ID,Timestamp,RSSI,Status\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (student in detectionList) { csvContent.append("${student.usn},${dateFormat.format(Date(student.timestamp))},${student.rssi},${if(student.isSuspect) "Suspect" else "Present"}\n") }
        try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SecureAttend_Report_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).apply { write(csvContent.toString().toByteArray()); close() }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@LecturerActivity, "$packageName.fileprovider", file)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share Report"))
        } catch (e: Exception) {}
    }



    private fun showSubjectSelectionDialog() {
        val instId = institutionId ?: return
        val currentUid = auth.currentUser?.uid ?: return
        
        firestore.collection("subjects")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("lecturerId", currentUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val subjects = mutableListOf<Subject>()
                val ids = mutableListOf<String>()
                snapshot.documents.forEach { doc ->
                    doc.toObject(Subject::class.java)?.let { sub ->
                        subjects.add(sub)
                        ids.add(doc.id)
                    }
                }
                if (subjects.isEmpty()) { 
                    Toast.makeText(this@LecturerActivity, "No courses found. Add a subject first.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener 
                }
                val displayNames = subjects.map { "[${it.subjectCode}] ${it.subjectName} (${it.section})" }.toTypedArray()
                AlertDialog.Builder(this@LecturerActivity)
                    .setTitle("Select Active Course")
                    .setItems(displayNames) { _, index -> showSubjectOptions(subjects[index], ids[index]) }
                    .show()
            }
    }

    private fun showSubjectOptions(subject: Subject, id: String) {
        AlertDialog.Builder(this).setTitle(subject.subjectName).setItems(arrayOf("Start Class Session", "Delete Subject")) { _, which ->
            if (which == 0) activateClassSession(subject, id) else deleteSubject(id)
        }.show()
    }

    private fun deleteSubject(id: String) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Permanently remove this course and all associated data?")
            .setPositiveButton("Delete Everything") { _, _ ->
                firestore.collection("subjects").document(id).delete()
                    .addOnSuccessListener { Toast.makeText(this, "Course purged successfully", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }



    private fun openAddSubjectDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_subject, null)
        val etCode = dialogView.findViewById<EditText>(R.id.etSubjectCode)
        val etName = dialogView.findViewById<EditText>(R.id.etSubjectName)
        val spinnerDept = dialogView.findViewById<Spinner>(R.id.spinnerDepartment).apply { adapter = ArrayAdapter(this@LecturerActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("CSE","AI","EEE","ECE","Mechanical","Civil")) }
        val spinnerSem = dialogView.findViewById<Spinner>(R.id.spinnerSemester).apply { adapter = ArrayAdapter(this@LecturerActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("SEM-1","SEM-2","SEM-3","SEM-4","SEM-5","SEM-6","SEM-7","SEM-8")) }
        val etSection = dialogView.findViewById<EditText>(R.id.etSection)
        val etBatch = dialogView.findViewById<EditText>(R.id.etBatch)
        
        AlertDialog.Builder(this).setTitle("Add Subject").setView(dialogView).setPositiveButton("Save") { _, _ ->
            val instId = institutionId ?: return@setPositiveButton
            val code = etCode.text.toString().trim().uppercase()
            val name = etName.text.toString().trim()
            val dept = spinnerDept.selectedItem.toString()
            val sem = spinnerSem.selectedItem.toString().replace("SEM-", "")
            val section = etSection.text.toString().trim().uppercase()
            val batch = etBatch.text.toString().trim()
            
            if (code.isEmpty() || name.isEmpty() || section.isEmpty() || batch.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            firestore.collection("subjects")
                .whereEqualTo("institutionId", instId)
                .whereEqualTo("department", dept)
                .whereEqualTo("semester", sem)
                .whereEqualTo("subjectCode", code)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        Toast.makeText(this, "Subject code $code already exists for this semester", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    val uniqueKey = "${instId}_${code}_${dept}_${sem}_${section}_${batch}".replace(Regex("[.#$\\[\\]]"), "_")
                    val subjectData = mapOf(
                        "institutionId" to instId,
                        "subjectCode" to code,
                        "subjectName" to name,
                        "department" to dept,
                        "semester" to sem,
                        "section" to section,
                        "batch" to batch,
                        "lecturerId" to (auth.currentUser?.uid ?: "")
                    )
                    
                    firestore.collection("subjects").document(uniqueKey).set(subjectData).addOnSuccessListener { 
                        Toast.makeText(this, "Added Successfully", Toast.LENGTH_SHORT).show() 
                    }
                }
        }.setNegativeButton("Cancel", null).show()
    }

    private inner class StudentListAdapter(context: Context, students: List<StudentDetection>) : ArrayAdapter<StudentDetection>(context, 0, students) {
        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.student_list_item, parent, false)
            val student = getItem(position) ?: return view
            
            view.findViewById<TextView>(R.id.tvStudentName).text = student.studentName
            view.findViewById<TextView>(R.id.tvStudentUsn).text = student.usn
            view.findViewById<TextView>(R.id.tvStudentInitials).text = student.studentName.firstOrNull()?.toString()?.uppercase() ?: "?"
            
            val badge = view.findViewById<TextView>(R.id.tvRssiBadge)
            badge.text = if (student.rssi != 0) "${student.rssi} dBm" else "MANUAL"
            
            if (student.isSuspect) { 
                badge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_danger)
            } else { 
                badge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.status_success)
            }
            
            val tvTime = view.findViewById<TextView>(R.id.tvDetectionTime)
            tvTime.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(student.timestamp))
            
            return view
        }
    }

    override fun onDestroy() { super.onDestroy() }
}
