package com.example.autoattendance.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.autoattendance.LecturerActivity
import com.example.autoattendance.R
import com.example.autoattendance.db.AppDatabase
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            performSync()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun performSync() {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.attendanceDao()
        val unsyncedRecords = dao.getUnsyncedRecords()

        if (unsyncedRecords.isEmpty()) return

        val firebaseDb = FirebaseDatabase.getInstance().reference
        val syncedIds = mutableListOf<Int>()

        val groupedRecords = unsyncedRecords.groupBy { it.sessionId }

        for ((sessionId, records) in groupedRecords) {
            val subjectName = records.first().subjectName
            
            for (record in records) {
                val studentData = mapOf(
                    "name" to record.studentName,
                    "rssi" to record.rssi,
                    "timestamp" to record.timestamp,
                    "isSuspect" to (record.rssi < -75)
                )

                try {
                    firebaseDb.child("sessions")
                        .child(sessionId)
                        .child("students")
                        .child(record.usn)
                        .setValue(studentData)
                        .await()
                    
                    syncedIds.add(record.id)
                } catch (e: Exception) {
                    // Skip
                }
            }

            if (syncedIds.isNotEmpty()) {
                dao.markRecordsAsSynced(syncedIds)
                showSyncNotification(sessionId, subjectName)
            }
        }
    }

    private fun showSyncNotification(sessionId: String, subjectName: String) {
        val channelId = "sync_channel_lecturer"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Attendance Sync", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, LecturerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, sessionId.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Attendance Synced: $subjectName")
            .setContentText("Offline data for session $sessionId is now on cloud. Tap to export.")
            .setSmallIcon(R.drawable.ic_attendit_logo)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(sessionId.hashCode(), notification)
    }
}
