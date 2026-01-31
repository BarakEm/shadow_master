package com.shadowmaster.library

import android.util.Log
import com.shadowmaster.data.model.ShadowItem
import com.shadowmaster.data.model.ShadowingConfig
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

/**
 * Handles playlist-to-audio conversion logic.
 * Generates practice audio with beeps, repeats, and silence gaps.
 * Extracted from AudioExporter for single responsibility.
 */
@Singleton
class PlaylistExporter @Inject constructor() {

    companion object {
        private const val TAG = "PlaylistExporter"
        private const val SAMPLE_RATE = 16000
        private const val BYTES_PER_SAMPLE = 2 // 16-bit audio

        // Beep frequencies (Hz)
        private const val PLAYBACK_BEEP_FREQ = 880.0  // A5 - playback start
        private const val YOUR_TURN_BEEP_FREQ = 1047.0 // C6 - your turn
        private const val SEGMENT_END_BEEP_FREQ = 660.0 // E5 - segment done

        // Durations (ms)
        private const val BEEP_DURATION_MS = 150
        private const val DOUBLE_BEEP_GAP_MS = 100
        private const val PRE_SEGMENT_PAUSE_MS = 300
        private const val POST_SEGMENT_PAUSE_MS = 500
        private const val BETWEEN_REPEATS_PAUSE_MS = 300
    }

    /**
     * Write a single segment's audio with all beeps and pauses.
     *
     * @param output The output stream to write to
     * @param item The shadow item to export
     * @param config Shadowing configuration
     * @param includeYourTurnSilence Whether to include silence for user practice
     */
    fun writeSegmentAudio(
        output: OutputStream,
        item: ShadowItem,
        config: ShadowingConfig,
        includeYourTurnSilence: Boolean
    ) {
        val segmentAudio = loadSegmentAudio(item.audioFilePath) ?: return

        // For each playback repeat
        for (repeat in 1..config.playbackRepeats) {
            // Playback start beep (single tone)
            writeBeep(output, PLAYBACK_BEEP_FREQ, BEEP_DURATION_MS)
            writeSilence(output, PRE_SEGMENT_PAUSE_MS.toLong())

            // Play the segment
            output.write(segmentAudio)

            // Pause after playback
            writeSilence(output, BETWEEN_REPEATS_PAUSE_MS.toLong())
        }

        // "Your turn" section (if not bus mode and silence included)
        if (!config.busMode && includeYourTurnSilence) {
            for (userRepeat in 1..config.userRepeats) {
                // Double beep for "your turn"
                writeBeep(output, YOUR_TURN_BEEP_FREQ, BEEP_DURATION_MS)
                writeSilence(output, DOUBLE_BEEP_GAP_MS.toLong())
                writeBeep(output, YOUR_TURN_BEEP_FREQ, BEEP_DURATION_MS)
                writeSilence(output, PRE_SEGMENT_PAUSE_MS.toLong())

                // Silence for user to shadow (same duration as segment)
                writeSilence(output, item.durationMs)

                // Small buffer after shadowing
                writeSilence(output, BETWEEN_REPEATS_PAUSE_MS.toLong())
            }

            // Segment complete beep (descending tone)
            writeBeep(output, SEGMENT_END_BEEP_FREQ, BEEP_DURATION_MS)
        }
    }

    /**
     * Write silence between segments.
     */
    fun writePostSegmentPause(output: OutputStream) {
        writeSilence(output, POST_SEGMENT_PAUSE_MS.toLong())
    }

    /**
     * Load segment audio from file.
     */
    private fun loadSegmentAudio(filePath: String): ByteArray? {
        return try {
            val file = File(filePath)
            if (file.exists()) file.readBytes() else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load segment: $filePath", e)
            null
        }
    }

    /**
     * Generate and write a beep tone.
     */
    private fun writeBeep(output: OutputStream, frequency: Double, durationMs: Int) {
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

        output.write(samples)
    }

    /**
     * Write silence (zeros) for specified duration.
     */
    private fun writeSilence(output: OutputStream, durationMs: Long) {
        val numSamples = ((SAMPLE_RATE * durationMs) / 1000).toInt()
        val silence = ByteArray(numSamples * BYTES_PER_SAMPLE) // Initialized to zeros
        output.write(silence)
    }
}
