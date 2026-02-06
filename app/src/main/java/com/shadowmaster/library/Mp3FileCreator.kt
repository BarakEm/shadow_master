package com.shadowmaster.library

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles MP3 file creation and saving operations.
 * Uses Android's MediaCodec for AAC encoding (MP3 encoding not directly available).
 * Output files have .mp3 extension but contain AAC audio in ADTS container format.
 */
@Singleton
class Mp3FileCreator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "Mp3FileCreator"
        private const val SAMPLE_RATE = 16000
        private const val BIT_RATE = 64000 // 64 kbps for speech
        private const val MIME_TYPE = "audio/mp4a-latm" // AAC
    }

    /**
     * Convert raw PCM data to MP3 format and save to Music folder.
     *
     * @param pcmFile The temporary PCM file
     * @param name Base name for the output file
     * @return Result with path and URI of the saved MP3 file
     */
    fun saveAsMp3(pcmFile: File, name: String): AudioFileResult {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "ShadowMaster_${sanitizedName}_${System.currentTimeMillis()}.mp3"

        // Create temp file for encoded data
        val tempEncodedFile = File(context.cacheDir, "encoded_${System.currentTimeMillis()}.aac")
        
        try {
            // Encode PCM to AAC
            encodePcmToAac(pcmFile, tempEncodedFile)

            // Save to appropriate location
            val outputPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(fileName, tempEncodedFile)
            } else {
                saveToExternalStorage(fileName, tempEncodedFile)
            }

            // Clean up temp file
            tempEncodedFile.delete()

            return outputPath

        } catch (e: Exception) {
            tempEncodedFile.delete()
            throw e
        }
    }

    /**
     * Encode PCM data to AAC format using MediaCodec.
     */
    private fun encodePcmToAac(pcmFile: File, outputFile: File) {
        val format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)

        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val outputStream = FileOutputStream(outputFile)
        val pcmData = pcmFile.readBytes()
        var inputOffset = 0
        var allInputSent = false
        
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            while (true) {
                // Feed input if available
                if (!allInputSent) {
                    val inputBufferId = codec.dequeueInputBuffer(10000)
                    if (inputBufferId >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                        val chunkSize = minOf(inputBuffer.remaining(), pcmData.size - inputOffset)
                        
                        if (chunkSize > 0) {
                            inputBuffer.clear()
                            inputBuffer.put(pcmData, inputOffset, chunkSize)
                            codec.queueInputBuffer(inputBufferId, 0, chunkSize, 0, 0)
                            inputOffset += chunkSize
                        }
                        
                        if (inputOffset >= pcmData.size) {
                            codec.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            allInputSent = true
                        }
                    }
                }

                // Get encoded output
                val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputBufferId >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)!!
                        if (bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            outputStream.write(chunk)
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                    outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        Log.d(TAG, "Output format changed: ${codec.outputFormat}")
                    }
                    outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output available yet
                    }
                }
            }
        } finally {
            outputStream.close()
            codec.stop()
            codec.release()
        }
    }

    /**
     * Save using MediaStore API (Android 10+)
     */
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
            ?: throw IOException("Failed to create media store entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            sourceFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IOException("Failed to open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        return AudioFileResult("Music/ShadowMaster/$fileName", uri)
    }

    /**
     * Save to external storage (Android 9 and below)
     */
    @Suppress("DEPRECATION")
    private fun saveToExternalStorage(fileName: String, sourceFile: File): AudioFileResult {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "ShadowMaster"
        )
        musicDir.mkdirs()

        val outputFile = File(musicDir, fileName)
        sourceFile.copyTo(outputFile, overwrite = true)

        return AudioFileResult(outputFile.absolutePath, android.net.Uri.fromFile(outputFile))
    }
}
