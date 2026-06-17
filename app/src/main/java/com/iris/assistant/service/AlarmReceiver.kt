package com.iris.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val title = when (action) {
            ACTION_ALARM   -> intent.getStringExtra("label") ?: "IRIS Alarmı"
            ACTION_REMINDER -> intent.getStringExtra("title") ?: "IRIS Hatırlatıcı"
            else           -> return
        }

        val channelId = "iris_alarm_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "IRIS Alarm ve Hatırlatıcılar",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = android.app.Notification.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(if (action == ACTION_ALARM) "Alarm zamanı!" else "Hatırlatıcı: $title")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        const val ACTION_ALARM    = "com.iris.assistant.ALARM_ACTION"
        const val ACTION_REMINDER = "com.iris.assistant.REMINDER_ACTION"
    }
}
