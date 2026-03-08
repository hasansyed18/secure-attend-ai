package com.example.autoattendance

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

        val userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE)
        val usn = userPrefs.getString("usn", "") ?: ""

        if (usn.isNotEmpty()) {
            fetchHistory(usn)
        }
    }

    private fun fetchHistory(usn: String) {
        val database = FirebaseDatabase.getInstance().reference
        val studentHistoryRef = database.child("attendance_by_student").child(usn)
        val sessionsRef = database.child("sessions")

        studentHistoryRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                historyList.clear()
                val sessionIds = snapshot.children.mapNotNull { it.key }
                tvStats.text = "Total Classes Attended: ${sessionIds.size}"

                for (sessionId in sessionIds) {
                    sessionsRef.child(sessionId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(sessionSnapshot: DataSnapshot) {
                            val subject = sessionSnapshot.child("subjectName").getValue(String::class.java) ?: "Unknown"
                            val timestamp = sessionSnapshot.child("startTime").getValue(Long::class.java) ?: 0L
                            
                            val date = if (timestamp > 0) {
                                SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
                            } else "N/A"

                            historyList.add("$subject\nDate: $date")
                            historyList.sortDescending() // Most recent first
                            adapter.notifyDataSetChanged()
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
