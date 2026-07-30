package com.example.autoattendance.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.autoattendance.db.AppDatabase
import java.util.concurrent.TimeUnit

class CleanupWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val dao = database.attendanceDao()
            
            // Delete records older than 30 days
            val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            dao.deleteOldSyncedRecords(thirtyDaysAgo)
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
