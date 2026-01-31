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
import com.shadowmaster.library.AudioImportError.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val vadDetector: SileroVadDetector,
    private val importedAudioDao: com.shadowmaster.data.local.ImportedAudioDao,
    private val segmentationConfigDao: com.shadowmaster.data.local.SegmentationConfigDao
) {
    companion object {
        private const val TAG = "AudioImporter"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val MIN_SEGMENT_DURATION_MS = 500L
        private const val MAX_SEGMENT_DURATION_MS = 8000L
        private const val SILENCE_THRESHOLD_MS = 700L
        private const val PRE_SPEECH_BUFFER_MS = 200L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val segmentsDir: File by lazy {
        File(context.filesDir, "shadow_segments").also { it.mkdirs() }
    }

    private val importedAudioDir: File by lazy {
        File(context.filesDir, "imported_audio").also { it.mkdirs() }
    }

    private val playlistCreationMutex = Mutex()
    


    /**
     * Import audio file and store PCM without segmentation.
     * Returns ImportedAudio entity for later segmentation.
     */
    suspend fun importAudioOnly(
        uri: Uri,
        language: String = "auto"
    ): Result<ImportedAudio> = withContext(Dispatchers.IO) {
        var tempPcmFile: File? = null
        try {
            // Extract audio to PCM
            val extractResult = extractAudioToPcmWithError(uri)
            tempPcmFile = extractResult.first
            val extractError = extractResult.second

            if (tempPcmFile == null) {
                return@withContext Result.failure(
                    DecodingFailed(extractError ?: "Failed to extract audio")
                )
            }

            // Move PCM from cache to persistent storage
            val persistentPcmFile = File(importedAudioDir, "${UUID.randomUUID()}.pcm")
            tempPcmFile.copyTo(persistentPcmFile, overwrite = false)
            tempPcmFile.delete()

            // Calculate duration
            val bytesPerMs = (TARGET_SAMPLE_RATE * 2) / 1000 // 16-bit mono
            val durationMs = persistentPcmFile.length() / bytesPerMs

            // Create ImportedAudio entity
            val importedAudio = ImportedAudio(
                sourceUri = uri.toString(),
                sourceFileName = getFileName(uri) ?: "Unknown",
                originalFormat = detectFormat(uri),
                pcmFilePath = persistentPcmFile.absolutePath,
                durationMs = durationMs,
                sampleRate = TARGET_SAMPLE_RATE,
                channels = 1,
                fileSizeBytes = persistentPcmFile.length(),
                language = language
            )

            // Save to database
            importedAudioDao.insert(importedAudio)

            Log.i(TAG, "Imported audio: ${importedAudio.id}, ${importedAudio.durationMs}ms")
            Result.success(importedAudio)

        } catch (e: AudioImportError) {
            Log.e(TAG, "Import failed", e)
            tempPcmFile?.delete()
            Result.failure(e)
        } catch (e: IOException) {
            Log.e(TAG, "Import failed - storage error", e)
            tempPcmFile?.delete()
            Result.failure(StorageError("Failed to save imported audio: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Import failed - unexpected error", e)
            tempPcmFile?.delete()
            Result.failure(StorageError("Unexpected error during import: ${e.message}"))
        }
    }

    private fun detectFormat(uri: Uri): String {
        val fileName = getFileName(uri) ?: ""
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
     * Apply segmentation to imported audio with specified configuration.
     * Creates a new playlist with segmented items.
     */
    suspend fun segmentImportedAudio(
        importedAudioId: String,
        playlistName: String? = null,
        config: SegmentationConfig,
        enableTranscription: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get imported audio
            val importedAudio = importedAudioDao.getById(importedAudioId)
                ?: return@withContext Result.failure(FileNotFound("Imported audio not found: $importedAudioId"))

            val pcmFile = File(importedAudio.pcmFilePath)
            if (!pcmFile.exists()) {
                return@withContext Result.failure(FileNotFound(importedAudio.pcmFilePath))
            }

            // Create playlist
            val playlistId = UUID.randomUUID().toString()
            val playlist = ShadowPlaylist(
                id = playlistId,
                name = playlistName ?: "${importedAudio.sourceFileName} (${config.name})",
                language = importedAudio.language,
                sourceType = SourceType.IMPORTED,
                sourceUri = importedAudio.sourceUri
            )
            shadowPlaylistDao.insert(playlist)

            // Initialize VAD (with retry logic)
            if (!initializeVad()) {
                shadowPlaylistDao.delete(playlist)
                return@withContext Result.failure(DecodingFailed("Failed to initialize VAD for speech detection"))
            }

            // Detect speech segments with config parameters
            val segments = detectSpeechSegmentsWithConfig(pcmFile, config)

            if (segments.isEmpty()) {
                shadowPlaylistDao.delete(playlist)
                return@withContext Result.failure(DecodingFailed("No speech segments detected in audio file"))
            }

            // Extract and save each segment
            val shadowItems = mutableListOf<ShadowItem>()
            segments.forEachIndexed { index, segment ->
                val segmentFile = extractSegment(pcmFile, segment)
                if (segmentFile != null) {
                    val item = ShadowItem(
                        sourceFileUri = importedAudio.sourceUri,
                        sourceFileName = importedAudio.sourceFileName,
                        sourceStartMs = segment.startMs,
                        sourceEndMs = segment.endMs,
                        audioFilePath = segmentFile.absolutePath,
                        durationMs = segment.endMs - segment.startMs,
                        language = importedAudio.language,
                        playlistId = playlistId,
                        orderInPlaylist = index,
                        importedAudioId = importedAudioId,
                        segmentationConfigId = config.id
                    )
                    shadowItems.add(item)
                }
            }

            // Save all items
            shadowItemDao.insertAll(shadowItems)

            // Update ImportedAudio tracking
            importedAudioDao.markSegmented(importedAudioId)

            Log.i(TAG, "Segmentation complete: ${shadowItems.size} segments created")
            Result.success(playlistId)

        } catch (e: AudioImportError) {
            Log.e(TAG, "Segmentation failed", e)
            Result.failure(e)
        } catch (e: IOException) {
            Log.e(TAG, "Segmentation failed - storage error", e)
            Result.failure(StorageError("Failed to save segments: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Segmentation failed - unexpected error", e)
            Result.failure(StorageError("Unexpected error during segmentation: ${e.message}"))
        }
    }

    private suspend fun initializeVad(): Boolean {
        var vadInitialized = false
        repeat(5) { attempt ->
            try {
                if (vadDetector.initialize()) {
                    vadInitialized = true
                    return@repeat
                }
            } catch (e: Exception) {
                Log.e(TAG, "VAD initialization attempt ${attempt + 1} failed", e)
            }
            if (attempt < 4) {
                delay((attempt + 1) * 500L)
            }
        }
        return vadInitialized
    }

    /**
     * Detect speech segments using configurable parameters.
     * Replaces hardcoded constants with config values.
     */
    private fun detectSpeechSegmentsWithConfig(
        pcmFile: File,
        config: SegmentationConfig
    ): List<AudioSegmentBounds> {
        val rawSegments = detectSpeechSegmentsWithParams(
            pcmFile,
            config.minSegmentDurationMs,
            config.maxSegmentDurationMs,
            config.silenceThresholdMs,
            config.preSpeechBufferMs
        )

        // Apply segment mode (WORD vs SENTENCE)
        return applySegmentMode(rawSegments, config.segmentMode)
    }

    private fun detectSpeechSegmentsWithParams(
        pcmFile: File,
        minSegmentDurationMs: Long,
        maxSegmentDurationMs: Long,
        silenceThresholdMs: Long,
        preSpeechBufferMs: Long
    ): List<AudioSegmentBounds> {
        val segments = mutableListOf<AudioSegmentBounds>()
        val frameSize = SileroVadDetector.FRAME_SIZE_SAMPLES
        val frameDurationMs = (frameSize * 1000L) / TARGET_SAMPLE_RATE

        var isSpeaking = false
        var speechStartMs = 0L
        var lastSpeechMs = 0L
        var currentMs = 0L

        DataInputStream(BufferedInputStream(FileInputStream(pcmFile))).use { inputStream ->
            val frameBuffer = ShortArray(frameSize)
            val byteBuffer = ByteArray(frameSize * 2)

            while (inputStream.available() >= byteBuffer.size) {
                inputStream.readFully(byteBuffer)

                // Convert bytes to shorts
                for (i in 0 until frameSize) {
                    frameBuffer[i] = ((byteBuffer[i * 2 + 1].toInt() shl 8) or
                            (byteBuffer[i * 2].toInt() and 0xFF)).toShort()
                }

                val hasSpeech = vadDetector.isSpeech(frameBuffer)

                if (hasSpeech) {
                    if (!isSpeaking) {
                        isSpeaking = true
                        speechStartMs = maxOf(0, currentMs - preSpeechBufferMs)
                    }
                    lastSpeechMs = currentMs
                } else if (isSpeaking) {
                    val silenceDuration = currentMs - lastSpeechMs

                    if (silenceDuration >= silenceThresholdMs) {
                        val segmentDuration = lastSpeechMs - speechStartMs + silenceThresholdMs

                        if (segmentDuration >= minSegmentDurationMs) {
                            segments.add(AudioSegmentBounds(
                                startMs = speechStartMs,
                                endMs = minOf(lastSpeechMs + silenceThresholdMs, currentMs)
                            ))
                        }
                        isSpeaking = false
                    }
                }

                // Force segment break if too long
                if (isSpeaking && (currentMs - speechStartMs) >= maxSegmentDurationMs) {
                    segments.add(AudioSegmentBounds(
                        startMs = speechStartMs,
                        endMs = currentMs
                    ))
                    isSpeaking = false
                }

                currentMs += frameDurationMs
            }

            // Handle final segment
            if (isSpeaking && (lastSpeechMs - speechStartMs) >= minSegmentDurationMs) {
                segments.add(AudioSegmentBounds(
                    startMs = speechStartMs,
                    endMs = lastSpeechMs + 100
                ))
            }
        }

        return segments
    }

    /**
     * Apply segment mode to adjust boundaries.
     * WORD mode: Split longer segments into shorter chunks
     * SENTENCE mode: Keep VAD-detected boundaries as-is
     */
    private fun applySegmentMode(
        segments: List<AudioSegmentBounds>,
        mode: SegmentMode
    ): List<AudioSegmentBounds> {
        return when (mode) {
            SegmentMode.SENTENCE -> segments  // Use VAD boundaries as-is

            SegmentMode.WORD -> {
                // Split longer segments into word-sized chunks (~1-2 seconds)
                val wordSegments = mutableListOf<AudioSegmentBounds>()
                val targetWordDurationMs = 1500L

                segments.forEach { segment ->
                    val duration = segment.endMs - segment.startMs

                    if (duration <= targetWordDurationMs * 1.5) {
                        // Already word-sized or close enough
                        wordSegments.add(segment)
                    } else {
                        // Split into smaller chunks
                        val numChunks = ((duration / targetWordDurationMs).toInt()).coerceAtLeast(2)
                        val chunkSize = duration / numChunks

                        for (i in 0 until numChunks) {
                            val start = segment.startMs + (i * chunkSize)
                            val end = if (i == numChunks - 1) {
                                segment.endMs
                            } else {
                                segment.startMs + ((i + 1) * chunkSize)
                            }

                            wordSegments.add(AudioSegmentBounds(start, end))
                        }
                    }
                }

                wordSegments
            }
        }
    }

    /**
     * Import an audio file and create a playlist with segmented items.
     * Maintains backward compatibility - imports and immediately segments with default config.
     */
    suspend fun importAudioFile(
        uri: Uri,
        playlistName: String? = null,
        language: String = "auto",
        enableTranscription: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Phase 1: Import audio
            val importResult = importAudioOnly(uri, language)
            if (importResult.isFailure) {
                return@withContext Result.failure(
                    importResult.exceptionOrNull() ?: Exception("Import failed")
                )
            }

            val importedAudio = importResult.getOrThrow()

            // Phase 2: Segment with default config
            val defaultConfig = segmentationConfigDao.getById("default-config")
                ?: SegmentationConfig(
                    id = "default-config",
                    name = "Default"
                ).also { segmentationConfigDao.insert(it) }

            return@withContext segmentImportedAudio(
                importedAudioId = importedAudio.id,
                playlistName = playlistName,
                config = defaultConfig,
                enableTranscription = enableTranscription
            )

        } catch (e: AudioImportError) {
            Log.e(TAG, "Import and segment failed", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Import and segment failed - unexpected error", e)
            Result.failure(StorageError("Unexpected error: ${e.message}"))
        }
    }


    /**
     * Extract and decode audio from any format to mono 16kHz PCM.
     * Returns Pair(file, errorMessage) - file is null on failure with error message.
     * @throws AudioImportError for specific error conditions
     */
    private fun extractAudioToPcmWithError(uri: Uri): Pair<File?, String?> {
        val tempFile = File(context.cacheDir, "temp_pcm_${System.currentTimeMillis()}.pcm")
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            extractor = MediaExtractor()

            // Handle different URI schemes
            var dataSourceSet = false

            // For file:// URIs, use the path directly
            if (uri.scheme == "file") {
                val filePath = uri.path
                if (filePath != null && File(filePath).exists()) {
                    try {
                        extractor.setDataSource(filePath)
                        dataSourceSet = true
                        Log.d(TAG, "Set data source via file path: $filePath")
                    } catch (e: Exception) {
                        Log.d(TAG, "File path method failed: ${e.message}")
                    }
                }
            }

            // Try context/URI method (works well with content:// URIs)
            if (!dataSourceSet) {
                try {
                    extractor.setDataSource(context, uri, null)
                    dataSourceSet = true
                    Log.d(TAG, "Set data source via context/URI")
                } catch (e: Exception) {
                    Log.d(TAG, "Context/URI method failed, trying file descriptor: ${e.message}")
                }
            }

            // Fallback to file descriptor method
            if (!dataSourceSet) {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    Log.e(TAG, "Failed to open file descriptor for URI: $uri")
                    return Pair(null, "Cannot read file - permission denied or file not found")
                }

                try {
                    extractor.setDataSource(pfd.fileDescriptor)
                    Log.d(TAG, "Set data source via file descriptor")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied for file descriptor", e)
                    pfd?.close()
                    throw PermissionDenied("Storage read permission")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set data source via file descriptor", e)
                    pfd?.close()
                    return Pair(null, "Cannot read audio file: ${e.message}")
                }
            }

            // Find audio track
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            val foundMimeTypes = mutableListOf<String>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                foundMimeTypes.add(mime)
                Log.d(TAG, "Track $i: $mime")
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                val trackInfo = if (extractor.trackCount == 0) {
                    "File not recognized as media"
                } else {
                    "Found tracks: ${foundMimeTypes.joinToString()}"
                }
                Log.e(TAG, "No audio track found. $trackInfo")
                pfd?.close()
                extractor.release()
                throw UnsupportedFormat("No audio track found in file. $trackInfo")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            Log.i(TAG, "Audio: $mime, ${sampleRate}Hz, $channels channels")

            // Create and configure the decoder
            try {
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(inputFormat, null, null, 0)
                codec.start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create decoder for $mime", e)
                pfd?.close()
                extractor.release()
                throw UnsupportedFormat("$mime - try converting to MP3 or WAV first")
            }

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
            pfd?.close()

            Log.i(TAG, "Decoded ${tempFile.length()} bytes of PCM audio")
            return Pair(tempFile, null)

        } catch (e: AudioImportError) {
            // Re-throw structured errors
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Failed to decode audio - I/O error", e)
            tempFile.delete()
            try { codec?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
            throw StorageError("Failed to read or write audio data: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode audio", e)
            tempFile.delete()
            try { codec?.release() } catch (ignored: Exception) {}
            try { extractor?.release() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
            val errorMsg = when {
                e.message?.contains("codec", ignoreCase = true) == true ->
                    "Audio format not supported: ${e.message}"
                e.message?.contains("mime", ignoreCase = true) == true ->
                    "Unknown audio format"
                else -> "Failed to decode audio: ${e.message}"
            }
            return Pair(null, errorMsg)
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
     * Save a captured audio segment to the library.
     * Creates a "Captured Audio" playlist if it doesn't exist.
     * @param segment The audio segment to save
     * @return The created ShadowItem, or null if save failed
     */
    suspend fun saveCapturedSegment(segment: AudioSegment): ShadowItem? = withContext(Dispatchers.IO) {
        var segmentFile: File? = null
        try {
            // Validate sample rate
            if (segment.sampleRate != TARGET_SAMPLE_RATE) {
                Log.w(TAG, "Segment sample rate ${segment.sampleRate} doesn't match target $TARGET_SAMPLE_RATE")
            }

            // Get or create "Captured Audio" playlist
            val playlistId = getOrCreateCapturedAudioPlaylist()

            // Get current item count for ordering
            val itemCount = shadowItemDao.getItemCountByPlaylist(playlistId)

            // Save segment audio to file
            segmentFile = File(segmentsDir, "${UUID.randomUUID()}.pcm")
            FileOutputStream(segmentFile).use { output ->
                val byteBuffer = ByteArray(segment.samples.size * 2)
                for (i in segment.samples.indices) {
                    val sample = segment.samples[i]
                    byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }
                output.write(byteBuffer)
            }

            // Create ShadowItem
            val item = ShadowItem(
                sourceFileUri = "captured://audio",
                sourceFileName = "Captured Audio",
                sourceStartMs = 0L,
                sourceEndMs = segment.durationMs,
                audioFilePath = segmentFile.absolutePath,
                durationMs = segment.durationMs,
                language = "unknown",
                playlistId = playlistId,
                orderInPlaylist = itemCount
            )

            // Save to database
            shadowItemDao.insert(item)
            Log.i(TAG, "Saved captured segment: ${segment.durationMs}ms, ${segment.samples.size} samples")

            item
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save captured segment", e)
            // Clean up orphaned file on failure
            segmentFile?.delete()
            null
        }
    }

    /**
     * Get or create the "Captured Audio" playlist for saving captured segments.
     * Uses synchronization to prevent race conditions when multiple segments
     * are detected simultaneously.
     * @return The playlist ID
     */
    private suspend fun getOrCreateCapturedAudioPlaylist(): String {
        // Use explicit lock/unlock instead of withLock to allow suspension inside
        playlistCreationMutex.lock()
        try {
            // Check if playlist already exists
            val allPlaylists = shadowPlaylistDao.getAllPlaylists().first()
            val existingPlaylist = allPlaylists.find {
                it.sourceType == SourceType.RECORDED
            }

            if (existingPlaylist != null) {
                return existingPlaylist.id
            } else {
                // Create new playlist
                val playlist = ShadowPlaylist(
                    name = "Captured Audio",
                    description = "Audio segments captured from other apps",
                    language = "unknown",
                    sourceType = SourceType.RECORDED,
                    sourceUri = null
                )
                shadowPlaylistDao.insert(playlist)
                return playlist.id
            }
        } finally {
            playlistCreationMutex.unlock()
        }
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
