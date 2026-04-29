package com.vibecode.dogbarkdetector.ui

import com.vibecode.dogbarkdetector.data.BarkRecord

data class MainUiState(
    val host: String = "",
    val port: String = "587",
    val username: String = "",
    val password: String = "",
    val senderAddress: String = "",
    val recipientAddress: String = "",
    val useTls: Boolean = true,
    val cooldownSeconds: Long = 120L,
    val confidenceThreshold: Float = 0.25f,
    val isMonitoring: Boolean = false,
    val settingsSaved: Boolean = false,
    val detectorStatus: String = "监控已停止。",
    val featureSummary: String = "等待音频分析...",
    val lastDetectionSummary: String = "暂无检测记录。",
    val emailStatus: String = "暂无邮件通知。",
    val alertCount: Int = 0,
    val cooldownRemainingSeconds: Int = 0,
    val isSendingTestEmail: Boolean = false,
    val testEmailStatus: String = "",
    val barkHistory: List<BarkRecord> = emptyList(),
    val todayBarkCount: Int = 0,
    val totalBarkCount: Int = 0
) {
    val statusMessage: String
        get() = if (isMonitoring) {
            detectorStatus
        } else {
            "监控已停止，点击开关开始监听。"
        }
}
