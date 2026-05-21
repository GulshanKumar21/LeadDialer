package com.adyapan.leaddialer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object IncomingCallNotificationHelper {

    private const val CHANNEL_ID   = "incoming_lead_call"
    private const val CHANNEL_NAME = "Incoming Lead Calls"
    private const val NOTIF_ID     = 9001

    fun show(context: Context, lead: Lead) {
        createChannel(context)

        // Tap notification → open app directly
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_leads", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Status emoji
        val statusEmoji = when (lead.status) {
            "Interested"     -> "⭐"
            "Connected"      -> "✅"
            "Not Connected"  -> "📵"
            "Busy"           -> "🔴"
            "Not Interested" -> "❌"
            else             -> "⏳"
        }

        // Build college info line
        val collegeInfo = buildString {
            if (lead.collegeName.isNotBlank()) append(lead.collegeName)
            if (lead.collegeCity.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(lead.collegeCity)
            }
        }

        val titleText = "📞 ${lead.name} is calling!"
        val bigText = buildString {
            appendLine("📱 ${lead.phone}")
            appendLine("$statusEmoji Status: ${lead.status.ifBlank { "Pending" }}")
            if (collegeInfo.isNotBlank()) appendLine("🏫 $collegeInfo")
            if (!lead.notes.isNullOrBlank()) appendLine("📝 ${lead.notes}")
        }.trim()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle(titleText)
            .setContentText("$statusEmoji ${lead.status.ifBlank { "Pending" }} • ${lead.phone}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bigText)
                    .setBigContentTitle(titleText)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF6A00.toInt())                // Orange accent
            .setColorized(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // Show on lock screen
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description       = "Shows CRM lead info when a lead calls back"
                enableLights(true)
                lightColor        = android.graphics.Color.parseColor("#FF6A00")
                enableVibration(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
