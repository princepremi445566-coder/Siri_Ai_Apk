package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

data class CapturedNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class SiriNotificationListenerService : NotificationListenerService() {

    companion object {
        private val recentNotifications = mutableListOf<CapturedNotification>()
        var isConnected: Boolean = false

        fun getRecentNotifications(): List<CapturedNotification> {
            synchronized(recentNotifications) {
                return recentNotifications.toList()
            }
        }

        fun clearNotifications() {
            synchronized(recentNotifications) {
                recentNotifications.clear()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d("SiriNotifListener", "Notification listener connected successfully")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (title.isNotBlank() || text.isNotBlank()) {
            val captured = CapturedNotification(
                packageName = sbn.packageName,
                title = title,
                text = text
            )
            synchronized(recentNotifications) {
                recentNotifications.add(0, captured)
                if (recentNotifications.size > 20) {
                    recentNotifications.removeAt(recentNotifications.lastIndex)
                }
            }
        }
    }
}
