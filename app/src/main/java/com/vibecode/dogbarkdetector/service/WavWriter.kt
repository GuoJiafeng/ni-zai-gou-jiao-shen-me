package com.vibecode.dogbarkdetector.service

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    fun write(file: File, samples: ShortArray, sampleRate: Int = 16000, channels: Int = 1) {
        file.parentFile?.mkdirs()
        val dataLength = samples.size * 2

        RandomAccessFile(file, "rw").use { raf ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataLength)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * 2)
            header.putShort((channels * 2).toShort())
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(dataLength)
            raf.write(header.array())

            val chunkSize = 8192
            val chunkBuf = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 0
            while (offset < samples.size) {
                chunkBuf.clear()
                val end = (offset + chunkSize / 2).coerceAtMost(samples.size)
                for (i in offset until end) {
                    chunkBuf.putShort(samples[i])
                }
                raf.write(chunkBuf.array(), 0, (end - offset) * 2)
                offset = end
            }
        }
    }
}
