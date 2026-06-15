package com.adyapan.leaddialer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: CallRecord)

    @Query("SELECT * FROM call_records ORDER BY calledAt DESC")
    fun getAll(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE phone = :phone ORDER BY calledAt DESC LIMIT 1")
    suspend fun getLatestByPhone(phone: String): CallRecord?

    @Query("SELECT * FROM call_records WHERE phone = :phone AND calledAt = :calledAt LIMIT 1")
    suspend fun getByPhoneAndTime(phone: String, calledAt: Long): CallRecord?

    @Query("SELECT COUNT(*) FROM call_records WHERE status = 'Connected'")
    suspend fun totalConnected(): Int

    @Query("DELETE FROM call_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM call_records")
    suspend fun getAllOnce(): List<CallRecord>

    /** Returns today's records sorted oldest-first — used to compute inter-call gaps. */
    @Query("SELECT * FROM call_records WHERE calledAt >= :dayStart AND calledAt < :dayEnd ORDER BY calledAt ASC")
    suspend fun getTodaySortedAsc(dayStart: Long, dayEnd: Long): List<CallRecord>

    @Query("SELECT COUNT(*) FROM call_records WHERE calledAt >= :dayStart AND calledAt < :dayEnd")
    suspend fun countTodayCalls(dayStart: Long, dayEnd: Long): Int
}