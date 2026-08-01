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
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.widget.Button
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
import com.example.autoattendance.ui.Screen
import com.example.autoattendance.ui.components.AppDrawer
import com.example.autoattendance.ui.theme.AutoAttendanceTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class StudentActivity : AppCompatActivity() {

    private var attendanceAttempted = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
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

    private val SERVICE_UUID = ParcelUuid.fromString("0000FEAF-0000-1000-8000-00805F9B34FB")

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            ensureBluetoothEnabled()
        } else { Toast.makeText(this, "Bluetooth permissions are required for scanning.", Toast.LENGTH_SHORT).show() }
    }

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (bluetoothAdapter?.isEnabled == true) startBLEScan() else tvStatus.text = "Please enable Bluetooth."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        if (userPrefs.getString("role", null) != "student") { 
            startActivity(Intent(this, StudentLoginActivity::class.java))
            finish()
            return 
        }
        
        setContentView(R.layout.activity_student)
        institutionId = userPrefs.getString("institutionId", null)
        
        initViews(userPrefs)
        
        bluetoothAdapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        btnMarkAttendance.setOnClickListener { 
            Log.d("AttendanceFlow", "Mark Attendance button clicked")
            checkPermissionsAndProcess() 
        }
        btnMenu.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        setupDrawer(userPrefs)

        findViewById<View>(android.R.id.content).alpha = 0f
        findViewById<View>(android.R.id.content).animate().alpha(1f).setDuration(500).start()
    }

    private fun setupDrawer(prefs: SharedPreferences) {
        val name = prefs.getString("name", "User") ?: "User"
        val email = prefs.getString("email", "") ?: ""
        val role = prefs.getString("role", "student") ?: "student"

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
                    userRole = role
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
            try { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (e: Exception) { tvStatus.text = "Enable Bluetooth manually." }
        } else startBLEScan()
    }

    private fun startBLEScan() {
        bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        
        Log.d("AttendanceFlow", "Bluetooth scan started (BLE-only)")
        attendanceAttempted = false
        tvStatus.text = "Searching for classroom beacon..."
        ivStatusIcon.setImageResource(android.R.drawable.stat_sys_data_bluetooth)
        ivStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.brand_accent)
        
        handler.postDelayed({ 
            if (!attendanceAttempted) {
                stopBLEScan()
                tvStatus.text = "Classroom beacon not found."
                Log.d("AttendanceFlow", "Scan timeout: Beacon not found")
            }
        }, 10000)

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.scanRecord?.getServiceData(SERVICE_UUID)?.let { 
                    processBlePacket(String(it), result.rssi) 
                }
            }
        }
        try { 
            bleScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback) 
        } catch (e: SecurityException) { 
            tvStatus.text = "Bluetooth permission error." 
            Log.e("AttendanceFlow", "Scan failed: SecurityException")
        }
    }

    private fun stopBLEScan() { 
        try { 
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bleScanner?.stopScan(scanCallback) 
                Log.d("AttendanceFlow", "Bluetooth scan stopped")
            }
        } catch (e: Exception) {
            Log.e("AttendanceFlow", "Error stopping scan", e)
        } 
    }

    private fun processBlePacket(packet: String, rssi: Int) {
        if (attendanceAttempted) return
        
        val parts = packet.split("|")
        if (parts.size < 2) return
        
        val sessionId = parts[0]
        val token = parts[1]
        
        if (rssi >= -85) {
            Log.d("AttendanceFlow", "Class beacon detected: $sessionId. Validating with Firestore...")
            attendanceAttempted = true
            stopBLEScan()
            validateSessionAndMark(sessionId, token, rssi)
        }
    }

    private fun validateSessionAndMark(sessionId: String, token: String, rssi: Int) {
        val instId = institutionId ?: return
        tvStatus.text = "Securing session connection..."
        
        firestore.collection("attendance_sessions").document(sessionId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Log.e("AttendanceFlow", "Session not found in Firestore")
                resetMarking("Session invalid.")
                return@addOnSuccessListener
            }

            val dbToken = snapshot.getString("securityToken") ?: ""
            val status = snapshot.getString("status") ?: ""
            val expiresAt = snapshot.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
            val currentTime = System.currentTimeMillis()

            // 1. Security & Expiry Check
            if (token != dbToken || status != "active" || currentTime > expiresAt) {
                Log.e("AttendanceFlow", "Validation failed: Token match: ${token == dbToken}, Status: $status, Expired: ${currentTime > expiresAt}")
                resetMarking(if (currentTime > expiresAt) "Session expired." else "Security mismatch.")
                return@addOnSuccessListener
            }

            // 2. Enrollment Check (Multi-Institution Safety)
            val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
            val sDept = prefs.getString("department", "") ?: ""
            val sSem = prefs.getString("semester", "") ?: ""
            val sSec = prefs.getString("section", "") ?: ""
            val sBatch = prefs.getInt("batch", 0).toString()

            val bDept = snapshot.getString("department") ?: ""
            val bSem = snapshot.getString("semester") ?: ""
            val bSec = snapshot.getString("section") ?: ""
            val bBatch = snapshot.getString("batch") ?: ""
            val bInst = snapshot.getString("institutionId") ?: ""

            if (instId == bInst && sDept == bDept && sSem == bSem && sSec == bSec && sBatch == bBatch) {
                val subId = snapshot.getString("subjectId") ?: ""
                markAttendance(sessionId, subId, instId, rssi)
            } else {
                Log.e("AttendanceFlow", "Enrollment mismatch. Student: $sDept $sSem $sSec $sBatch, Session: $bDept $bSem $bSec $bBatch")
                resetMarking("Not enrolled in this class.")
            }

        }.addOnFailureListener { e ->
            Log.e("AttendanceFlow", "Firestore lookup failed", e)
            resetMarking("Validation error.")
        }
    }

    private fun resetMarking(message: String) {
        tvStatus.text = "❌ $message"
        attendanceAttempted = false
        // Optionally restart scan after delay
    }

    private fun markAttendance(sessionId: String, subjectId: String, instId: String, rssi: Int) {
        val uid = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val name = prefs.getString("name", "Unknown") ?: "Unknown"
        val usn = prefs.getString("usn", "---") ?: "---"

        Log.d("AttendanceFlow", "Attendance write started for session: $sessionId")
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
                Log.d("AttendanceFlow", "Attendance write successful")
                tvStatus.text = "✅ Attendance Marked"
                ivStatusIcon.setImageResource(android.R.drawable.checkbox_on_background)
                ivStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.status_success)
                btnMarkAttendance.isEnabled = false
            }
            .addOnFailureListener { e ->
                Log.e("AttendanceFlow", "Attendance write failed", e)
                tvStatus.text = "❌ Failed to mark attendance."
                attendanceAttempted = false
            }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this).setTitle("Logout").setMessage("Logout from SecureAttend?").setPositiveButton("Logout") { _, _ -> 
            auth.signOut()
            getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, StudentLoginActivity::class.java))
            finish() 
        }.setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() { stopBLEScan(); super.onDestroy() }
}
