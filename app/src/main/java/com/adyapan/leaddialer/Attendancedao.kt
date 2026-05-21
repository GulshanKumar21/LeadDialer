package com.adyapan.leaddialer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AttendanceRecord)

    @Update
    suspend fun update(record: AttendanceRecord)

    @Query("SELECT * FROM attendance ORDER BY punchInMs DESC")
    fun getAll(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): AttendanceRecord?

    @Query("SELECT * FROM attendance ORDER BY punchInMs DESC")
    suspend fun getAllOnce(): List<AttendanceRecord>

    @Query("SELECT COUNT(*) FROM attendance WHERE isLate = 1")
    suspend fun totalLateDays(): Int

    @Query("SELECT COUNT(*) FROM attendance")
    suspend fun totalDays(): Int
}