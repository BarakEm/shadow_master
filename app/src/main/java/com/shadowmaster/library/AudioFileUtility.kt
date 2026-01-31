package com.shadowmaster.library

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for common audio file operations.
 * Provides reusable functions for WAV header creation, format detection,
 * sample rate conversion, channel conversion, and PCM buffer operations.
 */
@Singleton
class AudioFileUtility @Inject constructor() {

    companion object {
        private const val TAG = "AudioFileUtility"
    }

    /**
     * Detect audio format from file name.
     * @param uri The URI of the audio file
     * @return Format string (e.g., "mp3", "wav", "m4a", "unknown")
     */
    fun detectFormat(uri: Uri, context: Context): String {
        val fileName = getFileName(uri, context) ?: ""
        return when {
            fileName.endsWith(".mp3", ignoreCase = true) -> "mp3"
            fileName.endsWith(".wav", ignoreCase = true) -> "wav"
            fileName.endsWith(".m4a", ignoreCase = true) -> "m4a"
            fileName.endsWith(".aac", ignoreCase = true) -> "aac"
            fileName.endsWith(".ogg", ignoreCase = true) -> "ogg"
            fileName.endsWith(".flac", ignoreCase = true) -> "flac"
            else -> "unknown"
        }
    }

    /**
     * Get file name from URI.
     * @param uri The URI of the file
     * @param context Android context for content resolver
     * @return File name or null if not found
     */
    fun getFileName(uri: Uri, context: Context): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    /**
     * Convert stereo/multi-channel PCM to mono by averaging channels.
     * @param pcmData 16-bit PCM audio data (little-endian)
     * @param channels Number of channels (1 = mono, 2 = stereo, etc.)
     * @return Mono PCM data (16-bit, little-endian)
     */
    fun convertToMono(pcmData: ByteArray, channels: Int): ByteArray {
        if (channels == 1) return pcmData

        val samplesPerChannel = pcmData.size / (channels * 2) // 16-bit samples
        val monoData = ByteArray(samplesPerChannel * 2)

        for (i in 0 until samplesPerChannel) {
            var sum = 0
            for (ch in 0 until channels) {
                val offset = (i * channels + ch) * 2
                val sample = (pcmData[offset + 1].toInt() shl 8) or (pcmData[offset].toInt() and 0xFF)
                sum += sample
            }
            val monoSample = (sum / channels).toShort()
            monoData[i * 2] = (monoSample.toInt() and 0xFF).toByte()
            monoData[i * 2 + 1] = ((monoSample.toInt() shr 8) and 0xFF).toByte()
        }

        return monoData
    }

    /**
     * Simple linear resampling for rate conversion.
     * @param pcmData 16-bit PCM audio data (little-endian)
     * @param fromRate Original sample rate (Hz)
     * @param toRate Target sample rate (Hz)
     * @return Resampled PCM data (16-bit, little-endian)
     */
    fun resample(pcmData: ByteArray, fromRate: Int, toRate: Int): ByteArray {
        if (fromRate == toRate) return pcmData

        val inputSamples = pcmData.size / 2
        val outputSamples = (inputSamples.toLong() * toRate / fromRate).toInt()
        val outputData = ByteArray(outputSamples * 2)

        val ratio = fromRate.toDouble() / toRate

        for (i in 0 until outputSamples) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt().coerceIn(0, inputSamples - 2)
            val frac = srcPos - srcIndex

            // Get two adjacent samples for interpolation
            val s1 = (pcmData[srcIndex * 2 + 1].toInt() shl 8) or (pcmData[srcIndex * 2].toInt() and 0xFF)
            val s2 = (pcmData[(srcIndex + 1) * 2 + 1].toInt() shl 8) or (pcmData[(srcIndex + 1) * 2].toInt() and 0xFF)

            // Linear interpolation
            val sample = (s1 + (s2 - s1) * frac).toInt().toShort()
            outputData[i * 2] = (sample.toInt() and 0xFF).toByte()
            outputData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return outputData
    }

