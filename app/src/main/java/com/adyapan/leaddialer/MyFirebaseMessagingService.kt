package com.adyapan.leaddialer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
        private const val CHANNEL_ID = "leaddialer_message_channel"
        private const val CHANNEL_NAME = "Lead Dialer Messages"
    }


    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Message From: ${remoteMessage.from}")

        val msgId = remoteMessage.messageId ?: java.util.UUID.randomUUID().toString()
        var title = "Lead Dialer"
        var body = "New Message"
        var hasContent = false

        remoteMessage.notification?.let {
            title = it.title ?: "Lead Dialer"
            body = it.body ?: "You received a new message"
            hasContent = true
            Log.d(TAG, "Notification Title: $title")
            Log.d(TAG, "Notification Body: $body")
            showNotification(title, body)
        }

        if (remoteMessage.data.isNotEmpty()) {
            title = remoteMessage.data["title"] ?: title
            body = remoteMessage.data["body"] ?: body
            hasContent = true
            Log.d(TAG, "Data Payload: ${remoteMessage.data}")
            if (remoteMessage.notification == null) {
                showNotification(title, body)
            }
        }

        if (hasContent) {
            saveMessageToFirestore(msgId, title, body)
        }
    }

    private fun saveMessageToFirestore(msgId: String, title: String, body: String) {
        val uid = FirebaseAuth.getInstance().uid
        if (uid == null) {
            Log.e(TAG, "User not logged in. Message not saved.")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val cleanMsgId = msgId.replace("/", "_").replace(":", "_")

        val isAnnouncement = title.contains("announcement", ignoreCase = true) ||
                title.contains("announcemint", ignoreCase = true) ||
                title.contains("update", ignoreCase = true) ||
                title.contains("notice", ignoreCase = true) ||
                title.contains("circular", ignoreCase = true)

        if (isAnnouncement) {
            val announcementDoc = mapOf(
                "text" to "$title: $body",
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("announcements")
                .document(cleanMsgId)
                .set(announcementDoc, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "FCM announcement saved: $cleanMsgId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save FCM announcement: ${e.message}")
                }
        } else {
            val inboxDoc = mapOf(
                "from" to title,
                "text" to body,
                "timestamp" to System.currentTimeMillis(),
                "read" to false,
                "replyCount" to 0
            )
            db.collection("messages")
                .document(uid)
                .collection("inbox")
                .document(cleanMsgId)
                .set(inboxDoc, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "FCM inbox message saved: $cleanMsgId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save FCM inbox message: ${e.message}")
                }
        }
    }


    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d(TAG, "New Token: $token")

        saveTokenToFirestore(token)
    }

    // ─────────────────────────────────────────────────────────────
    // Save token in Firestore
    // ─────────────────────────────────────────────────────────────
    private fun saveTokenToFirestore(token: String) {

        val uid = FirebaseAuth.getInstance().uid

        if (uid == null) {
            Log.e(TAG, "User not logged in. Token not saved.")
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)

            .addOnSuccessListener {
                Log.d(TAG, "FCM token saved successfully")
            }

            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save token: ${e.message}")
            }
    }

    // ─────────────────────────────────────────────────────────────
    // Show Notification
    // ─────────────────────────────────────────────────────────────
    private fun showNotification(title: String, messageBody: String) {

        createNotificationChannel()

        // Open app when notification clicked
        val intent = Intent(this, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification Builder
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(messageBody)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = NotificationManagerCompat.from(this)

        // Android 13+ Permission Check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                Log.e(TAG, "Notification permission not granted")
                return
            }
        }

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            builder.build()
        )
    }


    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val importance = NotificationManager.IMPORTANCE_HIGH

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {

                description = "Notifications for messages and updates"
                enableVibration(true)
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }
}