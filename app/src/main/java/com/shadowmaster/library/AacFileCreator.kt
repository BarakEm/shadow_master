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
 * Handles AAC file creation and saving operations.
 * Uses Android's MediaCodec for AAC encoding.
 * Output files have .aac extension and contain AAC audio in ADTS container format.
 */
@Singleton
class AacFileCreator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AacFileCreator"
        private const val SAMPLE_RATE = 16000
        private const val BIT_RATE = 64000 // 64 kbps for speech
        private const val MIME_TYPE = "audio/mp4a-latm" // AAC
    }

    /**
     * Convert raw PCM data to AAC format and save to Music folder.
     *
     * @param pcmFile The temporary PCM file
     * @param name Base name for the output file
     * @return Result with path and URI of the saved AAC file
     */
    fun saveAsAac(pcmFile: File, name: String): AudioFileResult {
        val sanitizedName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "ShadowMaster_${sanitizedName}_${System.currentTimeMillis()}.aac"

        // Create temp file for encoded data
        val tempEncodedFile = File.createTempFile("encoded_", ".aac", context.cacheDir)

        try {
            Log.d(TAG, "Starting AAC encoding for: $name")

            // Encode PCM to AAC
            encodePcmToAac(pcmFile, tempEncodedFile)
            Log.d(TAG, "AAC encoding completed, output size: ${tempEncodedFile.length()} bytes")

            // Save to appropriate location
            val outputPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Saving with MediaStore (Android 10+)")
                saveWithMediaStore(fileName, tempEncodedFile)
            } else {
                Log.d(TAG, "Saving to external storage (Android 9 and below)")
                saveToExternalStorage(fileName, tempEncodedFile)
            }

            // Clean up temp file
            tempEncodedFile.delete()
            Log.i(TAG, "AAC file saved successfully: ${outputPath.path}")

            return outputPath

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save AAC file: ${e.message}", e)
            tempEncodedFile.delete()
            throw e
        }
    }

    /**
     * Encode PCM data to AAC format using MediaCodec.
     */
    private fun encodePcmToAac(pcmFile: File, outputFile: File) {
        Log.d(TAG, "Creating AAC encoder for ${pcmFile.length()} bytes of PCM data")

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
        Log.d(TAG, "Encoder started, processing PCM data...")

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
                            inputOffset += chunkSize

                            // Send EOS flag with the last chunk of data
                            val isLastChunk = inputOffset >= pcmData.size
                            val flags = if (isLastChunk) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            codec.queueInputBuffer(inputBufferId, 0, chunkSize, 0, flags)

                            if (isLastChunk) {
                                allInputSent = true
                            }
                        } else if (inputOffset >= pcmData.size) {
                            // No more data to send, send EOS with empty buffer
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
            Log.d(TAG, "Encoder stopped and released")
        }
    }

    /**
     * Save using MediaStore API (Android 10+)
     */
    private fun saveWithMediaStore(fileName: String, sourceFile: File): AudioFileResult {
        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/aac")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ShadowMaster")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IOException("Failed to create media store entry for: $fileName")

        Log.d(TAG, "MediaStore entry created with URI: $uri")

        resolver.openOutputStream(uri)?.use { outputStream ->
            sourceFile.inputStream().use { inputStream ->
                val bytesWritten = inputStream.copyTo(outputStream)
                Log.d(TAG, "Wrote $bytesWritten bytes to MediaStore")
            }
        } ?: throw IOException("Failed to open output stream for URI: $uri")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            val updated = resolver.update(uri, contentValues, null, null)
            Log.d(TAG, "Updated IS_PENDING flag, rows affected: $updated")
        }

        val resultPath = "Music/ShadowMaster/$fileName"
        Log.i(TAG, "File saved to: $resultPath")
        return AudioFileResult(resultPath, uri)
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

        if (!musicDir.exists()) {
            val created = musicDir.mkdirs()
            Log.d(TAG, "Creating directory: ${musicDir.absolutePath}, success: $created")
        }

        val outputFile = File(musicDir, fileName)
        Log.d(TAG, "Saving to file: ${outputFile.absolutePath}")

        sourceFile.copyTo(outputFile, overwrite = true)
        Log.i(TAG, "File saved successfully: ${outputFile.absolutePath}")

        return AudioFileResult(outputFile.absolutePath, android.net.Uri.fromFile(outputFile))
    }
}
