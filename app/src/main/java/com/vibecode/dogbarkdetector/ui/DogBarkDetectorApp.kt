package com.vibecode.dogbarkdetector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

enum class Screen(val label: String) {
    HOME("主页"),
    HISTORY("历史"),
    SETTINGS("设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DogBarkDetectorApp(
    uiState: MainUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSenderChange: (String) -> Unit,
    onRecipientChange: (String) -> Unit,
    onTlsChange: (Boolean) -> Unit,
    onSaveSettings: () -> Unit,
    onSendTestEmail: () -> Unit,
    onToggleMonitoring: () -> Unit,
    onProviderSelected: (com.vibecode.dogbarkdetector.data.EmailProvider) -> Unit = {},
    onCooldownChange: (Long) -> Unit = {},
    onConfidenceThresholdChange: (Float) -> Unit = {},
    onClearHistory: () -> Unit = {}
) {
    val navController = rememberNavController()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("你在狗叫什么？") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            AppBottomBar(navController)
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.HOME.name,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.HOME.name) {
                HomeScreen(
                    uiState = uiState,
                    onToggleMonitoring = onToggleMonitoring
                )
            }
            composable(Screen.HISTORY.name) {
                HistoryScreen(
                    uiState = uiState,
                    onClearHistory = onClearHistory
                )
            }
            composable(Screen.SETTINGS.name) {
                SettingsScreen(
                    uiState = uiState,
                    onHostChange = onHostChange,
                    onPortChange = onPortChange,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange,
                    onSenderChange = onSenderChange,
                    onRecipientChange = onRecipientChange,
                    onTlsChange = onTlsChange,
                    onSaveSettings = onSaveSettings,
                    onSendTestEmail = onSendTestEmail,
                    onProviderSelected = onProviderSelected,
                    onCooldownChange = onCooldownChange,
                    onConfidenceThresholdChange = onConfidenceThresholdChange
                )
            }
        }
    }
}

@Composable
private fun AppBottomBar(navController: NavHostController) {
    val currentEntry = navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry.value?.destination?.route

    NavigationBar {
        Screen.entries.forEach { screen ->
            NavigationBarItem(
                selected = currentRoute == screen.name,
                onClick = {
                    navController.navigate(screen.name) {
                        popUpTo(Screen.HOME.name) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = when (screen) {
                            Screen.HOME -> Icons.Default.Home
                            Screen.HISTORY -> Icons.Default.List
                            Screen.SETTINGS -> Icons.Default.Settings
                        },
                        contentDescription = screen.label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = { Text(screen.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    uiState: MainUiState,
    onToggleMonitoring: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("监控控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isMonitoring) "监控中" else "已关闭",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (uiState.isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Switch(
                        checked = uiState.isMonitoring,
                        onCheckedChange = { onToggleMonitoring() }
                    )
                }

                Text(uiState.statusMessage, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "检测引擎：YAMNet AI 音频识别（设备端离线推理）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(uiState.featureSummary, style = MaterialTheme.typography.bodySmall)
                Text(uiState.lastDetectionSummary, style = MaterialTheme.typography.bodySmall)
                Text(uiState.emailStatus, style = MaterialTheme.typography.bodySmall)

                if (uiState.cooldownRemainingSeconds > 0) {
                    Text(
                        text = "邮件冷却中：剩余 ${uiState.cooldownRemainingSeconds} 秒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = "本次会话已发送邮件：${uiState.alertCount} 封",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("快速统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "今日", value = "${uiState.todayBarkCount}")
                    StatItem(label = "总计", value = "${uiState.totalBarkCount}")
                    StatItem(label = "本次邮件", value = "${uiState.alertCount}")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("当前配置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "检测阈值：${(uiState.confidenceThreshold * 100).toInt()}%    " +
                    "冷却时间：${uiState.cooldownSeconds}秒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
internal fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
