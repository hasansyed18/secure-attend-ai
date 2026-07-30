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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.autoattendance.Subject
import com.example.autoattendance.geofence.BoundarySetupActivity
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
        const val KEY_SUBJECT_NAME = "active_subject_name"
        const val KEY_DEPT = "active_dept"
        const val KEY_SEM = "active_sem"
        const val KEY_SEC = "active_sec"
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
    private lateinit var btnSetupBoundary: Button

    private lateinit var customAdapter: StudentListAdapter
    private val detectionList = mutableListOf<StudentDetection>()

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }
    private val sessionsRef = database.getReference("sessions")
    private val subjectsRef = database.getReference("subjects")

    private var currentSessionId: String? = null
    private var sessionListener: ValueEventListener? = null

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
        tvSystemStatus = findViewById(R.id.tvSystemStatus); statusDot = findViewById(R.id.statusDot)
        tvPresentCountLarge = findViewById(R.id.tvPresentCountLarge); tvAttendanceRate = findViewById(R.id.tvAttendanceRate)
        tvSuspiciousCount = findViewById(R.id.tvSuspiciousCount); tvActiveSubjectName = findViewById(R.id.tvActiveSubjectName)
        tvActiveSessionDetails = findViewById(R.id.tvActiveSessionDetails); tvLiveIndicator = findViewById(R.id.tvLiveIndicator)
        lvStudentList = findViewById(R.id.lvStudentList); btnStartClass = findViewById(R.id.btnStartClass)
        btnStopClass = findViewById(R.id.btnStopClass); btnAddSubject = findViewById(R.id.btnAddSubject)
        btnExportCsv = findViewById(R.id.btnExportCsv); btnViewAnalytics = findViewById(R.id.btnViewAnalytics)
        btnSetupBoundary = findViewById(R.id.btnSetupBoundary)
        
        customAdapter = StudentListAdapter(this, detectionList); lvStudentList.adapter = customAdapter
        
        btnStartClass.setOnClickListener { startClassProcess() }
        btnStopClass.setOnClickListener { stopClassSession() }
        btnAddSubject.setOnClickListener { openAddSubjectDialog() }
        btnExportCsv.setOnClickListener { exportAttendanceToCsv() }
        btnViewAnalytics.setOnClickListener { showAnalyticsDialog() }
        btnSetupBoundary.setOnClickListener { showSubjectForBoundary() }

        // Animation: Fade in the dashboard
        findViewById<View>(android.R.id.content).alpha = 0f
        findViewById<View>(android.R.id.content).animate().alpha(1f).setDuration(500).start()
    }

    private fun startClassProcess() {
        val required = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS))
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

    private fun activateClassSession(subject: Subject) {
        val sessionId = "S${System.currentTimeMillis()}"
        currentSessionId = sessionId
        
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_SUBJECT_NAME, subject.subjectName)
            .putString(KEY_DEPT, subject.department)
            .putString(KEY_SEM, subject.semester)
            .putString(KEY_SEC, subject.section)
            .apply()
        
        tvActiveSubjectName.text = subject.subjectName
        tvActiveSessionDetails.text = "${subject.department} • Sem ${subject.semester} • Sec ${subject.section}"

        val sessionData = hashMapOf<String, Any>(
            "isActive" to true, 
            "lecturerUid" to (auth.currentUser?.uid ?: ""), 
            "subjectName" to subject.subjectName, 
            "department" to subject.department, 
            "startTime" to ServerValue.TIMESTAMP, 
            "details" to "${subject.semester} - ${subject.section}"
        )
        sessionsRef.child(sessionId).setValue(sessionData)
        database.reference.child("current_session").setValue(sessionId)

        // START ADVERTISING
        val deptCode = when(subject.department) { "CSE" -> "CS"; "AI" -> "AI"; "EEE" -> "EE"; "ECE" -> "EC"; "Mechanical" -> "ME"; "Civil" -> "CV"; else -> "XX" }
        val intent = Intent(this, BleAdvertisingService::class.java).apply { 
            putExtra("SESSION_ID", sessionId)
            putExtra("DEPT", deptCode)
            putExtra("SEM", subject.semester)
            putExtra("SECTION", subject.section)
        }
        ContextCompat.startForegroundService(this, intent)
        
        updateUiActive(true)
        listenForAttendance(sessionId)
    }

    private fun stopClassSession() {
        val sessionId = currentSessionId ?: return
        stopService(Intent(this, BleAdvertisingService::class.java))
        sessionListener?.let { sessionsRef.child(sessionId).child("students").removeEventListener(it) }
        sessionListener = null
        sessionsRef.child(sessionId).child("isActive").setValue(false)
        database.reference.child("current_session").removeValue()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
        currentSessionId = null; updateUiActive(false); btnExportCsv.visibility = View.VISIBLE
    }

    private fun listenForAttendance(sessionId: String) {
        val studentsRef = sessionsRef.child(sessionId).child("students")
        sessionListener?.let { studentsRef.removeEventListener(it) }
        sessionListener = studentsRef.addValueEventListener(object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(snapshot: DataSnapshot) {
                detectionList.clear()
                var suspectCount = 0
                for (child in snapshot.children) {
                    val fullName = child.child("name").getValue(String::class.java) ?: "Unknown"
                    val rssi = child.child("rssi").getValue(Long::class.java)?.toInt() ?: 0
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    val isSuspect = child.child("isSuspect").getValue(Boolean::class.java) == true
                    if (isSuspect) suspectCount++
                    val namePart = fullName.substringBefore(" (")
                    val usnPart = fullName.substringAfter("(", "").replace(")", "")
                    detectionList.add(StudentDetection(usnPart, namePart, rssi, timestamp, isSuspect))
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
            btnStartClass.visibility = View.GONE; btnStopClass.visibility = View.VISIBLE; tvLiveIndicator.visibility = View.VISIBLE
        } else {
            tvSystemStatus.text = "System Inactive"; statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.text_secondary)
            btnStartClass.visibility = View.VISIBLE; btnStopClass.visibility = View.GONE; tvLiveIndicator.visibility = View.GONE
        }
    }

    private fun restoreSession() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedId = prefs.getString(KEY_SESSION_ID, null) ?: return
        currentSessionId = savedId
        val subName = prefs.getString(KEY_SUBJECT_NAME, "Active Session")
        tvActiveSubjectName.text = subName
        updateUiActive(true); listenForAttendance(savedId)
    }

    private fun exportAttendanceToCsv() {
        if (detectionList.isEmpty()) return
        val csvContent = StringBuilder("Student Name,USN,Timestamp,RSSI\n")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (student in detectionList) { csvContent.append("\"${student.studentName}\",${student.usn},${dateFormat.format(Date(student.timestamp))},${student.rssi}\n") }
        try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SecureAttend_Report_${System.currentTimeMillis()}.csv")
            FileOutputStream(file).apply { write(csvContent.toString().toByteArray()); close() }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this@LecturerActivity, "$packageName.fileprovider", file)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share Report"))
        } catch (e: Exception) {}
    }

    private fun showAnalyticsDialog() {
        val uid = auth.currentUser?.uid ?: return
        subjectsRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val subjects = snapshot.children.mapNotNull { it.getValue(Subject::class.java) }
                val displayNames = subjects.map { "${it.subjectName} (${it.section})" }.toTypedArray()
                AlertDialog.Builder(this@LecturerActivity).setTitle("Select for Analytics").setItems(displayNames) { _, index ->
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
                val subjects = mutableListOf<Subject>()
                val subjectKeys = mutableListOf<String>()
                snapshot.children.forEach { 
                    it.getValue(Subject::class.java)?.let { sub -> subjects.add(sub); subjectKeys.add(it.key ?: "") }
                }
                if (subjects.isEmpty()) { Toast.makeText(this@LecturerActivity, "Add a subject first", Toast.LENGTH_SHORT).show(); return }
                val displayNames = subjects.map { "${it.subjectName} (${it.section})" }.toTypedArray()
                AlertDialog.Builder(this@LecturerActivity).setTitle("Select Subject").setItems(displayNames) { _, index -> showSubjectOptions(subjects[index], subjectKeys[index]) }.show()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showSubjectOptions(subject: Subject, key: String) {
        AlertDialog.Builder(this).setTitle(subject.subjectName).setItems(arrayOf("Start Class Session", "Delete Subject")) { _, which ->
            if (which == 0) activateClassSession(subject) else deleteSubject(key)
        }.show()
    }

    private fun deleteSubject(key: String) {
        val uid = auth.currentUser?.uid ?: return
        AlertDialog.Builder(this).setTitle("Delete Subject?").setMessage("Permanently remove this subject?").setPositiveButton("Delete") { _, _ ->
            subjectsRef.child(uid).child(key).removeValue().addOnSuccessListener { Toast.makeText(this, "Subject Deleted", Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("Cancel", null).show()
    }

    private fun showSubjectForBoundary() {
        val uid = auth.currentUser?.uid ?: return
        subjectsRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val subjects = mutableListOf<Subject>()
                val subjectKeys = mutableListOf<String>()
                snapshot.children.forEach { it.getValue(Subject::class.java)?.let { sub -> subjects.add(sub); subjectKeys.add(it.key ?: "") } }
                if (subjects.isEmpty()) return
                val displayNames = subjects.map { "${it.subjectName} (${it.section})" }.toTypedArray()
                AlertDialog.Builder(this@LecturerActivity).setTitle("Select for Boundary").setItems(displayNames) { _, index ->
                    val intent = Intent(this@LecturerActivity, BoundarySetupActivity::class.java).apply {
                        putExtra(BoundarySetupActivity.EXTRA_SUBJECT_ID, subjectKeys[index])
                        putExtra(BoundarySetupActivity.EXTRA_CLASSROOM_NAME, subjects[index].subjectName)
                    }
                    startActivity(intent)
                }.show()
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
            val uid = auth.currentUser?.uid ?: return@setPositiveButton
            val name = etSubject.text.toString().trim()
            val dept = spinnerDept.selectedItem.toString()
            val sem = spinnerSem.selectedItem.toString().replace("SEM-", "")
            val section = etSection.text.toString().trim().uppercase()
            if (name.isEmpty() || section.isEmpty()) return@setPositiveButton
            val uniqueKey = "${name}_${dept}_${sem}_${section}".replace(Regex("[.#$\\[\\]]"), "_")
            subjectsRef.child(uid).child(uniqueKey).setValue(Subject(name, dept, sem, section)).addOnSuccessListener { Toast.makeText(this, "Added Successfully", Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("Cancel", null).show()
    }

    private inner class StudentListAdapter(context: Context, private val students: List<StudentDetection>) : ArrayAdapter<StudentDetection>(context, 0, students) {
        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.student_list_item, parent, false)
            val student = students[position]
            view.findViewById<TextView>(R.id.tvStudentName).text = student.studentName; view.findViewById<TextView>(R.id.tvStudentUsn).text = student.usn; view.findViewById<TextView>(R.id.tvStudentInitials).text = student.studentName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
            val badge = view.findViewById<TextView>(R.id.tvRssiBadge); badge.text = "${student.rssi} dBm"
            if (student.isSuspect) { badge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.signal_weak); badge.setTextColor(Color.WHITE) } 
            else { badge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.signal_strong); badge.setTextColor(Color.WHITE) }
            
            // Animation for list items
            view.translationX = 100f
            view.alpha = 0f
            view.animate().translationX(0f).alpha(1f).setDuration(300).setStartDelay(position * 50L).start()
            
            return view
        }
    }

    override fun onDestroy() { 
        super.onDestroy() 
    }
}
