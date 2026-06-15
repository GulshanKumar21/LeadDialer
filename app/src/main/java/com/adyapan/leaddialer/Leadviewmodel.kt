package com.adyapan.leaddialer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG_REPO = "LeadRepository"


class LeadRepository(
    private val dao     : LeadDao,
    private val context : Context
) {
    val allLeads: Flow<List<Lead>> = dao.getAllLeads()

    suspend fun insertAll(leads: List<Lead>)     = dao.insertAll(leads)
    suspend fun update(lead: Lead)               = dao.update(lead)
    suspend fun deleteAll()                      = dao.deleteAll()
    suspend fun totalLeads()                     = dao.totalLeads()
    suspend fun totalInterested()                = dao.totalInterested()
    suspend fun totalConnected()                 = dao.totalConnected()
    suspend fun totalCalled()                    = dao.totalCalled()
    suspend fun totalPending()                   = dao.totalPending()
    suspend fun getAllLeadsOnce(): List<Lead>     = dao.getAllOnce()
    suspend fun getByPhone(phone: String): Lead? = dao.getByPhone(phone)

    suspend fun insertLocal(lead: Lead) = dao.insert(lead)
    suspend fun updateLocal(lead: Lead) = dao.update(lead)

    suspend fun insert(lead: Lead) {
        dao.insert(lead)
        syncLeadToFirestore(lead)
    }

    suspend fun delete(lead: Lead) {
        dao.delete(lead)
        if (NetworkUtils.isOnline(context)) {
            try {
                FirestoreSource.deleteLead(lead.phone)
            } catch (e: Exception) {
                Log.e(TAG_REPO, "deleteLead Firestore error: ${e.message}")
            }
        }
    }

    suspend fun syncLeadToFirestore(lead: Lead) {
        if (!NetworkUtils.isOnline(context)) return
        try {
            val docId = FirestoreSource.saveLead(lead)
            if (docId != null && lead.firestoreId == null) {
                dao.update(lead.copy(firestoreId = docId))
            }
        } catch (e: Exception) {
            Log.e(TAG_REPO, "syncLeadToFirestore error: ${e.message}")
        }
    }

    suspend fun syncFromFirestore() {
        if (!NetworkUtils.isOnline(context)) return
        try {
            // Pull from Firestore (Hot/Interested leads)
            val remoteLeads = FirestoreSource.fetchLeadsOnce()
            // Pull from RTDB (Pending/bulk leads)
            val rtdbLeads   = FirestoreSource.fetchRtdbLeadsOnce()
            val allRemote   = remoteLeads + rtdbLeads

            allRemote.forEach { remoteLead ->
                val local = dao.getByPhone(remoteLead.phone)
                if (local == null) {
                    dao.insert(remoteLead)
                } else if (remoteLead.calledAt > local.calledAt) {
                    dao.update(
                        local.copy(
                            status      = remoteLead.status,
                            notes       = remoteLead.notes,
                            calledAt    = remoteLead.calledAt,
                            duration    = remoteLead.duration,
                            firestoreId = remoteLead.firestoreId,
                            collegeName = remoteLead.collegeName.ifBlank { local.collegeName },
                            collegeCity = remoteLead.collegeCity.ifBlank { local.collegeCity },
                            isHotLead   = false
                        )
                    )
                } else if (local.firestoreId.isNullOrBlank() && !remoteLead.firestoreId.isNullOrBlank()) {
                    // Patch: local lead is missing its firestoreId (e.g. calledAt=0 skipped update)
                    dao.update(local.copy(firestoreId = remoteLead.firestoreId))
                    Log.d(TAG_REPO, "syncFromFirestore: patched firestoreId for ${remoteLead.phone}")
                }
            }
            Log.d(TAG_REPO, "syncFromFirestore: ${allRemote.size} leads pulled (${remoteLeads.size} Firestore + ${rtdbLeads.size} RTDB)")
        } catch (e: Exception) {
            Log.e(TAG_REPO, "syncFromFirestore error: ${e.message}")
        }
    }
}


class LeadViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: LeadRepository

    val allLeads: LiveData<List<Lead>>

    val totalLeads      = MutableLiveData(0)
    val totalInterested = MutableLiveData(0)
    val totalConnected  = MutableLiveData(0)
    val totalCalled     = MutableLiveData(0)
    val totalPending    = MutableLiveData(0)

    // Expected Sales & Admin Target States
    val expectedSales   = MutableLiveData(0)
    val adminTarget     = MutableLiveData("Not Set")

    private var syncJob: Job? = null


    @Volatile private var isBatchInserting = false

    init {
        val dao = AppDatabase.getInstance(application).leadDao()
        repo    = LeadRepository(dao, application.applicationContext)
        allLeads = repo.allLeads.asLiveData()

        // ── Auto-refresh stats whenever Room DB changes ───────────────────────
        // Room DB = primary source of truth for employee.
        // No Firebase read on every startup — employee data is already in Room.
        repo.allLeads
            .onEach { refreshStats() }
            .launchIn(viewModelScope)

        // Stats load from Room immediately (zero network cost)
        refreshStats()
    }

    /**
     * One-time sync from Firebase → Room.
     * Call this ONLY on first login or when employee explicitly pulls to refresh.
     * Normal operation never reads from Firebase — Room DB is sufficient.
     */
    fun syncFromFirebaseOnce() = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG_REPO, "syncFromFirebaseOnce: starting")
        repo.syncFromFirestore()
        refreshStats()
        Log.d(TAG_REPO, "syncFromFirebaseOnce: done")
    }


    // Real-time Firestore sync REMOVED — employees only see their own data
    // which is already in Room DB. No cross-employee sync needed.
    // Firebase reads = 0 during normal operation.

    fun loadTargets() = viewModelScope.launch(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val dateKey = dateStr.replace("/", "-")

        try {
            val expectedSalesSnapshot = FirebaseDatabase.getInstance()
                .getReference("expectedSales")
                .child(uid)
                .child(dateKey)
                .get()
                .await()
            val sales = (expectedSalesSnapshot.value as? Number)?.toInt() ?: 0
            expectedSales.postValue(sales)
        } catch (e: Exception) {
            Log.e("LeadVM", "Error loading expected sales: ${e.message}")
        }

        try {
            val adminTargetSnapshot = FirebaseDatabase.getInstance()
                .getReference("adminTargets")
                .child(uid)
                .child("target")
                .get()
                .await()
            val target = (adminTargetSnapshot.value as? Number)?.toInt() ?: 0
            adminTarget.postValue(if (target > 0) target.toString() else "Not Set")
        } catch (e: Exception) {
            Log.e("LeadVM", "Error loading admin target: ${e.message}")
        }
    }

    fun updateExpectedSales(newVal: Int) = viewModelScope.launch(Dispatchers.IO) {
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val success = FirestoreSource.saveExpectedSales(dateStr, newVal)
        if (success) {
            expectedSales.postValue(newVal)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }


    fun refreshStats() = viewModelScope.launch(Dispatchers.IO) {
        totalLeads.postValue(repo.totalLeads())
        totalInterested.postValue(repo.totalInterested())
        totalConnected.postValue(repo.totalConnected())
        totalCalled.postValue(repo.totalCalled())
        totalPending.postValue(repo.totalPending())
    }

    fun insertAll(leads: List<Lead>) = viewModelScope.launch(Dispatchers.IO) {
        isBatchInserting = true
        try {
            // Step 1: Save to local Room DB (never throws with IGNORE strategy)
            repo.insertAll(leads)
            refreshStats()

            // Step 2: Sync to Firestore + Sheets — catch separately so a
            // network failure does NOT crash the coroutine / the app
            try {
                FirestoreSource.saveLeadsBatch(leads)
                val allLeads = repo.getAllLeadsOnce()
                SheetsSync.syncAllLeads(getApplication(), allLeads)
                Log.d(TAG_REPO, "insertAll: cloud sync complete")
            } catch (syncEx: Exception) {
                Log.e(TAG_REPO, "insertAll: cloud sync failed (non-fatal): ${syncEx.message}")
                // Local DB insert already succeeded — just skip cloud sync
            }
        } catch (e: Exception) {
            Log.e(TAG_REPO, "insertAll: local DB error: ${e.message}")
        } finally {
            delay(3000L)
            isBatchInserting = false
            Log.d(TAG_REPO, "insertAll: complete, listener re-enabled")
        }
    }

    fun assignLeadsToEmployee(
        targetUserId: String,
        leads: List<Lead>,
        onResult: (success: Boolean) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        isBatchInserting = true
        try {
            val success = FirestoreSource.saveLeadsBatch(leads, targetUserId = targetUserId)
            onResult(success)
        } finally {
            delay(3000L)
            isBatchInserting = false
            Log.d(TAG_REPO, "assignLeadsToEmployee: complete, listener re-enabled")
        }
    }

    fun insert(lead: Lead) = viewModelScope.launch(Dispatchers.IO) {
        repo.insert(lead)
        refreshStats()
    }

    fun updateStatus(lead: Lead, status: String) = viewModelScope.launch(Dispatchers.IO) {
        val employeeName = SheetsSync.getEmployeeNameStatic(getApplication())
        val updated = lead.copy(
            status   = status,
            calledAt = System.currentTimeMillis(),
            calledBy = employeeName
        )
        // Always update Room DB immediately
        repo.update(updated)

        // Sync to Firestore/RTDB immediately so that Admin receives real-time updates
        repo.syncLeadToFirestore(updated)
        refreshStats()
    }

    fun updateStatusAndNotes(lead: Lead, status: String, notes: String) = viewModelScope.launch(Dispatchers.IO) {
        val employeeName = SheetsSync.getEmployeeNameStatic(getApplication())
        val updated = lead.copy(
            status   = status,
            notes    = notes,
            calledAt = System.currentTimeMillis(),
            calledBy = employeeName
        )
        // Always update Room DB immediately
        repo.update(updated)

        // Sync to Firestore/RTDB immediately so that Admin receives real-time updates
        repo.syncLeadToFirestore(updated)
        refreshStats()
    }

    fun updateLeadDirectly(lead: Lead) = viewModelScope.launch(Dispatchers.IO) {
        repo.update(lead)
        repo.syncLeadToFirestore(lead)
        refreshStats()
    }

    /** Patches only the firestoreId field in Room DB — no network call needed. */
    fun patchFirestoreId(lead: Lead, firestoreId: String) = viewModelScope.launch(Dispatchers.IO) {
        val patched = lead.copy(firestoreId = firestoreId)
        repo.updateLocal(patched)
        Log.d(TAG_REPO, "patchFirestoreId: ${lead.phone} → $firestoreId")
    }

    fun delete(lead: Lead) = viewModelScope.launch(Dispatchers.IO) {
        repo.delete(lead)
        refreshStats()
    }

    fun deleteAll() = viewModelScope.launch(Dispatchers.IO) {
        repo.deleteAll()                        // clear Room
        FirestoreSource.deleteAllLeads()        // clear Firestore too
        refreshStats()
        Log.d(TAG_REPO, "deleteAll: Room + Firestore cleared")
    }
}


class LeadViewModelFactory(private val application: Application) :
    androidx.lifecycle.ViewModelProvider.Factory {

    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LeadViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LeadViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}