package com.vibecode.dogbarkdetector.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            MonitoringStateStore.init(context.applicationContext)
            if (MonitoringStateStore.shouldBeRunning()) {
                Log.d(TAG, "开机完成，恢复监控服务")
                MonitoringService.start(context)
            } else {
                Log.d(TAG, "开机完成，服务未在运行，不自动启动")
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
