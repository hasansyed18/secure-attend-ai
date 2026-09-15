package com.example.autoattendance

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.autoattendance.models.SessionContract
import com.example.autoattendance.ui.Screen
import com.example.autoattendance.ui.components.AppDrawer
import com.example.autoattendance.ui.components.GamificationSummaryCard
import com.example.autoattendance.ui.theme.AutoAttendanceTheme
import com.example.autoattendance.ui.theme.ThemeConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudentActivity : AppCompatActivity() {

    private var attendanceAttempted = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val ignoredSessions = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvStatus: TextView
    private lateinit var ivStatusIcon: ImageView
    private lateinit var btnMarkAttendance: Button
    private lateinit var btnMenu: ImageView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerComposeView: ComposeView
    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentUsn: TextView
    private lateinit var tvStudentInitials: TextView
    private lateinit var tvStudentDetails: TextView
    
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private var institutionId: String? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            ensureBluetoothEnabled()
        } else { 
            Toast.makeText(this, "Bluetooth permissions are required.", Toast.LENGTH_SHORT).show()
            btnMarkAttendance.isEnabled = true
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (bluetoothAdapter?.isEnabled == true) {
            handler.postDelayed({ startBLEScan() }, 1000) // Small delay to let scanner warm up
        } else {
            resetMarking("Enable Bluetooth.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeConfig.load(this)
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        if (userPrefs.getString("role", null) != "student") { 
            startActivity(Intent(this, StudentLoginActivity::class.java))
            finish()
            return 
        }
        
        setContentView(R.layout.activity_student)
        institutionId = userPrefs.getString("institutionId", null)
        
        setupThemeToggle()
        setupGamificationSummary()
        initViews(userPrefs)
        
        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        btnMarkAttendance.setOnClickListener { 
            btnMarkAttendance.isEnabled = false
            checkPermissionsAndProcess() 
        }
        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        setupDrawer(userPrefs)

        findViewById<View>(android.R.id.content).alpha = 0f
        findViewById<View>(android.R.id.content).animate().alpha(1f).setDuration(500).start()
    }

    private fun setupThemeToggle() {
        val toggleView = findViewById<ComposeView>(R.id.themeToggleCompose)
        toggleView.setContent {
            AutoAttendanceTheme {
                com.example.autoattendance.ui.components.ThemeToggle()
            }
        }
    }

    private fun setupGamificationSummary() {
        val summaryView = findViewById<ComposeView>(R.id.gamificationSummaryCompose)
        summaryView.setContent {
            AutoAttendanceTheme {
                GamificationSummaryCard(onClick = {
                    val intent = Intent(this, HomeActivity::class.java).apply {
                        putExtra("TARGET_SCREEN", Screen.HallOfFame.route)
                    }
                    startActivity(intent)
                })
            }
        }
    }

    private fun setupDrawer(prefs: SharedPreferences) {
        val name = prefs.getString("name", "User") ?: "User"
        val email = prefs.getString("email", "") ?: ""
        val role = prefs.getString("role", "student") ?: "student"
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

    @SuppressLint("SetTextI18n")
    private fun initViews(prefs: SharedPreferences) {
        tvStatus = findViewById(R.id.tvStatus)
        ivStatusIcon = findViewById(R.id.ivStatusIcon)
        btnMarkAttendance = findViewById(R.id.btnMarkAttendance)
        btnMenu = findViewById(R.id.btnMenu)
        drawerLayout = findViewById(R.id.drawerLayout)
        drawerComposeView = findViewById(R.id.drawerComposeView)
        tvStudentName = findViewById(R.id.tvStudentName)
        tvStudentUsn = findViewById(R.id.tvStudentUsn)
        tvStudentInitials = findViewById(R.id.tvStudentInitials)
        tvStudentDetails = findViewById(R.id.tvStudentDetails)
        refreshProfileUI(prefs)
    }

    @SuppressLint("SetTextI18n")
    private fun refreshProfileUI(prefs: SharedPreferences) {
        val name = prefs.getString("name", "Student") ?: "Student"
        val usn = prefs.getString("usn", "---") ?: "---"
        val dept = prefs.getString("department", "---") ?: "---"
        val sem = prefs.getString("semester", "---") ?: "---"
        val sec = prefs.getString("section", "---") ?: "---"
        tvStudentName.text = name
        tvStudentUsn.text = "USN: $usn"
        tvStudentInitials.text = name.split(" ").asSequence().mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
        tvStudentDetails.text = "$dept | Sem $sem | Sec $sec"
    }

    override fun onResume() {
        super.onResume()
        refreshProfileUI(getSharedPreferences("UserPrefs", MODE_PRIVATE))
    }

    private fun checkPermissionsAndProcess() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { 
            required.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) 
        } else {
            required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) ensureBluetoothEnabled() else requestPermissionLauncher.launch(missing.toTypedArray())
    }

    private fun ensureBluetoothEnabled() {
        if (bluetoothAdapter?.isEnabled == false) {
            try { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (e: Exception) { resetMarking("Enable Bluetooth Manually.") }
        } else startBLEScan()
    }

    private fun startBLEScan(isRetry: Boolean = false) {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            resetMarking("Bluetooth Scanner unavailable.")
            return
        }
        bleScanner = scanner
        
        attendanceAttempted = false
        if (!isRetry) ignoredSessions.clear()
        
        runOnUiThread {
            tvStatus.text = "Searching for class beacon..."
            ivStatusIcon.setImageResource(android.R.drawable.stat_sys_data_bluetooth)
            ivStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.brand_accent)
        }
        
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ 
            if (!attendanceAttempted) {
                stopBLEScan()
                resetMarking("No class found nearby.")
            }
        }, 12000)

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // 🚀 Scan for session beacons in Manufacturer Data (0x00E0)
                result.scanRecord?.getManufacturerSpecificData(0x00E0)?.let { data ->
                    processBlePacket(String(data), result.rssi) 
                }
            }
        }
        try { 
            bleScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback) 
        } catch (e: SecurityException) { 
            resetMarking("Bluetooth Permission Error.")
        }
    }

    private fun stopBLEScan() { 
        try { 
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bleScanner?.stopScan(scanCallback) 
            }
        } catch (e: Exception) {
            Log.e("AttendanceFlow", "Error stopping scan", e)
        } 
    }

    private fun processBlePacket(packet: String, rssi: Int) {
        if (attendanceAttempted) return
        
        // Filter out any garbage data or non-app packets
        if (!packet.startsWith("ON|")) return
        
        val parts = packet.split("|")
        if (parts.size < 3) return
        
        val sessionId = parts[1]
        val token = parts[2]
        
        if (sessionId.isEmpty() || ignoredSessions.contains(sessionId)) return

        Log.d("AttendanceFlow", "Valid Packet: $packet (RSSI: $rssi)")
        
        attendanceAttempted = true
        stopBLEScan()
        runOnUiThread { validateEnrollmentAndShowContext(sessionId, token, rssi) }
    }

    private fun validateEnrollmentAndShowContext(sessionId: String, token: String, rssi: Int) {
        val instId = institutionId ?: return
        tvStatus.text = "Syncing class data..."
        
        firestore.collection("attendance_sessions").document(sessionId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    resetMarking("Session Invalid.")
                    return@addOnSuccessListener
                }

                // 🚀 v4.1 Use direct field, remove usersLookup
                val session = SessionContract(
                    sessionId = snapshot.id,
                    securityToken = snapshot.getString("securityToken") ?: "",
                    subjectId = snapshot.getString("subjectId") ?: "",
                    subjectCode = snapshot.getString("subjectCode") ?: "",
                    subjectName = snapshot.getString("subjectName") ?: "Unknown Subject",
                    lecturerId = snapshot.getString("lecturerId") ?: "",
                    lecturerName = snapshot.getString("lecturerName") ?: "Teacher",
                    institutionId = snapshot.getString("institutionId") ?: "",
                    department = snapshot.getString("department") ?: "",
                    semester = snapshot.getString("semester") ?: "",
                    section = snapshot.getString("section") ?: "",
                    batch = snapshot.getString("batch") ?: "",
                    expiresAt = snapshot.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
                )

                val status = snapshot.getString("status") ?: ""
                val currentTime = System.currentTimeMillis()

                if (status != "active" || currentTime > session.expiresAt || token != session.securityToken) {
                    resetMarking(if (currentTime > session.expiresAt) "Session Expired." else "Session Inactive.")
                    return@addOnSuccessListener
                }

                val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
                val sDept = prefs.getString("department", "") ?: ""
                val sSem = prefs.getString("semester", "") ?: ""
                val sSec = prefs.getString("section", "") ?: ""
                val sBatch = prefs.getInt("batch", 0).toString()

                Log.d("AttendanceFlow", "Validating: Session(${session.institutionId}, ${session.department}, ${session.semester}, ${session.section}, ${session.batch}) vs Student($instId, $sDept, $sSem, $sSec, $sBatch)")

                if (instId == session.institutionId && 
                    sDept.trim().equals(session.department.trim(), ignoreCase = true) && 
                    sSem.trim() == session.semester.trim() && 
                    sSec.trim().equals(session.section.trim(), ignoreCase = true) && 
                    sBatch.trim() == session.batch.trim()) {
                    
                    showPinDialog(session.sessionId, session.subjectName, session.subjectCode, session.lecturerName, rssi)
                } else {
                    Log.w("AttendanceFlow", "Enrollment Mismatch Detected")
                    ignoredSessions.add(sessionId)
                    attendanceAttempted = false
                    startBLEScan(isRetry = true)
                    btnMarkAttendance.isEnabled = true
                    tvStatus.text = "❌ Enrollment Mismatch"
                }
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceFlow", "Session sync failed", e)
                resetMarking("Sync Error: ${e.message}")
            }
    }

    private fun showPinDialog(
        sessionId: String, subjectName: String, code: String, lecturer: String, rssi: Int
    ) {
        val etPin = EditText(this)
        etPin.hint = "PIN"
        etPin.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        etPin.gravity = android.view.Gravity.CENTER
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("[$code] $subjectName")
        builder.setMessage("Lecturer: $lecturer\n\nEnter the 4-digit security PIN.")
        builder.setView(etPin)
        builder.setCancelable(false)
        builder.setPositiveButton("VERIFY", null)
        builder.setNegativeButton("ABORT") { dialog, _ ->
            dialog.dismiss()
            resetMarking("Verification Aborted.")
        }
        
        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val enteredPin = etPin.text.toString().trim()
            if (enteredPin.length != 4) {
                Toast.makeText(this, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss()
                validatePinAndMark(sessionId, enteredPin, rssi)
            }
        }
    }

    private fun validatePinAndMark(sessionId: String, pin: String, rssi: Int) {
        val instId = institutionId ?: return
        val uid = auth.currentUser?.uid ?: "unknown"
        tvStatus.text = "Verifying PIN..."
        
        firestore.collection("attendance_sessions").document(sessionId).get()
            .addOnSuccessListener { snapshot ->
                val dbPin = snapshot.getString("pin") ?: ""
                val subId = snapshot.getString("subjectId") ?: ""

                if (pin != dbPin) {
                    AuditLogger.log("ATTENDANCE_REJECTED", uid, "student", instId, sessionId, uid, subId, "INVALID_PIN")
                    resetMarking("Invalid Session PIN.")
                    return@addOnSuccessListener
                }

                firestore.collection("attendance_records")
                    .whereEqualTo("sessionId", sessionId)
                    .whereEqualTo("studentId", uid)
                    .whereEqualTo("institutionId", instId)
                    .get()
                    .addOnSuccessListener { dupSnap ->
                        if (!dupSnap.isEmpty) {
                            AuditLogger.log("ATTENDANCE_REJECTED", uid, "student", instId, sessionId, uid, subId, "DUPLICATE_ATTENDANCE")
                            tvStatus.text = "✅ Already Marked"
                            btnMarkAttendance.isEnabled = false
                        } else {
                            markAttendance(sessionId, subId, instId, rssi)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("AttendanceFlow", "Duplicate check failed", e)
                        resetMarking("Verify Error: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceFlow", "PIN fetch failed", e)
                resetMarking("Verify Error: ${e.message}")
            }
    }

    private fun resetMarking(message: String) {
        runOnUiThread {
            tvStatus.text = "❌ $message"
            attendanceAttempted = false
            btnMarkAttendance.isEnabled = true
            ivStatusIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            ivStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.status_danger)
        }
    }

    private fun markAttendance(sessionId: String, subjectId: String, instId: String, rssi: Int) {
        val uid = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val name = prefs.getString("name", "Unknown") ?: "Unknown"
        val usn = prefs.getString("usn", "---") ?: "---"

        val record = mapOf(
            "sessionId" to sessionId,
            "subjectId" to subjectId,
            "institutionId" to instId,
            "studentId" to uid,
            "studentName" to name,
            "studentUsn" to usn,
            "status" to "present",
            "timestamp" to com.google.firebase.Timestamp.now(),
            "rssi" to rssi,
            "gpsValidated" to false 
        )
        firestore.collection("attendance_records").add(record)
            .addOnSuccessListener {
                AuditLogger.log("ATTENDANCE_MARKED", uid, "student", instId, sessionId, uid, subjectId, "BLE_VERIFIED")
                
                lifecycleScope.launch {
                    val dept = prefs.getString("department", "") ?: ""
                    val sem = prefs.getString("semester", "") ?: ""
                    com.example.autoattendance.domain.usecase.GamificationManager.onAttendanceMarked(uid, instId, dept, sem, sessionId)
                }

                tvStatus.text = "✅ Attendance Marked"
                ivStatusIcon.setImageResource(android.R.drawable.checkbox_on_background)
                ivStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.status_success)
                btnMarkAttendance.isEnabled = false
            }
            .addOnFailureListener { e -> 
                Log.e("AttendanceFlow", "Record write failed", e)
                resetMarking("Write Failed: ${e.message}") 
            }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this).setTitle("Logout").setMessage("Logout from SecureAttend?").setPositiveButton("Logout") { _, _ -> 
            auth.signOut()
            val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val role = prefs.getString("role", "student")
            prefs.edit().clear().apply()
            getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().putString("role", role).apply()
            startActivity(Intent(this, StudentLoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish() 
        }.setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() { stopBLEScan(); super.onDestroy() }
}
