package com.adyapan.leaddialer

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object CrmApi {
    private val baseUrl = BuildConfig.CRM_API_BASE_URL.trimEnd('/')

    data class CrmUser(
        val id: String,
        val name: String,
        val email: String,
        val role: String
    )

    suspend fun getCurrentUser(): CrmUser {
        val payload = getJson("/mobile/me")
        val user = payload.optJSONObject("user") ?: JSONObject()
        return CrmUser(
            id = user.optString("id"),
            name = user.optString("name"),
            email = user.optString("email"),
            role = user.optString("role").uppercase()
        )
    }

    suspend fun getHrEmployees(): List<EmployeeSummary> {
        val payload = getJson("/mobile/hr/employees")
        val rows = payload.optJSONArray("employees") ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                if (!row.optBoolean("isActive", true)) continue
                val id = row.optString("id")
                val name = row.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    EmployeeSummary(
                        employeeName = name,
                        userId = id,
                        totalLeads = 0,
                        connected = 0,
                        interested = 0,
                        pending = 0,
                        salesDone = 0,
                        expectedSales = 0,
                        adminTarget = 0,
                        tlName = row.optString("reportingManager").ifBlank { row.optString("teamName") },
                        leads = emptyList()
                    )
                )
            }
        }
    }

    suspend fun getAdminEmployees(): List<EmployeeSummary> {
        val payload = getJson("/mobile/admin/employees")
        val rows = payload.optJSONArray("employees") ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                if (!row.optBoolean("isActive", true)) continue
                val crmId = row.optString("id")
                val firebaseUid = row.optString("firebaseUid")
                val id = firebaseUid.ifBlank { crmId }
                val name = row.optString("name")
                if (id.isBlank() || name.isBlank()) continue
                add(
                    EmployeeSummary(
                        employeeName = name,
                        userId = id,
                        totalLeads = row.optInt("leadsTotal", 0),
                        connected = row.optInt("leadsConnected", 0),
                        interested = row.optInt("leadsInterested", 0),
                        pending = row.optInt("leadsPending", 0),
                        salesDone = row.optInt("leadsConverted", row.optInt("salesCount", 0)),
                        expectedSales = 0,
                        adminTarget = 0,
                        tlName = row.optString("teamLeaderName").ifBlank { row.optString("teamName") },
                        leads = emptyList()
                    )
                )
            }
        }
    }

    suspend fun getAdminLeads(userId: String? = null, limit: Int = 1000): List<Lead> {
        val query = buildString {
            append("/mobile/admin/leads?limit=$limit")
            if (!userId.isNullOrBlank()) {
                append("&userId=")
                append(URLEncoder.encode(userId, "UTF-8"))
            }
        }
        val payload = getJson(query)
        val rows = payload.optJSONArray("leads") ?: return emptyList()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id")
                val status = row.optString("status")
                val mappedStatus = mapLeadStatus(status)
                add(
                    Lead(
                        id = 0,
                        name = row.optString("name").ifBlank { "Unknown" },
                        phone = row.optString("mobile"),
                        status = mappedStatus,
                        notes = row.optString("source"),
                        calledAt = parseIsoMillis(row.optString("updatedAt").ifBlank { row.optString("createdAt") }),
                        duration = 0,
                        firestoreId = id,
                        collegeName = "",
                        collegeCity = "",
                        isHotLead = status.equals("INTERESTED", ignoreCase = true),
                        calledBy = row.optJSONObject("assignedTo")?.optString("name") ?: "",
                        salesDone = status.equals("CONVERTED", ignoreCase = true)
                    )
                )
            }
        }
    }

    suspend fun getHrAttendance(date: String): Map<String, String> {
        val payload = getJson("/mobile/hr/attendance?date=$date")
        val rows = payload.optJSONArray("attendance") ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val userId = row.optString("firebaseUid").ifBlank { row.optString("userId") }
            if (userId.isBlank()) continue
            result[userId] = mapAttendanceStatus(row.optString("status"))
        }
        return result
    }

    private fun mapAttendanceStatus(status: String): String {
        return when (status.uppercase()) {
            "PRESENT", "WORK_FROM_HOME" -> "Present"
            "LATE" -> "Late"
            "HALF_DAY" -> "Half Day"
            "LEAVE" -> "Leave"
            else -> "Absent"
        }
    }

    private fun mapLeadStatus(status: String): String {
        return when (status.uppercase()) {
            "NEW" -> "Pending"
            "CONTACTED", "DEMO_SCHEDULED" -> "Connected"
            "INTERESTED" -> "Interested"
            "CONVERTED" -> "Connected"
            "LOST" -> "Not Interested"
            else -> status.ifBlank { "Pending" }
        }
    }

    fun parseIsoMillis(value: String): Long {
        if (value.isBlank()) return 0L
        return runCatching {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            formatter.parse(value)?.time ?: 0L
        }.getOrElse {
            runCatching {
                val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                formatter.parse(value)?.time ?: 0L
            }.getOrDefault(0L)
        }
    }

    /**
     * Sync the new password to the CRM (Supabase) after it has been changed in Firebase.
     * Ensures the user can log in to both the mobile app and CRM website with the same password.
     */
    suspend fun syncPasswordToCrm(newPassword: String): Boolean {
        return try {
            val body = JSONObject().put("newPassword", newPassword)
            val result = postJson("/mobile/sync-password", body)
            result.optBoolean("success", false)
        } catch (e: Exception) {
            android.util.Log.e("CrmApi", "Password sync to CRM failed: ${e.message}")
            false
        }
    }

    private suspend fun postJson(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val token = FirebaseAuth.getInstance().currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: throw IllegalStateException("Firebase session missing")

        val url = URL("$baseUrl${if (path.startsWith("/")) path else "/$path"}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }

        try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val payload = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) {
                throw IllegalStateException(payload.optString("message").ifBlank { "CRM request failed: HTTP $code" })
            }
            payload
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val token = FirebaseAuth.getInstance().currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?: throw IllegalStateException("Firebase session missing")

        val url = URL("$baseUrl${if (path.startsWith("/")) path else "/$path"}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val payload = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (code !in 200..299) {
                throw IllegalStateException(payload.optString("message").ifBlank { "CRM request failed: HTTP $code" })
            }
            payload
        } finally {
            connection.disconnect()
        }
    }
}
