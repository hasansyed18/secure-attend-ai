package com.example.autoattendance

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.autoattendance.geofence.GeofenceValidator
import com.example.autoattendance.geofence.ClassroomBoundaryManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
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
    private lateinit var btnViewHistory: Button
    private lateinit var btnLogout: ImageView
    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentUsn: TextView
    private lateinit var tvStudentInitials: TextView

    private val database = FirebaseDatabase.getInstance()
    private val sessionsRef = database.reference.child("sessions")
    private val rootRef = database.reference

    private val SERVICE_UUID = ParcelUuid.fromString("0000FEAF-0000-1000-8000-00805F9B34FB")

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            Handler(Looper.getMainLooper()).postDelayed({ checkLocationEnabledAndProceed() }, 500)
        } else { Toast.makeText(this, "Permissions are required for scanning.", Toast.LENGTH_SHORT).show() }
    }

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (bluetoothAdapter?.isEnabled == true) startBLEScan() else tvStatus.text = "Please enable Bluetooth."
    }

    private val enableLocationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkLocationEnabledAndProceed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        if (userPrefs.getString("role", null) != "student") { 
            startActivity(Intent(this, StudentLoginActivity::class.java))
            finish()
            return 
        }
        
        setContentView(R.layout.activity_student)
        initViews(userPrefs)
        
        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        
        btnMarkAttendance.setOnClickListener { checkPermissionsAndProcess() }
        btnViewHistory.setOnClickListener { startActivity(Intent(this, AttendanceHistoryActivity::class.java)) }
        btnLogout.setOnClickListener { confirmLogout() }

        // Entrance Animation
        findViewById<View>(android.R.id.content).alpha = 0f
        findViewById<View>(android.R.id.content).animate().alpha(1f).setDuration(500).start()
    }

    @SuppressLint("SetTextI18n")
    private fun initViews(prefs: SharedPreferences) {
        tvStatus = findViewById(R.id.tvStatus)
        ivStatusIcon = findViewById(R.id.ivStatusIcon)
        btnMarkAttendance = findViewById(R.id.btnMarkAttendance)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        btnLogout = findViewById(R.id.btnLogout)
        tvStudentName = findViewById(R.id.tvStudentName)
        tvStudentUsn = findViewById(R.id.tvStudentUsn)
        tvStudentInitials = findViewById(R.id.tvStudentInitials)

        val name = prefs.getString("name", "Student") ?: "Student"
        val usn = prefs.getString("usn", "---") ?: "---"
        
        tvStudentName.text = name
        tvStudentUsn.text = "USN: $usn"
        tvStudentInitials.text = name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
    }

    private fun checkPermissionsAndProcess() {
        val required = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { 
            required.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)) 
        }
        val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) checkLocationEnabledAndProceed() else requestPermissionLauncher.launch(missing.toTypedArray())
    }

    private fun checkLocationEnabledAndProceed() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this).setTitle("Location Required").setMessage("GPS is required for class detection.").setPositiveButton("Settings") { _, _ -> enableLocationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }.show()
        } else ensureBluetoothEnabled()
    }

    private fun ensureBluetoothEnabled() {
        if (bluetoothAdapter?.isEnabled == false) {
            try { enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (e: Exception) { tvStatus.text = "Enable Bluetooth manually." }
        } else startBLEScan()
    }

    private fun startBLEScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        
        bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        attendanceAttempted = false
        tvStatus.text = "Searching for class..."
        ivStatusIcon.setImageResource(android.R.drawable.stat_sys_data_bluetooth)
        ivStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.brand_accent)
        
        handler.postDelayed({ 
            stopBLEScan()
            if (!attendanceAttempted) tvStatus.text = "No session found." 
        }, 20000)

        val filters = listOf<ScanFilter>() 
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Check Service Data manually (Reliable on all devices)
                val data = result.scanRecord?.getServiceData(SERVICE_UUID)
                if (data != null) {
                    processBlePacket(String(data), result.rssi)
                }
            }
        }
        
        try { 
            bleScanner?.startScan(filters, settings, scanCallback) 
        } catch (e: SecurityException) {
            tvStatus.text = "Permission error."
        }
    }

    private fun stopBLEScan() { 
        try { 
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                bleScanner?.stopScan(scanCallback) 
            }
        } catch (e: Exception) {} 
    }

    private fun processBlePacket(packet: String, rssi: Int) {
        if (attendanceAttempted || rssi < -85) return
        
        val parts = packet.split("|")
        if (parts.size < 4) return
        
        val broadcastDept = parts[0]
        val broadcastSem = parts[1]
        val broadcastSec = parts[2]
        val sessionId = parts[3]

        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val studentDept = prefs.getString("department", "") ?: ""
        val studentSem = prefs.getString("semester", "")?.filter { it.isDigit() } ?: ""
        val studentSec = prefs.getString("section", "") ?: ""

        val deptMatches = when(studentDept) {
            "CSE" -> broadcastDept == "CS" || broadcastDept == "CSE"
            "AI" -> broadcastDept == "AI"
            "EEE" -> broadcastDept == "EE" || broadcastDept == "EEE"
            "ECE" -> broadcastDept == "EC" || broadcastDept == "ECE"
            "Mechanical" -> broadcastDept == "ME" || broadcastDept == "Mechanical"
            "Civil" -> broadcastDept == "CV" || broadcastDept == "Civil"
            else -> broadcastDept.equals(studentDept, ignoreCase = true)
        }

        if (deptMatches && studentSem == broadcastSem && studentSec.equals(broadcastSec, ignoreCase = true)) {
            attendanceAttempted = true
            stopBLEScan()
            runGeofenceCheck(sessionId, rssi)
        }
    }

    private fun runGeofenceCheck(sessionId: String, rssi: Int) {
        tvStatus.text = "Verifying classroom position..."
        
        sessionsRef.child(sessionId).get().addOnSuccessListener { snapshot ->
            val name = snapshot.child("subjectName").value as? String ?: ""
            val dept = snapshot.child("department").value as? String ?: ""
            val details = snapshot.child("details").value as? String ?: ""
            val sem = details.split(" - ").firstOrNull()?.filter { it.isDigit() } ?: ""
            val sec = details.split(" - ").lastOrNull()?.trim() ?: ""
            
            val subjectId = "${name}_${dept}_${sem}_${sec}".replace(Regex("[.#$\\[\\]]"), "_")

            GeofenceValidator.validate(
                context = this,
                subjectId = subjectId,
                sessionId = sessionId,
                studentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown",
                rssi = rssi,
                isPacketValid = true,
                isTimestampValid = true,
                onResult = { result ->
                    runOnUiThread {
                        when (result.verdict) {
                            ClassroomBoundaryManager.Verdict.PRESENT -> markAttendance(sessionId, rssi, false)
                            ClassroomBoundaryManager.Verdict.SUSPECT -> {
                                markAttendance(sessionId, rssi, true)
                                AlertDialog.Builder(this).setTitle("⚠️ Weak Signal").setMessage("Attendance flagged. Please move closer.").setPositiveButton("OK", null).show()
                            }
                            ClassroomBoundaryManager.Verdict.REJECTED -> {
                                tvStatus.text = "❌ Outside Classroom"
                                attendanceAttempted = false
                            }
                        }
                    }
                },
                onNoBoundary = { runOnUiThread { markAttendance(sessionId, rssi, false) } },
                onLocationError = { runOnUiThread { markAttendance(sessionId, rssi, false) } }
            )
        }.addOnFailureListener {
            tvStatus.text = "Verification failed."
            attendanceAttempted = false
        }
    }

    private fun markAttendance(sessionId: String, rssi: Int, isSuspect: Boolean) {
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val usn = prefs.getString("usn", "") ?: return
        val name = prefs.getString("name", "Unknown")
        
        val data = mapOf(
            "name" to "$name ($usn)", 
            "rssi" to rssi, 
            "zone" to (if (rssi > -60) "Strong" else "Normal"), 
            "isSuspect" to isSuspect,
            "timestamp" to ServerValue.TIMESTAMP
        )
        
        sessionsRef.child(sessionId).child("students").child(usn).setValue(data)
        rootRef.child("attendance_by_student").child(usn).child(sessionId).setValue(true)
        
        tvStatus.text = "✅ Attendance Marked"
        ivStatusIcon.setImageResource(android.R.drawable.checkbox_on_background)
        ivStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.status_success)
        btnMarkAttendance.isEnabled = false
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this).setTitle("Logout").setMessage("Logout from SecureAttend?").setPositiveButton("Logout") { _, _ -> 
            getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, MainActivity::class.java))
            finish() 
        }.setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() {
        stopBLEScan()
        super.onDestroy()
    }
}
