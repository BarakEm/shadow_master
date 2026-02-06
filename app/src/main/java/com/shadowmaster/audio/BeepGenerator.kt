package com.shadowmaster.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates beep audio samples for playback through AudioTrack.
 * This ensures beeps are on the same audio stream as the media content,
 * making them audible during playback (unlike ToneGenerator which uses notification stream).
 */
object BeepGenerator {
    private const val SAMPLE_RATE = 16000
    private const val BYTES_PER_SAMPLE = 2 // 16-bit audio

    // Beep frequencies (Hz) - matching PlaylistExporter
    const val PLAYBACK_BEEP_FREQ = 880.0  // A5 - playback start
    const val YOUR_TURN_BEEP_FREQ = 1047.0 // C6 - your turn
    const val SEGMENT_END_BEEP_FREQ = 660.0 // E5 - segment done

    // Timing constants (ms)
    const val DOUBLE_BEEP_GAP_MS = 100L  // Gap between double beeps
    const val PRE_BEEP_PAUSE_MS = 300L   // Pause before playing segment after beep

    /**
     * Generate a beep tone as PCM audio data.
     *
     * @param frequency Frequency in Hz (e.g., 880.0 for A5)
     * @param durationMs Duration in milliseconds
     * @return PCM audio data (16-bit little-endian)
     */
    fun generateBeep(frequency: Double, durationMs: Int): ByteArray {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ByteArray(numSamples * BYTES_PER_SAMPLE)

        for (i in 0 until numSamples) {
            val angle = 2.0 * PI * i * frequency / SAMPLE_RATE
            // Apply envelope to avoid clicks (fade in/out)
            val envelope = when {
                i < numSamples / 10 -> i.toDouble() / (numSamples / 10)
                i > numSamples * 9 / 10 -> (numSamples - i).toDouble() / (numSamples / 10)
                else -> 1.0
            }
            val sample = (sin(angle) * 16000 * envelope).toInt().toShort()

            // Little-endian 16-bit
            samples[i * 2] = (sample.toInt() and 0xFF).toByte()
            samples[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return samples
    }

    /**
     * Generate silence as PCM audio data.
     *
     * @param durationMs Duration in milliseconds
     * @return PCM audio data filled with zeros
     */
    fun generateSilence(durationMs: Long): ByteArray {
        val numSamples = ((SAMPLE_RATE * durationMs) / 1000).toInt()
        return ByteArray(numSamples * BYTES_PER_SAMPLE) // Initialized to zeros
    }
}
