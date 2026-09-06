package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CyclicFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "CyclicFCM"
        const val CHANNEL_ID = "announcements_channel"
        const val TOPIC_ANNOUNCEMENTS = "announcements"
        const val TOPIC_ALL = "all"

        /**
         * Subscribe device to common broadcast topics so the developer can broadcast
         * to all app users from Firebase Console without needing individual tokens.
         */
        fun subscribeToDefaultTopics() {
            try {
                FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ANNOUNCEMENTS)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Subscribed to '$TOPIC_ANNOUNCEMENTS' topic")
                        }
                    }
                FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "Subscribed to '$TOPIC_ALL' topic")
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error subscribing to FCM topics", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        // Ensure topics are subscribed when token refreshes
        subscribeToDefaultTopics()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // 1. Extract title and body from notification payload or data payload
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Announcement"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: ""

        // Optional custom download or web URL (e.g. for update notifications)
        val targetUrl = remoteMessage.data["url"]
            ?: remoteMessage.data["download_url"]

        if (body.isNotBlank() || title.isNotBlank()) {
            showNotification(title, body, targetUrl)
        }
    }

    private fun showNotification(title: String, body: String, targetUrl: String?) {
        val context = applicationContext
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure announcement notification channel exists
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Announcements & Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Messages from developer about updates and announcements"
                enableVibration(false)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap intent: open web browser if targetUrl is provided, otherwise open MainActivity
        val pendingIntent = if (!targetUrl.isNullOrBlank() && (targetUrl.startsWith("http://") || targetUrl.startsWith("https://"))) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                browserIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("fcm_title", title)
                putExtra("fcm_body", body)
            }
            PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
