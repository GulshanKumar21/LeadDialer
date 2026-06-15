package com.adyapan.leaddialer

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class LeadDialerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyTheme(this)

        // Initialize Firebase and App Check (Release Play Integrity mode only)
        FirebaseApp.initializeApp(this)
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // ── Firebase RTDB Offline Persistence ──────────────────────────────────
        // Jab network off ho tab bhi sare Firebase writes disk par queue hote hain.
        // Jaise hi internet wapas aata hai, Firebase KHUD APNE AAP sync kar deta hai.
        // Iska matlab: employee offline ho kar bhi calls save kar sakta hai —
        // aur network aate hi admin portal par turant dikh jaata hai.
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        scheduleSelfieCleanup()
    }

    /**
     * हर 7 दिन में एक बार selfie photos cleanup run होगी।
     * Photos 60 दिन के बाद delete होंगी, लेकिन attendance time/status रहेगा।
     */
    private fun scheduleSelfieCleanup() {
        val cleanupRequest = PeriodicWorkRequestBuilder<AttendanceSelfieCleanupWorker>(
            repeatInterval = 7,
            repeatIntervalTimeUnit = TimeUnit.DAYS   // हर 7 दिन में एक बार
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AttendanceSelfieCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }
}
