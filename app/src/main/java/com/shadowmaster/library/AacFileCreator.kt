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
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
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
     * Streams PCM from file to avoid loading entire file into memory.
     */
    private fun encodePcmToAac(pcmFile: File, outputFile: File) {
        val pcmSize = pcmFile.length()
        Log.d(TAG, "Creating AAC encoder for $pcmSize bytes of PCM data")

        if (pcmSize == 0L) {
            throw IOException("PCM file is empty, nothing to encode")
        }

        val format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, 1)
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        var outputStream: FileOutputStream? = null
        var inputStream: BufferedInputStream? = null

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            outputStream = FileOutputStream(outputFile)
            inputStream = BufferedInputStream(FileInputStream(pcmFile), 65536)
            var allInputSent = false
            var totalBytesRead = 0L
            var presentationTimeUs = 0L
            var noProgressCount = 0

            val bufferInfo = MediaCodec.BufferInfo()
            val readBuffer = ByteArray(16384)
            val maxNoProgressIterations = 500
            Log.d(TAG, "Encoder started, processing PCM data...")

            while (true) {
                var madeProgress = false

                // Feed input from file stream
                if (!allInputSent) {
                    val inputBufferId = codec.dequeueInputBuffer(1000)
                    if (inputBufferId >= 0) {
                        madeProgress = true
                        val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                        val maxRead = minOf(inputBuffer.remaining(), readBuffer.size)
                        val bytesRead = inputStream.read(readBuffer, 0, maxRead)

                        if (bytesRead > 0) {
                            inputBuffer.clear()
                            inputBuffer.put(readBuffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val isLast = totalBytesRead >= pcmSize
                            val flags = if (isLast) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            codec.queueInputBuffer(inputBufferId, 0, bytesRead, presentationTimeUs, flags)
                            presentationTimeUs += (bytesRead / 2) * 1_000_000L / SAMPLE_RATE

                            if (isLast) allInputSent = true
                            madeProgress = true
                        } else {
                            codec.queueInputBuffer(inputBufferId, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            allInputSent = true
                            madeProgress = true
                        }
                    }
                }

                // Drain encoded output
                val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 1000)
                when {
                    outputBufferId >= 0 -> {
                        madeProgress = true
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferId)!!
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)

                            val adtsHeader = createAdtsHeader(bufferInfo.size)
                            outputStream.write(adtsHeader)
                            outputStream.write(chunk)
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        madeProgress = true

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                    outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        madeProgress = true
                        Log.d(TAG, "Output format changed: ${codec.outputFormat}")
                        madeProgress = true
                    }
                }

                if (!madeProgress) {
                    noProgressCount++
                    if (noProgressCount >= maxNoProgressIterations) {
                        throw IOException("Encoder stalled: no progress after $maxNoProgressIterations iterations")
                    }
                } else {
                    noProgressCount = 0
                }
            }
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            Log.d(TAG, "Encoder stopped and released")
        }
    }

    /**
     * Create an ADTS header for a single AAC frame.
     * Without ADTS framing, raw AAC output from MediaCodec cannot be played by media players.
     */
    private fun createAdtsHeader(frameLength: Int): ByteArray {
        val packetLength = frameLength + 7 // ADTS header is 7 bytes (no CRC)
        val header = ByteArray(7)

        // Syncword 0xFFF, MPEG-4, Layer 0, no CRC
        header[0] = 0xFF.toByte()
        header[1] = 0xF9.toByte() // 1111 1001 = syncword(4) + ID=1(MPEG-4) + layer=00 + protection_absent=1

        // Profile: AAC-LC = 1 (2 bits), Sampling freq index: 16kHz = 8 (4 bits),
        // private_bit = 0 (1 bit), channel_config high bit = 0 (1 bit)
        val profile = 1 // AAC-LC (profile - 1 in ADTS)
        val freqIndex = 8 // 16000 Hz
        val channelConfig = 1 // mono
        header[2] = ((profile shl 6) or (freqIndex shl 2) or (channelConfig shr 2)).toByte()

        // channel_config low 2 bits (2 bits), originality=0, home=0, copyright_id=0, copyright_start=0,
        // frame_length high 2 bits
        header[3] = (((channelConfig and 0x3) shl 6) or (packetLength shr 11)).toByte()

        // frame_length middle 8 bits
        header[4] = ((packetLength shr 3) and 0xFF).toByte()

        // frame_length low 3 bits, buffer_fullness high 5 bits (0x7FF = VBR)
        header[5] = (((packetLength and 0x7) shl 5) or 0x1F).toByte()

        // buffer_fullness low 6 bits, number_of_raw_data_blocks = 0
        header[6] = 0xFC.toByte() // 111111 00

        return header
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
