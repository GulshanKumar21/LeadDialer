package com.adyapan.leaddialer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AttendanceSelfieCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        const val WORK_NAME = "attendance_selfie_cleanup"
    }
}
