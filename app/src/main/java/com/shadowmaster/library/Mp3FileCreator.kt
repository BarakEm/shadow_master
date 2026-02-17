package com.shadowmaster.library

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles MP3 file creation using the LAME encoder.
 * Converts raw 16kHz mono PCM to MP3 format.
 */
@Singleton
class Mp3FileCreator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Mp3FileCreator"
        private const val SAMPLE_RATE = 16000
        private const val BIT_RATE = 64 // 64 kbps for speech
        private const val PCM_BUFFER_SIZE = 8192 // samples per read (16384 bytes for 16-bit)
    }

    fun saveAsMp3(pcmFile: File, name: String): AudioFileResult {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "ShadowMaster_${sanitizedName}_${System.currentTimeMillis()}.mp3"

        val tempEncodedFile = File.createTempFile("encoded_", ".mp3", context.cacheDir)

        try {
            Log.d(TAG, "Starting MP3 encoding for: $name")
            encodePcmToMp3(pcmFile, tempEncodedFile)
            Log.d(TAG, "MP3 encoding completed, output size: ${tempEncodedFile.length()} bytes")

            val outputPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(fileName, tempEncodedFile)
            } else {
                saveToExternalStorage(fileName, tempEncodedFile)
            }

            tempEncodedFile.delete()
            Log.i(TAG, "MP3 file saved successfully: ${outputPath.path}")
            return outputPath

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save MP3 file: ${e.message}", e)
            tempEncodedFile.delete()
            throw e
        }
    }

    private fun encodePcmToMp3(pcmFile: File, outputFile: File) {
        val pcmSize = pcmFile.length()
        if (pcmSize == 0L) {
            throw IOException("PCM file is empty, nothing to encode")
        }

        val lame = LameBuilder()
            .setInSampleRate(SAMPLE_RATE)
            .setOutChannels(1)
            .setOutBitrate(BIT_RATE)
            .setOutSampleRate(SAMPLE_RATE)
            .setQuality(5) // 0=best, 9=fastest; 5 is good balance
            .build()

        var inputStream: BufferedInputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = BufferedInputStream(FileInputStream(pcmFile), 65536)
            outputStream = FileOutputStream(outputFile)

            val pcmBuffer = ShortArray(PCM_BUFFER_SIZE)
            val mp3Buffer = ByteArray((1.25 * PCM_BUFFER_SIZE + 7350).toInt())
            val readByteBuffer = ByteArray(PCM_BUFFER_SIZE * 2) // 2 bytes per 16-bit sample

            while (true) {
                val bytesRead = inputStream.read(readByteBuffer)
                if (bytesRead <= 0) break

                // Convert bytes to shorts (little-endian 16-bit PCM)
                val samplesRead = bytesRead / 2
                for (i in 0 until samplesRead) {
                    pcmBuffer[i] = ((readByteBuffer[i * 2 + 1].toInt() shl 8) or
                            (readByteBuffer[i * 2].toInt() and 0xFF)).toShort()
                }

                val encodedBytes = lame.encode(pcmBuffer, pcmBuffer, samplesRead, mp3Buffer)
                if (encodedBytes > 0) {
                    outputStream.write(mp3Buffer, 0, encodedBytes)
                }
            }

            // Flush remaining MP3 data
            val flushBytes = lame.flush(mp3Buffer)
            if (flushBytes > 0) {
                outputStream.write(mp3Buffer, 0, flushBytes)
            }

            lame.close()
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun saveWithMediaStore(fileName: String, sourceFile: File): AudioFileResult {
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ShadowMaster")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create media store entry for: $fileName")

        resolver.openOutputStream(uri)?.use { outputStream ->
            sourceFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IOException("Failed to open output stream for URI: $uri")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        val resultPath = "Music/ShadowMaster/$fileName"
        return AudioFileResult(resultPath, uri)
    }

    @Suppress("DEPRECATION")
    private fun saveToExternalStorage(fileName: String, sourceFile: File): AudioFileResult {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "ShadowMaster"
        )
        if (!musicDir.exists()) musicDir.mkdirs()

        val outputFile = File(musicDir, fileName)
        sourceFile.copyTo(outputFile, overwrite = true)

        return AudioFileResult(outputFile.absolutePath, android.net.Uri.fromFile(outputFile))
    }
}
