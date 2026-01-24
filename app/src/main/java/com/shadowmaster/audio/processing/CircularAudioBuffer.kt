package com.shadowmaster.audio.processing

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CircularAudioBuffer(
    private val maxDurationMs: Long = 30000,
    private val sampleRate: Int = 16000
) {
    private val maxSamples = ((maxDurationMs * sampleRate) / 1000).toInt()
    private val buffer = ShortArray(maxSamples)
    private var writeIndex = 0
    private var sampleCount = 0
    private val lock = ReentrantLock()

    fun write(samples: ShortArray) {
        lock.withLock {
            for (sample in samples) {
                buffer[writeIndex] = sample
                writeIndex = (writeIndex + 1) % maxSamples
                if (sampleCount < maxSamples) {
                    sampleCount++
                }
            }
        }
    }

    fun getLastNSamples(n: Int): ShortArray {
        lock.withLock {
            val actualCount = minOf(n, sampleCount)
            if (actualCount == 0) return ShortArray(0)

            val result = ShortArray(actualCount)
            var readIndex = (writeIndex - actualCount + maxSamples) % maxSamples

            for (i in 0 until actualCount) {
                result[i] = buffer[readIndex]
                readIndex = (readIndex + 1) % maxSamples
            }

            return result
        }
    }

    fun getLastNMillis(durationMs: Long): ShortArray {
        val sampleCount = ((durationMs * sampleRate) / 1000).toInt()
        return getLastNSamples(sampleCount)
    }

    fun clear() {
        lock.withLock {
            writeIndex = 0
            sampleCount = 0
        }
    }

    fun getSampleCount(): Int = lock.withLock { sampleCount }

    fun getDurationMs(): Long = lock.withLock {
        (sampleCount * 1000L) / sampleRate
    }
}
