package com.example.autoattendance

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.*
import java.util.UUID
import android.os.ParcelUuid
import android.util.Log

class StudentActivity : AppCompatActivity() {

    private var attendanceAttempted = false
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null

    private val scanTimeoutHandler = Handler(Looper.getMainLooper())
    private var scanTimeoutRunnable: Runnable? = null

    private lateinit var tvStatus: TextView
    private lateinit var ivStatusIcon: ImageView
    private lateinit var btnMarkAttendance: Button
    private lateinit var btnViewHistory: Button
    private lateinit var btnLogout: ImageView
    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentUsn: TextView
    private lateinit var tvStudentInitials: TextView

    private val database = FirebaseDatabase.getInstance()
    private val rootRef = database.reference

    private val SERVICE_UUID = ParcelUuid.fromString("0000FEAF-0000-1000-8000-00805F9B34FB")

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.entries.all { it.value }) {
            Handler(Looper.getMainLooper()).postDelayed({ checkLocationEnabledAndProceed() }, 500)
        } else { Toast.makeText(this, "Permissions required.", Toast.LENGTH_SHORT).show() }
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

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkPermissionsAndProcess() {
        val required = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { 
            required.add(Manifest.permission.BLUETOOTH_SCAN)
            required.add(Manifest.permission.BLUETOOTH_CONNECT) 
        }
        required.add(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) checkLocationEnabledAndProceed() else requestPermissionLauncher.launch(missing.toTypedArray())
    }

    private fun checkLocationEnabledAndProceed() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this).setTitle("Location Required").setMessage("GPS must be ON for verification.").setPositiveButton("Settings") { _, _ -> enableLocationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }.show()
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
        
        scanTimeoutRunnable?.let { scanTimeoutHandler.removeCallbacks(it) }
        scanTimeoutRunnable = Runnable { stopBLEScan(); if (!attendanceAttempted) tvStatus.text = "No session found." }
        scanTimeoutHandler.postDelayed(scanTimeoutRunnable!!, 20000)

        val filters = listOf(ScanFilter.Builder().setServiceData(SERVICE_UUID, null).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.scanRecord?.getServiceData(SERVICE_UUID)?.let { processBlePacket(String(it), result.rssi) }
            }
        }
        try { bleScanner?.startScan(filters, settings, scanCallback) } catch (e: SecurityException) {}
    }

    private fun stopBLEScan() { 
        try { bleScanner?.stopScan(scanCallback) } catch (e: Exception) {} 
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
            scanTimeoutRunnable?.let { scanTimeoutHandler.removeCallbacks(it) }
            markAttendance(sessionId, rssi, !isOnline())
        }
    }

    private fun markAttendance(sessionId: String, rssi: Int, wasOffline: Boolean) {
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val usn = prefs.getString("usn", "") ?: return
        val name = prefs.getString("name", "Unknown")
        
        val data = mapOf("name" to "$name ($usn)", "rssi" to rssi, "zone" to (if (rssi > -60) "Strong" else "Normal"), "timestamp" to ServerValue.TIMESTAMP)
        
        if (wasOffline) {
            getSharedPreferences("OfflineSyncs", MODE_PRIVATE).edit().putBoolean(sessionId, true).apply()
        }

        rootRef.child("sessions").child(sessionId).child("students").child(usn).setValue(data).addOnCompleteListener { task ->
            if (task.isSuccessful && getSharedPreferences("OfflineSyncs", MODE_PRIVATE).contains(sessionId)) {
                showSyncNotification(sessionId)
                getSharedPreferences("OfflineSyncs", MODE_PRIVATE).edit().remove(sessionId).apply()
            }
        }
        rootRef.child("attendance_by_student").child(usn).child(sessionId).setValue(true)
        
        tvStatus.text = if (wasOffline) "✅ Stored Offline (Syncing...)" else "✅ Attendance Marked"
        if (wasOffline) Toast.makeText(this, "Offline: Stored locally. Open app later to confirm sync.", Toast.LENGTH_LONG).show()

        ivStatusIcon.setImageResource(android.R.drawable.checkbox_on_background)
        ivStatusIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.status_success)
        btnMarkAttendance.isEnabled = false
    }

    private fun showSyncNotification(sid: String) {
        val channelId = "sync_channel_student"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Attendance Sync", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Attendance Synced")
            .setContentText("Your session ($sid) is now on cloud.")
            .setSmallIcon(R.drawable.ic_secure_attend_logo)
            .setAutoCancel(true)
            .build()
        manager.notify(sid.hashCode(), notification)
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this).setTitle("Logout").setMessage("Logout?").setPositiveButton("Logout") { _, _ -> getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).edit().clear().apply(); startActivity(Intent(this, MainActivity::class.java)); finish() }.setNegativeButton("Cancel", null).show()
    }

    override fun onDestroy() { scanTimeoutRunnable?.let { scanTimeoutHandler.removeCallbacks(it) }; stopBLEScan(); super.onDestroy() }
}
