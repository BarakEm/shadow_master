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
 * Handles WAV file creation and saving operations.
 * Extracted from AudioExporter for single responsibility.
 */
@Singleton
class WavFileCreator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioFileUtility: AudioFileUtility
) {
    companion object {
        private const val SAMPLE_RATE = 16000
    }

    /**
     * Convert raw PCM data to WAV format and save to Music folder.
     *
     * @param pcmFile The temporary PCM file
     * @param name Base name for the output file
     * @return Path to the saved WAV file
     */
    fun saveAsWav(pcmFile: File, name: String): String {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "ShadowMaster_${sanitizedName}_${System.currentTimeMillis()}.wav"

        val pcmData = pcmFile.readBytes()
        val wavData = audioFileUtility.createWavHeader(pcmData.size) + pcmData

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(fileName, wavData)
        } else {
            saveToExternalStorage(fileName, wavData)
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
