package com.adyapan.leaddialer

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit


class LeadDialerApp : Application() {

    companion object {
        lateinit var applicationScope: CoroutineScope
            private set
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ThemeManager.applyTheme(this)

        //  SECURITY FIX: Initialize Firebase App Check with Play Integrity
        // This prevents bots and unauthorized clients from accessing Firebase APIs.
        // In debug/testing, DebugAppCheckProviderFactory is automatically used
        // when firebase.json debug token is configured.
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            // Debug builds: use debug provider (requires debug token in Firebase console)
            firebaseAppCheck.installAppCheckProviderFactory(
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            // Release builds: use Play Integrity (most secure)
            firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
        }

        // Firebase auto-initializes from google-services.json — no manual init needed

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
            repeatInterval = 7L,
            repeatIntervalTimeUnit = TimeUnit.DAYS   // हर 7 दिन में एक बार
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AttendanceSelfieCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }
}
