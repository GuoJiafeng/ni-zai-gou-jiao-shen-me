package com.vibecode.dogbarkdetector.service

import android.content.Context
import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.audio.classifier.AudioClassifier.AudioClassifierOptions
import org.tensorflow.lite.task.core.BaseOptions
import java.io.File
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class YamNetBarkDetectionEngine(
    private val context: Context,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    private val requiredConsecutiveHits: Int = DEFAULT_CONSECUTIVE_HITS,
    private val detectionWindowMs: Long = DEFAULT_DETECTION_WINDOW_MS
) : BarkDetectionEngine {

    private var classifier: AudioClassifier? = null
    private var tensorAudio: TensorAudio? = null
    private var recorder: AudioRecord? = null
    private var executor: ScheduledThreadPoolExecutor? = null
    private val running = AtomicBoolean(false)

    private val audioBuffer = AudioRingBuffer(16000 * 15)

    @Volatile private var consecutiveHits = 0
    @Volatile private var lastHitTimeMs = 0L
    @Volatile private var lastDetectionTimeMs = 0L
    @Volatile private var lastDogScore = 0f
    @Volatile private var lastDogLabel = ""

    private var analysisCallback: (suspend (AudioAnalysisSnapshot) -> Unit)? = null
    private var detectionCallback: (suspend (BarkDetectionEvent) -> Unit)? = null
    private var audioCapturedCallback: (suspend (AudioCapturedEvent) -> Unit)? = null

    override suspend fun run(
        onAnalysis: suspend (AudioAnalysisSnapshot) -> Unit,
        onBarkDetected: suspend (BarkDetectionEvent) -> Unit,
        onAudioCaptured: suspend (AudioCapturedEvent) -> Unit
    ) = withContext(Dispatchers.Default) {
        analysisCallback = onAnalysis
        detectionCallback = onBarkDetected
        audioCapturedCallback = onAudioCaptured

        val baseOptions = BaseOptions.builder()
            .setNumThreads(2)
            .build()

        val options = AudioClassifierOptions.builder()
            .setScoreThreshold(0.1f)
            .setMaxResults(10)
            .setBaseOptions(baseOptions)
            .build()

        classifier = AudioClassifier.createFromFileAndOptions(context, MODEL_FILE, options)
        val cls = classifier!!

        tensorAudio = cls.createInputTensorAudio()
        recorder = cls.createAudioRecord()
        val rec = recorder!!

        rec.startRecording()
        running.set(true)
        audioBuffer.clear()

        val lengthInMilliSeconds = ((cls.requiredInputBufferSize * 1.0f) /
                cls.requiredTensorAudioFormat.sampleRate) * 1000
        val interval = (lengthInMilliSeconds * 0.5f).toLong()

        executor = ScheduledThreadPoolExecutor(2)
        executor!!.scheduleAtFixedRate(
            { classifyFrame() },
            0,
            interval,
            TimeUnit.MILLISECONDS
        )

        try {
            while (currentCoroutineContext().isActive) {
                currentCoroutineContext().ensureActive()
                Thread.sleep(200)

                if (running.get()) {
                    val snapshot = buildSnapshot()
                    kotlinx.coroutines.runBlocking {
                        analysisCallback?.invoke(snapshot)
                    }
                }
            }
        } finally {
            running.set(false)
            stopInternal()
        }
    }

    private fun classifyFrame() {
        if (!running.get()) return

        val cls = classifier ?: return
        val ta = tensorAudio ?: return

        try {
            ta.load(recorder!!)
            captureAudioToBuffer(ta)

            val output = cls.classify(ta)

            val categories = output.getOrNull(0)?.categories ?: return

            var bestScore = 0f
            var bestLabel = ""
            for (cat in categories) {
                val label = cat.label
                val score = cat.score
                if (isDogRelatedLabel(label) && score > bestScore) {
                    bestScore = score
                    bestLabel = label
                }
            }

            lastDogScore = bestScore
            lastDogLabel = bestLabel

            val now = SystemClock.elapsedRealtime()

            if (bestScore >= confidenceThreshold) {
                if (now - lastHitTimeMs > detectionWindowMs) {
                    consecutiveHits = 1
                } else {
                    consecutiveHits++
                }
                lastHitTimeMs = now

                if (consecutiveHits >= requiredConsecutiveHits &&
                    now - lastDetectionTimeMs >= DETECTION_COOLDOWN_MS
                ) {
                    lastDetectionTimeMs = now
                    consecutiveHits = 0

                    val detectionTimestamp = System.currentTimeMillis()
                    val event = BarkDetectionEvent(
                        occurredAtMillis = detectionTimestamp,
                        burstCount = requiredConsecutiveHits,
                        snapshot = buildSnapshot()
                    )

                    kotlinx.coroutines.runBlocking {
                        detectionCallback?.invoke(event)
                    }

                    scheduleAudioExport(detectionTimestamp)
                }
            } else {
                if (now - lastHitTimeMs > detectionWindowMs) {
                    consecutiveHits = 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classification error: ${e.message}")
        }
    }

    private fun captureAudioToBuffer(ta: TensorAudio) {
        try {
            val floatArray = ta.tensorBuffer.floatArray
            val shorts = ShortArray(floatArray.size) { i ->
                (floatArray[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }
            audioBuffer.write(shorts)
        } catch (_: Exception) {
        }
    }

    private fun scheduleAudioExport(detectionTimestamp: Long) {
        executor?.schedule({
            if (!running.get()) return@schedule
            try {
                val audioData = audioBuffer.snapshot()
                val file = File(context.filesDir, "audio/bark_${detectionTimestamp}.wav")
                WavWriter.write(file, audioData, 16000)
                Log.d(TAG, "音频已导出: ${file.absolutePath} (${audioData.size} samples)")

                kotlinx.coroutines.runBlocking {
                    audioCapturedCallback?.invoke(
                        AudioCapturedEvent(detectionTimestamp, file.absolutePath)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "音频导出失败: ${e.message}")
            }
        }, 5, TimeUnit.SECONDS)
    }

    private fun buildSnapshot(): AudioAnalysisSnapshot {
        return AudioAnalysisSnapshot(
            rms = lastDogScore,
            peak = if (lastDogScore > 0) lastDogScore else 0f,
            zeroCrossingRate = 0f,
            impulse = consecutiveHits.toFloat(),
            noiseFloor = confidenceThreshold,
            candidateBursts = consecutiveHits
        )
    }

    override suspend fun shutdown() {
        running.set(false)
        stopInternal()
    }

    private fun stopInternal() {
        try {
            executor?.shutdownNow()
        } catch (_: Exception) {
        }
        executor = null

        try {
            recorder?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {
        }
        recorder = null

        try {
            classifier?.close()
        } catch (_: Exception) {
        }
        classifier = null
        tensorAudio = null
    }

    companion object {
        private const val TAG = "YamNetBarkDetector"
        private const val MODEL_FILE = "yamnet.tflite"
        private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.25f
        private const val DEFAULT_CONSECUTIVE_HITS = 1
        private const val DEFAULT_DETECTION_WINDOW_MS = 3000L
        private const val DETECTION_COOLDOWN_MS = 3000L

        private val DOG_LABELS = setOf(
            "Dog", "Bark", "Dog bark", "Barking", "Yip",
            "Howl", "Growling", "Whimper", "Bow-wow", "Yapping"
        )

        fun isDogRelatedLabel(label: String): Boolean {
            val lower = label.lowercase()
            return DOG_LABELS.any { dogLabel ->
                lower.contains(dogLabel.lowercase())
            } || lower.contains("dog") || lower.contains("bark")
        }
    }
}
