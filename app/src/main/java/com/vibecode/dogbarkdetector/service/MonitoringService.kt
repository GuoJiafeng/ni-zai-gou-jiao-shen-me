package com.vibecode.dogbarkdetector.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var coordinator: AudioMonitorCoordinator
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        coordinator = AudioMonitorCoordinator(applicationContext)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                stopSelf()
            }
            else -> {
                startForegroundCompat(
                    NotificationChannels.MONITORING_NOTIFICATION_ID,
                    createNotification(MonitoringStateStore.snapshot.value)
                )
                serviceScope.launch {
                    coordinator.start(serviceScope, ::publishState)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runBlocking {
            coordinator.stop(::publishState)
        }
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "任务被移除，5秒后重启服务")
        ServiceRestartScheduler.scheduleRestart(applicationContext, 5000L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DogBarkDetector::MonitoringWakeLock"
            ).apply {
                acquire(12 * 60 * 60 * 1000L)
            }
            Log.d(TAG, "WakeLock 已获取")
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock 获取失败: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
                Log.d(TAG, "WakeLock 已释放")
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun createNotification(snapshot: MonitoringSnapshot): Notification {
        NotificationChannels.ensureCreated(this)
        return NotificationChannels.buildMonitoringNotification(this, snapshot)
    }

    private fun publishState(snapshot: MonitoringSnapshot) {
        NotificationManagerCompat.from(this).notify(
            NotificationChannels.MONITORING_NOTIFICATION_ID,
            createNotification(snapshot)
        )
    }

    private fun startForegroundCompat(notificationId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    companion object {
        private const val TAG = "MonitoringService"
        private const val ACTION_START = "com.vibecode.dogbarkdetector.action.START_MONITORING"
        private const val ACTION_STOP = "com.vibecode.dogbarkdetector.action.STOP_MONITORING"

        fun start(context: Context) {
            KeepAliveWorker.enqueue(context)
            val intent = Intent(context, MonitoringService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            KeepAliveWorker.cancel(context)
            ServiceRestartScheduler.cancelRestart(context)
            val intent = Intent(context, MonitoringService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
