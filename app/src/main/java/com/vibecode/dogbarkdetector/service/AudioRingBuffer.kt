package com.vibecode.dogbarkdetector.service

class AudioRingBuffer(private val capacityInShorts: Int) {
    private val buffer = ShortArray(capacityInShorts)
    private var writePos = 0
    @Volatile private var filled = 0

    @Synchronized
    fun write(samples: ShortArray, offset: Int = 0, length: Int = samples.size) {
        for (i in 0 until length) {
            buffer[writePos] = samples[offset + i]
            writePos = (writePos + 1) % capacityInShorts
        }
        filled = (filled + length).coerceAtMost(capacityInShorts)
    }

    @Synchronized
    fun snapshot(): ShortArray {
        val len = filled
        val result = ShortArray(len)
        if (len == 0) return result
        val startPos = if (filled < capacityInShorts) 0 else writePos
        for (i in 0 until len) {
            result[i] = buffer[(startPos + i) % capacityInShorts]
        }
        return result
    }

    fun clear() {
        writePos = 0
        filled = 0
    }
}
