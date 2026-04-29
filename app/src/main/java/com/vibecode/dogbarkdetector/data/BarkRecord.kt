package com.vibecode.dogbarkdetector.data

data class BarkRecord(
    val timestampMillis: Long,
    val confidence: Float,
    val label: String,
    val audioFilePath: String? = null
)
