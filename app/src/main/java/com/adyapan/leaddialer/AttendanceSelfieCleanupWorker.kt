package com.adyapan.leaddialer

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * AttendanceSelfieCleanupWorker
 *
 * यह Worker हर 24 घंटे में एक बार चलता है (WorkManager schedule करता है)।
 * यह सिर्फ 60 दिन से पुरानी SELFIE PHOTOS delete करता है।
 * Attendance records (check-in time, check-out time, status) सुरक्षित रहते हैं।
 */
class AttendanceSelfieCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "attendance_selfie_cleanup"
        private const val TAG = "SelfieCleanupWorker"
        private const val RETENTION_DAYS = 60
    }

    override suspend fun doWork(): Result {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Log.d(TAG, "No user logged in, skipping cleanup.")
                return Result.success()
            }

            // 60 दिन पहले की date calculate करें
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -RETENTION_DAYS)
            val thresholdDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

            Log.d(TAG, "Running cleanup for uid=$uid, deleting selfies before $thresholdDate")

            val db = FirebaseFirestore.getInstance()

            // 60 दिन से पुराने records fetch करें
            val snap = db.collection("attendance")
                .document(uid)
                .collection("dates")
                .whereLessThan(FieldPath.documentId(), thresholdDate)
                .get()
                .await()

            if (snap.isEmpty) {
                Log.d(TAG, "No old records found, nothing to clean.")
                return Result.success()
            }

            // Batch में सिर्फ selfie fields delete करें (बाकी सब रहे)
            val batch = db.batch()
            var deleteCount = 0

            for (doc in snap.documents) {
                val hasCheckInSelfie  = doc.contains("checkInSelfie")  && doc.get("checkInSelfie") != null
                val hasCheckOutSelfie = doc.contains("checkOutSelfie") && doc.get("checkOutSelfie") != null

                if (hasCheckInSelfie || hasCheckOutSelfie) {
                    val updates = hashMapOf<String, Any?>()
                    if (hasCheckInSelfie)  updates["checkInSelfie"]  = FieldValue.delete()
                    if (hasCheckOutSelfie) updates["checkOutSelfie"] = FieldValue.delete()
                    batch.update(doc.reference, updates)
                    deleteCount++
                }
            }

            if (deleteCount > 0) {
                batch.commit().await()
                Log.d(TAG, "Cleanup done: $deleteCount records cleaned (photos deleted, times kept).")
            } else {
                Log.d(TAG, "No selfies to delete in old records.")
            }

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed: ${e.message}")
            Result.retry() // अगर fail हो तो दोबारा try करे
        }
    }
}
