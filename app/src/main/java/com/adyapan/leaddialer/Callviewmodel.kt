package com.adyapan.leaddialer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG_CALL_VM   = "CallViewModel"
/** Sync every 40 calls during day. After 7:50 PM → sync on every single call. */
private const val BATCH_SYNC_THRESHOLD = 40

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val repo    = CallRepository.create(application)
    private val leadDao = AppDatabase.getInstance(application).leadDao()

    val allRecords: LiveData<List<CallRecord>> = repo.allRecords.asLiveData()

    // Shown in UI so employee knows how many calls until next sync
    private val _callsUntilSync = MutableLiveData(BATCH_SYNC_THRESHOLD)
    val callsUntilSync: LiveData<Int> = _callsUntilSync

    private val _lastSyncedAt = MutableLiveData(0L)
    val lastSyncedAt: LiveData<Long> = _lastSyncedAt

    /** Returns true if current time is 8:00 PM or later (end-of-day mode). */
    private fun isAfterEvening(): Boolean {
        val cal  = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val min  = cal.get(java.util.Calendar.MINUTE)
        return hour > 20 || (hour == 20 && min >= 0)  // 20:00 = 8:00 PM
    }

    /**
     * Working hours: 11:15 AM (11:15) to 8:00 PM (20:00).
     * Before 11:15 AM — employees haven't started, no Firebase writes needed.
     * After  8:00 PM  — isAfterEvening() handles (every-call sync).
     */
    private fun isWorkingHours(): Boolean {
        val cal  = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val min  = cal.get(java.util.Calendar.MINUTE)
        val afterStart = hour > 11 || (hour == 11 && min >= 15)  // 11:15 AM
        val beforeEnd  = hour < 20                                // before 8 PM
        return afterStart && beforeEnd
    }

    init {
        repo.startRealtimeSync(viewModelScope)  // no-op, kept for compatibility
    }

    override fun onCleared() {
        super.onCleared()
        repo.stopRealtimeSync()
    }

    /**
     * Save a call record to Room DB immediately (instant, offline-first)
     * and trigger an immediate real-time sync to Firebase and Sheets.
     *
     * Flow:
     *  Step A — Single record pushed to RTDB instantly (~100ms) → Admin sees it LIVE.
     *  Step B — Full batch sync runs in background (leads + all records + Sheets).
     */
    fun saveRecord(record: CallRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Save to Room immediately (instant, works offline)
            repo.insert(record)

            // 2. Update lead status in Room
            val lead = leadDao.getByPhone(record.phone)
            if (lead != null) {
                leadDao.update(
                    lead.copy(
                        status   = record.status,
                        calledAt = record.calledAt,
                        duration = record.duration.toInt()
                    )
                )
            }

            // ── STEP A: Push ONLY this one record to RTDB instantly ──────────────────────
            // This triggers the admin's ValueEventListener within ~100-300ms.
            // Admin screen updates in real-time WITHOUT waiting for full batch.
            try {
                FirestoreSource.saveCallRecord(record)
                Log.d(TAG_CALL_VM, "Instant push: single record synced to RTDB → admin updated")
            } catch (e: Exception) {
                Log.w(TAG_CALL_VM, "Instant single-record push failed (will retry in batch): ${e.message}")
            }

            // 3. Update today's attendance call count automatically
            try {
                val dayStart = getTodayStartMs()
                val dayEnd = dayStart + 24 * 60 * 60 * 1000L
                val todayCount = repo.countTodayCalls(dayStart, dayEnd)
                
                val todayStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(record.calledAt))
                val attendanceDao = AppDatabase.getInstance(getApplication()).attendanceDao()
                val existingAttend = attendanceDao.getByDate(todayStr)
                if (existingAttend != null) {
                    val updatedAttend = existingAttend.copy(totalCalls = todayCount)
                    attendanceDao.update(updatedAttend)
                    try {
                        FirestoreSource.saveAttendance(updatedAttend)
                    } catch (fe: Exception) {
                        Log.e(TAG_CALL_VM, "Failed to sync attendance to Firestore: ${fe.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG_CALL_VM, "Failed to update attendance call count: ${e.message}")
            }

            // ── STEP B: Full batch sync in background (leads + all records + Sheets) ──
            Log.d(TAG_CALL_VM, "Background batch sync starting...")
            _callsUntilSync.postValue(0)
            batchSyncNow()
        }
    }

    /** Pull call records from Firebase RTDB into Room — called once after login */
    fun syncCallRecordsFromFirebase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remoteRecords = FirestoreSource.fetchCallRecordsOnce()
                remoteRecords.forEach { record ->
                    repo.insert(record) // IGNORE strategy = no duplicates
                }
                Log.d(TAG_CALL_VM, "syncCallRecordsFromFirebase: ${remoteRecords.size} records loaded")
            } catch (e: Exception) {
                Log.e(TAG_CALL_VM, "syncCallRecordsFromFirebase error: ${e.message}")
            }
        }
    }

    /** Force a batch sync right now (e.g. at end of day or when sales marked) */
    fun forceSyncNow() {
        viewModelScope.launch(Dispatchers.IO) {
            batchSyncNow()
        }
    }

    private suspend fun batchSyncNow() {
        try {
            val allLeads   = leadDao.getAllOnce()
            val allRecords = repo.getAllOnce()

            // 1. Push all leads to RTDB in one batch (updateChildren = 1 network call)
            if (allLeads.isNotEmpty()) {
                FirestoreSource.saveLeadsBatch(allLeads)
                Log.d(TAG_CALL_VM, "Batch: ${allLeads.size} leads pushed to RTDB")
            }

            // 2. Push ALL call records as a single RTDB batch
            // Key = uid_phone_calledAt — same phone 50 calls = 50 DIFFERENT keys
            // No deduplication! Every call gets stored.
            if (allRecords.isNotEmpty()) {
                FirestoreSource.saveCallRecordsBatch(allRecords)
                Log.d(TAG_CALL_VM, "Batch: ${allRecords.size} call records pushed to RTDB")
            }

            // 3. Google Sheets sync (background, non-blocking)
            try {
                val notesMap = allLeads.associateBy({ it.phone }, { it.notes })
                SheetsSync.syncAllLeads(getApplication(), allLeads)
                SheetsSync.syncCallRecords(getApplication(), allRecords, notesMap)
            } catch (e: Exception) {
                Log.w(TAG_CALL_VM, "Sheets sync failed (non-fatal): ${e.message}")
            }

            _lastSyncedAt.postValue(System.currentTimeMillis())
            Log.d(TAG_CALL_VM, "Batch sync DONE — ${allLeads.size} leads, ${allRecords.size} calls")
        } catch (e: Exception) {
            Log.e(TAG_CALL_VM, "batchSyncNow error: ${e.message}")
        }
    }

    private fun getTodayStartMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

class CallViewModelFactory(private val app: Application) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CallViewModel(app) as T
    }
}
