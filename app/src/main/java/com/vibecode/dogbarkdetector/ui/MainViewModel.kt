package com.vibecode.dogbarkdetector.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibecode.dogbarkdetector.data.AppSettingsRepository
import com.vibecode.dogbarkdetector.data.BarkHistoryRepository
import com.vibecode.dogbarkdetector.data.BarkRecord
import com.vibecode.dogbarkdetector.data.EmailProvider
import com.vibecode.dogbarkdetector.data.SmtpSettings
import com.vibecode.dogbarkdetector.data.SmtpSettingsRepository
import com.vibecode.dogbarkdetector.mail.SmtpAlertMailer
import com.vibecode.dogbarkdetector.service.MonitoringService
import com.vibecode.dogbarkdetector.service.MonitoringStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmtpSettingsRepository(application.applicationContext)
    private val historyRepository = BarkHistoryRepository(application.applicationContext)
    private val appSettingsRepository = AppSettingsRepository(application.applicationContext)
    private val smtpAlertMailer = SmtpAlertMailer()
    private val draftState = MutableStateFlow(MainUiState())
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                draftState,
                MonitoringStateStore.snapshot,
                repository.settings,
                historyRepository.records,
                appSettingsRepository.settings
            ) { draft, monitoringSnapshot, persisted, history, appSettings ->
                val source = if (draft.settingsSaved || draft.isEmpty()) persisted else draft.toSmtpSettings()
                val today = LocalDate.now(ZoneId.systemDefault())
                val todayCount = history.count { record ->
                    val date = Instant.ofEpochMilli(record.timestampMillis)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    date == today
                }
                draft.copy(
                    host = source.host,
                    port = source.port,
                    username = source.username,
                    password = source.password,
                    senderAddress = source.senderAddress,
                    recipientAddress = source.recipientAddress,
                    useTls = source.useTls,
                    cooldownSeconds = appSettings.cooldownSeconds,
                    confidenceThreshold = appSettings.confidenceThreshold,
                    isMonitoring = monitoringSnapshot.isMonitoring,
                    detectorStatus = monitoringSnapshot.detectorStatus,
                    featureSummary = monitoringSnapshot.featureSummary,
                    lastDetectionSummary = monitoringSnapshot.lastDetectionSummary,
                    emailStatus = monitoringSnapshot.emailStatus,
                    alertCount = monitoringSnapshot.alertCount,
                    cooldownRemainingSeconds = monitoringSnapshot.cooldownRemainingSeconds,
                    barkHistory = history,
                    todayBarkCount = todayCount,
                    totalBarkCount = history.size
                )
            }.collect { merged ->
                _uiState.value = merged
            }
        }
    }

    fun onHostChange(value: String) = updateDraft { copy(host = value, settingsSaved = false) }
    fun onPortChange(value: String) = updateDraft { copy(port = value, settingsSaved = false) }
    fun onUsernameChange(value: String) = updateDraft { copy(username = value, settingsSaved = false) }
    fun onPasswordChange(value: String) = updateDraft { copy(password = value, settingsSaved = false) }
    fun onSenderChange(value: String) = updateDraft { copy(senderAddress = value, settingsSaved = false) }
    fun onRecipientChange(value: String) = updateDraft { copy(recipientAddress = value, settingsSaved = false) }
    fun onTlsChange(value: Boolean) = updateDraft { copy(useTls = value, settingsSaved = false) }

    fun onProviderSelected(provider: EmailProvider) {
        updateDraft {
            copy(
                host = provider.host,
                port = provider.port,
                useTls = provider.useTls,
                settingsSaved = false
            )
        }
    }

    fun onCooldownChange(seconds: Long) {
        viewModelScope.launch {
            appSettingsRepository.updateCooldown(seconds)
        }
    }

    fun onConfidenceThresholdChange(threshold: Float) {
        viewModelScope.launch {
            appSettingsRepository.updateConfidenceThreshold(threshold)
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            repository.save(_uiState.value.toSmtpSettings())
            draftState.update { it.copy(settingsSaved = true) }
        }
    }

    fun sendTestEmail() {
        viewModelScope.launch {
            draftState.update {
                it.copy(isSendingTestEmail = true, testEmailStatus = "正在发送测试邮件...", settingsSaved = false)
            }
            val settings = _uiState.value.toSmtpSettings()
            val result = smtpAlertMailer.send(settings, SmtpAlertMailer.buildTestRequest())
            draftState.update {
                it.copy(
                    isSendingTestEmail = false,
                    testEmailStatus = result.fold(
                        onSuccess = { "测试邮件已发送至 ${settings.recipientAddress}" },
                        onFailure = { error -> error.message ?: "测试邮件发送失败" }
                    )
                )
            }
            if (result.isSuccess) {
                MonitoringStateStore.update { s ->
                    s.copy(emailStatus = "最新操作：测试邮件已发送至 ${settings.recipientAddress}")
                }
            }
        }
    }

    fun toggleMonitoring(context: Context) {
        if (_uiState.value.isMonitoring) {
            MonitoringService.stop(context)
        } else {
            MonitoringService.start(context)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    private fun updateDraft(transform: MainUiState.() -> MainUiState) {
        draftState.value = _uiState.value.transform()
    }

    private fun MainUiState.toSmtpSettings(): SmtpSettings = SmtpSettings(
        host = host, port = port, username = username, password = password,
        senderAddress = senderAddress, recipientAddress = recipientAddress, useTls = useTls
    )

    private fun MainUiState.isEmpty(): Boolean {
        return host.isBlank() && username.isBlank() && password.isBlank() &&
            senderAddress.isBlank() && recipientAddress.isBlank()
    }
}

private typealias Instant = java.time.Instant
