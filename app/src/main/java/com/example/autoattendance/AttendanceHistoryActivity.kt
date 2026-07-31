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
import java.text.SimpleDateFormat
import java.util.*

class AttendanceHistoryActivity : AppCompatActivity() {

    private lateinit var tvStats: TextView
    private lateinit var lvHistory: ListView
    private val historyList = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_history)

        tvStats = findViewById(R.id.tvAttendanceStats)
        lvHistory = findViewById(R.id.lvHistory)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, historyList)
        lvHistory.adapter = adapter

        fetchHistoryFirestore()
    }

    private fun fetchHistoryFirestore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val instId = prefs.getString("institutionId", "") ?: ""
        val dept = prefs.getString("department", "") ?: ""
        val sem = prefs.getString("semester", "") ?: ""
        val sec = prefs.getString("section", "") ?: ""

        val db = FirebaseFirestore.getInstance()

        // 1. Fetch all sessions for this specific group
        db.collection("attendance_sessions")
            .whereEqualTo("institutionId", instId)
            .whereEqualTo("department", dept)
            .whereEqualTo("semester", sem)
            .whereEqualTo("section", sec)
            .get()
            .addOnSuccessListener { sessionSnap ->
                val allSessions = sessionSnap.documents
                
                // 2. Fetch student's attendance records
                db.collection("attendance_records")
                    .whereEqualTo("studentId", uid)
                    .get()
                    .addOnSuccessListener { recordSnap ->
                        val attendedSessionIds = recordSnap.documents.mapNotNull { it.getString("sessionId") }.toSet()
                        
                        // 3. Aggregate subject-wise
                        val statsMap = mutableMapOf<String, SubjectStats>()
                        
                        allSessions.forEach { sDoc ->
                            val subName = sDoc.getString("subjectName") ?: "Unknown Subject"
                            val isPresent = attendedSessionIds.contains(sDoc.id)
                            
                            val stats = statsMap.getOrPut(subName) { SubjectStats(subName) }
                            stats.total++
                            if (isPresent) stats.present++
                        }

                        historyList.clear()
                        var totalPresentTotal = 0
                        var totalClassesTotal = 0

                        statsMap.values.sortedBy { it.name }.forEach { stats ->
                            val percent = if (stats.total > 0) (stats.present.toFloat() / stats.total.toFloat() * 100).toInt() else 0
                            historyList.add("${stats.name}\nAttendance: $percent% (${stats.present}/${stats.total} Classes)")
                            
                            totalPresentTotal += stats.present
                            totalClassesTotal += stats.total
                        }

                        if (historyList.isEmpty()) {
                            historyList.add("No attendance data found for your section.")
                        }
                        
                        tvStats.text = "Overall Attendance: $totalPresentTotal/$totalClassesTotal Classes"
                        adapter.notifyDataSetChanged()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("HistoryActivity", "Error fetching history", e)
                Toast.makeText(this, "Error loading history", Toast.LENGTH_SHORT).show()
            }
    }

    private class SubjectStats(val name: String) {
        var present: Int = 0
        var total: Int = 0
    }
}
