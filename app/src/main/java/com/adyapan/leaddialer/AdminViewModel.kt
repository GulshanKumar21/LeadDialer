package com.adyapan.leaddialer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collect

/**
 * AdminViewModel — COST-OPTIMISED version
 *
 * Strategy:
 *  1. Load employee list from "users" collection ONCE (small — 200 docs max)
 *  2. Listen to RTDB "rtdb_leads" for per-user lead COUNTS only (free tier)
 *  3. Listen to Firestore "leads" per-user ONLY when admin clicks an employee
 *     (NOT a full-collection listener — that caused 1,00,000+ reads!)
 *  4. ExpectedSales from RTDB (free)
 *
 *  This reduces Firestore reads from ~1,00,000/day → ~200/day for admin list.
 */
class AdminViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().getReference("rtdb_leads")

    private val _employees = MutableLiveData<List<EmployeeSummary>>()
    val employees: LiveData<List<EmployeeSummary>> = _employees

    private val _userProfiles = MutableLiveData<List<UserProfile>>(emptyList())
    val userProfiles: LiveData<List<UserProfile>> = _userProfiles

    data class DaySummary(
        val dateDisplay: String,
        val dateKey: String,
        var totalCalled: Int = 0,
        var connected: Int = 0,
        var sales: Int = 0
    )

    private val _dayWiseStats = MutableLiveData<List<DaySummary>>()
    val dayWiseStats: LiveData<List<DaySummary>> = _dayWiseStats

    private val _todayAttendance = MutableLiveData<Map<String, String>>(emptyMap())
    val todayAttendance: LiveData<Map<String, String>> = _todayAttendance

    private var firestoreSalesDocs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // TL list — observed by fragment to populate spinner on each employee card
    private val _tlList = MutableLiveData<List<TeamLeaderManager.TeamLeader>>(emptyList())
    val tlList: LiveData<List<TeamLeaderManager.TeamLeader>> = _tlList

    // ── Completed sales across all employees ─────────────────────────────────
    private val _allSales = MutableLiveData<List<SaleRecord>>(emptyList())
    val allSales: LiveData<List<SaleRecord>> = _allSales

    // ── Leads for a specific employee (loaded on demand) ─────────────────────
    private val _employeeLeads = MutableLiveData<List<Lead>>(emptyList())
    val employeeLeads: LiveData<List<Lead>> = _employeeLeads

    private val _leadsLoading = MutableLiveData(false)
    val leadsLoading: LiveData<Boolean> = _leadsLoading

    // ── Internal caches ───────────────────────────────────────────────────────
    // uid → employee name (loaded from users collection once)
    private var uidToName: Map<String, String> = emptyMap()

    // UIDs that are admins — excluded from employee list
    private var adminUids: Set<String> = emptySet()

    // uid → designation (loaded from users collection once)
    private var uidToDesignation: Map<String, String> = emptyMap()

    private var _isHRPortal: Boolean = false

    fun setHRPortal(enabled: Boolean) {
        _isHRPortal = enabled
        rebuildSummaries()
    }

    // uid → RTDB lead counts (updated by RTDB listener — free)
    private var rtdbCounts: Map<String, RtdbCounts> = emptyMap()

    // uid → Firestore sales count (loaded once when admin opens list)
    private var firestoreSalesCounts: Map<String, Int> = emptyMap()

    // uid → expectedSales for today (set by employee)
    private var uidToExpectedSales: Map<String, Int> = emptyMap()

    // uid → adminTarget (target set by admin per employee, stored in RTDB)
    private var uidToAdminTarget: Map<String, Int> = emptyMap()

    // uid → TL name (resolved at rebuild time from uidToTlId + tlIdToName)
    private var uidToTlName: Map<String, String> = emptyMap()

    // uid → TL id (raw — populated from Firestore users.teamLeaderId, race-condition safe)
    private var uidToTlId: Map<String, String> = emptyMap()

    // tlId → TL name (populated by RTDB teamLeaders listener)
    private var tlIdToName: Map<String, String> = emptyMap()

    // Active RTDB listeners
    private var rtdbLeadsListener    : ValueEventListener? = null
    private var expectedSalesListener: ValueEventListener? = null
    private var adminTargetsListener : ValueEventListener? = null
    private var tlAssignListener     : ValueEventListener? = null  // live TL assignment listener
    private var crmPrimary = false

    data class RtdbCounts(
        val total: Int,
        val connected: Int,
        val interested: Int,
        val pending: Int,
        val salesDone: Int
    )

    init {
        startMonitoring()
    }

    fun loadAllEmployeeData() {
        startMonitoring()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 1 + 2 + 3: Load users (once) + RTDB listener (free) + sales counts
    // ─────────────────────────────────────────────────────────────────────────
    private fun startMonitoring() {
        _isLoading.postValue(true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Sync users from Supabase live to Firestore before loading
                try {
                    SupabaseSync.syncSupabaseUsersToFirestore(db)
                    SupabaseSync.syncSupabaseDocumentsToFirestore(db)
                } catch (e: Exception) {
                    android.util.Log.e("AdminViewModel", "Supabase sync failed: ${e.message}")
                }

                // ── Step 1: Load admin UIDs to EXCLUDE from employee list ────────────
                val adminSet = mutableSetOf<String>()
                try {
                    val adminSnap = db.collection("admins").get().await()
                    for (doc in adminSnap.documents) adminSet.add(doc.id)
                } catch (e: Exception) { /* ignore */ }
                adminUids = adminSet

                // ── Step 2: Load employee names, designations, and profiles ─────────
                val nameMap = mutableMapOf<String, String>()
                val designationMap = mutableMapOf<String, String>()
                val profilesList = mutableListOf<UserProfile>()
                try {
                    val usersSnap = db.collection("users").get().await()
                    for (doc in usersSnap.documents) {
                        val uid = doc.id
                        val des = doc.getString("designation") ?: ""
                        designationMap[uid] = des

                        val name = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                            ?: doc.getString("name")?.takeIf { it.isNotBlank() }
                            ?: doc.getString("email")?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                            ?: continue
                        nameMap[uid] = name

                        val profile = UserProfile(
                            uid              = uid,
                            name             = doc.getString("name") ?: name,
                            email            = doc.getString("email") ?: "",
                            phone            = doc.getString("phone") ?: "",
                            employeeId       = doc.getString("employeeId") ?: "",
                            designation      = des,
                            department       = doc.getString("department") ?: "",
                            reportingManager = doc.getString("reportingManager") ?: "",
                            workLocation     = doc.getString("workLocation") ?: "",
                            dateOfJoining    = doc.getString("dateOfJoining") ?: "",
                            dob              = doc.getString("dob") ?: ""
                        )
                        profilesList.add(profile)
                    }
                    _userProfiles.postValue(profilesList)
                } catch (e: Exception) { /* fallback — names picked up from RTDB */ }

                // Fallback: attendance collection for employee names
                if (nameMap.isEmpty()) {
                    try {
                        val attendSnap = db.collection("attendance").get().await()
                        for (doc in attendSnap.documents) {
                            val uid  = doc.getString("userId")       ?: continue
                            val name = doc.getString("employeeName") ?: continue
                            if (name.isNotBlank()) nameMap[uid] = name
                        }
                    } catch (e: Exception) { /* ignore */ }
                }

                uidToName = nameMap
                uidToDesignation = designationMap

                // ── Step 2: Load Firestore sales counts once (only salesDone=true docs) ─
                // Cost: Only documents where salesDone=true — much smaller than full collection
                try {
                    val salesSnap = db.collection("leads")
                        .whereEqualTo("salesDone", true)
                        .get().await()
                    firestoreSalesDocs = salesSnap.documents
                    val salesMap = mutableMapOf<String, Int>()
                    for (doc in salesSnap.documents) {
                        val uid = doc.getString("userId") ?: continue
                        salesMap[uid] = (salesMap[uid] ?: 0) + 1
                    }
                    firestoreSalesCounts = salesMap
                } catch (e: Exception) {
                    // non-critical — sales counts from RTDB salesDone flag
                }

                // ── Step 3: Today's date key for expectedSales ────────────────────────
                val todayStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

                // ── Step 4: RTDB listener for lead counts (free — structured by userId) ─
                // RTDB path: rtdb_leads/{userId}/{leadId}
                // We listen to the whole node but only compute COUNTS — no full download of lead details
                val rtdbListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val countsMap = mutableMapOf<String, RtdbCounts>()
                        val daySummaryMap = mutableMapOf<String, DaySummary>()
                        val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val sdfDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

                        snapshot.children.forEach { userNode ->
                            val uid = userNode.key ?: return@forEach
                            var total = 0; var connected = 0; var interested = 0
                            var pending = 0; var salesDone = 0
                            // Also extract employee name from RTDB if not already known
                            userNode.children.forEach { leadNode ->
                                val m = leadNode.value as? Map<*, *> ?: return@forEach
                                total++
                                val status = m["status"]?.toString()
                                val calledAt = (m["calledAt"] as? Number)?.toLong() ?: 0L
                                val isCalled = calledAt > 0L || (status != null && status != "Pending" && status.isNotBlank())
                                if (isCalled) {
                                    if (status == "Interested" || status == "Connected" || status == "Not Interested" || status?.startsWith("Not Interested") == true) {
                                        connected++
                                    }
                                    if (status == "Interested" || status == "Connected") {
                                        interested++
                                    }
                                } else {
                                    pending++
                                }
                                val leadSalesDone = (m["salesDone"] as? Boolean) ?: false
                                if (leadSalesDone) salesDone++

                                // Day-wise stats: Called status on that day
                                if (calledAt > 0L) {
                                    val dateKey = sdfKey.format(Date(calledAt))
                                    val dateDisplay = sdfDisplay.format(Date(calledAt))
                                    val summary = daySummaryMap.getOrPut(dateKey) {
                                        DaySummary(dateDisplay, dateKey)
                                    }
                                    summary.totalCalled++
                                    if (status == "Interested" || status == "Connected" || status == "Not Interested" || status?.startsWith("Not Interested") == true) {
                                        summary.connected++
                                    }
                                    if (leadSalesDone) {
                                        summary.sales++
                                    }
                                }
                            }
                            if (total > 0) {
                                countsMap[uid] = RtdbCounts(total, connected, interested, pending, salesDone)
                            }
                        }

                        // Add Firestore Sales Docs to Day Summary
                        firestoreSalesDocs.forEach { doc ->
                            val calledAt = doc.getLong("calledAt") ?: 0L
                            if (calledAt > 0L) {
                                val dateKey = sdfKey.format(Date(calledAt))
                                val dateDisplay = sdfDisplay.format(Date(calledAt))
                                val summary = daySummaryMap.getOrPut(dateKey) {
                                    DaySummary(dateDisplay, dateKey)
                                }
                                summary.totalCalled++
                                val status = doc.getString("status")
                                if (status == "Interested" || status == "Connected" || status == "Not Interested" || status?.startsWith("Not Interested") == true) {
                                    summary.connected++
                                }
                                summary.sales++
                            }
                        }

                        val dayList = daySummaryMap.values.sortedByDescending { it.dateKey }
                        _dayWiseStats.postValue(dayList)

                        rtdbCounts = countsMap
                        uidToName  = nameMap
                        rebuildSummaries()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        _error.postValue("RTDB error: ${error.message}")
                    }
                }
                rtdbLeadsListener = rtdbListener
                rtdb.addValueEventListener(rtdbListener)

                // ── Step 5: Expected sales listener ──────────────────────────────────
                val expectedRef = FirebaseDatabase.getInstance().getReference("expectedSales")
                val expListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val map = mutableMapOf<String, Int>()
                        snapshot.children.forEach { userNode ->
                            val uid   = userNode.key ?: return@forEach
                            if (adminUids.contains(uid)) return@forEach   // skip admins
                            val sales = (userNode.child(todayStr).value as? Number)?.toInt() ?: 0
                            if (sales > 0) map[uid] = sales
                        }
                        uidToExpectedSales = map
                        rebuildSummaries()
                    }
                    override fun onCancelled(error: DatabaseError) { /* ignore */ }
                }
                expectedSalesListener = expListener
                expectedRef.addValueEventListener(expListener)

                // ── Step 6: Admin targets listener (RTDB: adminTargets/{uid}/target) ──
                val adminTargetsRef = FirebaseDatabase.getInstance().getReference("adminTargets")
                val adminTargetListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val map = mutableMapOf<String, Int>()
                        snapshot.children.forEach { userNode ->
                            val uid    = userNode.key ?: return@forEach
                            val target = (userNode.child("target").value as? Number)?.toInt() ?: 0
                            if (target > 0) map[uid] = target
                        }
                        uidToAdminTarget = map
                        rebuildSummaries()
                    }
                    override fun onCancelled(error: DatabaseError) { /* ignore */ }
                }
                adminTargetsListener = adminTargetListener
                adminTargetsRef.addValueEventListener(adminTargetListener)

                // ── Step 7: LIVE listener for teamLeaders + users TL assignments ──
                val tlRef   = FirebaseDatabase.getInstance().getReference("teamLeaders")

                // Keep tlIdToName in a shared mutable so both listeners can read it
                val tlIdToNameShared = mutableMapOf<String, String>()

                val tlListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        tlIdToNameShared.clear()
                        val tlObjList = mutableListOf<TeamLeaderManager.TeamLeader>()
                        snapshot.children.forEach { node ->
                            val tlId   = node.key ?: return@forEach
                            val map    = node.value as? Map<*, *> ?: return@forEach
                            val tlName = map["name"]?.toString() ?: return@forEach
                            tlIdToNameShared[tlId] = tlName
                            tlObjList.add(TeamLeaderManager.TeamLeader(
                                id        = tlId,
                                name      = tlName,
                                sheetUrl  = map["sheetUrl"]?.toString() ?: "",
                                userId    = map["userId"]?.toString()   ?: "",
                                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
                            ))
                        }
                        _tlList.postValue(tlObjList.sortedBy { it.name })

                        // Update shared tlId→name cache and re-resolve all assignments
                        tlIdToName = tlIdToNameShared.toMap()
                        rebuildSummaries()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        android.util.Log.e("AdminVM", "tlListener cancelled: ${error.message}")
                    }
                }
                tlAssignListener = tlListener
                tlRef.addValueEventListener(tlListener)

                // Read teamLeaderId from Firestore "users" — store raw tlId (race-condition safe)
                // TL name is resolved later at rebuildSummaries() time
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val usersSnap = db.collection("users").get().await()
                        val userTlIdMap = mutableMapOf<String, String>()
                        usersSnap.documents.forEach { doc ->
                            val uid  = doc.id
                            val tlId = doc.getString("teamLeaderId")
                            if (!tlId.isNullOrBlank()) {
                                userTlIdMap[uid] = tlId
                            }
                        }
                        uidToTlId = userTlIdMap
                        rebuildSummaries()
                    } catch (e: Exception) {
                        android.util.Log.e("AdminVM", "Firestore usersListener error: ${e.message}")
                    }
                }

                // ── Step 8: Live listener for all completed sales (Flat Collection) ──
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        FirestoreSource.salesFlow().collect { salesList ->
                            _allSales.postValue(salesList)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AdminVM", "salesFlow error: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                _error.postValue("Initialization failed: ${e.message}")
                _isLoading.postValue(false)
            }
        }
    }

    /** Rebuilds EmployeeSummary list from cached counts — NO extra reads */
    private suspend fun loadCrmEmployeesSnapshot(): Boolean {
        try {
            val crmEmployees = CrmApi.getAdminEmployees()
            if (crmEmployees.isEmpty()) return false

            crmPrimary = true
            uidToName = crmEmployees.associate { it.userId to it.employeeName }
            _employees.postValue(crmEmployees.sortedBy { it.employeeName.lowercase() })
            _isLoading.postValue(false)
            loadTodayAttendance(crmEmployees.map { it.userId })
            loadCrmDaySummaries()
            return true
        } catch (e: Exception) {
            android.util.Log.w("AdminVM", "CRM employees load failed: ${e.message}")
            return false
        }
    }

    private fun loadCrmDaySummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val leads = CrmApi.getAdminLeads(limit = 1000)
                val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sdfDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val summaries = leads
                    .filter { it.calledAt > 0L }
                    .groupBy { sdfKey.format(Date(it.calledAt)) }
                    .map { (dateKey, rows) ->
                        DaySummary(
                            dateDisplay = sdfDisplay.format(Date(rows.first().calledAt)),
                            dateKey = dateKey,
                            totalCalled = rows.size,
                            connected = rows.count { it.status == "Connected" || it.status == "Interested" },
                            sales = rows.count { it.salesDone }
                        )
                    }
                    .sortedByDescending { it.dateKey }
                _dayWiseStats.postValue(summaries)
            } catch (e: Exception) {
                android.util.Log.w("AdminVM", "CRM day summary load failed: ${e.message}")
            }
        }
    }

    private fun rebuildSummaries() {
        val allUids = (uidToName.keys + rtdbCounts.keys).toSet()

        // Resolve uid → TL name using the raw tlId map + current tlIdToName lookup
        // This is race-condition safe: works even if tlIdToName loads after uidToTlId
        val resolvedTlNames = uidToTlId.mapNotNull { (uid, tlId) ->
            val name = tlIdToName[tlId]
            if (!name.isNullOrBlank()) uid to name else null
        }.toMap()
        uidToTlName = resolvedTlNames

        val summaries = allUids.mapNotNull { uid ->
            if (adminUids.contains(uid)) {
                val des = uidToDesignation[uid] ?: ""
                val isHrUser = des.contains("HR", ignoreCase = true)
                if (_isHRPortal || !isHrUser) {
                    return@mapNotNull null
                }
            }

            // Also exclude if designation is Admin
            val des = uidToDesignation[uid] ?: ""
            if (des.equals("Admin", ignoreCase = true)) {
                return@mapNotNull null
            }

            val name = uidToName[uid] ?: return@mapNotNull null
            val rtdb = rtdbCounts[uid] ?: RtdbCounts(0, 0, 0, 0, 0)
            val totalSales = maxOf(rtdb.salesDone, firestoreSalesCounts[uid] ?: 0)
            EmployeeSummary(
                employeeName  = name.replaceFirstChar { it.uppercase() },
                userId        = uid,
                totalLeads    = rtdb.total,
                connected     = rtdb.connected,
                interested    = rtdb.interested,
                pending       = rtdb.pending,
                salesDone     = totalSales,
                expectedSales = uidToExpectedSales[uid] ?: 0,
                adminTarget   = uidToAdminTarget[uid]   ?: 0,
                tlName        = uidToTlName[uid]        ?: "",
                leads         = emptyList()
            )
        }.sortedByDescending { it.totalLeads }

        _employees.postValue(summaries)
        _isLoading.postValue(false)
        loadTodayAttendance(summaries.map { it.userId })
    }

    var selectedDateStr: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun loadTodayAttendance(employeeIds: List<String>) {
        loadTodayAttendance(selectedDateStr, employeeIds)
    }

    fun loadTodayAttendance(dateStr: String, employeeIds: List<String>) {
        selectedDateStr = dateStr
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val crmAttendance = CrmApi.getHrAttendance(dateStr)
                if (crmAttendance.isNotEmpty()) {
                    _todayAttendance.postValue(crmAttendance)
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.w("AdminVM", "CRM attendance load failed: ${e.message}")
            }

            val attendanceMap = mutableMapOf<String, String>()
            val jobs = employeeIds.map { uid ->
                async {
                    try {
                        val doc = db.collection("attendance").document(uid)
                            .collection("dates").document(dateStr).get().await()
                        val status = if (doc.exists()) {
                            doc.getString("status") ?: "Present"
                        } else {
                            "Absent"
                        }
                        uid to status
                    } catch (e: Exception) {
                        uid to "Absent"
                    }
                }
            }
            val results = jobs.awaitAll()
            results.forEach { (uid, status) ->
                attendanceMap[uid] = status
            }
            _todayAttendance.postValue(attendanceMap)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // On-demand: Load leads for ONE employee when admin clicks
    // Cost: Only that employee's docs (~500 RTDB nodes + their Firestore sales)
    // ─────────────────────────────────────────────────────────────────────────
    fun loadEmployeeLeads(userId: String) {
        _leadsLoading.postValue(true)
        _employeeLeads.postValue(emptyList())

        viewModelScope.launch(Dispatchers.IO) {
            val leads = mutableListOf<Lead>()

            try {
                val crmLeads = CrmApi.getAdminLeads(userId = userId, limit = 1000)
                if (crmLeads.isNotEmpty() || crmPrimary) {
                    _employeeLeads.postValue(crmLeads.sortedByDescending { it.calledAt })
                    _leadsLoading.postValue(false)
                    return@launch
                }
            } catch (e: Exception) {
                android.util.Log.w("AdminVM", "CRM employee leads load failed: ${e.message}")
            }

            // 1. RTDB leads for this employee (free)
            try {
                val rtdbSnap = FirebaseDatabase.getInstance()
                    .getReference("rtdb_leads").child(userId).get().await()
                rtdbSnap.children.forEach { leadNode ->
                    val m = leadNode.value as? Map<*, *> ?: return@forEach
                    runCatching {
                        leads.add(Lead(
                            id          = 0,
                            name        = m["name"]?.toString()         ?: "",
                            phone       = m["phone"]?.toString()        ?: "",
                            status      = m["status"]?.toString()       ?: "Pending",
                            notes       = m["notes"]?.toString()        ?: "",
                            calledAt    = (m["calledAt"] as? Number)?.toLong() ?: 0L,
                            duration    = (m["duration"] as? Number)?.toInt()  ?: 0,
                            firestoreId = leadNode.key ?: "",
                            collegeName = m["collegeName"]?.toString()  ?: "",
                            collegeCity = m["collegeCity"]?.toString()  ?: "",
                            isHotLead   = (m["isHotLead"] as? Boolean) ?: false,
                            calledBy    = m["calledBy"]?.toString()     ?: "",
                            salesDone   = (m["salesDone"] as? Boolean) ?: false
                        ))
                    }
                }
            } catch (e: Exception) {
                // non-critical
            }

            // 2. Firestore leads for this employee (hot/interested/sales — small subset)
            try {
                val fsSnap = db.collection("leads")
                    .whereEqualTo("userId", userId)
                    .get().await()
                fsSnap.documents.forEach { doc ->
                    runCatching {
                        leads.add(Lead(
                            id          = 0,
                            name        = doc.getString("name")        ?: "",
                            phone       = doc.getString("phone")       ?: "",
                            status      = doc.getString("status")      ?: "Pending",
                            notes       = doc.getString("notes")       ?: "",
                            calledAt    = doc.getLong("calledAt")      ?: 0L,
                            duration    = (doc.getLong("duration")     ?: 0L).toInt(),
                            firestoreId = doc.id,
                            collegeName = doc.getString("collegeName") ?: "",
                            collegeCity = doc.getString("collegeCity") ?: "",
                            isHotLead   = doc.getBoolean("isHotLead") ?: false,
                            calledBy    = doc.getString("calledBy")    ?: "",
                            salesDone   = doc.getBoolean("salesDone") ?: false
                        ))
                    }
                }
            } catch (e: Exception) {
                // non-critical
            }

            // Deduplicate by phone (RTDB and Firestore may have overlaps during promotion)
            val deduplicated = leads
                .groupBy { it.phone }
                .values
                .map { group ->
                    // Prefer Firestore version (has firestoreId, more up-to-date)
                    group.firstOrNull { it.firestoreId?.contains("_") == false && it.firestoreId?.isNotBlank() == true }
                        ?: group.maxByOrNull { it.calledAt }
                        ?: group.first()
                }
                .sortedByDescending { it.calledAt }

            _employeeLeads.postValue(deduplicated)
            _leadsLoading.postValue(false)
        }
    }

    /**
     * Toggles the salesDone flag for a specific lead in the local LiveData.
     * Provides an instantaneous UI response when Admin toggles a checkbox.
     */
    fun updateLeadSalesDoneLocally(firestoreId: String, salesDone: Boolean) {
        val currentList = _employeeLeads.value ?: return
        val updatedList = currentList.map {
            if (it.firestoreId == firestoreId) {
                it.copy(salesDone = salesDone)
            } else {
                it
            }
        }
        _employeeLeads.postValue(updatedList)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup — MUST call before logout to prevent "permission denied" errors
    // ─────────────────────────────────────────────────────────────────────────
    fun clearAllListeners() {
        rtdbLeadsListener?.let { rtdb.removeEventListener(it) }
        rtdbLeadsListener = null

        expectedSalesListener?.let {
            FirebaseDatabase.getInstance()
                .getReference("expectedSales")
                .removeEventListener(it)
        }
        expectedSalesListener = null

        adminTargetsListener?.let {
            FirebaseDatabase.getInstance()
                .getReference("adminTargets")
                .removeEventListener(it)
        }
        adminTargetsListener = null

        tlAssignListener?.let {
            FirebaseDatabase.getInstance()
                .getReference("teamLeaders")
                .removeEventListener(it)
        }
        tlAssignListener = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admin sets target for an employee (RTDB: adminTargets/{userId}/target)
    // Free — no Firestore cost
    // ─────────────────────────────────────────────────────────────────────────
    fun saveAdminTarget(userId: String, target: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FirebaseDatabase.getInstance()
                    .getReference("adminTargets")
                    .child(userId)
                    .child("target")
                    .setValue(target)
                    .await()
                android.util.Log.d("AdminVM", "saveAdminTarget OK: $userId → $target")
            } catch (e: Exception) {
                _error.postValue("Target save failed: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Assign TL to employee — writes to RTDB + refreshes badge immediately
    // ─────────────────────────────────────────────────────────────────────────────
    fun saveTlAssignment(userId: String, tl: TeamLeaderManager.TeamLeader) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                TeamLeaderManager.assignTlToUser(userId, tl.id)
                // Update both raw ID cache and name cache immediately
                val updatedId = uidToTlId.toMutableMap()
                updatedId[userId] = tl.id
                uidToTlId = updatedId
                rebuildSummaries()
                android.util.Log.d("AdminVM", "saveTlAssignment OK: $userId → ${tl.name}")
            } catch (e: Exception) {
                _error.postValue("TL assign failed: ${e.message}")
            }
        }
    }

    fun removeTlAssignment(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                TeamLeaderManager.unassignTlFromUser(userId)
                val updatedId = uidToTlId.toMutableMap()
                updatedId.remove(userId)
                uidToTlId = updatedId
                rebuildSummaries()
                android.util.Log.d("AdminVM", "removeTlAssignment OK: $userId")
            } catch (e: Exception) {
                _error.postValue("TL remove failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        clearAllListeners()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AdminLeadAdapter — unchanged
// ─────────────────────────────────────────────────────────────────────────────
class AdminLeadAdapter(
    private val onSalesDoneToggle: (Lead) -> Unit = {}
) : ListAdapter<Lead, AdminLeadAdapter.VH>(DIFF) {

    private val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar  : TextView = view.findViewById(R.id.tvAvatar)
        val name    : TextView = view.findViewById(R.id.tvName)
        val phone   : TextView = view.findViewById(R.id.tvPhone)
        val status  : TextView = view.findViewById(R.id.tvStatus)
        val callBtn : View     = view.findViewById(R.id.btnCall)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lead, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val lead = getItem(position)
        holder.avatar.text = lead.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.name.text   = lead.name
        holder.phone.text  = lead.phone

        holder.status.text = buildString {
            if (lead.salesDone) append("💰 SOLD • ")
            append(lead.status)
            if (lead.calledAt > 0) {
                append(" • ")
                append(dateFormat.format(Date(lead.calledAt)))
            }
        }

        holder.status.setTextColor(
            when {
                lead.salesDone                 -> 0xFF22C55E.toInt()
                lead.status == "Connected"     -> 0xFF22C55E.toInt()
                lead.status == "Interested"    -> 0xFF3B82F6.toInt()
                lead.status == "Not Connected" -> 0xFFEF4444.toInt()
                else                           -> 0xFF999999.toInt()
            }
        )

        holder.itemView.setBackgroundColor(
            if (lead.salesDone) 0x1A22C55E.toInt() else android.graphics.Color.TRANSPARENT
        )

        holder.callBtn.visibility = View.GONE

        holder.itemView.setOnLongClickListener {
            onSalesDoneToggle(lead)
            true
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Lead>() {
            override fun areItemsTheSame(a: Lead, b: Lead) = a.phone == b.phone
            override fun areContentsTheSame(a: Lead, b: Lead) = a == b
        }
    }
}
