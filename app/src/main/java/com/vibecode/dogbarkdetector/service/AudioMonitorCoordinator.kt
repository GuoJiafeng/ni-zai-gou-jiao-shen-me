package com.vibecode.dogbarkdetector.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vibecode.dogbarkdetector.data.BarkHistoryRepository
import com.vibecode.dogbarkdetector.data.BarkRecord
import com.vibecode.dogbarkdetector.data.SmtpSettingsRepository
import com.vibecode.dogbarkdetector.mail.SmtpAlertMailer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

class AudioMonitorCoordinator(
    context: Context,
    private val barkDetectionEngine: BarkDetectionEngine = YamNetBarkDetectionEngine(context.applicationContext),
    private val smtpAlertMailer: SmtpAlertMailer = SmtpAlertMailer(),
    private val repository: SmtpSettingsRepository = SmtpSettingsRepository(context.applicationContext),
    private val historyRepository: BarkHistoryRepository = BarkHistoryRepository(context.applicationContext)
) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var monitoringJob: Job? = null
    private var lastAlertAtMillis = 0L
    private var cooldownMs = DEFAULT_COOLDOWN_MS

    fun setCooldownMs(ms: Long) {
        cooldownMs = ms
    }

    suspend fun start(
        scope: CoroutineScope,
        onStateChanged: (MonitoringSnapshot) -> Unit
    ) {
        mutex.withLock {
            if (monitoringJob != null) return

            MonitoringStateStore.set(
                MonitoringSnapshot(
                    isMonitoring = true,
                    detectorStatus = "YAMNet 音频分类已启动，正在监听狗叫...",
                    featureSummary = "等待音频分析结果...",
                    lastDetectionSummary = "暂无检测记录。",
                    emailStatus = "暂无邮件通知。",
                    cooldownRemainingSeconds = remainingCooldownSeconds()
                )
            )
            onStateChanged(MonitoringStateStore.snapshot.value)

            monitoringJob = scope.launch {
                runCatching {
                    barkDetectionEngine.run(
                        onAnalysis = { analysis ->
                            val snapshot = MonitoringStateStore.snapshot.value.copy(
                                isMonitoring = true,
                                detectorStatus = if (remainingCooldownSeconds() > 0) {
                                    "冷却中，继续监听但暂不发邮件。"
                                } else {
                                    "YAMNet 正在分析音频，监听狗叫类别..."
                                },
                                featureSummary = analysis.summary(),
                                cooldownRemainingSeconds = remainingCooldownSeconds()
                            )
                            MonitoringStateStore.set(snapshot)
                            onStateChanged(snapshot)
                        },
                        onBarkDetected = { event ->
                            handleBarkDetected(event, onStateChanged)
                        },
                        onAudioCaptured = { event ->
                            handleAudioCaptured(event, onStateChanged)
                        }
                    )
                }.onFailure { error ->
                    val snapshot = MonitoringStateStore.snapshot.value.copy(
                        isMonitoring = false,
                        detectorStatus = "监控因音频采集失败而停止。",
                        emailStatus = error.message ?: "监控失败。",
                        cooldownRemainingSeconds = remainingCooldownSeconds()
                    )
                    MonitoringStateStore.set(snapshot)
                    onStateChanged(snapshot)
                }

                monitoringJob = null
            }
        }
    }

    suspend fun stop(onStateChanged: (MonitoringSnapshot) -> Unit) {
        mutex.withLock {
            monitoringJob?.cancelAndJoin()
            monitoringJob = null
            barkDetectionEngine.shutdown()
            val snapshot = MonitoringStateStore.snapshot.value.copy(
                isMonitoring = false,
                detectorStatus = "监控已停止。",
                cooldownRemainingSeconds = remainingCooldownSeconds()
            )
            MonitoringStateStore.set(snapshot)
            onStateChanged(snapshot)
        }
    }

    private suspend fun handleBarkDetected(
        event: BarkDetectionEvent,
        onStateChanged: (MonitoringSnapshot) -> Unit
    ) {
        vibrate()

        val record = BarkRecord(
            timestampMillis = event.occurredAtMillis,
            confidence = event.snapshot.rms,
            label = "Bark"
        )
        historyRepository.addRecord(record)

        val cooldownRemaining = remainingCooldownSeconds()
        val baseSnapshot = MonitoringStateStore.snapshot.value.copy(
            lastDetectionSummary = "🐶 检测到狗叫 ${formatTime(event.occurredAtMillis)}，连续 ${event.burstCount} 帧。正在录制音频...",
            featureSummary = event.snapshot.summary(),
            cooldownRemainingSeconds = cooldownRemaining
        )

        val snapshot = if (cooldownRemaining > 0) {
            baseSnapshot.copy(
                detectorStatus = "检测到狗叫，邮件冷却中（震动已触发）。",
                emailStatus = "冷却剩余 ${cooldownRemaining} 秒，音频仍在录制中。"
            )
        } else {
            baseSnapshot.copy(
                detectorStatus = "🐶 检测到狗叫！正在录制前后音频...",
                emailStatus = "音频录制中，完成后自动发送邮件。"
            )
        }

        MonitoringStateStore.set(snapshot)
        onStateChanged(snapshot)
    }

    private suspend fun handleAudioCaptured(
        event: AudioCapturedEvent,
        onStateChanged: (MonitoringSnapshot) -> Unit
    ) {
        historyRepository.updateAudioPath(event.occurredAtMillis, event.wavFilePath)

        val cooldownRemaining = remainingCooldownSeconds()
        if (cooldownRemaining > 0) {
            val snapshot = MonitoringStateStore.snapshot.value.copy(
                detectorStatus = "音频已保存，但邮件冷却中。",
                emailStatus = "冷却剩余 ${cooldownRemaining} 秒，本次邮件已跳过。"
            )
            MonitoringStateStore.set(snapshot)
            onStateChanged(snapshot)
            return
        }

        val settings = repository.settings.first()
        if (!settings.isComplete()) {
            val snapshot = MonitoringStateStore.snapshot.value.copy(
                detectorStatus = "音频已保存（SMTP 未配置）。",
                emailStatus = "SMTP 未配置，仅本地震动提醒。"
            )
            MonitoringStateStore.set(snapshot)
            onStateChanged(snapshot)
            return
        }

        val audioFile = File(event.wavFilePath)
        val request = SmtpAlertMailer.buildAlertRequest(
            detectorSummary = "YAMNet 狗叫检测事件",
            featureSummary = MonitoringStateStore.snapshot.value.featureSummary,
            occurredAtMillis = event.occurredAtMillis,
            burstCount = 1,
            audioFile = if (audioFile.exists()) audioFile else null
        )

        val result = smtpAlertMailer.send(settings, request)
        val snapshot = result.fold(
            onSuccess = {
                lastAlertAtMillis = System.currentTimeMillis()
                MonitoringStateStore.snapshot.value.copy(
                    detectorStatus = "🐶 狗叫提醒邮件已发送（含音频）。",
                    emailStatus = "邮件已发送至 ${settings.recipientAddress}（${formatTime(lastAlertAtMillis)}）。",
                    alertCount = MonitoringStateStore.snapshot.value.alertCount + 1,
                    cooldownRemainingSeconds = remainingCooldownSeconds()
                )
            },
            onFailure = { error ->
                MonitoringStateStore.snapshot.value.copy(
                    detectorStatus = "音频已保存，但邮件发送失败。",
                    emailStatus = error.message ?: "邮件发送失败。",
                    cooldownRemainingSeconds = 0
                )
            }
        )

        MonitoringStateStore.set(snapshot)
        onStateChanged(snapshot)
    }

    private fun remainingCooldownSeconds(nowMillis: Long = System.currentTimeMillis()): Int {
        if (lastAlertAtMillis == 0L) return 0
        val remainingMs = (lastAlertAtMillis + cooldownMs) - nowMillis
        return max(0, (remainingMs / 1000L).toInt())
    }

    private fun formatTime(timestampMillis: Long): String {
        return formatter.format(Instant.ofEpochMilli(timestampMillis))
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            val pattern = longArrayOf(0, 300, 100, 300, 100, 300)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Exception) {
        }
    }

    private companion object {
        private const val DEFAULT_COOLDOWN_MS = 120_000L
        private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }
}
