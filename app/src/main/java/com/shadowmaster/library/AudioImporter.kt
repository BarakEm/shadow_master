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
import com.shadowmaster.transcription.ProviderConfig
import com.shadowmaster.transcription.TranscriptionProviderType
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
    private val segmentationConfigDao: com.shadowmaster.data.local.SegmentationConfigDao,
    private val audioFileUtility: AudioFileUtility,
    private val settingsRepository: com.shadowmaster.data.repository.SettingsRepository,
    private val transcriptionService: com.shadowmaster.transcription.TranscriptionService
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
     * Import an audio file and store as PCM without performing segmentation.
     * 
     * This method extracts audio from any supported format (MP3, WAV, M4A, AAC, OGG, FLAC)
     * and converts it to 16kHz mono PCM format suitable for VAD processing. The PCM data
     * is stored persistently for later segmentation with different configurations.
     * 
     * The import process includes:
     * 1. Decode audio using MediaCodec
     * 2. Convert to mono if multi-channel
     * 3. Resample to 16kHz if needed
     * 4. Save to persistent storage
     * 5. Create database record
     * 
     * @param uri The URI of the audio file to import (supports content://, file://, etc.)
     * @param language The language code for the audio content (e.g., "en", "es", "fr").
     *                 Defaults to "auto" for automatic detection (not yet implemented).
     * @return Result containing the [ImportedAudio] entity on success, or an exception on failure.
     *         Failure reasons include: file not found, unsupported format, decode errors, or I/O errors.
     * 
     * @throws Exception if audio extraction fails or database operations fail
     * 
     * Thread Safety: This method is thread-safe. It executes on [Dispatchers.IO] context.
     * 
     * Example usage:
     * ```kotlin
     * val uri = Uri.parse("content://media/external/audio/media/1234")
     * val result = audioImporter.importAudioOnly(uri, "en")
     * result.onSuccess { importedAudio ->
     *     println("Imported: ${importedAudio.sourceFileName}, ${importedAudio.durationMs}ms")
     *     // Later: segment with different configs
     *     segmentImportedAudio(importedAudio.id, config = sentenceConfig)
     * }.onFailure { error ->
     *     println("Import failed: ${error.message}")
     * }
     * ```
     * 
     * @see segmentImportedAudio for applying segmentation to imported audio
     * @see importAudioFile for combined import and segmentation
     * @see ImportedAudio for the returned entity structure
     */
    suspend fun importAudioOnly(
        uri: Uri,
        language: String = "auto",
        jobId: String? = null
    ): Result<ImportedAudio> = withContext(Dispatchers.IO) {
        var tempPcmFile: File? = null
        try {
            // Validate URI before processing
            InputValidator.validateUri(uri).onFailure { error ->
                return@withContext Result.failure(error)
            }

            // Extract audio to PCM with progress updates
            jobId?.let { updateImportJob(it, ImportStatus.EXTRACTING_AUDIO, 10) }
            
            val extractResult = extractAudioToPcmWithError(uri)
            tempPcmFile = extractResult.first
            val extractError = extractResult.second

            if (tempPcmFile == null) {
                return@withContext Result.failure(
                    DecodingFailed(extractError ?: "Failed to extract audio")
                )
            }

            jobId?.let { updateImportJob(it, ImportStatus.EXTRACTING_AUDIO, 40) }

            // Move PCM from cache to persistent storage
            val persistentPcmFile = File(importedAudioDir, "${UUID.randomUUID()}.pcm")
            tempPcmFile.copyTo(persistentPcmFile, overwrite = false)
            tempPcmFile.delete()

            jobId?.let { updateImportJob(it, ImportStatus.EXTRACTING_AUDIO, 50) }

            // Calculate duration
            val bytesPerMs = (TARGET_SAMPLE_RATE * 2) / 1000 // 16-bit mono
            val durationMs = persistentPcmFile.length() / bytesPerMs

            // Create ImportedAudio entity
            val importedAudio = ImportedAudio(
                sourceUri = uri.toString(),
                sourceFileName = audioFileUtility.getFileName(uri, context) ?: "Unknown",
                originalFormat = audioFileUtility.detectFormat(uri, context),
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

    /**
     * Apply VAD-based segmentation to previously imported audio with customizable configuration.
     * 
     * This method uses the Silero VAD detector to identify speech boundaries in the PCM audio
     * and creates individual segment files. Each segment is saved as a separate audio file and
     * database record, organized into a playlist for practice sessions.
     * 
     * The segmentation process includes:
     * 1. Load PCM audio from persistent storage
     * 2. Initialize VAD detector (with retry logic)
     * 3. Detect speech segments using config parameters
     * 4. Apply segment mode (WORD vs SENTENCE)
     * 5. Extract and save individual segments
     * 6. Create playlist and ShadowItem records
     * 
     * @param importedAudioId The unique identifier of the [ImportedAudio] entity to segment.
     *                        Must reference an existing record from [importAudioOnly].
     * @param playlistName Optional custom name for the created playlist. If null, generates
     *                     a name from the source filename and config name.
     * @param config The [SegmentationConfig] defining segmentation parameters:
     *               - minSegmentDurationMs: Minimum segment length (default 500ms)
     *               - maxSegmentDurationMs: Maximum segment length (default 8000ms)
     *               - silenceThresholdMs: Required silence to end segment (default 700ms)
     *               - preSpeechBufferMs: Pre-speech padding (default 200ms)
     *               - segmentMode: WORD (short chunks) or SENTENCE (natural boundaries)
     * @param enableTranscription Reserved for future automatic transcription feature.
     *                            Currently not implemented.
     * @return Result containing the playlist ID (String) on success, or an exception on failure.
     *         Failure reasons include: imported audio not found, PCM file missing, VAD initialization
     *         failure, no speech detected, or database errors.
     * 
     * @throws Exception if segmentation fails or database operations fail
     * 
     * Thread Safety: This method is thread-safe. It executes on [Dispatchers.IO] context.
     * 
     * Example usage:
     * ```kotlin
     * // First import the audio
     * val importResult = importAudioOnly(uri, "en")
     * val importedAudio = importResult.getOrThrow()
     * 
     * // Create or get segmentation config
     * val wordConfig = SegmentationConfig(
     *     name = "Word Mode",
     *     segmentMode = SegmentMode.WORD,
     *     minSegmentDurationMs = 500,
     *     maxSegmentDurationMs = 2000
     * )
     * 
     * // Segment with the config
     * val result = segmentImportedAudio(
     *     importedAudioId = importedAudio.id,
     *     playlistName = "My Practice Session",
     *     config = wordConfig
     * )
     * result.onSuccess { playlistId ->
     *     println("Created playlist: $playlistId")
     * }
     * ```
     * 
     * @see importAudioOnly for importing audio without segmentation
     * @see importAudioFile for combined import and segmentation
     * @see SegmentationConfig for configuration options
     * @see SileroVadDetector for VAD implementation details
     */
    suspend fun segmentImportedAudio(
        importedAudioId: String,
        playlistName: String? = null,
        config: SegmentationConfig,
        enableTranscription: Boolean = false,
        jobId: String? = null
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

            jobId?.let { updateImportJob(it, ImportStatus.DETECTING_SEGMENTS, 55) }

            // Initialize VAD (with retry logic)
            if (!initializeVad()) {
                shadowPlaylistDao.delete(playlist)
                return@withContext Result.failure(DecodingFailed("Failed to initialize VAD for speech detection"))
            }

            jobId?.let { updateImportJob(it, ImportStatus.DETECTING_SEGMENTS, 60) }

            // Detect speech segments with config parameters
            val segments = detectSpeechSegmentsWithConfig(pcmFile, config)

            if (segments.isEmpty()) {
                shadowPlaylistDao.delete(playlist)
                return@withContext Result.failure(DecodingFailed("No speech segments detected in audio file"))
            }

            jobId?.let { 
                updateImportJob(it, ImportStatus.DETECTING_SEGMENTS, 75, 
                    totalSegments = segments.size, processedSegments = 0)
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
                } else {
                    Log.w(TAG, "Failed to extract segment $index: ${segment.startMs}ms-${segment.endMs}ms")
                }
                
                // Update progress for each processed segment
                jobId?.let {
                    val progress = 75 + ((index + 1) * 20 / segments.size)
                    updateImportJob(it, ImportStatus.DETECTING_SEGMENTS, progress,
                        totalSegments = segments.size, processedSegments = index + 1)
                }
            }

            // Check if all segment extractions failed
            if (shadowItems.isEmpty()) {
                shadowPlaylistDao.delete(playlist)
                Log.e(TAG, "All ${segments.size} segment extractions failed for ${importedAudio.sourceFileName}")
                return@withContext Result.failure(StorageError("Failed to extract any audio segments from file"))
            }

            jobId?.let { updateImportJob(it, ImportStatus.DETECTING_SEGMENTS, 95) }

            // Save all items
            shadowItemDao.insertAll(shadowItems)

            // Transcribe segments if enabled
            if (enableTranscription) {
                jobId?.let { updateImportJob(it, ImportStatus.TRANSCRIBING, 95) }
                
                val settings = settingsRepository.config.first()
                val transcriptionConfig = settings.transcription
                
                Log.i(TAG, "Starting transcription for ${shadowItems.size} segments")
                
                // Get provider type safely
                val providerType = getProviderType(transcriptionConfig.defaultProvider)
                if (providerType == null) {
                    Log.w(TAG, "Invalid transcription provider: ${transcriptionConfig.defaultProvider}, skipping transcription")
                } else {
                    val providerConfig = createProviderConfig(transcriptionConfig)
                    
                    shadowItems.forEachIndexed { index, item ->
                        try {
                            val audioFile = File(item.audioFilePath)
                            if (audioFile.exists()) {
                                val result = transcriptionService.transcribe(
                                    audioFile = audioFile,
                                    language = importedAudio.language,
                                    providerType = providerType,
                                    config = providerConfig
                                )
                                
                                result.onSuccess { transcribedText ->
                                    // Update the item with transcription
                                    val updatedItem = item.copy(transcription = transcribedText)
                                    shadowItemDao.update(updatedItem)
                                    Log.d(TAG, "Transcribed segment ${index + 1}: $transcribedText")
                                }.onFailure { error ->
                                    Log.w(TAG, "Failed to transcribe segment ${index + 1}: ${error.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error transcribing segment ${index + 1}: ${e.message}")
                        }
                    }
                }
            }

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
     * Import an audio file and immediately create a segmented playlist with default configuration.
     * 
     * This is a convenience method that combines [importAudioOnly] and [segmentImportedAudio]
     * in a single call. It maintains backward compatibility with the original import workflow
     * and is suitable when you want to use the default segmentation settings.
     * 
     * The method performs the following steps:
     * 1. Import audio and convert to 16kHz mono PCM
     * 2. Store PCM persistently
     * 3. Load or create default segmentation config
     * 4. Apply VAD-based segmentation
     * 5. Create playlist with segmented items
     * 
     * If you need multiple segmentations with different configurations, use [importAudioOnly]
     * followed by multiple calls to [segmentImportedAudio] instead.
     * 
     * @param uri The URI of the audio file to import (supports content://, file://, etc.)
     * @param playlistName Optional custom name for the created playlist. If null, generates
     *                     a name from the source filename.
     * @param language The language code for the audio content (e.g., "en", "es", "fr").
     *                 Defaults to "auto" for automatic detection (not yet implemented).
     * @param enableTranscription Reserved for future automatic transcription feature.
     *                            Currently not implemented.
     * @return Result containing the playlist ID (String) on success, or an exception on failure.
     *         Failure reasons include: file not found, unsupported format, decode errors,
     *         VAD initialization failure, no speech detected, or database errors.
     * 
     * @throws Exception if import or segmentation fails
     * 
     * Thread Safety: This method is thread-safe. It executes on [Dispatchers.IO] context.
     * 
     * Example usage:
     * ```kotlin
     * val uri = Uri.parse("content://media/external/audio/media/1234")
     * val result = audioImporter.importAudioFile(
     *     uri = uri,
     *     playlistName = "Spanish Lesson 1",
     *     language = "es"
     * )
     * result.onSuccess { playlistId ->
     *     println("Import complete! Playlist ID: $playlistId")
     *     // Navigate to practice screen with playlistId
     * }.onFailure { error ->
     *     println("Import failed: ${error.message}")
     * }
     * ```
     * 
     * @see importAudioOnly for import without immediate segmentation
     * @see segmentImportedAudio for applying custom segmentation configs
     */
    suspend fun importAudioFile(
        uri: Uri,
        playlistName: String? = null,
        language: String = "auto",
        enableTranscription: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        // Create import job for progress tracking
        val fileName = audioFileUtility.getFileName(uri, context) ?: "Unknown"
        val job = ImportJob(
            sourceUri = uri.toString(),
            fileName = fileName,
            status = ImportStatus.PENDING,
            language = language,
            enableTranscription = enableTranscription
        )
        importJobDao.insert(job)
        
        try {
            // Phase 1: Import audio with progress tracking
            updateImportJob(job.id, ImportStatus.EXTRACTING_AUDIO, 0)
            
            val importResult = importAudioOnly(uri, language, job.id)
            if (importResult.isFailure) {
                updateImportJob(job.id, ImportStatus.FAILED, 0, 
                    errorMessage = importResult.exceptionOrNull()?.message ?: "Import failed")
                return@withContext Result.failure(
                    importResult.exceptionOrNull() ?: Exception("Import failed")
                )
            }

            val importedAudio = importResult.getOrThrow()

            // Phase 2: Segment with default config and progress tracking
            updateImportJob(job.id, ImportStatus.DETECTING_SEGMENTS, 50)

            // Get current user settings for segment mode
            val currentSettings = settingsRepository.config.first()

            val defaultConfig = segmentationConfigDao.getById("default-config")
                ?: SegmentationConfig(
                    id = "default-config",
                    name = "Default"
                ).also { segmentationConfigDao.insert(it) }

            // Apply user's segment mode preference to the config
            val configWithUserSettings = defaultConfig.copy(
                segmentMode = currentSettings.segmentMode
            )

            val segmentResult = segmentImportedAudio(
                importedAudioId = importedAudio.id,
                playlistName = playlistName,
                config = configWithUserSettings,
                enableTranscription = enableTranscription,
                jobId = job.id
            )
            
            if (segmentResult.isSuccess) {
                updateImportJob(job.id, ImportStatus.COMPLETED, 100)
            } else {
                updateImportJob(job.id, ImportStatus.FAILED, 50,
                    errorMessage = segmentResult.exceptionOrNull()?.message ?: "Segmentation failed")
            }
            
            return@withContext segmentResult

        } catch (e: AudioImportError) {
            Log.e(TAG, "Import and segment failed", e)
            updateImportJob(job.id, ImportStatus.FAILED, 0, errorMessage = e.message)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Import and segment failed - unexpected error", e)
            updateImportJob(job.id, ImportStatus.FAILED, 0, errorMessage = e.message ?: "Unexpected error")
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
                            audioFileUtility.convertToMono(pcmData, channels)
                        } else {
                            pcmData
                        }

                        // Resample if needed
                        val resampledData = if (sampleRate != TARGET_SAMPLE_RATE) {
                            audioFileUtility.resample(monoData, sampleRate, TARGET_SAMPLE_RATE)
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
        return audioFileUtility.extractSegment(
            pcmFile = pcmFile,
            startMs = bounds.startMs,
            endMs = bounds.endMs,
            sampleRate = TARGET_SAMPLE_RATE,
            outputDir = segmentsDir
        )
    }

    /**
     * Get real-time progress updates for an import job as a Flow.
     * 
     * This method returns a Flow that emits [ImportJob] updates every 500ms, allowing
     * UI components to display import progress, status, and error messages in real-time.
     * 
     * The Flow emits continuously until the coroutine scope is cancelled. Consumers should
     * collect with appropriate lifecycle awareness to avoid memory leaks.
     * 
     * Note: This method is currently designed for the import job tracking system but is
     * not actively used in the current import workflows ([importAudioOnly], [importAudioFile]).
     * It's retained for potential future use with background/queued imports.
     * 
     * @param jobId The unique identifier of the import job to track.
     * @return Flow<ImportJob?> that emits job updates every 500ms. Emits null if job not found.
     * 
     * Thread Safety: The Flow is safe to collect from any context. Database queries
     * execute on [Dispatchers.IO] internally.
     * 
     * Example usage:
     * ```kotlin
     * // In a ViewModel or Composable
     * audioImporter.getImportProgress(jobId)
     *     .collectAsState(initial = null)
     * 
     * // Or with lifecycle awareness
     * viewModelScope.launch {
     *     audioImporter.getImportProgress(jobId)
     *         .collect { job ->
     *             when (job?.status) {
     *                 ImportStatus.COMPLETED -> showSuccess()
     *                 ImportStatus.FAILED -> showError(job.errorMessage)
     *                 ImportStatus.IN_PROGRESS -> updateProgress(job.progress)
     *                 else -> {}
     *             }
     *         }
     * }
     * ```
     * 
     * @see ImportJob for the emitted job structure
     */
    fun getImportProgress(jobId: String): Flow<ImportJob?> {
        return flow {
            while (true) {
                emit(importJobDao.getJobById(jobId))
                delay(500)
            }
        }
    }

    /**
     * Helper method to update import job status and progress.
     */
    /**
     * Helper method to update import job status and progress.
     * 
     * Note: Once a job reaches a terminal state (COMPLETED or FAILED), the completedAt
     * timestamp is preserved to prevent it from being overwritten. This is safe because
     * our import workflow never transitions a job from a terminal state back to a
     * non-terminal state - once COMPLETED or FAILED, the job's lifecycle is finished.
     */
    private suspend fun updateImportJob(
        jobId: String,
        status: ImportStatus,
        progress: Int,
        errorMessage: String? = null,
        totalSegments: Int? = null,
        processedSegments: Int? = null
    ) {
        val job = importJobDao.getJobById(jobId) ?: return
        val updatedJob = job.copy(
            status = status,
            progress = progress,
            errorMessage = errorMessage,
            totalSegments = totalSegments ?: job.totalSegments,
            processedSegments = processedSegments ?: job.processedSegments,
            completedAt = when {
                // Preserve existing completedAt if already set
                job.completedAt != null -> job.completedAt
                // Set completedAt only when transitioning to terminal state
                status == ImportStatus.COMPLETED || status == ImportStatus.FAILED -> System.currentTimeMillis()
                else -> null
            }
        )
        importJobDao.update(updatedJob)
    }

    /**
     * Release resources and cancel all background operations.
     * 
     * This method should be called when the AudioImporter is no longer needed,
     * typically during application shutdown or when the dependency injection scope
     * is destroyed. It cancels the internal coroutine scope to stop any ongoing
     * background operations.
     * 
     * After calling this method, the AudioImporter instance should not be used
     * for any further operations.
     * 
     * Thread Safety: This method is thread-safe and can be called from any thread.
     * 
     * Example usage:
     * ```kotlin
     * // In a ViewModel's onCleared()
     * override fun onCleared() {
     *     super.onCleared()
     *     audioImporter.release()
     * }
     * 
     * // Or when tearing down dependency injection scope
     * fun cleanup() {
     *     audioImporter.release()
     * }
     * ```
     */
    fun release() {
        scope.cancel()
    }

    /**
     * Save a captured audio segment from live audio capture to the Shadow Library.
     * 
     * This method is used by the Live Shadow mode to save interesting audio segments
     * captured from other apps (e.g., language learning apps, podcasts, videos) for
     * later practice. Segments are automatically organized into a "Captured Audio" playlist.
     * 
     * The save process includes:
     * 1. Validate audio sample rate (should be 16kHz)
     * 2. Get or create "Captured Audio" playlist (thread-safe)
     * 3. Convert audio samples to PCM format
     * 4. Save to segment storage
     * 5. Create database record
     * 
     * The method uses a mutex to ensure thread-safe playlist creation when multiple
     * segments are detected simultaneously.
     * 
     * @param segment The [AudioSegment] to save, containing:
     *                - samples: ShortArray of audio data
     *                - sampleRate: Should be 16000 Hz
     *                - durationMs: Length in milliseconds
     * @return The created [ShadowItem] on success, or null if the save operation fails.
     *         Returns null for validation errors, I/O errors, or database errors.
     * 
     * Thread Safety: This method is thread-safe. Multiple segments can be saved
     * concurrently without race conditions. Uses [Dispatchers.IO] for I/O operations
     * and [playlistCreationMutex] to prevent duplicate playlist creation.
     * 
     * Example usage:
     * ```kotlin
     * // In AudioProcessingPipeline or ShadowingCoordinator
     * val segment = AudioSegment(
     *     samples = audioSamples,
     *     sampleRate = 16000,
     *     durationMs = 2500
     * )
     * 
     * val savedItem = audioImporter.saveCapturedSegment(segment)
     * if (savedItem != null) {
     *     println("Saved segment: ${savedItem.id}, ${savedItem.durationMs}ms")
     *     // Show notification or update UI
     * } else {
     *     println("Failed to save segment")
     * }
     * ```
     * 
     * @see AudioSegment for the input structure
     * @see ShadowItem for the returned entity
     * @see getOrCreateCapturedAudioPlaylist for playlist management
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

            // Transcribe if auto-transcribe is enabled
            try {
                val settings = settingsRepository.config.first()
                val transcriptionConfig = settings.transcription
                
                if (transcriptionConfig.autoTranscribeOnImport) {
                    Log.d(TAG, "Auto-transcribing captured segment")
                    
                    // Get provider type safely
                    val providerType = getProviderType(transcriptionConfig.defaultProvider)
                    if (providerType == null) {
                        Log.w(TAG, "Invalid transcription provider: ${transcriptionConfig.defaultProvider}, skipping transcription")
                    } else {
                        val providerConfig = createProviderConfig(transcriptionConfig)
                        
                        val result = transcriptionService.transcribe(
                            audioFile = segmentFile,
                            language = settings.language.code,
                            providerType = providerType,
                            config = providerConfig
                        )
                        
                        result.onSuccess { transcribedText ->
                            // Update the item with transcription
                            val updatedItem = item.copy(
                                transcription = transcribedText,
                                language = settings.language.code
                            )
                            shadowItemDao.update(updatedItem)
                            Log.i(TAG, "Transcribed captured segment: $transcribedText")
                        }.onFailure { error ->
                            Log.w(TAG, "Failed to transcribe captured segment: ${error.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error during transcription of captured segment: ${e.message}")
            }

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
     * Split a single segment into two separate segments at the specified timestamp.
     * 
     * This method is useful for manually refining automatic segmentation when a detected
     * segment is too long or contains multiple distinct phrases. The original segment is
     * deleted and replaced with two new segments.
     * 
     * The split process:
     * 1. Validate split point (must leave MIN_SEGMENT_DURATION_MS on each side)
     * 2. Read source audio data
     * 3. Create two new PCM files
     * 4. Create two new ShadowItem records
     * 5. Delete original segment and file
     * 6. Update playlist ordering for subsequent items
     * 
     * @param item The [ShadowItem] to split. Must have a valid audio file.
     * @param splitPointMs The position within the segment to split, in milliseconds
     *                     relative to the segment start (not absolute time).
     *                     Must be at least [MIN_SEGMENT_DURATION_MS] (500ms) from either edge.
     * @return List containing two new [ShadowItem] objects on success, or null if:
     *         - Split point is too close to edges (< 500ms from start or end)
     *         - Source file doesn't exist
     *         - I/O errors occur
     *         - Database operations fail
     * 
     * Thread Safety: This method is thread-safe. It executes on [Dispatchers.IO] context.
     * However, splitting the same item concurrently from multiple coroutines may cause
     * race conditions.
     * 
     * Example usage:
     * ```kotlin
     * // Split a 5-second segment at 2.5 seconds
     * val item = shadowItemDao.getItemById(itemId)
     * val splitResult = audioImporter.splitSegment(item, splitPointMs = 2500)
     * 
     * splitResult?.let { (firstPart, secondPart) ->
     *     println("Split into:")
     *     println("  Part 1: ${firstPart.durationMs}ms")
     *     println("  Part 2: ${secondPart.durationMs}ms")
     *     // Update UI to show new segments
     * } ?: println("Split failed - invalid split point or file error")
     * ```
     * 
     * @see mergeSegments for the reverse operation
     * @see MIN_SEGMENT_DURATION_MS for minimum allowed segment length
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
     * Merge multiple consecutive segments into a single combined segment.
     * 
     * This method is useful for combining short segments that were over-segmented by
     * VAD or for creating longer practice phrases. Audio data is concatenated in order,
     * and transcriptions/translations are combined with spaces.
     * 
     * The merge process:
     * 1. Validate input (at least 2 items, all from same playlist)
     * 2. Sort items by playlist order
     * 3. Concatenate audio data into new PCM file
     * 4. Combine metadata (transcriptions, translations)
     * 5. Create new merged ShadowItem
     * 6. Delete original items and their audio files
     * 7. Update playlist ordering for subsequent items
     * 
     * @param items List of [ShadowItem] objects to merge. Requirements:
     *              - Minimum 2 items
     *              - All items must be from the same playlist
     *              - Items should be consecutive for logical merging (not enforced)
     * @return The new merged [ShadowItem] on success, or null if:
     *         - Less than 2 items provided
     *         - Items are from different playlists
     *         - Source files don't exist
     *         - I/O errors occur
     *         - Database operations fail
     * 
     * Thread Safety: This method is thread-safe. It executes on [Dispatchers.IO] context.
     * However, merging overlapping item sets concurrently may cause race conditions.
     * 
     * Example usage:
     * ```kotlin
     * // Merge three consecutive segments
     * val items = listOf(item1, item2, item3)
     * val mergedItem = audioImporter.mergeSegments(items)
     * 
     * mergedItem?.let { merged ->
     *     println("Merged ${items.size} segments:")
     *     println("  Total duration: ${merged.durationMs}ms")
     *     println("  Combined transcription: ${merged.transcription}")
     *     // Update UI to reflect merged segment
     * } ?: println("Merge failed - check items are from same playlist")
     * ```
     * 
     * Notes:
     * - Transcriptions and translations are space-separated in the merged result
     * - Practice statistics (count, last practiced) are reset for the merged item
     * - Playlist order is adjusted to fill the gap left by removed items
     * 
     * @see splitSegment for the reverse operation
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

    /**
     * Creates a ProviderConfig from TranscriptionConfig settings.
     * Helper method to avoid duplication when building provider configs.
     */
    private fun createProviderConfig(transcriptionConfig: com.shadowmaster.data.model.TranscriptionConfig): ProviderConfig {
        return ProviderConfig(
            googleApiKey = transcriptionConfig.googleApiKey,
            azureApiKey = transcriptionConfig.azureApiKey,
            azureRegion = transcriptionConfig.azureRegion,
            whisperApiKey = transcriptionConfig.whisperApiKey,
            localModelPath = transcriptionConfig.localModelPath,
            customEndpointUrl = transcriptionConfig.customEndpointUrl,
            customEndpointApiKey = transcriptionConfig.customEndpointApiKey,
            customEndpointHeaders = transcriptionConfig.customEndpointHeaders
        )
    }

    /**
     * Safely gets TranscriptionProviderType from string, handling invalid values.
     * Returns null if the provider name is not valid.
     */
    private fun getProviderType(providerName: String): TranscriptionProviderType? {
        return try {
            TranscriptionProviderType.valueOf(providerName.uppercase())
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid transcription provider name: $providerName")
            null
        }
    }
}

data class AudioSegmentBounds(
    val startMs: Long,
    val endMs: Long
)
