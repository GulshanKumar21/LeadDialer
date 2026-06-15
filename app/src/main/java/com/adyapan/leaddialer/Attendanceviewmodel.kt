package com.adyapan.leaddialer


import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val TAG_ATTEND = "AttendanceViewModel"

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val dao       = AppDatabase.getInstance(application).attendanceDao()
    val allAttendance     : LiveData<List<AttendanceRecord>> = dao.getAll().asLiveData()

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** Real-time Firestore listener job. */
    private var syncJob: Job? = null

    init {
        // Initial pull from Firestore + real-time listener
        viewModelScope.launch(Dispatchers.IO) {
            syncFromFirestore()
        }
        startRealtimeSync()
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
    }

    // ── Firestore sync helpers ────────────────────────────────────────────────

    private suspend fun syncFromFirestore() {
        if (!NetworkUtils.isOnline(getApplication())) return
        try {
            val remoteRecords = FirestoreSource.fetchAttendanceOnce()
            remoteRecords.forEach { remote ->
                val existing = dao.getByDate(remote.date)
                if (existing == null) {
                    dao.insert(remote)
                } else {
                    // Keep the local record but update totalCalls if remote is higher
                    if (remote.totalCalls > existing.totalCalls) {
                        dao.update(existing.copy(totalCalls = remote.totalCalls))
                    }
                }
            }
            Log.d(TAG_ATTEND, "syncFromFirestore: ${remoteRecords.size} records pulled")
        } catch (e: Exception) {
            Log.e(TAG_ATTEND, "syncFromFirestore error: ${e.message}")
        }
    }

    private fun startRealtimeSync() {
        syncJob?.cancel()
        syncJob = FirestoreSource.attendanceFlow()
            .onEach { remoteRecords ->
                viewModelScope.launch(Dispatchers.IO) {
                    remoteRecords.forEach { remote ->
                        val existing = dao.getByDate(remote.date)
                        if (existing == null) {
                            dao.insert(remote)
                        } else if (remote.totalCalls > existing.totalCalls) {
                            dao.update(existing.copy(totalCalls = remote.totalCalls))
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Existing public API (unchanged signatures) ────────────────────────────

    suspend fun isTodayPunchedIn(): Boolean = withContext(Dispatchers.IO) {
        val today = dateFormat.format(Calendar.getInstance().time)
        if (dao.getByDate(today) != null) {
            return@withContext true
        }

        // If not found in Room SQLite, check Firestore under /attendance/{userId}/dates/{yyyy-MM-dd} (Selfie Check-in)
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
            try {
                val doc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("attendance").document(uid)
                    .collection("dates").document(dateKey)
                    .get().await()
                if (doc.exists()) {
                    val punchInTime = doc.getString("checkIn") ?: "00:00"
                    val isLateStr = doc.getString("status") ?: "Present"
                    val isLate = isLateStr == "Late"
                    val lateReason = doc.getString("lateReason") ?: ""
                    var employeeName = doc.getString("employeeName") ?: ""
                    if (employeeName.isEmpty()) {
                        employeeName = SheetsSync.getEmployeeNameStatic(getApplication())
                    }
                    val punchInMs = doc.getLong("timestamp") ?: System.currentTimeMillis()

                    val record = AttendanceRecord(
                        date         = today,
                        punchInTime  = punchInTime,
                        punchInMs    = punchInMs,
                        isLate       = isLate,
                        lateReason   = lateReason,
                        employeeName = employeeName
                    )
                    dao.insert(record)
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.e(TAG_ATTEND, "isTodayPunchedIn Firestore check error: ${e.message}")
            }
        }
        false
    }

    suspend fun getTodayAttendance(): AttendanceRecord? = withContext(Dispatchers.IO) {
        val today = dateFormat.format(Calendar.getInstance().time)
        dao.getByDate(today)
    }

    fun punchIn(
        context    : Context,
        lateReason : String = "",
        onDone     : (AttendanceRecord) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        val now      = Calendar.getInstance()
        val today    = dateFormat.format(now.time)
        val timeStr  = timeFormat.format(now.time)

        val existing = dao.getByDate(today)
        if (existing != null) {
            withContext(Dispatchers.Main) { onDone(existing) }
            return@launch
        }

        val hour   = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        // Late check-in threshold set to 11:30 AM
        val isLate = (hour > 11) || (hour == 11 && minute > 30)

        var employeeName = SheetsSync.getEmployeeNameStatic(context)
        if (employeeName == "Unknown" || employeeName.isEmpty()) {
            employeeName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@") ?: "Unknown"
        }

        val record = AttendanceRecord(
            date         = today,
            punchInTime  = timeStr,
            punchInMs    = now.timeInMillis,
            isLate       = isLate,
            lateReason   = lateReason,
            employeeName = employeeName
        )

        // 1. Save to Room first
        dao.insert(record)

        // 2. Sync to Firestore (background, non-blocking)
        launch {
            try {
                FirestoreSource.saveAttendance(record)
            } catch (e: Exception) {
                Log.e(TAG_ATTEND, "punchIn Firestore error: ${e.message}")
            }
        }

        // 3. Sheets sync
        try {
            SheetsSync.syncAttendance(context, dao.getAllOnce())
        } catch (e: Exception) { /* non-critical */ }

        withContext(Dispatchers.Main) { onDone(record) }
    }

    fun updateTodayCallCount(count: Int) = viewModelScope.launch(Dispatchers.IO) {
        val today    = dateFormat.format(Calendar.getInstance().time)
        val existing = dao.getByDate(today) ?: return@launch
        val updated  = existing.copy(totalCalls = count)
        dao.update(updated)

        // Sync updated record to Firestore
        launch {
            try {
                FirestoreSource.saveAttendance(updated)
            } catch (e: Exception) {
                Log.e(TAG_ATTEND, "updateTodayCallCount Firestore error: ${e.message}")
            }
        }
    }

    fun isCurrentlyLate(): Boolean {
        val now    = Calendar.getInstance()
        val hour   = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        // Late check-in threshold set to 11:30 AM
        return (hour > 11) || (hour == 11 && minute > 30)
    }

    fun getCurrentTime(): String = timeFormat.format(Calendar.getInstance().time)
}

class AttendanceViewModelFactory(private val app: Application) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AttendanceViewModel(app) as T
    }
}
