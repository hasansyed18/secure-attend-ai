package com.example.autoattendance

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*

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

        tvSubjectInfo.text = "Subject: $subjectName ($dept - $sem - $section)"

        fetchAnalytics(subjectName, dept, sem, section)
    }

    private fun fetchAnalytics(subjectName: String, dept: String, sem: String, section: String) {
        val database = FirebaseDatabase.getInstance().reference
        val sessionsRef = database.child("sessions")
        val studentsRef = database.child("students")

        // 1. Find all sessions for this specific subject/class
        sessionsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val relevantSessions = snapshot.children.filter {
                    it.child("subjectName").getValue(String::class.java) == subjectName &&
                    it.child("department").getValue(String::class.java) == dept &&
                    it.child("details").getValue(String::class.java)?.contains(sem) == true &&
                    it.child("details").getValue(String::class.java)?.contains(section) == true
                }

                val totalSessions = relevantSessions.size
                if (totalSessions == 0) {
                    analyticsList.add("No sessions found for this subject.")
                    adapter.notifyDataSetChanged()
                    return
                }

                // 2. Count attendance per student across these sessions
                val studentAttendanceCount = mutableMapOf<String, Int>() // USN -> Count

                for (session in relevantSessions) {
                    val studentsInSession = session.child("students")
                    for (student in studentsInSession.children) {
                        val usn = student.key ?: continue
                        studentAttendanceCount[usn] = studentAttendanceCount.getOrDefault(usn, 0) + 1
                    }
                }

                // 3. Fetch student names and display percentage
                analyticsList.clear()
                for ((usn, count) in studentAttendanceCount) {
                    val percentage = (count.toFloat() / totalSessions.toFloat()) * 100
                    analyticsList.add("USN: $usn\nAttendance: ${String.format("%.1f", percentage)}% ($count/$totalSessions classes)")
                }
                
                analyticsList.sortByDescending { it.substringAfter(": ").substringBefore("%").toFloatOrNull() ?: 0f }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
