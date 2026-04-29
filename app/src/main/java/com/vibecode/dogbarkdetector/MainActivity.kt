package com.vibecode.dogbarkdetector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibecode.dogbarkdetector.data.EmailProvider
import com.vibecode.dogbarkdetector.service.MonitoringStateStore
import com.vibecode.dogbarkdetector.ui.DogBarkDetectorApp
import com.vibecode.dogbarkdetector.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitoringStateStore.init(applicationContext)

        setContent {
            val context = LocalContext.current
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                val granted = result.values.all { it }
                if (granted) {
                    viewModel.toggleMonitoring(context)
                }
            }

            DogBarkDetectorApp(
                uiState = uiState,
                onHostChange = viewModel::onHostChange,
                onPortChange = viewModel::onPortChange,
                onUsernameChange = viewModel::onUsernameChange,
                onPasswordChange = viewModel::onPasswordChange,
                onSenderChange = viewModel::onSenderChange,
                onRecipientChange = viewModel::onRecipientChange,
                onTlsChange = viewModel::onTlsChange,
                onSaveSettings = { viewModel.saveSettings() },
                onSendTestEmail = { viewModel.sendTestEmail() },
                onToggleMonitoring = {
                    if (!uiState.isMonitoring) {
                        val missingPermissions = buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }.filterNot { permission ->
                            ContextCompat.checkSelfPermission(
                                context,
                                permission
                            ) == PackageManager.PERMISSION_GRANTED
                        }

                        if (missingPermissions.isEmpty()) {
                            viewModel.toggleMonitoring(context)
                        } else {
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        }
                    } else {
                        viewModel.toggleMonitoring(context)
                    }
                },
                onProviderSelected = { viewModel.onProviderSelected(it) },
                onCooldownChange = { viewModel.onCooldownChange(it) },
                onConfidenceThresholdChange = { viewModel.onConfidenceThresholdChange(it) },
                onClearHistory = { viewModel.clearHistory() }
            )
        }
    }
}
