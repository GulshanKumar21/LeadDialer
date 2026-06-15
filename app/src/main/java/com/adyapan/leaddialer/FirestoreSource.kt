package com.adyapan.leaddialer

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object FirestoreSource {

    private const val TAG = "FirestoreSource"

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance().getReference("rtdb_leads")

    private val leadsCol      = db.collection("leads")
    private val callsCol      = db.collection("callRecords")
    private val attendanceCol = db.collection("attendance")
    private val adminsCol     = db.collection("admins")
    private val usersCol      = db.collection("users")
    private val leavesCol     = db.collection("leaveRequests")

    @Volatile private var adminChecked = false
    @Volatile var isAdmin = false
        private set

    fun setAdminStatus(isUserAdmin: Boolean) {
        isAdmin = isUserAdmin
        adminChecked = true
        Log.d(TAG, "setAdminStatus: isAdmin=$isAdmin")
    }

    fun clearAdminStatus() {
        isAdmin = false
        adminChecked = false
        Log.d(TAG, "clearAdminStatus: Reset admin flags")
    }


    private fun currentUid(): String? = auth.currentUser?.uid

    private fun leadDocId(uid: String, phone: String) =
        "${uid}_${phone.filter { it.isDigit() }}"

    private fun callDocId(uid: String, phone: String, calledAt: Long) =
        "${uid}_${phone.filter { it.isDigit() }}_$calledAt"

    private fun attendDocId(uid: String, date: String) =
        "${uid}_${date.replace("/", "-")}"


    /** Fetches display name of an employee by their UID from the users collection. */
    suspend fun getEmployeeDisplayName(uid: String): String {
        return try {
            val doc = usersCol.document(uid).get().await()
            doc.getString("displayName")?.takeIf { it.isNotBlank() }
                ?: doc.getString("name")?.takeIf { it.isNotBlank() }
                ?: doc.getString("email")?.substringBefore("@")
                ?: "Employee"
        } catch (e: Exception) {
            Log.e(TAG, "getEmployeeDisplayName error: ${e.message}")
            "Employee"
        }
    }

    suspend fun saveLead(lead: Lead): String? {
        val uid = currentUid() ?: return null
        val docId = leadDocId(uid, lead.phone)

        val targetUserId = lead.calledBy.ifBlank { uid }
        // ALL leads go to RTDB. Only salesDone=true gets a Firestore backup (for admin reporting).
        val isPremium = lead.salesDone

        return try {
            val user = auth.currentUser
            val empName = user?.displayName?.takeIf { it.isNotBlank() }
                ?: user?.email?.substringBefore("@") ?: "Employee"

            val data = hashMapOf<String, Any>(
                "userId"       to targetUserId,
                "employeeName" to empName,
                "email"        to (user?.email ?: ""),
                "name"         to lead.name,
                "phone"        to lead.phone.filter { it.isDigit() },
                "status"       to lead.status,
                "notes"        to lead.notes,
                "calledAt"     to lead.calledAt,
                "duration"     to lead.duration,
                "collegeName"  to lead.collegeName,
                "collegeCity"  to lead.collegeCity,
                "calledBy"     to lead.calledBy,
                "salesDone"    to lead.salesDone,
                "updatedAt"    to FieldValue.serverTimestamp()
            )

            if (isPremium) {
                // Save to Firestore (High-value lead)
                leadsCol.document(docId).set(data, SetOptions.merge()).await()
                // Remove from RTDB if it existed there
                rtdb.child(targetUserId).child(docId).removeValue().await()
                Log.d(TAG, "saveLead (Premium) OK: $docId")
            } else {
                // Save to RTDB (Pending/Raw lead) to save costs
                val rtdbData = data.toMutableMap()
                rtdbData["updatedAt"] = System.currentTimeMillis() // RTDB uses Long timestamp
                rtdb.child(targetUserId).child(docId).setValue(rtdbData).await()
                // Remove from Firestore if it was downgraded
                leadsCol.document(docId).delete().await()
                Log.d(TAG, "saveLead (RTDB) OK: $docId")
            }
            docId
        } catch (e: Exception) {
            Log.e(TAG, "saveLead error: ${e.message}")
            null
        }
    }

    suspend fun saveLeadsBatch(leads: List<Lead>, targetUserId: String? = null): Boolean {
        if (leads.isEmpty()) return true
        val uid = targetUserId ?: currentUid() ?: return false
        val user = auth.currentUser
        val empName = if (targetUserId == null) {
            user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email?.substringBefore("@") ?: "Employee"
        } else {
            // Try to fetch the target employee's real name from Firestore users collection
            try {
                getEmployeeDisplayName(targetUserId)
            } catch (e: Exception) {
                "Employee"
            }
        }

        return try {
            // Write ALL bulk uploads to RTDB to save 90% cost
            val targetRef = rtdb.child(uid)
            leads.chunked(400).forEach { chunk ->
                val updates = mutableMapOf<String, Any>()
                chunk.forEach { lead ->
                    val docId = leadDocId(uid, lead.phone)
                    val rtdbData = hashMapOf<String, Any>(
                        "userId"       to uid,
                        "employeeName" to empName,
                        "email"        to (if (targetUserId == null) user?.email ?: "" else ""),
                        "name"         to lead.name,
                        "phone"        to lead.phone.filter { it.isDigit() },
                        "status"       to lead.status,
                        "notes"        to lead.notes,
                        "calledAt"     to lead.calledAt,
                        "duration"     to lead.duration,
                        "collegeName"  to lead.collegeName,
                        "collegeCity"  to lead.collegeCity,
                        "calledBy"     to lead.calledBy,
                        "salesDone"    to lead.salesDone,
                        "updatedAt"    to System.currentTimeMillis()
                    )
                    updates[docId] = rtdbData
                }
                targetRef.updateChildren(updates).await()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveLeadsBatch error: ${e.message}")
            false
        }
    }

    suspend fun deleteLead(phone: String): Boolean {
        val uid = currentUid() ?: return false
        val docId = leadDocId(uid, phone)
        return try {
            leadsCol.document(docId).delete().await()
            rtdb.child(uid).child(docId).removeValue().await()
            Log.d(TAG, "deleteLead OK: $docId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteLead error: ${e.message}")
            false
        }
    }


    suspend fun deleteAllLeads(): Boolean {
        val uid = currentUid() ?: return false
        return try {
            val snapshot = leadsCol.whereEqualTo("userId", uid).get().await()
            snapshot.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            rtdb.child(uid).removeValue().await()
            Log.d(TAG, "deleteAllLeads OK: uid=$uid")
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteAllLeads error: ${e.message}")
            false
        }
    }


    suspend fun fetchLeadsOnce(): List<Lead> {
        val uid = currentUid() ?: return emptyList()
        return try {
            val query = if (isAdmin) leadsCol
            else leadsCol.whereEqualTo("userId", uid)
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { doc ->
                runCatching {
                    Lead(
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
                        isHotLead   = false,
                        calledBy    = doc.getString("calledBy")    ?: "",
                        salesDone   = doc.getBoolean("salesDone")  ?: false
                    )
                }.getOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchLeadsOnce error: ${e.message}")
            emptyList()
        }
    }

    suspend fun performLeadRetentionCleanup(uid: String) {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            
            // 1. Cleanup RTDB Leads older than 30 days that are NOT important (salesDone, Interested, Hot)
            val targetRef = rtdb.child(uid)
            val snapshot = targetRef.get().await()
            val updates = mutableMapOf<String, Any?>()
            
            snapshot.children.forEach { leadNode ->
                val map = leadNode.value as? Map<*, *>
                if (map != null) {
                    val calledAt = (map["calledAt"] as? Number)?.toLong() ?: 0L
                    val salesDone = (map["salesDone"] as? Boolean) ?: false
                    val status = map["status"]?.toString() ?: "Pending"
                    
                    val isImportant = salesDone || status == "Interested" || status == "Hot"
                    if (calledAt > 0 && calledAt < thirtyDaysAgo && !isImportant) {
                        updates[leadNode.key ?: ""] = null
                    }
                }
            }
            if (updates.isNotEmpty()) {
                targetRef.updateChildren(updates).await()
                Log.d(TAG, "performLeadRetentionCleanup (RTDB): deleted ${updates.size} old leads for user $uid")
            }

            // 2. Cleanup Firestore Leads older than 30 days that are NOT important (just in case)
            val fsSnapshot = leadsCol.whereEqualTo("userId", uid).get().await()
            val batch = db.batch()
            var hasFsUpdates = false
            fsSnapshot.documents.forEach { doc ->
                val calledAt = doc.getLong("calledAt") ?: 0L
                val salesDone = doc.getBoolean("salesDone") ?: false
                val status = doc.getString("status") ?: "Pending"
                
                val isImportant = salesDone || status == "Interested" || status == "Hot"
                if (calledAt > 0 && calledAt < thirtyDaysAgo && !isImportant) {
                    batch.delete(doc.reference)
                    hasFsUpdates = true
                }
            }
            if (hasFsUpdates) {
                batch.commit().await()
                Log.d(TAG, "performLeadRetentionCleanup (Firestore): deleted old non-important leads")
            }
        } catch (e: Exception) {
            Log.e(TAG, "performLeadRetentionCleanup error: ${e.message}")
        }
    }

    /** One-time fetch of all RTDB leads for the current user (used on app start to populate Room DB). */
    suspend fun fetchRtdbLeadsOnce(): List<Lead> {
        val uid = currentUid() ?: return emptyList()
        if (!isAdmin) {
            performLeadRetentionCleanup(uid)
        }
        return try {
            val targetRef = if (isAdmin) rtdb else rtdb.child(uid)
            val snapshot = targetRef.get().await()
            val list = mutableListOf<Lead>()
            if (isAdmin) {
                snapshot.children.forEach { userNode ->
                    userNode.children.forEach { leadNode ->
                        parseRtdbLead(leadNode.key ?: "", leadNode.value as? Map<*, *>)?.let { list.add(it) }
                    }
                }
            } else {
                snapshot.children.forEach { leadNode ->
                    parseRtdbLead(leadNode.key ?: "", leadNode.value as? Map<*, *>)?.let { list.add(it) }
                }
            }
            Log.d(TAG, "fetchRtdbLeadsOnce: ${list.size} leads")
            list
        } catch (e: Exception) {
            Log.e(TAG, "fetchRtdbLeadsOnce error: ${e.message}")
            emptyList()
        }
    }


    private fun parseRtdbLead(docId: String, map: Map<*, *>?): Lead? {
        if (map == null) return null
        return runCatching {
            Lead(
                id          = 0,
                name        = map["name"]?.toString()        ?: "",
                phone       = map["phone"]?.toString()       ?: "",
                status      = map["status"]?.toString()      ?: "Pending",
                notes       = map["notes"]?.toString()       ?: "",
                calledAt    = (map["calledAt"] as? Number)?.toLong() ?: 0L,
                duration    = (map["duration"] as? Number)?.toInt()  ?: 0,
                firestoreId = docId,
                collegeName = map["collegeName"]?.toString() ?: "",
                collegeCity = map["collegeCity"]?.toString() ?: "",
                isHotLead   = false,
                calledBy    = map["calledBy"]?.toString()    ?: "",
                salesDone   = (map["salesDone"] as? Boolean) ?: false
            )
        }.getOrNull()
    }

    fun leadsFlow(): Flow<List<Lead>> = callbackFlow {
        val uid = currentUid()
        if (uid == null) {
            close()
            return@callbackFlow
        }
        
        var firestoreLeads = emptyList<Lead>()
        var rtdbLeads = emptyList<Lead>()

        fun emitCombined() {
            trySend(firestoreLeads + rtdbLeads)
        }

        // 1. Listen to Firestore (High-value leads)
        val query: Query = if (isAdmin) leadsCol else leadsCol.whereEqualTo("userId", uid)
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "leadsFlow firestore error: ${error.message}")
                return@addSnapshotListener
            }
            firestoreLeads = snapshot?.documents?.mapNotNull { doc ->
                runCatching {
                    Lead(
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
                        isHotLead   = false,
                        calledBy    = doc.getString("calledBy")    ?: "",
                        salesDone   = doc.getBoolean("salesDone")  ?: false
                    )
                }.getOrNull()
            } ?: emptyList()
            emitCombined()
        }

        // 2. Listen to RTDB (Pending/Raw leads)
        val targetRef = if (isAdmin) rtdb else rtdb.child(uid)
        val rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Lead>()
                if (isAdmin) {
                    snapshot.children.forEach { userNode ->
                        userNode.children.forEach { leadNode ->
                            val map = leadNode.value as? Map<*, *>
                            parseRtdbLead(leadNode.key ?: "", map)?.let { list.add(it) }
                        }
                    }
                } else {
                    snapshot.children.forEach { leadNode ->
                        val map = leadNode.value as? Map<*, *>
                        parseRtdbLead(leadNode.key ?: "", map)?.let { list.add(it) }
                    }
                }
                rtdbLeads = list
                emitCombined()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "rtdb error: ${error.message}")
            }
        }
        targetRef.addValueEventListener(rtdbListener)

        awaitClose { 
            registration.remove() 
            targetRef.removeEventListener(rtdbListener)
        }
    }

    /**
     * Update the salesDone flag for a lead.
     * Handles both Firestore-stored leads and RTDB-stored (Pending/bulk) leads.
     * When marking salesDone=true, a RTDB lead is promoted to Firestore.
     */
    suspend fun updateSalesDone(firestoreId: String, salesDone: Boolean, employeeUid: String? = null): Boolean {
        val uid = currentUid() ?: return false
        val targetUid = employeeUid ?: uid

        try {
            // ── Try Firestore first (Hot/Interested leads live here) ──────────────
            var inFirestore = false
            try {
                val docSnap = leadsCol.document(firestoreId).get().await()
                inFirestore = docSnap.exists()
            } catch (e: Exception) {
                Log.w(TAG, "updateSalesDone: Firestore get failed, moving to RTDB. Error: ${e.message}")
                inFirestore = false
            }

            if (inFirestore) {
                leadsCol.document(firestoreId)
                    .update(
                        "salesDone", salesDone,
                        "updatedAt", FieldValue.serverTimestamp()
                    ).await()
                Log.d(TAG, "updateSalesDone (Firestore) OK: $firestoreId → $salesDone")
                return true
            }

            // ── Firestore doc not found → lead is in RTDB ────────────────────
            Log.d(TAG, "updateSalesDone: not in Firestore, trying RTDB for $firestoreId")
            val rtdbRef = rtdb.child(targetUid).child(firestoreId)
            val rtdbSnap = rtdbRef.get().await()

            if (!rtdbSnap.exists()) {
                Log.e(TAG, "updateSalesDone: doc not found in Firestore OR RTDB — $firestoreId")
                return false
            }

            val map = rtdbSnap.value as? Map<*, *> ?: return false

            if (salesDone) {
                // Promote to Firestore so it becomes a "premium" lead
                val data = hashMapOf<String, Any>(
                    "userId"       to (map["userId"]?.toString()       ?: targetUid),
                    "employeeName" to (map["employeeName"]?.toString() ?: ""),
                    "email"        to (map["email"]?.toString()        ?: ""),
                    "name"         to (map["name"]?.toString()         ?: ""),
                    "phone"        to (map["phone"]?.toString()        ?: ""),
                    "status"       to (map["status"]?.toString()       ?: "Pending"),
                    "notes"        to (map["notes"]?.toString()        ?: ""),
                    "calledAt"     to ((map["calledAt"] as? Number)?.toLong() ?: 0L),
                    "duration"     to ((map["duration"] as? Number)?.toLong() ?: 0L),
                    "collegeName"  to (map["collegeName"]?.toString()  ?: ""),
                    "collegeCity"  to (map["collegeCity"]?.toString()  ?: ""),
                    "isHotLead"    to false,
                    "calledBy"     to (map["calledBy"]?.toString()     ?: ""),
                    "salesDone"    to true,
                    "updatedAt"    to FieldValue.serverTimestamp()
                )
                leadsCol.document(firestoreId).set(data, SetOptions.merge()).await()
                rtdbRef.removeValue().await()          // remove from RTDB after promoting
                Log.d(TAG, "updateSalesDone: RTDB lead promoted to Firestore — $firestoreId")
            } else {
                // Just flip the flag in RTDB (no promotion needed)
                rtdbRef.child("salesDone").setValue(false).await()
                rtdbRef.child("updatedAt").setValue(System.currentTimeMillis()).await()
                Log.d(TAG, "updateSalesDone (RTDB) OK: $firestoreId → false")
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "updateSalesDone error: ${e.message}")
            return false
        }
    }

    private val rtdbCalls = FirebaseDatabase.getInstance().getReference("callRecords")

    suspend fun saveCallRecord(record: CallRecord): Boolean {
        val uid = currentUid() ?: return false
        val docId = callDocId(uid, record.phone, record.calledAt)
        return try {
            val data = hashMapOf(
                "userId"   to uid,
                "name"     to record.name,
                "phone"    to record.phone.filter { it.isDigit() },
                "status"   to record.status,
                "duration" to record.duration,
                "calledAt" to record.calledAt,
                "note"     to record.note          // Custom message for admin to see
            )
            rtdbCalls.child(uid).child(docId).setValue(data).await()
            Log.d(TAG, "saveCallRecord (RTDB) OK: $docId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveCallRecord (RTDB) error: ${e.message}")
            false
        }
    }

    /**
     * Batch push ALL call records for the current user to RTDB in ONE network call.
     *
     * Key = uid_phone_calledAt
     * Same phone called 50 times = 50 DIFFERENT timestamps = 50 DIFFERENT RTDB nodes.
     * No deduplication of calls — every attempt is stored.
     *
     * Example:
     *   9876543210 called at 11:00 → key: uid_9876543210_1715760000000
     *   9876543210 called at 11:15 → key: uid_9876543210_1715760900000  ← different!
     *   9876543210 called at 11:30 → key: uid_9876543210_1715761800000  ← different!
     */
    suspend fun saveCallRecordsBatch(records: List<CallRecord>): Boolean {
        if (records.isEmpty()) return true
        val uid = currentUid() ?: return false
        return try {
            val updates = mutableMapOf<String, Any>()
            records.forEach { record ->
                val docId = callDocId(uid, record.phone, record.calledAt)
                updates[docId] = hashMapOf(
                    "userId"   to uid,
                    "name"     to record.name,
                    "phone"    to record.phone.filter { it.isDigit() },
                    "status"   to record.status,
                    "duration" to record.duration,
                    "calledAt" to record.calledAt,
                    "note"     to record.note      // Custom message
                )
            }
            // Single network call — all records at once
            rtdbCalls.child(uid).updateChildren(updates).await()
            Log.d(TAG, "saveCallRecordsBatch: ${records.size} records pushed in 1 RTDB call")
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveCallRecordsBatch error: ${e.message}")
            false
        }
    }



    suspend fun fetchCallRecordsOnce(): List<CallRecord> {
        val uid = currentUid() ?: return emptyList()
        val list = mutableListOf<CallRecord>()

        // ── Only RTDB — all new call records go here (old Firestore removed to save reads) ──
        try {
            val targetRef = if (isAdmin) rtdbCalls else rtdbCalls.child(uid)
            val rtdbSnap = targetRef.get().await()
            if (isAdmin) {
                rtdbSnap.children.forEach { userNode ->
                    userNode.children.forEach { callNode ->
                        parseRtdbCall(callNode.value as? Map<*, *>)?.let { list.add(it) }
                    }
                }
            } else {
                rtdbSnap.children.forEach { callNode ->
                    parseRtdbCall(callNode.value as? Map<*, *>)?.let { list.add(it) }
                }
            }
            Log.d(TAG, "fetchCallRecordsOnce (RTDB): ${list.size} records")
        } catch (e: Exception) {
            Log.e(TAG, "fetchCallRecordsOnce (RTDB) error: ${e.message}")
        }

        return list
    }

    private fun parseRtdbCall(map: Map<*, *>?): CallRecord? {
        if (map == null) return null
        return runCatching {
            CallRecord(
                id       = 0,
                name     = map["name"]?.toString() ?: "",
                phone    = map["phone"]?.toString() ?: "",
                status   = map["status"]?.toString() ?: "Not Connected",
                duration = (map["duration"] as? Number)?.toLong() ?: 0L,
                calledAt = (map["calledAt"] as? Number)?.toLong() ?: 0L,
                note     = map["note"]?.toString() ?: ""  // Custom employee note
            )
        }.getOrNull()
    }

    fun callRecordsFlow(): Flow<List<CallRecord>> = callbackFlow {
        val uid = currentUid()
        if (uid == null) { close(); return@callbackFlow }

        // Sirf RTDB — har call apne unique callDocId (uid_phone_calledAt) se alag node hai
        val targetRef = if (isAdmin) rtdbCalls else rtdbCalls.child(uid)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CallRecord>()
                if (isAdmin) {
                    snapshot.children.forEach { userNode ->
                        userNode.children.forEach { callNode ->
                            parseRtdbCall(callNode.value as? Map<*, *>)?.let { list.add(it) }
                        }
                    }
                } else {
                    snapshot.children.forEach { callNode ->
                        parseRtdbCall(callNode.value as? Map<*, *>)?.let { list.add(it) }
                    }
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "callRecordsFlow (RTDB) error: ${error.message}")
            }
        }

        targetRef.addValueEventListener(listener)
        awaitClose { targetRef.removeEventListener(listener) }
    }


    suspend fun saveAttendance(record: AttendanceRecord): Boolean {
        val uid = currentUid() ?: return false
        val docId = attendDocId(uid, record.date)
        return try {
            val data = hashMapOf(
                "userId"       to uid,
                "date"         to record.date,
                "punchInTime"  to record.punchInTime,
                "punchInMs"    to record.punchInMs,
                "isLate"       to record.isLate,
                "lateReason"   to record.lateReason,
                "totalCalls"   to record.totalCalls,
                "employeeName" to record.employeeName
            )
            attendanceCol.document(docId).set(data, SetOptions.merge()).await()
            Log.d(TAG, "saveAttendance OK: $docId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveAttendance error: ${e.message}")
            false
        }
    }

    private val rtdbExpectedSales = FirebaseDatabase.getInstance().getReference("expectedSales")

    suspend fun saveExpectedSales(date: String, expectedSales: Int): Boolean {
        val uid = currentUid() ?: return false
        return try {
            // Path: expectedSales/{userId}/{date} = expectedSales
            rtdbExpectedSales.child(uid).child(date.replace("/", "-")).setValue(expectedSales).await()
            Log.d(TAG, "saveExpectedSales OK: $expectedSales")
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveExpectedSales error: ${e.message}")
            false
        }
    }

    fun expectedSalesFlow(userId: String, date: String): Flow<Int> = callbackFlow {
        val ref = rtdbExpectedSales.child(userId).child(date.replace("/", "-"))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = (snapshot.value as? Number)?.toInt() ?: 0
                trySend(value)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "expectedSalesFlow error: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }


    suspend fun fetchAttendanceOnce(): List<AttendanceRecord> {
        val uid = currentUid() ?: return emptyList()
        return try {
            val query = if (isAdmin) attendanceCol
            else attendanceCol.whereEqualTo("userId", uid)
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { doc ->
                runCatching {
                    AttendanceRecord(
                        id           = 0,
                        date         = doc.getString("date")         ?: "",
                        punchInTime  = doc.getString("punchInTime")  ?: "",
                        punchInMs    = doc.getLong("punchInMs")      ?: 0L,
                        isLate       = doc.getBoolean("isLate")      ?: false,
                        lateReason   = doc.getString("lateReason")   ?: "",
                        totalCalls   = (doc.getLong("totalCalls")    ?: 0L).toInt(),
                        employeeName = doc.getString("employeeName") ?: ""
                    )
                }.getOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAttendanceOnce error: ${e.message}")
            emptyList()
        }
    }

    fun attendanceFlow(): Flow<List<AttendanceRecord>> = callbackFlow {
        val uid = currentUid()
        if (uid == null) { close(); return@callbackFlow }

        val query: Query = if (isAdmin) attendanceCol
        else attendanceCol.whereEqualTo("userId", uid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "attendanceFlow error: ${error.message}")
                return@addSnapshotListener
            }
            val records = snapshot?.documents?.mapNotNull { doc ->
                runCatching {
                    AttendanceRecord(
                        id           = 0,
                        date         = doc.getString("date")         ?: "",
                        punchInTime  = doc.getString("punchInTime")  ?: "",
                        punchInMs    = doc.getLong("punchInMs")      ?: 0L,
                        isLate       = doc.getBoolean("isLate")      ?: false,
                        lateReason   = doc.getString("lateReason")   ?: "",
                        totalCalls   = (doc.getLong("totalCalls")    ?: 0L).toInt(),
                        employeeName = doc.getString("employeeName") ?: ""
                    )
                }.getOrNull()
            } ?: emptyList()

            trySend(records)
        }
        awaitClose { registration.remove() }
    }


    suspend fun saveUserProfile(profile: UserProfile): Boolean {
        return try {
            val data = hashMapOf(
                "uid"              to profile.uid,
                "name"             to profile.name,
                "email"            to profile.email,
                "phone"            to profile.phone,
                "employeeId"       to profile.employeeId,
                "designation"      to profile.designation,
                "department"       to profile.department,
                "reportingManager" to profile.reportingManager,
                "workLocation"     to profile.workLocation,
                "dateOfJoining"    to profile.dateOfJoining,
                "updatedAt"        to FieldValue.serverTimestamp()
            )
            usersCol.document(profile.uid).set(data, SetOptions.merge()).await()
            Log.d(TAG, "saveUserProfile OK: ${profile.uid}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveUserProfile error: ${e.message}")
            false
        }
    }

    // ─── Leave Requests ────────────────────────────────────────────────────────

    /** Save a new leave request to Firestore. Returns the generated document ID or null on failure. */
    suspend fun saveLeaveRequest(request: LeaveRequest): String? {
        val uid = currentUid()
        if (uid == null) {
            Log.e(TAG, "saveLeaveRequest: user not authenticated")
            return null
        }
        return try {
            val data = hashMapOf(
                "uid"           to uid,
                "employeeName"  to request.employeeName,
                "employeeEmail" to request.employeeEmail,
                "leaveType"     to request.leaveType,
                "fromDate"      to request.fromDate,
                "toDate"        to request.toDate,
                "reason"        to request.reason,
                "status"        to "Pending",
                "appliedAt"     to FieldValue.serverTimestamp()
            )
            val docRef = leavesCol.add(data).await()
            Log.d(TAG, "saveLeaveRequest OK: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            Log.e(TAG, "saveLeaveRequest FAILED: ${e.javaClass.simpleName} — ${e.message}")
            null
        }
    }

    /**
     * Admin-only: Update the status of a leave request.
     * @param leaveId Firestore document ID
     * @param newStatus "Approved" or "Rejected"
     */
    suspend fun updateLeaveStatus(leaveId: String, newStatus: String): Boolean {
        return try {
            leavesCol.document(leaveId)
                .update(
                    "status",     newStatus,
                    "reviewedAt", FieldValue.serverTimestamp()
                ).await()
            Log.d(TAG, "updateLeaveStatus OK: $leaveId → $newStatus")
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateLeaveStatus error: ${e.message}")
            false
        }
    }

    /**
     * Fetch holidays list from Firestore (collection "holidays").
     * Document ID is the date key "yyyy-MM-dd", and the document has a field "name" (String).
     */
    suspend fun fetchHolidaysOnce(): Map<String, String> {
        val fallback = mapOf(
            "2026-01-26" to "Republic Day",
            "2026-08-15" to "Independence Day",
            "2026-10-02" to "Gandhi Jayanti",
            "2026-12-25" to "Christmas"
        )
        return try {
            val snap = db.collection("holidays").get().await()
            val map = mutableMapOf<String, String>()
            map.putAll(fallback)
            for (doc in snap.documents) {
                val name = doc.getString("name") ?: "Holiday"
                map[doc.id] = name
            }
            map
        } catch (e: Exception) {
            Log.e(TAG, "fetchHolidaysOnce error: ${e.message}")
            fallback
        }
    }


    /** Real-time flow of leave requests for the current employee. */
    fun leaveRequestsFlow(): Flow<List<LeaveRequest>> = callbackFlow {
        val uid = currentUid()
        if (uid == null) { close(); return@callbackFlow }

        // ⚠️ Using whereEqualTo + orderBy requires a Firestore composite index.
        // To avoid that setup burden, we filter by uid only and sort client-side.
        val query = if (isAdmin) leavesCol
        else leavesCol.whereEqualTo("uid", uid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "leaveRequestsFlow error: ${error.message}")
                return@addSnapshotListener
            }
            val requests = snapshot?.documents?.mapNotNull { doc ->
                runCatching {
                    LeaveRequest(
                        id            = doc.id,
                        uid           = doc.getString("uid")           ?: "",
                        employeeName  = doc.getString("employeeName")  ?: "",
                        employeeEmail = doc.getString("employeeEmail") ?: "",
                        leaveType     = doc.getString("leaveType")     ?: "",
                        fromDate      = doc.getString("fromDate")      ?: "",
                        toDate        = doc.getString("toDate")        ?: "",
                        reason        = doc.getString("reason")        ?: "",
                        status        = doc.getString("status")        ?: "Pending",
                        appliedAt     = doc.getTimestamp("appliedAt")?.toDate()?.time ?: 0L
                    )
                }.getOrNull()
            } ?: emptyList()

            // Sort newest-first on the client side (no composite index needed)
            trySend(requests.sortedByDescending { it.appliedAt })
        }
        awaitClose { registration.remove() }
    }

    fun userProfileFlow(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = usersCol.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "userProfileFlow error: ${error.message}")
                trySend(null)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = UserProfile(
                    uid              = snapshot.id,
                    name             = snapshot.getString("name")             ?: "",
                    email            = snapshot.getString("email")            ?: "",
                    phone            = snapshot.getString("phone")            ?: "",
                    employeeId       = snapshot.getString("employeeId")       ?: "",
                    designation      = snapshot.getString("designation")      ?: "",
                    department       = snapshot.getString("department")       ?: "",
                    reportingManager = snapshot.getString("reportingManager") ?: "",
                    workLocation     = snapshot.getString("workLocation")     ?: "",
                    dateOfJoining    = snapshot.getString("dateOfJoining")    ?: ""
                )
                trySend(profile)
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }
}