package com.adyapan.leaddialer

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {

    @Query("SELECT * FROM leads ORDER BY id DESC")
    fun getAllLeads(): Flow<List<Lead>>

    @Query("SELECT * FROM leads WHERE status = :status ORDER BY id DESC")
    fun getLeadsByStatus(status: String): Flow<List<Lead>>

    @Query("SELECT COUNT(*) FROM leads")
    suspend fun totalLeads(): Int

    @Query("SELECT COUNT(*) FROM leads WHERE status = 'Interested'")
    suspend fun totalInterested(): Int

    @Query("SELECT COUNT(*) FROM leads WHERE status = 'Interested' OR status = 'Connected'")
    suspend fun totalConnected(): Int

    @Query("SELECT COUNT(*) FROM leads WHERE calledAt > 0")
    suspend fun totalCalled(): Int

    @Query("SELECT COUNT(*) FROM leads WHERE status = 'Pending'")
    suspend fun totalPending(): Int

    // Batch import: IGNORE keeps existing leads safe (no overwrite)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(leads: List<Lead>)

    // Single upsert: REPLACE so individual lead updates work
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrReplace(lead: Lead)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(lead: Lead)

    @Query("DELETE FROM leads WHERE phone = :phone")
    suspend fun deleteByPhone(phone: String)

    @Update
    suspend fun update(lead: Lead)

    @Delete
    suspend fun delete(lead: Lead)

    @Query("DELETE FROM leads")
    suspend fun deleteAll()

    @Query("SELECT * FROM leads")
    suspend fun getAllOnce(): List<Lead>

    @Query("SELECT * FROM leads WHERE phone = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): Lead?
}