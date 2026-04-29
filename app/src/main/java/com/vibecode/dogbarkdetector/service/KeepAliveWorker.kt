package com.vibecode.dogbarkdetector.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        MonitoringStateStore.init(applicationContext)
        val shouldRun = MonitoringStateStore.shouldBeRunning()

        if (shouldRun && !MonitoringStateStore.snapshot.value.isMonitoring) {
            Log.d(TAG, "服务应运行但未运行，拉起监控服务")
            MonitoringService.start(applicationContext)
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "KeepAliveWorker"
        private const val WORK_NAME = "dog_bark_keep_alive"

        fun enqueue(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "保活 WorkManager 已注册")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "保活 WorkManager 已取消")
        }
    }
}
