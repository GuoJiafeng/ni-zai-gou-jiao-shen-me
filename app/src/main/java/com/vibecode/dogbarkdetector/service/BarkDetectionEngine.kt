package com.vibecode.dogbarkdetector.service

data class AudioAnalysisSnapshot(
    val rms: Float,
    val peak: Float,
    val zeroCrossingRate: Float,
    val impulse: Float,
    val noiseFloor: Float,
    val candidateBursts: Int
) {
    fun summary(): String {
        return "狗叫得分 ${rms.format(2)}, 阈值 ${noiseFloor.format(2)}, 连续 $candidateBursts / $impulse"
    }
}

data class BarkDetectionEvent(
    val occurredAtMillis: Long,
    val burstCount: Int,
    val snapshot: AudioAnalysisSnapshot
)

data class AudioCapturedEvent(
    val occurredAtMillis: Long,
    val wavFilePath: String
)

interface BarkDetectionEngine {
    suspend fun run(
        onAnalysis: suspend (AudioAnalysisSnapshot) -> Unit,
        onBarkDetected: suspend (BarkDetectionEvent) -> Unit,
        onAudioCaptured: suspend (AudioCapturedEvent) -> Unit = {}
    )

    suspend fun shutdown()
}

internal fun Float.format(decimals: Int): String = "%1$,.${decimals}f".format(this)
