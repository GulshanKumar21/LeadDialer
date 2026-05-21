package com.adyapan.leaddialer

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * TeamLeaderManager — FREE tier (100% RTDB, zero Firestore reads)
 *
 * Firebase RTDB structure:
 *
 *  teamLeaders/
 *    {tlId}/
 *      name        : "TL Placeholder 1"
 *      sheetUrl    : ""          ← aap baad mein URL daalenge
 *      createdAt   : 1716000000000
 *
 *  users/{userId}/
 *    teamLeaderId  : "{tlId}"   ← employee ka TL assignment
 *    (baaki fields already exist)
 */
object TeamLeaderManager {

    private const val TAG = "TeamLeaderManager"

    private val rtdb        = FirebaseDatabase.getInstance()
    private val tlRef       = rtdb.getReference("teamLeaders")
    private val usersRtdb   = rtdb.getReference("users")

    // ─────────────────────────────────────────────────────────────────────────
    //  Data model
    // ─────────────────────────────────────────────────────────────────────────
    data class TeamLeader(
        val id        : String = "",
        val name      : String = "",
        val sheetUrl  : String = "",
        val userId    : String = "",   // TL's own Firebase Auth UID (so their calls route to their sheet)
        val createdAt : Long   = 0L
    )

    // ─────────────────────────────────────────────────────────────────────────
    //  CREATE  — new TL
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Add a new TL.
     * [tlUserId] = TL's own Firebase Auth UID (optional).
     * If provided, their calls will auto-route to this TL's sheet.
     */
    suspend fun addTeamLeader(
        name      : String,
        sheetUrl  : String = "",
        tlUserId  : String = ""
    ): String? {
        return try {
            val newRef = tlRef.push()
            val id     = newRef.key ?: return null
            val data   = mapOf(
                "name"      to name.trim(),
                "sheetUrl"  to sheetUrl.trim(),
                "userId"    to tlUserId.trim(),
                "createdAt" to System.currentTimeMillis()
            )
            newRef.setValue(data).await()

            // Auto-assign TL to their own sheet (so their calls route correctly)
            if (tlUserId.isNotBlank()) {
                assignTlToUser(tlUserId, id)
            }

            Log.d(TAG, "addTeamLeader OK: $id → $name (tlUserId=$tlUserId)")
            id
        } catch (e: Exception) {
            Log.e(TAG, "addTeamLeader error: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UPDATE  — name / sheetUrl
    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Update TL name, sheetUrl, and optionally the linked userId.
     * If [tlUserId] changes, the old user's assignment is NOT removed automatically
     * (admin should do that via bulk assign). The new user gets auto-assigned.
     */
    suspend fun updateTeamLeader(
        tlId     : String,
        name     : String,
        sheetUrl : String,
        tlUserId : String = ""
    ): Boolean {
        return try {
            val updates = mutableMapOf<String, Any>(
                "name"     to name.trim(),
                "sheetUrl" to sheetUrl.trim()
            )
            if (tlUserId.isNotBlank()) updates["userId"] = tlUserId.trim()
            tlRef.child(tlId).updateChildren(updates).await()

            // Auto-assign updated userId to their own sheet
            if (tlUserId.isNotBlank()) {
                assignTlToUser(tlUserId, tlId)
            }

            Log.d(TAG, "updateTeamLeader OK: $tlId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateTeamLeader error: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DELETE  — remove TL (does NOT unassign members — do that separately)
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun deleteTeamLeader(tlId: String): Boolean {
        return try {
            tlRef.child(tlId).removeValue().await()
            Log.d(TAG, "deleteTeamLeader OK: $tlId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteTeamLeader error: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FETCH ALL — one-time list
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun fetchAllTeamLeaders(): List<TeamLeader> {
        return try {
            val snap = tlRef.get().await()
            snap.children.mapNotNull { parseSnapshot(it) }
                .sortedBy { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAllTeamLeaders error: ${e.message}")
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LIVE FLOW — real-time TL list
    // ─────────────────────────────────────────────────────────────────────────
    fun teamLeadersFlow(): Flow<List<TeamLeader>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { parseSnapshot(it) }
                    .sortedBy { it.name }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "teamLeadersFlow error: ${error.message}")
            }
        }
        tlRef.addValueEventListener(listener)
        awaitClose { tlRef.removeEventListener(listener) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ASSIGN  — set teamLeaderId on a user (RTDB users node)
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun assignTlToUser(userId: String, tlId: String): Boolean {
        return try {
            // 1. RTDB me save
            usersRtdb.child(userId).child("teamLeaderId").setValue(tlId).await()
            // 2. Firestore me save (taaki Admin dashboard ko hamesha latest mile)
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .update("teamLeaderId", tlId).await()

            Log.d(TAG, "assignTlToUser OK: user=$userId → TL=$tlId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "assignTlToUser error: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UNASSIGN — remove TL from a user
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun unassignTlFromUser(userId: String): Boolean {
        return try {
            // 1. RTDB se remove
            usersRtdb.child(userId).child("teamLeaderId").removeValue().await()
            // 2. Firestore se remove
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .update("teamLeaderId", com.google.firebase.firestore.FieldValue.delete()).await()

            Log.d(TAG, "unassignTlFromUser OK: user=$userId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "unassignTlFromUser error: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GET TL ID for a user (used in SheetsSync to pick correct URL)
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun getTlIdForUser(userId: String): String? {
        return try {
            val snap = usersRtdb.child(userId).child("teamLeaderId").get().await()
            snap.getValue(String::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "getTlIdForUser error: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GET Sheet URL for a TL ID
    // ─────────────────────────────────────────────────────────────────────────
    suspend fun getSheetUrlForTl(tlId: String): String? {
        return try {
            val snap = tlRef.child(tlId).child("sheetUrl").get().await()
            snap.getValue(String::class.java)?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "getSheetUrlForTl error: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper — parse RTDB snapshot → TeamLeader
    // ─────────────────────────────────────────────────────────────────────────
    private fun parseSnapshot(snap: DataSnapshot): TeamLeader? {
        val id  = snap.key ?: return null
        val map = snap.value as? Map<*, *> ?: return null
        return TeamLeader(
            id        = id,
            name      = map["name"]?.toString()      ?: "Unnamed TL",
            sheetUrl  = map["sheetUrl"]?.toString()  ?: "",
            userId    = map["userId"]?.toString()    ?: "",
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
