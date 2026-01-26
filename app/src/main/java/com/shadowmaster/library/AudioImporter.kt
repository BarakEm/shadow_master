package com.shadowmaster.library

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
        var pcmFile: File? = null
        try {
            // Update status
            importJobDao.updateProgress(jobId, ImportStatus.EXTRACTING_AUDIO, 10)

            // Extract audio to PCM
            pcmFile = try {
                extractAudioToPcm(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract audio to PCM", e)
                null
            }

            if (pcmFile == null) {
                importJobDao.markFailed(jobId, "Failed to extract audio - unsupported format or corrupted file")
                return
            }

            // Update status
            importJobDao.updateProgress(jobId, ImportStatus.DETECTING_SEGMENTS, 30)

            // Initialize VAD with retry
            var vadInitialized = false
            repeat(3) { attempt ->
                try {
                    if (vadDetector.initialize()) {
                        vadInitialized = true
                        return@repeat
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "VAD initialization attempt ${attempt + 1} failed", e)
                }
            }

            if (!vadInitialized) {
                importJobDao.markFailed(jobId, "Failed to initialize voice detection")
                pcmFile.delete()
                return
            }

            // Detect speech segments
            val segments = try {
                detectSpeechSegments(pcmFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to detect speech segments", e)
                emptyList()
            }

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
            val totalSegments = segments.size

            segments.forEachIndexed { index, segment ->
                try {
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract segment $index", e)
                }

                // Update progress (avoid division by zero)
                if (totalSegments > 0) {
                    val progress = 60 + (index * 30 / totalSegments)
                    importJobDao.updateProgress(jobId, ImportStatus.DETECTING_SEGMENTS, progress)
                }
            }

            if (shadowItems.isEmpty()) {
                importJobDao.markFailed(jobId, "Failed to extract any segments")
                pcmFile.delete()
                return
            }

            // Save all items
            shadowItemDao.insertAll(shadowItems)

            // Clean up temp PCM file
            pcmFile.delete()

            // Mark complete
            importJobDao.markCompleted(jobId, shadowItems.size)
            Log.i(TAG, "Import complete: ${shadowItems.size} segments created")

        } catch (e: Exception) {
            Log.e(TAG, "Import failed with exception", e)
            try {
                importJobDao.markFailed(jobId, e.message ?: "Unknown error")
            } catch (dbError: Exception) {
                Log.e(TAG, "Failed to update job status", dbError)
            }
            try {
                pcmFile?.delete()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Extract and decode audio from any format to mono 16kHz PCM.
     * Uses MediaCodec for proper audio decoding.
     */
    private fun extractAudioToPcm(uri: Uri): File? {
        val tempFile = File(context.cacheDir, "temp_pcm_${System.currentTimeMillis()}.pcm")
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            extractor = MediaExtractor()

            // Keep file descriptor open throughout extraction
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd == null) {
                Log.e(TAG, "Failed to open file descriptor for URI: $uri")
                return null
            }

            extractor.setDataSource(pfd.fileDescriptor)

            // Find audio track
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                Log.e(TAG, "No audio track found")
                pfd.close()
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            Log.i(TAG, "Audio: $mime, ${sampleRate}Hz, $channels channels")

            // Create and configure the decoder
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val outputStream = FileOutputStream(tempFile)
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false

            while (!isEOS) {
                // Feed input to decoder
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputBufferIndex)
                    if (buffer != null) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // Get decoded output
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val pcmData = ByteArray(bufferInfo.size)
                        outputBuffer.get(pcmData)

                        // Convert to mono 16kHz if needed
                        val monoData = if (channels > 1) {
                            convertToMono(pcmData, channels)
                        } else {
                            pcmData
                        }

                        // Resample if needed
                        val resampledData = if (sampleRate != TARGET_SAMPLE_RATE) {
                            resample(monoData, sampleRate, TARGET_SAMPLE_RATE)
                        } else {
                            monoData
                        }

                        outputStream.write(resampledData)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }

            outputStream.close()
            codec.stop()
            codec.release()
            extractor.release()
            pfd.close()

            Log.i(TAG, "Decoded ${tempFile.length()} bytes of PCM audio")
            return tempFile

        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode audio", e)
            tempFile.delete()
            try { codec?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
            return null
        }
    }

    /**
     * Convert stereo/multi-channel PCM to mono by averaging channels.
     */
    private fun convertToMono(pcmData: ByteArray, channels: Int): ByteArray {
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
     */
    private fun resample(pcmData: ByteArray, fromRate: Int, toRate: Int): ByteArray {
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

    /**
     * Split a segment into two parts at the given timestamp.
     * @param item The item to split
     * @param splitPointMs The position within the segment to split (relative to segment start)
     * @return List of two new ShadowItems, or null if split failed
     */
    suspend fun splitSegment(item: ShadowItem, splitPointMs: Long): List<ShadowItem>? = withContext(Dispatchers.IO) {
        try {
            if (splitPointMs <= MIN_SEGMENT_DURATION_MS || splitPointMs >= item.durationMs - MIN_SEGMENT_DURATION_MS) {
                Log.w(TAG, "Split point too close to edge: $splitPointMs / ${item.durationMs}")
                return@withContext null
            }

            val sourceFile = File(item.audioFilePath)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file not found: ${item.audioFilePath}")
                return@withContext null
            }

            val bytesPerMs = (TARGET_SAMPLE_RATE * 2) / 1000 // 16-bit mono
            val splitByte = (splitPointMs * bytesPerMs).toInt()
            val totalBytes = sourceFile.length().toInt()

            // Read source audio
            val audioData = sourceFile.readBytes()

            // Create first segment
            val segment1File = File(segmentsDir, "${UUID.randomUUID()}.pcm")
            FileOutputStream(segment1File).use { output ->
                output.write(audioData, 0, splitByte)
            }

            // Create second segment
            val segment2File = File(segmentsDir, "${UUID.randomUUID()}.pcm")
            FileOutputStream(segment2File).use { output ->
                output.write(audioData, splitByte, totalBytes - splitByte)
            }

            // Create new ShadowItems
            val item1 = item.copy(
                id = UUID.randomUUID().toString(),
                sourceEndMs = item.sourceStartMs + splitPointMs,
                audioFilePath = segment1File.absolutePath,
                durationMs = splitPointMs,
                createdAt = System.currentTimeMillis()
            )

            val item2 = item.copy(
                id = UUID.randomUUID().toString(),
                sourceStartMs = item.sourceStartMs + splitPointMs,
                audioFilePath = segment2File.absolutePath,
                durationMs = item.durationMs - splitPointMs,
                orderInPlaylist = item.orderInPlaylist + 1,
                createdAt = System.currentTimeMillis()
            )

            // Insert new items
            shadowItemDao.insert(item1)
            shadowItemDao.insert(item2)

            // Delete original
            shadowItemDao.delete(item)
            sourceFile.delete()

            // Update order for subsequent items in playlist
            item.playlistId?.let { playlistId ->
                val items = shadowItemDao.getItemsByPlaylist(playlistId).first()
                items.filter { it.orderInPlaylist > item.orderInPlaylist && it.id != item1.id && it.id != item2.id }
                    .forEach { laterItem ->
                        shadowItemDao.update(laterItem.copy(orderInPlaylist = laterItem.orderInPlaylist + 1))
                    }
            }

            Log.i(TAG, "Split segment ${item.id} into ${item1.id} and ${item2.id}")
            listOf(item1, item2)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to split segment", e)
            null
        }
    }

    /**
     * Merge multiple segments into one.
     * @param items The items to merge (must be from same playlist, in order)
     * @return The new merged ShadowItem, or null if merge failed
     */
    suspend fun mergeSegments(items: List<ShadowItem>): ShadowItem? = withContext(Dispatchers.IO) {
        try {
            if (items.size < 2) {
                Log.w(TAG, "Need at least 2 items to merge")
                return@withContext null
            }

            // Verify all items are from same playlist
            val playlistId = items.first().playlistId
            if (items.any { it.playlistId != playlistId }) {
                Log.w(TAG, "Cannot merge items from different playlists")
                return@withContext null
            }

            // Sort by order in playlist
            val sortedItems = items.sortedBy { it.orderInPlaylist }

            // Concatenate audio data
            val mergedFile = File(segmentsDir, "${UUID.randomUUID()}.pcm")
            FileOutputStream(mergedFile).use { output ->
                sortedItems.forEach { item ->
                    val sourceFile = File(item.audioFilePath)
                    if (sourceFile.exists()) {
                        output.write(sourceFile.readBytes())
                    }
                }
            }

            // Calculate total duration
            val totalDuration = sortedItems.sumOf { it.durationMs }

            // Create merged item
            val mergedItem = sortedItems.first().copy(
                id = UUID.randomUUID().toString(),
                sourceEndMs = sortedItems.last().sourceEndMs,
                audioFilePath = mergedFile.absolutePath,
                durationMs = totalDuration,
                transcription = sortedItems.mapNotNull { it.transcription }.joinToString(" ").takeIf { it.isNotBlank() },
                translation = sortedItems.mapNotNull { it.translation }.joinToString(" ").takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis(),
                practiceCount = 0,
                lastPracticedAt = null
            )

            // Insert merged item
            shadowItemDao.insert(mergedItem)

            // Delete original items and their audio files
            sortedItems.forEach { item ->
                shadowItemDao.delete(item)
                File(item.audioFilePath).delete()
            }

            // Update order for subsequent items
            playlistId?.let { pid ->
                val lowestOrder = sortedItems.minOf { it.orderInPlaylist }
                val itemsRemoved = sortedItems.size - 1
                val allItems = shadowItemDao.getItemsByPlaylist(pid).first()
                allItems.filter { it.orderInPlaylist > lowestOrder && it.id != mergedItem.id }
                    .forEach { laterItem ->
                        shadowItemDao.update(laterItem.copy(orderInPlaylist = laterItem.orderInPlaylist - itemsRemoved))
                    }
            }

            Log.i(TAG, "Merged ${sortedItems.size} segments into ${mergedItem.id}")
            mergedItem

        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge segments", e)
            null
        }
    }
}

data class AudioSegmentBounds(
    val startMs: Long,
    val endMs: Long
)
