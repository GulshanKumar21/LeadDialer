package com.adyapan.leaddialer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SheetsSync {

    private const val TAG = "SheetsSync"

    // ── Fallback URL (used only if employee has NO TL assigned) ──────────────
    private const val DEFAULT_SCRIPT_URL =
        "https://script.google.com/macros/s/AKfycbx7q3iVs3h0tVUArSKJ5MF9EaogNPeuGCf6St4jBqDmO1pTC9O6QNhMsJscFH2lXHqRhg/exec"

    // SharedPrefs key to cache TL sheet URL locally (avoids RTDB lookup every sync)
    private const val KEY_TL_SHEET_URL = "tl_sheet_url"

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    /**
     * Returns the correct Google Sheet Script URL for the current employee.
     *
     * Priority:
     *  1. RTDB: teamLeaders/{tlId}/sheetUrl  (always fresh — reflects latest TL assignment)
     *  2. Cached value in SharedPrefs        (fallback if offline)
     *  3. DEFAULT_SCRIPT_URL                 (if employee has no TL assigned)
     *
     * Also caches the fetched URL locally so repeated syncs don't hit RTDB every time.
     * When admin reassigns TL → next sync fetches new URL from RTDB automatically.
     */
    private suspend fun resolveScriptUrl(context: Context, userId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val tlId = TeamLeaderManager.getTlIdForUser(userId)
                if (tlId != null) {
                    val url = TeamLeaderManager.getSheetUrlForTl(tlId)
                    if (!url.isNullOrBlank()) {
                        // Cache locally for offline fallback
                        LoginPage.getEncryptedPrefs(context)
                            .edit()
                            .putString(KEY_TL_SHEET_URL, url)
                            .apply()
                        Log.d(TAG, "resolveScriptUrl: TL=$tlId → URL=$url")
                        return@withContext url
                    }
                }
                // No TL or no URL set → try cached value
                val cached = LoginPage.getEncryptedPrefs(context).getString(KEY_TL_SHEET_URL, null)
                if (!cached.isNullOrBlank()) {
                    Log.d(TAG, "resolveScriptUrl: using cached URL")
                    return@withContext cached
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveScriptUrl error: ${e.message}")
            }
            // Ultimate fallback
            Log.d(TAG, "resolveScriptUrl: using DEFAULT URL")
            DEFAULT_SCRIPT_URL
        }
    }

    /** Call this when admin changes TL assignment — clears cached URL so next sync picks new one */
    fun clearCachedSheetUrl(context: Context) {
        LoginPage.getEncryptedPrefs(context)
            .edit()
            .remove(KEY_TL_SHEET_URL)
            .apply()
        Log.d(TAG, "clearCachedSheetUrl: cache cleared")
    }

    private fun getEmployeeName(context: Context): String = getEmployeeNameStatic(context)

    /** Public — used by ViewModel to set calledBy when saving status. */
    fun getEmployeeNameStatic(
        context: Context
    ): String {

        val prefs =
            LoginPage.getEncryptedPrefs(context)

        val displayName =
            prefs.getString(
                LoginPage.KEY_EMPLOYEE_NAME,
                ""
            ) ?: ""

        if (displayName.isNotBlank()) {

            return displayName
        }

        val email =
            prefs.getString(
                LoginPage.KEY_EMAIL,
                "Unknown"
            ) ?: "Unknown"

        return email.substringBefore("@")
    }

    suspend fun checkIfAlreadyCalled(phone: String): CallCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                val cleanPhone = phone.filter { it.isDigit() }
                val url = URL("$DEFAULT_SCRIPT_URL?phone=$cleanPhone")
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(response)
                CallCheckResult(
                    called = json.optBoolean("called", false),
                    calledBy = json.optString("calledBy", ""),
                    status = json.optString("status", "")
                )
            } catch (e: Exception) {
                Log.e(TAG, "checkIfAlreadyCalled error: ${e.message}")
                CallCheckResult(false, "", "")
            }
        }
    }

    suspend fun fetchAllCalledNumbers(
        context: Context,
        phones: List<String>
    ): Map<String, CallCheckResult> {
        return withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, CallCheckResult>()
            try {
                val allPhones = phones
                    .map { it.filter { c -> c.isDigit() } }
                    .joinToString(",")

                val url = URL("$DEFAULT_SCRIPT_URL?phones=$allPhones")
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(response)
                val data = json.optJSONObject("data")

                if (data != null) {
                    phones.forEach { phone ->
                        val clean = phone.filter { it.isDigit() }
                        if (data.has(clean)) {
                            val obj = data.getJSONObject(clean)
                            results[clean] = CallCheckResult(
                                called = obj.optBoolean("called", false),
                                calledBy = obj.optString("calledBy", ""),
                                status = obj.optString("status", "")
                            )
                        } else {
                            results[clean] = CallCheckResult(false, "", "")
                        }
                    }
                }
                Log.d(TAG, "fetchAllCalledNumbers: ${results.size} phones cached")
            } catch (e: Exception) {
                Log.e(TAG, "fetchAllCalledNumbers error: ${e.message}")
            }
            results
        }
    }

    suspend fun syncAllLeads(context: Context, leads: List<Lead>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val employeeName = getEmployeeName(context)
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val scriptUrl = resolveScriptUrl(context, uid)

                val array = JSONArray()
                leads.forEach { lead ->
                    JSONObject().apply {
                        put("name", lead.name)
                        put("phone", lead.phone)
                        put("status", lead.status ?: "Pending")
                        put("notes", lead.notes ?: "")
                        put("duration", lead.duration)
                        put("calledBy", lead.calledBy.ifBlank { employeeName })
                        put("collegeName", lead.collegeName)
                        put("collegeCity", lead.collegeCity)
                        put(
                            "calledAt",
                            if (lead.calledAt > 0)
                                dateFormat.format(Date(lead.calledAt))
                            else "Not called"
                        )
                    }.also { array.put(it) }
                }

                val body = JSONObject()
                    .put("type", "leads")
                    .put("employeeName", employeeName)
                    .put("leads", array)
                    .put("syncedAt", dateFormat.format(Date()))
                    .toString()

                postToScript(body, scriptUrl)

            } catch (e: Exception) {
                Log.e(TAG, "syncAllLeads error: ${e.message}")
                false
            }
        }
    }

    suspend fun syncCallRecords(context: Context, records: List<CallRecord>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val employeeName = getEmployeeName(context)
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val scriptUrl = resolveScriptUrl(context, uid)

                // ── Calculate gap for each record ──────────────────────────────
                // Sort ascending so we can compute gap per call
                val sorted = records.sortedBy { it.calledAt }
                val gapMap  = mutableMapOf<Long, Long>() // calledAt → gapSeconds

                // Work starts at 11:15 AM — first call's gap measured from there
                val WORK_START_HOUR = 11
                val WORK_START_MIN  = 15

                sorted.forEachIndexed { i, curr ->
                    if (i == 0) {
                        // First call: gap from 11:15 AM to call start
                        if (curr.calledAt > 0) {
                            val cal = java.util.Calendar.getInstance().apply {
                                timeInMillis = curr.calledAt
                                set(java.util.Calendar.HOUR_OF_DAY, WORK_START_HOUR)
                                set(java.util.Calendar.MINUTE, WORK_START_MIN)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }
                            val workStartMs = cal.timeInMillis
                            val gap = (curr.calledAt - workStartMs) / 1000L
                            if (gap > 0) gapMap[curr.calledAt] = gap
                        }
                    } else {
                        // Subsequent calls: gap from end of previous call
                        val prev    = sorted[i - 1]
                        val prevEnd = prev.calledAt + prev.duration * 1000L
                        val gap     = (curr.calledAt - prevEnd) / 1000L
                        if (gap > 0) gapMap[curr.calledAt] = gap
                    }
                }

                val array = JSONArray()
                records.forEach { record ->
                    JSONObject().apply {
                        put("name",      record.name)
                        put("phone",     record.phone)
                        put("status",    record.status ?: "—")
                        put("duration",  record.duration)
                        put("calledAt",
                            if (record.calledAt > 0)
                                dateFormat.format(Date(record.calledAt))
                            else "Not called"
                        )
                        // Raw milliseconds — GAS uses this as the exact dedup key
                        put("calledAtMs", record.calledAt)
                        // Gap in seconds (from 11:15 AM for first call, from prev call end for others)
                        put("gapSeconds", gapMap[record.calledAt] ?: 0L)
                    }.also { array.put(it) }
                }

                val body = JSONObject()
                    .put("type", "callRecords")
                    .put("employeeName", employeeName)
                    .put("records", array)
                    .put("syncedAt", dateFormat.format(Date()))
                    .toString()

                postToScript(body, scriptUrl)

            } catch (e: Exception) {
                Log.e(TAG, "syncCallRecords error: ${e.message}")
                false
            }
        }
    }



    suspend fun syncAttendance(context: Context, records: List<AttendanceRecord>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val scriptUrl = resolveScriptUrl(context, uid)

                val array = JSONArray()
                records.forEach { rec ->
                    JSONObject().apply {
                        put("date", rec.date)
                        put("punchInTime", rec.punchInTime)
                        put("isLate", rec.isLate)
                        put("lateReason", rec.lateReason)
                        put("totalCalls", rec.totalCalls)
                        put("employeeName", rec.employeeName)
                    }.also { array.put(it) }
                }

                val body = JSONObject()
                    .put("type", "attendance")
                    .put("attendance", array)
                    .put("syncedAt", dateFormat.format(Date()))
                    .toString()

                postToScript(body, scriptUrl)

            } catch (e: Exception) {
                Log.e(TAG, "syncAttendance error: ${e.message}")
                false
            }
        }
    }

    /**
     * Sends a leave request email automatically via the Google Apps Script backend.
     * GAS uses MailApp.sendEmail() — no user action needed, email goes straight to admin inbox.
     *
     * Returns true if GAS confirmed success, false if network/GAS error
     * (caller should fall back to opening the email app).
     */
    suspend fun sendLeaveEmail(
        adminEmail: String,
        empName: String,
        empEmail: String,
        leaveType: String,
        fromDate: String,
        toDate: String,
        reason: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("type", "leaveEmail")
                    put("adminEmail", adminEmail)
                    put("empName", empName)
                    put("empEmail", empEmail)
                    put("leaveType", leaveType)
                    put("fromDate", fromDate)
                    put("toDate", toDate)
                    put("reason", reason)
                }.toString()

                val result = postToScript(body)
                Log.d(TAG, "sendLeaveEmail via GAS: success=$result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "sendLeaveEmail error: ${e.message}")
                false
            }
        }
    }

    /**
     * Sends an approval/rejection notification email to the employee via GAS.
     * Called by admin after updating leave status in Firestore.
     *
     * @param empEmail   Employee's email address
     * @param empName    Employee's display name
     * @param leaveType  e.g. "Casual Leave"
     * @param fromDate   Leave start date
     * @param toDate     Leave end date
     * @param newStatus  "Approved" or "Rejected"
     */
    suspend fun sendLeaveStatusEmail(
        empEmail : String,
        empName  : String,
        leaveType: String,
        fromDate : String,
        toDate   : String,
        newStatus: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("type",      "leaveStatusEmail")
                    put("empEmail",  empEmail)
                    put("empName",   empName)
                    put("leaveType", leaveType)
                    put("fromDate",  fromDate)
                    put("toDate",    toDate)
                    put("status",    newStatus)
                }.toString()

                val result = postToScript(body)
                Log.d(TAG, "sendLeaveStatusEmail via GAS: success=$result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "sendLeaveStatusEmail error: ${e.message}")
                false
            }
        }
    }


    private fun postToScript(body: String, scriptUrl: String = DEFAULT_SCRIPT_URL): Boolean {
        return try {
            val url = URL(scriptUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true   // ✅ 302 redirect follow karega
                setRequestProperty("Content-Type", "application/json")
                outputStream.write(body.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseBody = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            Log.d(TAG, "postToScript code=$responseCode body=$responseBody")

            // ✅ 200 ya 302 dono accept karo
            val ok = responseCode in 200..299 || responseCode in 300..399

            // ✅ GAS response mein "success:true" check karo
            if (ok) {
                try {
                    JSONObject(responseBody).optBoolean("success", false)
                } catch (e: Exception) {
                    ok
                }
            } else false

        } catch (e: Exception) {
            Log.e(TAG, "postToScript error: ${e.message}")
            false
        }
    }

    suspend fun adminSyncEmployeeToTl(
        context: Context,
        employeeUid: String,
        employeeName: String,
        tlId: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get script URL for TL
                val scriptUrl = TeamLeaderManager.getSheetUrlForTl(tlId)
                if (scriptUrl.isNullOrBlank()) return@withContext false
                
                // 2. Fetch all leads for employee
                val leads = mutableListOf<Lead>()
                
                // RTDB leads
                try {
                    val rtdbSnap = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("rtdb_leads").child(employeeUid).get().await()
                    rtdbSnap.children.forEach { leadNode ->
                        val m = leadNode.value as? Map<*, *> ?: return@forEach
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
                            calledBy    = m["calledBy"]?.toString()     ?: employeeName,
                            salesDone   = (m["salesDone"] as? Boolean) ?: false
                        ))
                    }
                } catch (e: Exception) { }

                // Firestore leads
                try {
                    val fsSnap = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("leads").whereEqualTo("userId", employeeUid).get().await()
                    fsSnap.documents.forEach { doc ->
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
                            calledBy    = doc.getString("calledBy")    ?: employeeName,
                            salesDone   = doc.getBoolean("salesDone") ?: false
                        ))
                    }
                } catch (e: Exception) { }

                // Deduplicate
                val deduplicated = leads.groupBy { it.phone }.values.map { group ->
                    group.firstOrNull { it.firestoreId?.contains("_") == false && !it.firestoreId.isNullOrBlank() }
                        ?: group.maxByOrNull { it.calledAt }
                        ?: group.first()
                }
                
                if (deduplicated.isEmpty()) return@withContext true // Nothing to sync

                // 3. Post to script
                val array = JSONArray()
                deduplicated.forEach { lead ->
                    JSONObject().apply {
                        put("name", lead.name)
                        put("phone", lead.phone)
                        put("status", lead.status ?: "Pending")
                        put("notes", lead.notes ?: "")
                        put("duration", lead.duration)
                        put("calledBy", lead.calledBy.ifBlank { employeeName })
                        put("collegeName", lead.collegeName)
                        put("collegeCity", lead.collegeCity)
                        put(
                            "calledAt",
                            if (lead.calledAt > 0)
                                dateFormat.format(Date(lead.calledAt))
                            else "Not called"
                        )
                    }.also { array.put(it) }
                }

                val body = JSONObject()
                    .put("type", "leads")
                    .put("employeeName", employeeName)
                    .put("leads", array)
                    .put("syncedAt", dateFormat.format(Date()))
                    .toString()

                postToScript(body, scriptUrl)
            } catch (e: Exception) {
                Log.e(TAG, "adminSyncEmployeeToTl error: ${e.message}")
                false
            }
        }
    }
}

data class CallCheckResult(
    val called   : Boolean,
    val calledBy : String,
    val status   : String
)