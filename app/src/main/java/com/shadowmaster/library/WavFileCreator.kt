package com.shadowmaster.library

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates WAV files from PCM audio data and saves them to device storage.
 * Handles both MediaStore API (Android 10+) and legacy external storage.
 */
@Singleton
class WavFileCreator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val BYTES_PER_SAMPLE = 2 // 16-bit audio
    }

    /**
     * Convert raw PCM file to WAV and save to Music folder.
     *
     * @param pcmFile The raw PCM audio file
     * @param name The name for the output file (will be sanitized)
     * @return The path/location string of the saved file
     */
    fun saveAsWav(pcmFile: File, name: String): String {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "ShadowMaster_${sanitizedName}_${System.currentTimeMillis()}.wav"

        val pcmData = pcmFile.readBytes()
        val wavData = createWavHeader(pcmData.size) + pcmData

        // Save to Music folder using MediaStore (works on all Android versions)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(fileName, wavData)
        } else {
            saveToExternalStorage(fileName, wavData)
        }
    }

    /**
     * Create WAV file header for 16kHz mono PCM audio.
     *
     * @param pcmDataSize The size of the PCM data in bytes
     * @return A 44-byte WAV header as ByteArray
     */
    private fun createWavHeader(pcmDataSize: Int): ByteArray {
        val totalDataLen = pcmDataSize + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8 // mono, 16-bit

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

            // NumChannels (1 = mono)
            this[22] = 1
            this[23] = 0

            // SampleRate
            this[24] = (SAMPLE_RATE and 0xff).toByte()
            this[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
            this[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
            this[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()

            // ByteRate
            this[28] = (byteRate and 0xff).toByte()
            this[29] = ((byteRate shr 8) and 0xff).toByte()
            this[30] = ((byteRate shr 16) and 0xff).toByte()
            this[31] = ((byteRate shr 24) and 0xff).toByte()

            // BlockAlign
            this[32] = 2 // mono 16-bit
            this[33] = 0

            // BitsPerSample
            this[34] = 16
            this[35] = 0

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
     * Save using MediaStore API (Android 10+)
     */
    private fun saveWithMediaStore(fileName: String, data: ByteArray): String {
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ShadowMaster")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create media store entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(data)
        } ?: throw IOException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        return "Music/ShadowMaster/$fileName"
    }

    /**
     * Save to external storage (Android 9 and below)
     */
    @Suppress("DEPRECATION")
    private fun saveToExternalStorage(fileName: String, data: ByteArray): String {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "ShadowMaster"
        )
        musicDir.mkdirs()

        val outputFile = File(musicDir, fileName)
        FileOutputStream(outputFile).use { it.write(data) }

        return outputFile.absolutePath
    }
}
