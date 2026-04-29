package com.vibecode.dogbarkdetector.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vibecode.dogbarkdetector.MainActivity
import com.vibecode.dogbarkdetector.R

object NotificationChannels {
    private const val MONITORING_CHANNEL_ID = "monitoring"
    const val MONITORING_NOTIFICATION_ID = 1001

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            MONITORING_CHANNEL_ID,
            context.getString(R.string.monitoring_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.monitoring_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildMonitoringNotification(
        context: Context,
        snapshot: MonitoringSnapshot
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, MONITORING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.monitoring_notification_title))
            .setContentText(snapshot.detectorStatus)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    listOf(snapshot.detectorStatus, snapshot.emailStatus, snapshot.featureSummary)
                        .joinToString(separator = "\n")
                )
            )
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }
}
