package com.vibecode.dogbarkdetector.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MonitoringSnapshot(
    val isMonitoring: Boolean = false,
    val detectorStatus: String = "监控已停止。",
    val featureSummary: String = "等待音频分析...",
    val lastDetectionSummary: String = "暂无检测记录。",
    val emailStatus: String = "暂无邮件通知。",
    val alertCount: Int = 0,
    val cooldownRemainingSeconds: Int = 0
)

object MonitoringStateStore {
    private const val PREFS_NAME = "monitoring_state"
    private const val KEY_SHOULD_BE_RUNNING = "should_be_running"

    private val mutableSnapshot = MutableStateFlow(MonitoringSnapshot())
    val snapshot: StateFlow<MonitoringSnapshot> = mutableSnapshot.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun shouldBeRunning(): Boolean = prefs?.getBoolean(KEY_SHOULD_BE_RUNNING, false) ?: false

    fun update(transform: (MonitoringSnapshot) -> MonitoringSnapshot) {
        mutableSnapshot.update { current ->
            val updated = transform(current)
            persistRunningState(updated.isMonitoring)
            updated
        }
    }

    fun set(snapshot: MonitoringSnapshot) {
        mutableSnapshot.value = snapshot
        persistRunningState(snapshot.isMonitoring)
    }

    private fun persistRunningState(running: Boolean) {
        prefs?.edit()?.putBoolean(KEY_SHOULD_BE_RUNNING, running)?.apply()
    }
}
