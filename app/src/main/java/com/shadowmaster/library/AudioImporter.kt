package com.shadowmaster.library

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.shadowmaster.audio.vad.SileroVadDetector
import com.shadowmaster.data.local.ImportJobDao
import com.shadowmaster.data.local.ShadowItemDao
import com.shadowmaster.data.local.ShadowPlaylistDao
import com.shadowmaster.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports audio files, segments them by speech boundaries, and stores in library.
 */
@Singleton
class AudioImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowItemDao: ShadowItemDao,
    private val shadowPlaylistDao: ShadowPlaylistDao,
    private val importJobDao: ImportJobDao,
    private val vadDetector: SileroVadDetector
) {
    companion object {
        private const val TAG = "AudioImporter"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val MIN_SEGMENT_DURATION_MS = 500L
        private const val MAX_SEGMENT_DURATION_MS = 15000L
        private const val SILENCE_THRESHOLD_MS = 700L
        private const val PRE_SPEECH_BUFFER_MS = 200L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val segmentsDir: File by lazy {
        File(context.filesDir, "shadow_segments").also { it.mkdirs() }
    }

    /**
     * Import an audio file and create a playlist with segmented items.
     */
    suspend fun importAudioFile(
        uri: Uri,
        playlistName: String? = null,
        language: String = "auto",
        enableTranscription: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get file metadata
            val fileName = getFileName(uri) ?: "Unknown"
            val displayName = playlistName ?: fileName.substringBeforeLast(".")

            // Create import job for tracking
            val jobId = UUID.randomUUID().toString()
            val job = ImportJob(
                id = jobId,
                sourceUri = uri.toString(),
                fileName = fileName,
                status = ImportStatus.PENDING,
                language = language,
                enableTranscription = enableTranscription
            )
            importJobDao.insert(job)

            // Create playlist
            val playlistId = UUID.randomUUID().toString()
            val playlist = ShadowPlaylist(
                id = playlistId,
                name = displayName,
                language = language,
                sourceType = SourceType.IMPORTED,
                sourceUri = uri.toString()
            )
            shadowPlaylistDao.insert(playlist)

            // Update job with playlist ID
            importJobDao.update(job.copy(targetPlaylistId = playlistId))

            // Start processing in background
            scope.launch {
                processAudioFile(jobId, uri, playlistId, language, enableTranscription)
            }

            Result.success(playlistId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start import", e)
            Result.failure(e)
        }
    }

    private suspend fun processAudioFile(
        jobId: String,
        uri: Uri,
        playlistId: String,
        language: String,
        enableTranscription: Boolean
    ) {
        try {
            // Update status
            importJobDao.updateProgress(jobId, ImportStatus.EXTRACTING_AUDIO, 10)

            // Extract audio to PCM
            val pcmFile = extractAudioToPcm(uri)
            if (pcmFile == null) {
                importJobDao.markFailed(jobId, "Failed to extract audio")
                return
            }

            // Update status
            importJobDao.updateProgress(jobId, ImportStatus.DETECTING_SEGMENTS, 30)

            // Initialize VAD
            if (!vadDetector.initialize()) {
                importJobDao.markFailed(jobId, "Failed to initialize voice detection")
                return
            }

            // Detect speech segments
            val segments = detectSpeechSegments(pcmFile)
            Log.i(TAG, "Detected ${segments.size} speech segments")

            if (segments.isEmpty()) {
                importJobDao.markFailed(jobId, "No speech segments detected in audio")
                pcmFile.delete()
                return
            }

            // Update status
            importJobDao.updateProgress(jobId, ImportStatus.DETECTING_SEGMENTS, 60)

            // Extract and save each segment
            val shadowItems = mutableListOf<ShadowItem>()
            val fileName = getFileName(uri) ?: "audio"

            segments.forEachIndexed { index, segment ->
                val segmentFile = extractSegment(pcmFile, segment)
                if (segmentFile != null) {
                    val item = ShadowItem(
                        sourceFileUri = uri.toString(),
                        sourceFileName = fileName,
                        sourceStartMs = segment.startMs,
                        sourceEndMs = segment.endMs,
                        audioFilePath = segmentFile.absolutePath,
                        durationMs = segment.endMs - segment.startMs,
                        language = language,
                        playlistId = playlistId,
                        orderInPlaylist = index
                    )
                    shadowItems.add(item)
                }

                // Update progress
                val progress = 60 + (index * 30 / segments.size)
                importJobDao.updateProgress(jobId, ImportStatus.DETECTING_SEGMENTS, progress)
            }

            // Save all items
            shadowItemDao.insertAll(shadowItems)

            // Clean up temp PCM file
            pcmFile.delete()

            // Mark complete
            importJobDao.markCompleted(jobId, shadowItems.size)
            Log.i(TAG, "Import complete: ${shadowItems.size} segments created")

        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            importJobDao.markFailed(jobId, e.message ?: "Unknown error")
        }
    }

    /**
     * Extract audio from any format to mono 16kHz PCM.
     */
    private fun extractAudioToPcm(uri: Uri): File? {
        val tempFile = File(context.cacheDir, "temp_pcm_${System.currentTimeMillis()}.pcm")

        try {
            val extractor = MediaExtractor()
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: return null

            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex < 0) {
                Log.e(TAG, "No audio track found")
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            Log.i(TAG, "Audio: ${sampleRate}Hz, $channels channels")

            // For now, we'll use a simple approach - just extract raw samples
            // In production, you'd use MediaCodec to decode properly
            // This is simplified for the initial implementation

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val outputStream = FileOutputStream(tempFile)

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val bytes = ByteArray(sampleSize)
                buffer.get(bytes, 0, sampleSize)
                outputStream.write(bytes)
                buffer.clear()

                extractor.advance()
            }

            outputStream.close()
            extractor.release()

            Log.i(TAG, "Extracted ${tempFile.length()} bytes of audio")
            return tempFile

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract audio", e)
            tempFile.delete()
            return null
        }
    }

    /**
     * Detect speech segments using VAD.
     */
    private fun detectSpeechSegments(pcmFile: File): List<AudioSegmentBounds> {
        val segments = mutableListOf<AudioSegmentBounds>()
        val frameSize = SileroVadDetector.FRAME_SIZE_SAMPLES
        val frameDurationMs = (frameSize * 1000L) / TARGET_SAMPLE_RATE

        var isSpeaking = false
        var speechStartMs = 0L
        var lastSpeechMs = 0L
        var currentMs = 0L

        try {
            val inputStream = DataInputStream(BufferedInputStream(FileInputStream(pcmFile)))
            val frameBuffer = ShortArray(frameSize)
            val byteBuffer = ByteArray(frameSize * 2)

            while (inputStream.available() >= byteBuffer.size) {
                inputStream.readFully(byteBuffer)

                // Convert bytes to shorts (little-endian)
                for (i in 0 until frameSize) {
                    frameBuffer[i] = ((byteBuffer[i * 2 + 1].toInt() shl 8) or
                            (byteBuffer[i * 2].toInt() and 0xFF)).toShort()
                }

                val hasSpeech = vadDetector.isSpeech(frameBuffer)

                if (hasSpeech) {
                    if (!isSpeaking) {
                        isSpeaking = true
                        speechStartMs = maxOf(0, currentMs - PRE_SPEECH_BUFFER_MS)
                    }
                    lastSpeechMs = currentMs
                } else if (isSpeaking) {
                    val silenceDuration = currentMs - lastSpeechMs

                    if (silenceDuration >= SILENCE_THRESHOLD_MS) {
                        val segmentDuration = lastSpeechMs - speechStartMs + SILENCE_THRESHOLD_MS

                        if (segmentDuration >= MIN_SEGMENT_DURATION_MS) {
                            segments.add(AudioSegmentBounds(
                                startMs = speechStartMs,
                                endMs = minOf(lastSpeechMs + SILENCE_THRESHOLD_MS, currentMs)
                            ))
                        }
                        isSpeaking = false
                    }
                }

                // Force segment break if too long
                if (isSpeaking && (currentMs - speechStartMs) >= MAX_SEGMENT_DURATION_MS) {
                    segments.add(AudioSegmentBounds(
                        startMs = speechStartMs,
                        endMs = currentMs
                    ))
                    isSpeaking = false
                }

                currentMs += frameDurationMs
            }

            // Handle final segment
            if (isSpeaking && (lastSpeechMs - speechStartMs) >= MIN_SEGMENT_DURATION_MS) {
                segments.add(AudioSegmentBounds(
                    startMs = speechStartMs,
                    endMs = lastSpeechMs + 100
                ))
            }

            inputStream.close()

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting segments", e)
        }

        return segments
    }

    /**
     * Extract a segment from the PCM file and save it.
     */
    private fun extractSegment(pcmFile: File, bounds: AudioSegmentBounds): File? {
        try {
            val segmentFile = File(segmentsDir, "${UUID.randomUUID()}.pcm")
            val bytesPerMs = (TARGET_SAMPLE_RATE * 2) / 1000 // 16-bit mono

            val startByte = bounds.startMs * bytesPerMs
            val endByte = bounds.endMs * bytesPerMs
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

    private fun getFileName(uri: Uri): String? {
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
     * Get import progress as a flow.
     */
    fun getImportProgress(jobId: String): Flow<ImportJob?> {
        return flow {
            while (true) {
                emit(importJobDao.getJobById(jobId))
                delay(500)
            }
        }
    }

    fun release() {
        scope.cancel()
    }
}

data class AudioSegmentBounds(
    val startMs: Long,
    val endMs: Long
)
