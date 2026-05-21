package com.adyapan.leaddialer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

private const val TAG_CALL_REPO = "CallRepository"

class CallRepository(
    private val dao     : CallRecordDao,
    private val context : Context
) {
    // ── Room stream ──────────────────────────────────────────────────────────
    val allRecords: Flow<List<CallRecord>> = dao.getAll()

    // ── Room direct ops ──────────────────────────────────────────────────────
    suspend fun totalConnected()                      = dao.totalConnected()
    suspend fun getLatestByPhone(phone: String)       = dao.getLatestByPhone(phone)
    suspend fun deleteAll()                           = dao.deleteAll()
    suspend fun getAllOnce(): List<CallRecord>         = dao.getAllOnce()

    // ── Insert: Room first → Firestore in background ─────────────────────────
    suspend fun insert(record: CallRecord) {
        dao.insert(record)
        syncRecordToFirestore(record)
    }

    suspend fun syncRecordToFirestore(record: CallRecord) {
        if (!NetworkUtils.isOnline(context)) return
        try {
            FirestoreSource.saveCallRecord(record)
        } catch (e: Exception) {
            Log.e(TAG_CALL_REPO, "syncRecordToFirestore error: ${e.message}")
        }
    }

    // ── One-time pull from Firebase → Room (call only on first login) ──────────
    // Normal operation NEVER reads from Firebase — Room DB is sufficient.
    suspend fun syncFromFirestore() {
        if (!NetworkUtils.isOnline(context)) return
        try {
            val remoteRecords = FirestoreSource.fetchCallRecordsOnce()
            remoteRecords.forEach { remote ->
                dao.insert(remote) // Room IGNORE strategy prevents duplicates
            }
            Log.d(TAG_CALL_REPO, "syncFromFirestore: ${remoteRecords.size} records pulled")
        } catch (e: Exception) {
            Log.e(TAG_CALL_REPO, "syncFromFirestore error: ${e.message}")
        }
    }

    // ── Real-time RTDB listener REMOVED ───────────────────────────────────────
    // Employee only sees their own call records which are in Room DB.
    // No cross-employee call sync needed — Firebase reads = 0.
    private var syncJob: Job? = null

    fun startRealtimeSync(scope: CoroutineScope) {
        // intentionally empty — Room DB is the source of truth
        Log.d(TAG_CALL_REPO, "startRealtimeSync: skipped (Room DB is primary)")
    }

    fun stopRealtimeSync() {
        syncJob?.cancel()
        syncJob = null
    }

    companion object {
        fun create(context: Context): CallRepository {
            val dao = AppDatabase.getInstance(context).callRecordDao()
            return CallRepository(dao, context)
        }
    }
}
