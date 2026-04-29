package com.vibecode.dogbarkdetector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vibecode.dogbarkdetector.data.EmailProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
    onProviderSelected: (EmailProvider) -> Unit,
    onCooldownChange: (Long) -> Unit,
    onConfidenceThresholdChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("邮件设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                val currentProvider = run {
                    EmailProvider.entries.firstOrNull {
                        it.host.isNotBlank() && it.host == uiState.host
                    } ?: EmailProvider.CUSTOM
                }
                val expanded = remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expanded.value,
                    onExpandedChange = { expanded.value = it }
                ) {
                    OutlinedTextField(
                        value = currentProvider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("邮箱类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded.value,
                        onDismissRequest = { expanded.value = false }
                    ) {
                        EmailProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    if (provider == EmailProvider.CUSTOM) {
                                        Text(provider.displayName)
                                    } else {
                                        Text("${provider.displayName}  (${provider.host}:${provider.port})")
                                    }
                                },
                                onClick = {
                                    expanded.value = false
                                    if (provider != EmailProvider.CUSTOM) {
                                        onProviderSelected(provider)
                                    }
                                }
                            )
                        }
                    }
                }

                SmtpTextField(label = "SMTP 服务器", value = uiState.host, onValueChange = onHostChange)
                SmtpTextField(label = "端口", value = uiState.port, onValueChange = onPortChange, keyboardType = KeyboardType.Number)
                SmtpTextField(label = "用户名", value = uiState.username, onValueChange = onUsernameChange)
                SmtpTextField(label = "密码（授权码）", value = uiState.password, onValueChange = onPasswordChange, isPassword = true)
                SmtpTextField(label = "发件地址", value = uiState.senderAddress, onValueChange = onSenderChange)
                SmtpTextField(label = "收件地址", value = uiState.recipientAddress, onValueChange = onRecipientChange)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用 TLS")
                    Switch(checked = uiState.useTls, onCheckedChange = onTlsChange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSaveSettings) {
                        Text(if (uiState.settingsSaved) "已保存" else "保存设置")
                    }
                    Button(onClick = onSendTestEmail, enabled = !uiState.isSendingTestEmail) {
                        Text(if (uiState.isSendingTestEmail) "发送中..." else "发送测试邮件")
                    }
                }
                if (uiState.testEmailStatus.isNotBlank()) {
                    Text(uiState.testEmailStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HorizontalDivider()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("检测参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                Text(
                    "检测置信度阈值：${(uiState.confidenceThreshold * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiState.confidenceThreshold,
                    onValueChange = onConfidenceThresholdChange,
                    valueRange = 0.05f..0.8f,
                    steps = 15,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "越低越灵敏，可能误报增多；越高越严格，可能漏报。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Text(
                    "邮件冷却时间：${uiState.cooldownSeconds} 秒",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = uiState.cooldownSeconds.toFloat(),
                    onValueChange = { onCooldownChange(it.toLong()) },
                    valueRange = 10f..600f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "两次邮件提醒之间的最短间隔。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SmtpTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true
    )
}
