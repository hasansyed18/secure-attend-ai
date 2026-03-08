package com.example.autoattendance

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class AutoAttendanceApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable Firebase offline persistence
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}