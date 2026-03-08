package com.example.autoattendance

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.autoattendance.Subject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class LecturerActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "LecturerActivity"
        const val PREFS_NAME = "LecturerSession"
        const val KEY_SESSION_ID = "active_session_id"
        const val OFFLINE_SYNC_PREFS = "OfflineSyncQueue"
    }

    private lateinit var tvSystemStatus: TextView
    private lateinit var statusDot: View
    private lateinit var tvPresentCountLarge: TextView
    private lateinit var tvAttendanceRate: TextView
    private lateinit var tvSuspiciousCount: TextView
    private lateinit var tvActiveSubjectName: TextView
    private lateinit var tvActiveSessionDetails: TextView
    private lateinit var tvLiveIndicator: TextView
    
    private lateinit var lvStudentList: ListView
    private lateinit var btnStartClass: Button
    private lateinit var btnStopClass: Button
    private lateinit var btnAddSubject: Button
    private lateinit var btnExportCsv: Button
    private lateinit var btnViewAnalytics: Button

    private lateinit var customAdapter: StudentListAdapter
    private val detectionList = mutableListOf<StudentDetection>()
    private val attendanceDataList = mutableListOf<Map<String, Any>>()

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private val sessionsRef = database.getReference("sessions")
    private val subjectsRef = database.getReference("subjects")

    private var currentSessionId: String? = null
    private var sessionListener: ValueEventListener? = null

    data class StudentDetection(
        val name: String,
        val usn: String,
        val rssi: Int,
        val timestamp: Long,
        val isSuspect: Boolean
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            checkBluetoothAndStart()
        } else {
            Toast.makeText(this, "Permissions denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (auth.currentUser == null) {
            startActivity(Intent(this, LecturerLoginActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_lecturer)
        initViews()
        restoreSession()
        monitorConnectivity()
    }

    private fun monitorConnectivity() {
        database.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                updateUiActive(currentSessionId != null)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(net) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun initViews() {
        tvSystemStatus = findViewById(R.id.tvSystemStatus)
        statusDot = findViewById(R.id.statusDot)
        tvPresentCountLarge = findViewById(R.id.tvPresentCountLarge)
        tvAttendanceRate = findViewById(R.id.tvAttendanceRate)
        tvSuspiciousCount = findViewById(R.id.tvSuspiciousCount)
        tvActiveSubjectName = findViewById(R.id.tvActiveSubjectName)
        tvActiveSessionDetails = findViewById(R.id.tvActiveSessionDetails)
        tvLiveIndicator = findViewById(R.id.tvLiveIndicator)

        lvStudentList = findViewById(R.id.lvStudentList)
        btnStartClass = findViewById(R.id.btnStartClass)
        btnStopClass = findViewById(R.id.btnStopClass)
        btnAddSubject = findViewById(R.id.btnAddSubject)
        btnExportCsv = findViewById(R.id.btnExportCsv)
        btnViewAnalytics = findViewById(R.id.btnViewAnalytics)

        customAdapter = StudentListAdapter(this, detectionList)
        lvStudentList.adapter = customAdapter

        btnStartClass.setOnClickListener { startClassProcess() }
        btnStopClass.setOnClickListener { stopClassSession() }
        btnAddSubject.setOnClickListener { openAddSubjectDialog() }
        btnExportCsv.setOnClickListener { exportAttendanceToCsv() }
        btnViewAnalytics.setOnClickListener { showAnalyticsDialog() }
    }

    private fun startClassProcess() {
        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = requiredPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) checkBluetoothAndStart() else requestPermissionLauncher.launch(missing.toTypedArray())
    }

    private fun checkBluetoothAndStart() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (bluetoothManager.adapter == null || !bluetoothManager.adapter.isEnabled) {
            Toast.makeText(this, "Enable Bluetooth first", Toast.LENGTH_SHORT).show()
            return
        }
        showSubjectSelectionDialog()
    }

    private fun activateClassSession(subject: Subject) {
        val sessionId = "S${System.currentTimeMillis()}"
        currentSessionId = sessionId
        
        val sessionData = hashMapOf(
            "isActive" to true, 
            "lecturerUid" to (auth.currentUser?.uid ?: ""), 
            "subjectName" to subject.subjectName, 
            "department" to subject.department, 
            "startTime" to ServerValue.TIMESTAMP, 
            "details" to "${subject.semester} - ${subject.section}"
        )

        val isOff = !isOnline()
        if (isOff) {
            getSharedPreferences(OFFLINE_SYNC_PREFS, MODE_PRIVATE).edit().putBoolean(sessionId, true).apply()
            Toast.makeText(this, "Offline Mode: Session will sync when internet returns.", Toast.LENGTH_LONG).show()
        }

        sessionsRef.child(sessionId).setValue(sessionData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val syncQueue = getSharedPreferences(OFFLINE_SYNC_PREFS, MODE_PRIVATE)
                if (syncQueue.contains(sessionId)) {
                    showSyncNotification(sessionId, subject.subjectName)
                    syncQueue.edit().remove(sessionId).apply()
                }
            }
        }
        database.reference.child("current_session").setValue(sessionId)
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_SESSION_ID, sessionId).apply()

        tvActiveSubjectName.text = subject.subjectName
        tvActiveSessionDetails.text = "${subject.department} • Sem ${subject.semester} • Sec ${subject.section}"

        val deptCode = when(subject.department) { "CSE" -> "CS"; "AI" -> "AI"; "EEE" -> "EE"; "ECE" -> "EC"; "Mechanical" -> "ME"; "Civil" -> "CV"; else -> "XX" }
        val intent = Intent(this, BleAdvertisingService::class.java).apply { 
            putExtra("SESSION_ID", sessionId)
            putExtra("SUBJECT", subject.subjectName)
            putExtra("DEPT", deptCode)
            putExtra("SEM", subject.semester.filter { it.isDigit() })
            putExtra("SECTION", subject.section)
        }
        ContextCompat.startForegroundService(this, intent)
        updateUiActive(true)
        listenForAttendance(sessionId)
    }

    private fun showSyncNotification(sid: String, subjectName: String) {
        val channelId = "sync_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Data Sync", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent(this, LecturerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, sid.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Attendance Synced: $subjectName")
            .setContentText("Offline session data is now on cloud. Open to export.")
            .setSmallIcon(R.drawable.ic_secure_attend_logo)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(sid.hashCode(), notification)
    }

    private fun stopClassSession() {
        val sessionId = currentSessionId ?: return
        stopService(Intent(this, BleAdvertisingService::class.java))
        sessionListener?.let { sessionsRef.child(sessionId).child("students").removeEventListener(it) }
        sessionListener = null
        sessionsRef.child(sessionId).child("isActive").setValue(false)
        database.reference.child("current_session").removeValue()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_SESSION_ID).apply()
        updateUiActive(false)
        btnExportCsv.visibility = View.VISIBLE
    }

    private fun listenForAttendance(sessionId: String) {
        val studentsRef = sessionsRef.child(sessionId).child("students")
        sessionListener?.let { studentsRef.removeEventListener(it) }
        sessionListener = studentsRef.addValueEventListener(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                detectionList.clear()
                attendanceDataList.clear()
                var suspectCount = 0
                for (child in snapshot.children) {
                    val fullName = child.child("name").getValue(String::class.java) ?: "Unknown"
                    val rssi = child.child("rssi").getValue(Long::class.java)?.toInt() ?: 0
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val isSuspect = child.child("isSuspect").getValue(Boolean::class.java) == true
                    if (isSuspect) suspectCount++
                    val namePart = fullName.substringBefore(" (")
                    val usnPart = fullName.substringAfter("(", "").replace(")", "")
                    detectionList.add(StudentDetection(namePart, usnPart, rssi, timestamp, isSuspect))
                    attendanceDataList.add(mutableMapOf("name" to fullName, "timestamp" to timestamp, "zone" to (child.child("zone").value ?: "")))
                }
                detectionList.sortByDescending { it.timestamp }
                customAdapter.notifyDataSetChanged()
                tvPresentCountLarge.text = "${detectionList.size}/60"
                tvSuspiciousCount.text = suspectCount.toString()
                val rate = if (60 > 0) (detectionList.size.toFloat() / 60f * 100).toInt() else 0
                tvAttendanceRate.text = "$rate% attendance"
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateUiActive(isActive: Boolean) {
        if (isActive) {
            val online = isOnline()
            tvSystemStatus.text = if (online) "System Active" else "System Active (Offline)"
            statusDot.backgroundTintList = ContextCompat.getColorStateList(this, if(online) R.color.status_success else R.color.status_warning)
            btnStartClass.visibility = View.GONE
            btnStopClass.visibility = View.VISIBLE
            tvLiveIndicator.visibility = View.VISIBLE
            btnExportCsv.visibility = View.GONE
        } else {
            tvSystemStatus.text = "System Inactive"
            statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
            btnStartClass.visibility = View.VISIBLE
            btnStopClass.visibility = View.GONE
            tvLiveIndicator.visibility = View.GONE
            tvActiveSubjectName.text = "No Active Session"
            tvActiveSessionDetails.text = "Choose a subject to begin"
        }
    }

    private fun restoreSession() {
        val savedId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_SESSION_ID, null) ?: return
        sessionsRef.child(savedId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.child("isActive").getValue(Boolean::class.java) == true) {
                    currentSessionId = savedId
                    tvActiveSubjectName.text = snapshot.child("subjectName").getValue(String::class.java) ?: "Active Session"
                    tvActiveSessionDetails.text = snapshot.child("details").getValue(String::class.java) ?: ""
                    updateUiActive(true)
                    listenForAttendance(savedId)
                } else {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_SESSION_ID).apply()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun exportAttendanceToCsv() {
        if (detectionList.isEmpty()) return
        val csvContent = StringBuilder("Student Name,USN,Timestamp,Zone\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (student in detectionList) {
            val time = if (student.timestamp > 0) dateFormat.format(Date(student.timestamp)) else "N/A"
            csvContent.append("\"${student.name}\",${student.usn},$time,${if(student.rssi > -60) "Strong" else "Normal"}\n")
        }
        try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Attendance_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).apply { write(csvContent.toString().toByteArray()); close() }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share Report"))
        } catch (e: Exception) { Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show() }
    }

    private fun showAnalyticsDialog() {
        val uid = auth.currentUser?.uid ?: return
        subjectsRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val subjects = snapshot.children.mapNotNull { it.getValue(Subject::class.java) }
                if (subjects.isEmpty()) return
                val displayNames = subjects.map { "${it.subjectName} (${it.section})" }.toTypedArray()
                AlertDialog.Builder(this@LecturerActivity).setTitle("Select Subject for Analytics").setItems(displayNames) { _, index ->
                    val s = subjects[index]
                    startActivity(Intent(this@LecturerActivity, LecturerAnalyticsActivity::class.java).apply { putExtra("SUBJECT_NAME", s.subjectName); putExtra("DEPT", s.department); putExtra("SEM", s.semester); putExtra("SECTION", s.section) })
                }.show()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showSubjectSelectionDialog() {
        val uid = auth.currentUser?.uid ?: return
        subjectsRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val subjects = snapshot.children.mapNotNull { it.getValue(Subject::class.java) }
                if (subjects.isEmpty()) return
                val displayNames = subjects.map { "${it.subjectName} (${it.section})" }.toTypedArray()
                AlertDialog.Builder(this@LecturerActivity).setTitle("Select Subject").setItems(displayNames) { _, index -> activateClassSession(subjects[index]) }.show()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun openAddSubjectDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_subject, null)
        val etSubject = dialogView.findViewById<EditText>(R.id.etSubjectName)
        val spinnerDept = dialogView.findViewById<Spinner>(R.id.spinnerDepartment).apply { adapter = ArrayAdapter(this@LecturerActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("CSE","AI","EEE","ECE","Mechanical","Civil")) }
        val spinnerSem = dialogView.findViewById<Spinner>(R.id.spinnerSemester).apply { adapter = ArrayAdapter(this@LecturerActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("SEM-1","SEM-2","SEM-3","SEM-4","SEM-5","SEM-6","SEM-7","SEM-8")) }
        val etSection = dialogView.findViewById<EditText>(R.id.etSection)
        AlertDialog.Builder(this).setTitle("Add Subject").setView(dialogView).setPositiveButton("Save") { _, _ ->
            val sId = subjectsRef.child(auth.currentUser?.uid ?: "").push().key ?: return@setPositiveButton
            subjectsRef.child(auth.currentUser?.uid ?: "").child(sId).setValue(Subject(etSubject.text.toString(), spinnerDept.selectedItem.toString(), spinnerSem.selectedItem.toString().replace("SEM-",""), etSection.text.toString())).addOnSuccessListener {
                Toast.makeText(this, "Subject Added Successfully", Toast.LENGTH_SHORT).show()
            }
        }.setNegativeButton("Cancel", null).show()
    }

    private inner class StudentListAdapter(context: Context, private val students: List<StudentDetection>) : 
        ArrayAdapter<StudentDetection>(context, 0, students) {
        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.student_list_item, parent, false)
            val student = students[position]
            val tvName = view.findViewById<TextView>(R.id.tvStudentName)
            val tvUsn = view.findViewById<TextView>(R.id.tvStudentUsn)
            val tvInitials = view.findViewById<TextView>(R.id.tvStudentInitials)
            val tvRssi = view.findViewById<TextView>(R.id.tvRssiBadge)
            val tvTime = view.findViewById<TextView>(R.id.tvDetectionTime)
            tvName.text = student.name
            tvUsn.text = student.usn
            tvInitials.text = student.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
            tvRssi.text = "${student.rssi} dBm"
            tvTime.text = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(student.timestamp))
            if (student.isSuspect) { tvRssi.backgroundTintList = ContextCompat.getColorStateList(context, R.color.signal_weak); tvRssi.setTextColor(Color.WHITE) } 
            else { tvRssi.backgroundTintList = ContextCompat.getColorStateList(context, R.color.signal_strong); tvRssi.setTextColor(Color.WHITE) }
            return view
        }
    }

    override fun onDestroy() {
        currentSessionId?.let { sid -> sessionListener?.let { sessionsRef.child(sid).child("students").removeEventListener(it) } }
        super.onDestroy()
    }
}
