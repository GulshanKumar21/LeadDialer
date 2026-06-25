package com.adyapan.leaddialer

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object SupabaseSync {
    private const val TAG = "SupabaseSync"
    private const val SUPABASE_URL = "https://jcmzfwweoinfpjfumlda.supabase.co/rest/v1/User"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpjbXpmd3dlb2luZnBqZnVtbGRhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExNjMyNzUsImV4cCI6MjA5NjczOTI3NX0.-7HR7LeHBLo1rFNqpVjadHy2dzk-VVsevCInPKMC4vE"

    private suspend fun getAuthToken(): String {
        return try {
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val tokenResult = user.getIdToken(false).await()
                tokenResult?.token ?: ANON_KEY
            } else {
                ANON_KEY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not get Firebase ID token, fallback to ANON_KEY: ${e.message}")
            ANON_KEY
        }
    }

    suspend fun syncSupabaseUsersToFirestore(db: FirebaseFirestore) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting sync from Supabase User table...")
                val token = getAuthToken()

                // Fetch Firestore users to map email -> uid
                val firestoreEmailToUidMap = mutableMapOf<String, String>()
                try {
                    val usersSnapshot = db.collection("users").get().await()
                    for (doc in usersSnapshot.documents) {
                        val email = doc.getString("email")?.lowercase()?.trim() ?: ""
                        if (email.isNotEmpty()) {
                            firestoreEmailToUidMap[email] = doc.id
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching Firestore users for mapping: ${e.message}")
                }

                val rawJson = fetchUsersFromSupabase(token)
                if (rawJson.isNullOrBlank()) {
                    Log.w(TAG, "No data received or error during fetch from Supabase.")
                    return@withContext
                }

                val jsonArray = JSONArray(rawJson)
                Log.d(TAG, "Fetched ${jsonArray.length()} records from Supabase. Processing...")

                for (i in 0 until jsonArray.length()) {
                    try {
                        val userObj = jsonArray.getJSONObject(i)
                        val email = userObj.optString("email", "").trim()
                        if (email.isEmpty()) continue

                        var firebaseUid = userObj.optString("firebaseUid", "").trim()
                        if (firebaseUid.isEmpty() || firebaseUid == "null") {
                            firebaseUid = firestoreEmailToUidMap[email.lowercase().trim()] ?: ""
                        }

                        if (firebaseUid.isEmpty()) {
                            Log.d(TAG, "Skipping user record at index $i because firebaseUid is empty and could not be mapped by email.")
                            continue
                        }

                        val name = userObj.optString("name", "").trim()
                        val mobile = userObj.optString("mobile", "").trim()
                        val employeeId = userObj.optString("employeeId", "").trim()
                        val designation = userObj.optString("designation", "").trim()
                        val specialization = userObj.optString("specialization", "").trim()
                        val joiningDate = userObj.optString("joiningDate", "").trim()
                        val isActive = userObj.optBoolean("isActive", true)
                        val rawRole = userObj.optString("role", "EMPLOYEE").trim().uppercase()

                        // Map role to standard lower-case strings
                        val mappedRole = when (rawRole) {
                            "ADMIN", "SUPER_ADMIN" -> "admin"
                            "HR" -> "hr"
                            else -> "employee"
                        }

                        // Map joining date to dd/MM/yyyy
                        val dateOfJoining = formatJoiningDate(joiningDate)

                        // 1. Update/Write to users/{firebaseUid}
                        val userData = hashMapOf<String, Any>(
                            "uid" to firebaseUid,
                            "name" to name,
                            "displayName" to name,
                            "email" to email,
                            "phone" to mobile,
                            "mobile" to mobile,
                            "employeeId" to employeeId,
                            "designation" to designation,
                            "department" to specialization,
                            "dateOfJoining" to dateOfJoining,
                            "isActive" to isActive,
                            "role" to mappedRole
                        )

                        db.collection("users").document(firebaseUid)
                            .set(userData, SetOptions.merge())
                            .await()

                        // 2. Manage admins and hrs collections
                        when (mappedRole) {
                            "admin" -> {
                                val adminData = hashMapOf("uid" to firebaseUid)
                                db.collection("admins").document(firebaseUid)
                                    .set(adminData, SetOptions.merge())
                                    .await()
                                db.collection("hrs").document(firebaseUid).delete().await()
                            }
                            "hr" -> {
                                val hrData = hashMapOf("uid" to firebaseUid)
                                db.collection("hrs").document(firebaseUid)
                                    .set(hrData, SetOptions.merge())
                                    .await()
                                db.collection("admins").document(firebaseUid).delete().await()
                            }
                            else -> {
                                db.collection("admins").document(firebaseUid).delete().await()
                                db.collection("hrs").document(firebaseUid).delete().await()
                            }
                        }

                        Log.d(TAG, "Successfully synced user: $email (UID: $firebaseUid, Role: $mappedRole)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing or syncing user record at index $i: ${e.message}")
                    }
                }
                Log.d(TAG, "Supabase to Firestore sync completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "General error during Supabase to Firestore sync: ${e.message}", e)
            }
        }
    }

    private fun fetchUsersFromSupabase(token: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(SUPABASE_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                response.toString()
            } else {
                Log.e(TAG, "Supabase HTTP error response code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Supabase: ${e.message}", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun formatJoiningDate(rawDate: String?): String {
        if (rawDate.isNullOrBlank() || rawDate == "null") return ""
        return try {
            // ISO format e.g., "2026-06-15T00:00:00.000Z" -> "2026-06-15"
            val datePart = rawDate.substringBefore("T")
            val parts = datePart.split("-")
            if (parts.size == 3) {
                val year = parts[0]
                val month = parts[1]
                val day = parts[2]
                "$day/$month/$year"
            } else {
                rawDate
            }
        } catch (e: Exception) {
            rawDate
        }
    }

    private fun mapSupabaseDocType(dbDocType: String, dbName: String): String {
        val cleanType = dbDocType.uppercase().trim()
        val cleanName = dbName.lowercase().trim()

        val appKeys = listOf("class10", "class12", "lastsemester", "passportphoto", "aadhar", "pan", "drivinglicense", "passport", "resume", "offerletter")
        if (appKeys.contains(dbDocType.lowercase().trim())) {
            return when (dbDocType.lowercase().trim()) {
                "lastsemester" -> "lastSemester"
                "passportphoto" -> "passportPhoto"
                "drivinglicense" -> "drivingLicense"
                "offerletter" -> "offerLetter"
                else -> dbDocType.trim()
            }
        }

        return when (cleanType) {
            "OFFER_LETTER" -> "offerLetter"
            "RESUME" -> "resume"
            "EDUCATION" -> {
                when {
                    cleanName.contains("10th") || cleanName.contains("class 10") || cleanName.contains("class10") -> "class10"
                    cleanName.contains("12th") || cleanName.contains("class 12") || cleanName.contains("class12") -> "class12"
                    cleanName.contains("marksheet") || cleanName.contains("semester") || cleanName.contains("mark sheet") -> "lastSemester"
                    else -> "class10"
                }
            }
            "IDENTITY" -> {
                when {
                    cleanName.contains("photo") || cleanName.contains("passport photo") || cleanName.contains("pic") -> "passportPhoto"
                    cleanName.contains("aadhar") || cleanName.contains("adhaar") -> "aadhar"
                    cleanName.contains("pan") -> "pan"
                    cleanName.contains("license") || cleanName.contains("driving") -> "drivingLicense"
                    cleanName.contains("passport") -> "passport"
                    else -> "aadhar"
                }
            }
            else -> dbDocType
        }
    }

    suspend fun syncSupabaseDocumentsToFirestore(db: FirebaseFirestore) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting sync of documents from Supabase...")
                val token = getAuthToken()

                // 1. Fetch Supabase users to build supabaseIdToEmailMap
                val usersJson = fetchUsersFromSupabase(token)
                val supabaseIdToEmailMap = mutableMapOf<String, String>()
                if (!usersJson.isNullOrBlank()) {
                    val usersArray = JSONArray(usersJson)
                    for (i in 0 until usersArray.length()) {
                        val u = usersArray.getJSONObject(i)
                        val id = u.optString("id", "").trim()
                        val firebaseUid = u.optString("firebaseUid", "").trim()
                        val email = u.optString("email", "").trim().lowercase()
                        if (email.isNotEmpty()) {
                            if (id.isNotEmpty()) {
                                supabaseIdToEmailMap[id] = email
                            }
                            if (firebaseUid.isNotEmpty() && firebaseUid != "null") {
                                supabaseIdToEmailMap[firebaseUid] = email
                            }
                        }
                    }
                }

                // 2. Fetch Firestore users to map email -> uid
                val firestoreEmailToUidMap = mutableMapOf<String, String>()
                try {
                    val usersSnapshot = db.collection("users").get().await()
                    for (doc in usersSnapshot.documents) {
                        val email = doc.getString("email")?.lowercase()?.trim() ?: ""
                        if (email.isNotEmpty()) {
                            firestoreEmailToUidMap[email] = doc.id
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching Firestore users for documents: ${e.message}")
                }

                // 3. Fetch documents from Supabase
                val rawJson = fetchDocumentsFromSupabase(token)
                if (rawJson.isNullOrBlank()) {
                    Log.w(TAG, "No documents received from Supabase.")
                    return@withContext
                }

                val jsonArray = JSONArray(rawJson)
                Log.d(TAG, "Fetched ${jsonArray.length()} document records from Supabase.")

                for (i in 0 until jsonArray.length()) {
                    try {
                        val docObj = jsonArray.getJSONObject(i)
                        val supabaseUserId = docObj.optString("userId", "").trim()
                        val documentType = docObj.optString("documentType", "").trim()
                        val fileUrl = docObj.optString("fileUrl", "").trim()
                        val name = docObj.optString("name", "").trim()

                        if (supabaseUserId.isEmpty() || documentType.isEmpty() || fileUrl.isEmpty()) {
                            continue
                        }

                        // Map Supabase User ID to Firebase UID
                        val email = supabaseIdToEmailMap[supabaseUserId]
                        if (email == null) {
                            Log.w(TAG, "Could not map Supabase userId $supabaseUserId to email")
                            continue
                        }
                        val firebaseUid = firestoreEmailToUidMap[email]
                        if (firebaseUid == null) {
                            Log.w(TAG, "Could not map email $email to Firebase UID")
                            continue
                        }

                        val mappedDocType = mapSupabaseDocType(documentType, name)
                        val userRef = db.collection("users").document(firebaseUid)
                        db.runTransaction { transaction ->
                            val snapshot = transaction.get(userRef)
                            val docs = mutableMapOf<String, String>()
                            if (snapshot.exists()) {
                                val rawDocs = snapshot.get("documents") as? Map<*, *>
                                rawDocs?.forEach { (k, v) ->
                                    if (k is String && v is String) {
                                        docs[k] = v
                                    }
                                }
                            }
                            docs[mappedDocType] = fileUrl
                            transaction.update(userRef, "documents", docs)
                        }.await()

                        Log.d(TAG, "Synced document: $mappedDocType for user: $firebaseUid (email: $email)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing document record at index $i: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "General error during Supabase document sync: ${e.message}")
            }
        }
    }

    private fun fetchDocumentsFromSupabase(token: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL("https://jcmzfwweoinfpjfumlda.supabase.co/rest/v1/EmployeeDocument")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                response.toString()
            } else {
                Log.e(TAG, "Supabase Documents HTTP error response code: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to Supabase for documents: ${e.message}", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun uploadDocumentToSupabase(userId: String, documentType: String, fileUrl: String, fileName: String) {
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                var token = ANON_KEY
                try {
                    val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val tokenResult = user.getIdToken(false).await()
                        if (tokenResult?.token != null) {
                            token = tokenResult.token!!
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not get Firebase ID token, fallback to ANON_KEY: ${e.message}")
                }

                val url = URL("https://jcmzfwweoinfpjfumlda.supabase.co/rest/v1/EmployeeDocument")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true
                connection.setRequestProperty("apikey", ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject().apply {
                    put("userId", userId)
                    put("documentType", documentType)
                    put("fileUrl", fileUrl)
                    put("name", fileName)
                }

                val os = connection.outputStream
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
                os.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == HttpURLConnection.HTTP_OK || responseCode == 204) {
                    Log.d(TAG, "Document successfully uploaded to Supabase: $documentType")
                } else {
                    val errStream = connection.errorStream
                    val response = errStream?.bufferedReader()?.use { it.readText() } ?: ""
                    Log.e(TAG, "Failed to upload document to Supabase (Code $responseCode): $response")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading document to Supabase: ${e.message}", e)
            } finally {
                connection?.disconnect()
            }
        }
    }
}
