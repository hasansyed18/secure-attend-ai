package com.example.autoattendance.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AttendanceRecord)

    @Query("SELECT * FROM attendance_records WHERE sessionId = :sessionId")
    fun getRecordsForSession(sessionId: String): LiveData<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<AttendanceRecord>

    @Query("UPDATE attendance_records SET isSynced = 1 WHERE id IN (:recordIds)")
    suspend fun markRecordsAsSynced(recordIds: List<Int>)

    @Query("DELETE FROM attendance_records WHERE isSynced = 1 AND timestamp < :threshold")
    suspend fun deleteOldSyncedRecords(threshold: Long)
}
