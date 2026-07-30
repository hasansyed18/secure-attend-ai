package com.example.autoattendance

import android.app.Application
import androidx.work.*
import com.example.autoattendance.sync.CleanupWorker
import com.example.autoattendance.sync.SyncWorker
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

class AutoAttendanceApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable Firebase offline persistence
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        // Schedule Workers
        scheduleSyncWork()
        scheduleCleanupWork()
    }

    private fun scheduleSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = PeriodicWorkRequest.Builder(SyncWorker::class.java, 15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AttendanceSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )
    }

    private fun scheduleCleanupWork() {
        val cleanupWorkRequest = PeriodicWorkRequest.Builder(CleanupWorker::class.java, 7, TimeUnit.DAYS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DatabaseCleanupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupWorkRequest
        )
    }
}
