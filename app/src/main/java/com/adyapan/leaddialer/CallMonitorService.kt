package com.adyapan.leaddialer

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class CallMonitorService : Service() {

    private var isMonitoring = false
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var callLogObserver: ContentObserver? = null
    private var lastCallTimestamp = 0L

    companion object {
        private const val NOTIFICATION_ID = 9876
        private const val CHANNEL_ID = "call_monitor_channel"

        fun start(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallMonitorService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Service restart hogi agar system kill kare
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring calls in background"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lead Dialer Active")
            .setContentText("Monitoring calls...")
            .setSmallIcon(R.drawable.ic_phone) // Apna icon use karo
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        // Method 1: CallLog Observer (real-time detection)
        callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                checkForNewCall()
            }
        }

        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            callLogObserver!!
        )

        // Method 2: Polling fallback (har 2 second check - battery friendly)
        serviceScope.launch {
            while (isMonitoring) {
                checkForNewCall()
                delay(2000) // 2 seconds
            }
        }

        Log.d("CallMonitorService", "Monitoring started")
    }

    private fun checkForNewCall() {
        if (!CallManager.callActive) return

        try {
            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                    val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                    val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)

                    if (numberIndex >= 0 && durationIndex >= 0 && dateIndex >= 0 && typeIndex >= 0) {
                        val number = it.getString(numberIndex) ?: ""
                        val duration = it.getLong(durationIndex)
                        val timestamp = it.getLong(dateIndex)
                        val callType = it.getInt(typeIndex)

                        // Check if this is a new outgoing call
                        if (callType == CallLog.Calls.OUTGOING_TYPE &&
                            timestamp > lastCallTimestamp &&
                            duration >= 0) {

                            val lead = CallManager.currentLead

                            // Match phone number
                            if (lead != null && number.endsWith(lead.phone.takeLast(10))) {
                                lastCallTimestamp = timestamp

                                val record = CallRecord(
                                    phone = lead.phone,
                                    name = lead.name,
                                    duration = duration,
                                    calledAt = timestamp,
                                    status = "Pending"
                                )

                                // Call ended - show popup
                                CallManager.callActive = false
                                showPopupActivity(record)

                                Log.d("CallMonitorService", "Call detected: $number, duration: $duration")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CallMonitorService", "Error checking call: ${e.message}")
        }
    }

    private fun showPopupActivity(record: CallRecord) {
        val intent = Intent(this, CallPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_record", record)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isMonitoring = false
        callLogObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        serviceScope.cancel()
        Log.d("CallMonitorService", "Monitoring stopped")
    }
}