    /**
     * Create WAV file header for PCM audio data.
     * @param pcmDataSize Size of PCM data in bytes
     * @param sampleRate Sample rate in Hz (default: 16000)
     * @param channels Number of channels (default: 1 = mono)
     * @param bitsPerSample Bits per sample (default: 16)
     * @return WAV header bytes (44 bytes)
     */
    fun createWavHeader(
        pcmDataSize: Int,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val totalDataLen = pcmDataSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        return ByteArray(44).apply {
            // RIFF header
            this[0] = 'R'.code.toByte()
            this[1] = 'I'.code.toByte()
            this[2] = 'F'.code.toByte()
            this[3] = 'F'.code.toByte()

            // File size - 8
            this[4] = (totalDataLen and 0xff).toByte()
            this[5] = ((totalDataLen shr 8) and 0xff).toByte()
            this[6] = ((totalDataLen shr 16) and 0xff).toByte()
            this[7] = ((totalDataLen shr 24) and 0xff).toByte()

            // WAVE
            this[8] = 'W'.code.toByte()
            this[9] = 'A'.code.toByte()
            this[10] = 'V'.code.toByte()
            this[11] = 'E'.code.toByte()

            // fmt chunk
            this[12] = 'f'.code.toByte()
            this[13] = 'm'.code.toByte()
            this[14] = 't'.code.toByte()
            this[15] = ' '.code.toByte()

            // Subchunk1Size (16 for PCM)
            this[16] = 16
            this[17] = 0
            this[18] = 0
            this[19] = 0

            // AudioFormat (1 = PCM)
            this[20] = 1
            this[21] = 0

            // NumChannels
            this[22] = (channels and 0xff).toByte()
            this[23] = ((channels shr 8) and 0xff).toByte()

            // SampleRate
            this[24] = (sampleRate and 0xff).toByte()
            this[25] = ((sampleRate shr 8) and 0xff).toByte()
            this[26] = ((sampleRate shr 16) and 0xff).toByte()
            this[27] = ((sampleRate shr 24) and 0xff).toByte()

            // ByteRate
            this[28] = (byteRate and 0xff).toByte()
            this[29] = ((byteRate shr 8) and 0xff).toByte()
            this[30] = ((byteRate shr 16) and 0xff).toByte()
            this[31] = ((byteRate shr 24) and 0xff).toByte()

            // BlockAlign
            val blockAlign = channels * bitsPerSample / 8
            this[32] = (blockAlign and 0xff).toByte()
            this[33] = ((blockAlign shr 8) and 0xff).toByte()

            // BitsPerSample
            this[34] = (bitsPerSample and 0xff).toByte()
            this[35] = ((bitsPerSample shr 8) and 0xff).toByte()

            // data chunk
            this[36] = 'd'.code.toByte()
            this[37] = 'a'.code.toByte()
            this[38] = 't'.code.toByte()
            this[39] = 'a'.code.toByte()

            // Subchunk2Size
            this[40] = (pcmDataSize and 0xff).toByte()
            this[41] = ((pcmDataSize shr 8) and 0xff).toByte()
            this[42] = ((pcmDataSize shr 16) and 0xff).toByte()
            this[43] = ((pcmDataSize shr 24) and 0xff).toByte()
        }
    }

    /**
     * Extract a segment from a PCM file and save it to a new file.
     * @param pcmFile Source PCM file
     * @param startMs Start time in milliseconds
     * @param endMs End time in milliseconds
     * @param sampleRate Sample rate of the PCM data (default: 16000 Hz)
     * @param outputDir Directory to save the extracted segment
     * @return Extracted segment file or null on error
     */
    fun extractSegment(
        pcmFile: File,
        startMs: Long,
        endMs: Long,
        sampleRate: Int = 16000,
        outputDir: File
    ): File? {
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val segmentFile = File(outputDir, "${UUID.randomUUID()}.pcm")
            val bytesPerMs = (sampleRate * 2) / 1000 // 16-bit mono

            val startByte = startMs * bytesPerMs
            val endByte = endMs * bytesPerMs
            val length = (endByte - startByte).toInt()

            RandomAccessFile(pcmFile, "r").use { input ->
                input.seek(startByte)
                val buffer = ByteArray(length)
                val read = input.read(buffer)

                if (read > 0) {
                    FileOutputStream(segmentFile).use { output ->
                        output.write(buffer, 0, read)
                    }
                    return segmentFile
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract segment", e)
        }
        return null
    }

    /**
     * Read 16-bit PCM samples from a file in frame-sized chunks.
     * @param pcmFile Source PCM file
     * @param frameSize Number of samples per frame
     * @param onFrame Callback invoked for each frame (receives ShortArray)
     */
    fun readPcmFrames(pcmFile: File, frameSize: Int, onFrame: (ShortArray) -> Unit) {
        DataInputStream(BufferedInputStream(FileInputStream(pcmFile))).use { inputStream ->
            val frameBuffer = ShortArray(frameSize)
            val byteBuffer = ByteArray(frameSize * 2)

            while (inputStream.available() >= byteBuffer.size) {
                inputStream.readFully(byteBuffer)

                // Convert bytes to shorts (little-endian)
                for (i in 0 until frameSize) {
                    frameBuffer[i] = ((byteBuffer[i * 2 + 1].toInt() shl 8) or
                            (byteBuffer[i * 2].toInt() and 0xFF)).toShort()
                }

                onFrame(frameBuffer)
            }
        }
    }

    /**
     * Calculate duration of PCM audio data.
     * @param pcmDataSize Size of PCM data in bytes
     * @param sampleRate Sample rate in Hz
     * @param channels Number of channels
     * @return Duration in milliseconds
     */
    fun calculateDurationMs(pcmDataSize: Long, sampleRate: Int = 16000, channels: Int = 1): Long {
        val bytesPerMs = (sampleRate * channels * 2) / 1000 // 16-bit per channel
        return pcmDataSize / bytesPerMs
    }
}
