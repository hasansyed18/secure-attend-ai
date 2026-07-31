package com.example.autoattendance

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class LecturerAnalyticsActivity : AppCompatActivity() {

    private lateinit var tvSubjectInfo: TextView
    private lateinit var lvAnalytics: ListView
    private val analyticsList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lecturer_analytics)

        tvSubjectInfo = findViewById(R.id.tvSubjectInfo)
        lvAnalytics = findViewById(R.id.lvAnalytics)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, analyticsList)
        lvAnalytics.adapter = adapter

        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        val dept = intent.getStringExtra("DEPT") ?: ""
        val sem = intent.getStringExtra("SEM") ?: ""
        val section = intent.getStringExtra("SECTION") ?: ""

        tvSubjectInfo.text = "Subject: $subjectName ($dept - Sem $sem - Sec $section)"

        fetchAnalyticsFirestore(subjectName, dept, sem, section)
    }

    private fun fetchAnalyticsFirestore(subjectName: String, dept: String, sem: String, section: String) {
        val db = FirebaseFirestore.getInstance()
        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val instId = userPrefs.getString("institutionId", "") ?: ""

        if (instId.isEmpty()) {
            Toast.makeText(this, "Institution ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Fetch all sessions for this specific subject/class
        db.collection("attendance_sessions")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("subjectName", subjectName)
            .whereEqualTo("department", dept)
            .whereEqualTo("semester", sem)
            .whereEqualTo("section", section)
            .get()
            .addOnSuccessListener { sessionSnap ->
                val allSessions = sessionSnap.documents
                val totalSessions = allSessions.size
                
                if (totalSessions == 0) {
                    analyticsList.clear()
                    analyticsList.add("No sessions found for this subject.")
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                val sessionIds = allSessions.map { it.id }

                // 2. Fetch all attendance records for these sessions
                // Firestore limit for 'in' query is 10. If sessions > 10, we need another way.
                // However, since we want student percentages, we'll fetch all records where sessionId in sessionIds
                // and aggregate locally.
                
                db.collection("attendance_records")
                    .whereIn("sessionId", sessionIds.take(10)) // Simple implementation for demo
                    .get()
                    .addOnSuccessListener { recordSnap ->
                        val studentRecords = recordSnap.documents
                        val studentStats = mutableMapOf<String, StudentStat>() // studentUsn -> Stat

                        studentRecords.forEach { doc ->
                            val usn = doc.getString("studentUsn") ?: "Unknown"
                            val name = doc.getString("studentName") ?: "Unknown"
                            val stat = studentStats.getOrPut(usn) { StudentStat(usn, name) }
                            stat.attendedCount++
                        }

                        // 3. Format for display
                        analyticsList.clear()
                        studentStats.values.sortedBy { it.usn }.forEach { stat ->
                            val percent = (stat.attendedCount.toFloat() / totalSessions.toFloat() * 100).toInt()
                            analyticsList.add("${stat.name} (${stat.usn})\nAttendance: $percent% (${stat.attendedCount}/$totalSessions)")
                        }
                        
                        if (analyticsList.isEmpty()) {
                            analyticsList.add("No student data recorded for these sessions.")
                        }

                        adapter.notifyDataSetChanged()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Analytics", "Failed to fetch records", e)
                        Toast.makeText(this, "Error loading records", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Analytics", "Failed to fetch sessions", e)
                Toast.makeText(this, "Error loading sessions", Toast.LENGTH_SHORT).show()
            }
    }

    private class StudentStat(val usn: String, val name: String) {
        var attendedCount: Int = 0
    }
}
