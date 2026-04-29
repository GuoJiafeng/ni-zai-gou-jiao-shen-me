package com.vibecode.dogbarkdetector.ui

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibecode.dogbarkdetector.data.BarkRecord
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    uiState: MainUiState,
    onClearHistory: () -> Unit
) {
    val context = LocalContext.current
    var playingIndex by remember { mutableIntStateOf(-1) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer.release() } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("检测记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "今日", value = "${uiState.todayBarkCount}")
            StatItem(label = "总计", value = "${uiState.totalBarkCount}")
            StatItem(label = "本次邮件", value = "${uiState.alertCount}")
        }

        if (uiState.barkHistory.isEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "暂无检测记录，开启监控后将自动记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            HorizontalDivider()

            uiState.barkHistory.forEachIndexed { index, record ->
                BarkRecordCard(
                    record = record,
                    index = index,
                    isPlaying = playingIndex == index,
                    onPlay = {
                        if (playingIndex == index) {
                            try { mediaPlayer.stop() } catch (_: Exception) {}
                            try { mediaPlayer.reset() } catch (_: Exception) {}
                            playingIndex = -1
                            return@BarkRecordCard
                        }

                        try { mediaPlayer.stop() } catch (_: Exception) {}
                        try { mediaPlayer.reset() } catch (_: Exception) {}

                        val audioPath = record.audioFilePath
                        if (audioPath != null && File(audioPath).exists()) {
                            try {
                                mediaPlayer.setDataSource(audioPath)
                                mediaPlayer.prepare()
                                mediaPlayer.setOnCompletionListener {
                                    playingIndex = -1
                                }
                                mediaPlayer.start()
                                playingIndex = index
                            } catch (_: Exception) {
                                playingIndex = -1
                            }
                        }
                    }
                )
            }

            if (uiState.barkHistory.size > 20) {
                Text(
                    "... 共 ${uiState.barkHistory.size} 条记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Button(
                onClick = onClearHistory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除所有记录")
            }
        }
    }
}

@Composable
private fun BarkRecordCard(
    record: BarkRecord,
    index: Int,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🐶 ${formatTimestamp(record.timestampMillis)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "置信度 ${(record.confidence * 100).toInt()}%" +
                        if (record.audioFilePath != null) " · 有音频" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (record.audioFilePath != null) {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = if (isPlaying)
                            Icons.Default.Stop
                        else
                            Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "停止" else "播放",
                        modifier = Modifier.size(32.dp),
                        tint = if (isPlaying) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    return DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}
