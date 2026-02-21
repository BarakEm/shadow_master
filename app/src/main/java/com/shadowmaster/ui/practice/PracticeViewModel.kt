package com.shadowmaster.ui.practice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.PlaybackParams
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.audio.BeepGenerator
import com.shadowmaster.feedback.AudioFeedbackSystem
import com.shadowmaster.data.model.ImportStatus
import com.shadowmaster.data.model.PracticeMode
import com.shadowmaster.data.model.ShadowItem
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject

/**
 * Offline practice states
 */
sealed class PracticeState {
    data object Loading : PracticeState()
    data object Ready : PracticeState()
    data class Playing(val itemIndex: Int, val repeatNumber: Int) : PracticeState()
    data class WaitingForUser(val itemIndex: Int, val repeatNumber: Int) : PracticeState()
    data class UserRecording(val itemIndex: Int, val repeatNumber: Int) : PracticeState()
    data object Paused : PracticeState()
    data object Finished : PracticeState()
}

@HiltViewModel
class PracticeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val audioFeedbackSystem: AudioFeedbackSystem,
    private val mediaControlManager: com.shadowmaster.media.MediaControlManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeViewModel"
        private const val SAMPLE_RATE = 16000
    }

    private val playlistId: String = savedStateHandle.get<String>("playlistId") ?: ""

    private val _state = MutableStateFlow<PracticeState>(PracticeState.Loading)
    val state: StateFlow<PracticeState> = _state.asStateFlow()

    private val _items = MutableStateFlow<List<ShadowItem>>(emptyList())
    val items: StateFlow<List<ShadowItem>> = _items.asStateFlow()

    private val _currentItemIndex = MutableStateFlow(0)
    val currentItemIndex: StateFlow<Int> = _currentItemIndex.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _importJobStatus = MutableStateFlow<ImportJobStatus?>(null)
    val importJobStatus: StateFlow<ImportJobStatus?> = _importJobStatus.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var practiceJob: Job? = null
    private var isPaused = false

    @Volatile private var _navigateRequest: Int? = null

    private val _loopModeEndless = MutableStateFlow(true)
    val loopModeEndless: StateFlow<Boolean> = _loopModeEndless.asStateFlow()

    private val _currentSpeed = MutableStateFlow(settingsRepository.configBlocking.playbackSpeed)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val config = settingsRepository.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = settingsRepository.configBlocking
        )

    init {
        audioFeedbackSystem.initialize()
        loadPlaylist()
        setupAudioFocusHandling()
    }

    private fun setupAudioFocusHandling() {
        // Pause playback when audio focus is lost (e.g., another app starts playing)
        mediaControlManager.setOnFocusLostCallback {
            if (_state.value is PracticeState.Playing || _state.value is PracticeState.UserRecording) {
                Log.d(TAG, "Audio focus lost - pausing practice")
                isPaused = true
                _state.value = PracticeState.Paused
            }
        }

        // Resume playback when audio focus is regained
        mediaControlManager.setOnFocusGainedCallback {
            if (_state.value is PracticeState.Paused) {
                Log.d(TAG, "Audio focus regained - resuming practice")
                isPaused = false
            }
        }
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            // Check import job status for this playlist first
            val job = libraryRepository.getImportJobForPlaylist(playlistId)
            _importJobStatus.value = when {
                job == null -> ImportJobStatus.UNKNOWN
                job.status == ImportStatus.FAILED -> ImportJobStatus.FAILED
                job.status == ImportStatus.COMPLETED -> ImportJobStatus.COMPLETED
                else -> ImportJobStatus.ACTIVE
            }

            // Continuously observe items to update UI when background import adds new items.
            // This is especially important for playlists that are still being processed.
            // The collection will be cancelled when the ViewModel is cleared.
            libraryRepository.getItemsByPlaylist(playlistId)
                .collect { loadedItems ->
                    _items.value = loadedItems
                    // Always set to Ready so UI can show appropriate message
                    // (either "No items" or actual items list)
                    if (_state.value == PracticeState.Loading) {
                        _state.value = PracticeState.Ready
                    }
                }
        }
    }

    fun startPractice() {
        if (_items.value.isEmpty()) return

        practiceJob?.cancel()
        practiceJob = viewModelScope.launch(Dispatchers.IO) {
            runPracticeLoop()
        }
    }

    fun toggleLoopMode() {
        _loopModeEndless.value = !_loopModeEndless.value
    }

    fun setSpeed(speed: Float) {
        _currentSpeed.value = speed
        try {
            audioTrack?.playbackParams = PlaybackParams().apply {
                setSpeed(speed)
                setPitch(1.0f)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not update playback speed on live track", e)
        }
    }

    fun navigatePrev() {
        val cur = _currentItemIndex.value
        if (cur > 0) {
            _navigateRequest = cur - 1
            audioTrack = null
        }
    }

    fun navigateNext() {
        val items = _items.value
        val cur = _currentItemIndex.value
        if (cur < items.size - 1) {
            _navigateRequest = cur + 1
            audioTrack = null
        }
    }

    private suspend fun runPracticeLoop() {
        val itemsList = _items.value
        val cfg = config.value
        val repeats = cfg.playbackRepeats
        val busMode = cfg.busMode
        val silenceBetweenRepeats = cfg.silenceBetweenRepeatsMs.toLong()

        var index = 0
        while (index < itemsList.size && coroutineContext.isActive) {
            _navigateRequest = null
            _currentItemIndex.value = index
            _progress.value = index.toFloat() / itemsList.size

            val item = itemsList[index]

            if (cfg.practiceMode == PracticeMode.BUILDUP && !busMode) {
                runBuildupForItem(index, item, cfg)
            } else {
                runStandardForItem(index, item, cfg, repeats, busMode, silenceBetweenRepeats)
            }

            if (!coroutineContext.isActive) break

            // Handle navigation request (from navigatePrev/navigateNext)
            val navReq = _navigateRequest
            if (navReq != null) {
                _navigateRequest = null
                index = navReq.coerceIn(0, itemsList.size - 1)
                continue
            }

            // In endless loop mode, replay same segment without advancing
            if (_loopModeEndless.value) {
                delay(300)
                continue
            }

            // Counted mode: brief pause then advance to next segment
            delay(500)
            if (index < itemsList.size - 1 && cfg.audioFeedbackEnabled) {
                playBeep(BeepGenerator.SEGMENT_END_BEEP_FREQ, cfg.beepDurationMs)
                delay(300)
            }
            index++
        }

        // Session complete (only reached in counted mode after all segments)
        if (coroutineContext.isActive && !_loopModeEndless.value) {
            if (config.value.audioFeedbackEnabled) {
                withContext(Dispatchers.Main) {
                    audioFeedbackSystem.playGoodScore()
                }
            }
            _state.value = PracticeState.Finished
            _progress.value = 1f
        }
    }

    /**
     * Standard practice: play full segment, user repeats.
     */
    private suspend fun runStandardForItem(
        index: Int,
        item: ShadowItem,
        cfg: com.shadowmaster.data.model.ShadowingConfig,
        repeats: Int,
        busMode: Boolean,
        silenceBetweenRepeats: Long
    ) {
        for (repeat in 1..repeats) {
            if (!coroutineContext.isActive || _navigateRequest != null) break

            while (isPaused && coroutineContext.isActive && _navigateRequest == null) { delay(100) }
            if (_navigateRequest != null) break

            _state.value = PracticeState.Playing(index, repeat)

            if (cfg.audioFeedbackEnabled) {
                playBeep(BeepGenerator.PLAYBACK_BEEP_FREQ, cfg.beepDurationMs)
                delay(BeepGenerator.PRE_BEEP_PAUSE_MS)
            }

            playAudioFile(item.audioFilePath)

            if (!coroutineContext.isActive || _navigateRequest != null) break

            if (busMode) {
                delay(silenceBetweenRepeats)
                if (repeat == repeats && cfg.audioFeedbackEnabled) {
                    playBeep(BeepGenerator.SEGMENT_END_BEEP_FREQ, cfg.beepDurationMs)
                }
                continue
            }

            delay(300)
            _state.value = PracticeState.UserRecording(index, repeat)

            if (cfg.audioFeedbackEnabled) {
                playDoubleBeep(BeepGenerator.YOUR_TURN_BEEP_FREQ, cfg.beepDurationMs)
                delay(BeepGenerator.PRE_BEEP_PAUSE_MS)
            }

            delay(item.durationMs)
            delay(300)

            libraryRepository.markItemPracticed(item.id)

            if (repeat < repeats) { delay(silenceBetweenRepeats) }
        }
    }

    /**
     * Backward buildup practice mode.
     *
     * Splits the segment audio into chunks and presents them from end to beginning,
     * gradually building up to the full phrase. Each buildup step is:
     *   1. Play the partial audio (from chunk N to end)
     *   2. Silence for user to repeat
     *
     * Example for a 4.5s segment with 1.5s chunks (3 chunks):
     *   Step 1: Play last 1.5s → user repeats
     *   Step 2: Play last 3.0s → user repeats
     *   Step 3: Play full 4.5s → user repeats
     *
     * This is the "back-chaining" technique used in language pedagogy.
     */
    private suspend fun runBuildupForItem(
        index: Int,
        item: ShadowItem,
        cfg: com.shadowmaster.data.model.ShadowingConfig
    ) {
        val file = File(item.audioFilePath)
        if (!file.exists()) return

        val audioData = file.readBytes()
        if (audioData.isEmpty()) return

        val chunkMs = cfg.buildupChunkMs.toLong()
        val totalMs = item.durationMs
        val bytesPerMs = (SAMPLE_RATE * 2) / 1000 // 16-bit mono

        // Calculate number of buildup steps
        // For short segments (<= chunk size), just play normally
        val numSteps = if (totalMs <= chunkMs) 1
                       else ((totalMs + chunkMs - 1) / chunkMs).toInt().coerceAtMost(8)

        for (step in 1..numSteps) {
            if (!coroutineContext.isActive || _navigateRequest != null) break

            while (isPaused && coroutineContext.isActive && _navigateRequest == null) { delay(100) }
            if (_navigateRequest != null) break

            // Calculate the portion to play: from (totalMs - step * chunkMs) to end
            val portionMs = if (step == numSteps) totalMs
                           else (step * chunkMs).coerceAtMost(totalMs)
            val startByteOffset = ((totalMs - portionMs) * bytesPerMs).toInt()
                .coerceIn(0, audioData.size)
            // Align to 2-byte boundary (16-bit samples)
            val alignedOffset = startByteOffset and 0xFFFFFFFE.toInt()

            _state.value = PracticeState.Playing(index, step)

            if (cfg.audioFeedbackEnabled) {
                playBeep(BeepGenerator.PLAYBACK_BEEP_FREQ, cfg.beepDurationMs)
                delay(BeepGenerator.PRE_BEEP_PAUSE_MS)
            }

            // Play the partial audio from alignedOffset to end
            playAudioData(audioData, alignedOffset, audioData.size - alignedOffset)

            if (!coroutineContext.isActive || _navigateRequest != null) break

            // User's turn
            delay(300)
            _state.value = PracticeState.UserRecording(index, step)

            if (cfg.audioFeedbackEnabled) {
                playDoubleBeep(BeepGenerator.YOUR_TURN_BEEP_FREQ, cfg.beepDurationMs)
                delay(BeepGenerator.PRE_BEEP_PAUSE_MS)
            }

            // Silence duration matches the portion just played
            delay(portionMs)
            delay(300)
        }

        libraryRepository.markItemPracticed(item.id)
    }

    /**
     * Play a beep using AudioTrack (same audio stream as speech for audibility).
     */
    private suspend fun playBeep(frequency: Double, durationMs: Int) {
        try {
            val beepData = BeepGenerator.generateBeep(frequency, durationMs)
            playAudioData(beepData, 0, beepData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing beep", e)
        }
    }

    /**
     * Play double beep (for "your turn" indicator).
     */
    private suspend fun playDoubleBeep(frequency: Double, durationMs: Int, gapMs: Long = BeepGenerator.DOUBLE_BEEP_GAP_MS) {
        try {
            playBeep(frequency, durationMs)
            delay(gapMs)
            playBeep(frequency, durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing double beep", e)
        }
    }

    /**
     * Play a portion of audio data directly (used by buildup mode).
     */
    private suspend fun playAudioData(audioData: ByteArray, offset: Int, length: Int) {
        var localTrack: AudioTrack? = null
        val speed = _currentSpeed.value
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferSize <= 0) return

            val bufferSize = maxOf(minBufferSize * 4, SAMPLE_RATE * 2)

            localTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (localTrack.state != AudioTrack.STATE_INITIALIZED) {
                localTrack.release()
                return
            }

            localTrack.playbackParams = PlaybackParams().apply {
                setSpeed(speed)
                setPitch(1.0f)
            }

            audioTrack = localTrack
            localTrack.play()

            var writeOffset = offset
            val end = offset + length
            val chunkSize = minBufferSize
            while (writeOffset < end) {
                if (isPaused) {
                    try { localTrack.pause() } catch (_: Exception) {}
                    while (isPaused && audioTrack != null) { delay(50) }
                    if (audioTrack == null) break
                    try { localTrack.play() } catch (_: Exception) {}
                }
                if (audioTrack == null) break

                val bytesToWrite = minOf(chunkSize, end - writeOffset)
                val written = localTrack.write(audioData, writeOffset, bytesToWrite)
                if (written < 0) break
                writeOffset += written
            }

            // Wait for buffer to drain
            val bufferDurationMs = (bufferSize.toLong() * 1000) / (SAMPLE_RATE * 2)
            delay((bufferDurationMs / speed).toLong() + 50)

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio data: ${e.message}", e)
        } finally {
            try { localTrack?.stop() } catch (_: Exception) {}
            try { localTrack?.release() } catch (_: Exception) {}
            if (audioTrack == localTrack) audioTrack = null
        }
    }

    private suspend fun playAudioFile(filePath: String) {
        var localTrack: AudioTrack? = null
        val speed = _currentSpeed.value
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "Audio file not found: $filePath")
                return
            }

            // Check file size to avoid OOM
            val fileSize = file.length()
            if (fileSize > 50 * 1024 * 1024) { // 50MB limit
                Log.e(TAG, "Audio file too large: $fileSize bytes")
                return
            }

            val audioData = file.readBytes()
            if (audioData.isEmpty()) {
                Log.e(TAG, "Audio file is empty: $filePath")
                return
            }

            // Create AudioTrack for PCM playback
            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // Validate buffer size - getMinBufferSize can return ERROR (-1) or ERROR_BAD_VALUE (-2)
            if (minBufferSize <= 0) {
                Log.e(TAG, "Invalid buffer size from getMinBufferSize: $minBufferSize")
                return
            }

            // Use larger buffer to prevent underruns and stuttering
            val bufferSize = maxOf(minBufferSize * 4, SAMPLE_RATE * 2) // At least 0.5s buffer

            localTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            // Verify AudioTrack was created successfully
            if (localTrack.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack failed to initialize, state: ${localTrack.state}")
                localTrack.release()
                return
            }

            // Apply playback speed
            val playbackParams = PlaybackParams().apply {
                setSpeed(speed)
                setPitch(1.0f) // Keep pitch constant
            }
            localTrack.playbackParams = playbackParams

            audioTrack = localTrack
            localTrack.play()

            // Write audio data in chunks
            var offset = 0
            val chunkSize = minBufferSize // Write in smaller chunks for responsiveness
            while (offset < audioData.size) {
                // Check for pause - wait without thrashing AudioTrack state
                if (isPaused) {
                    try { localTrack.pause() } catch (_: Exception) {}
                    while (isPaused && audioTrack != null) {
                        delay(50)
                    }
                    if (audioTrack == null) break
                    try { localTrack.play() } catch (_: Exception) {}
                }

                // Check if we should stop (e.g., skip was called)
                if (audioTrack == null) break

                val bytesToWrite = minOf(chunkSize, audioData.size - offset)
                val written = localTrack.write(audioData, offset, bytesToWrite)
                if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: $written")
                    break
                }
                offset += written
            }

            // Wait for AudioTrack to finish playing buffered data
            // Calculate remaining audio duration based on data written
            val audioDurationMs = (audioData.size.toLong() * 1000) / (SAMPLE_RATE * 2) // 16-bit mono
            val playbackDurationMs = (audioDurationMs / speed).toLong()
            // AudioTrack.write() blocks when buffer is full, so by the time we finish writing,
            // most audio has already played. Wait for the buffer to drain.
            val bufferDurationMs = (bufferSize.toLong() * 1000) / (SAMPLE_RATE * 2)
            delay((bufferDurationMs / speed).toLong() + 50)

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio: ${e.message}", e)
        } finally {
            // Always clean up
            try {
                localTrack?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping AudioTrack", e)
            }
            try {
                localTrack?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioTrack", e)
            }
            if (audioTrack == localTrack) {
                audioTrack = null
            }
        }
    }

    fun pauseResume() {
        isPaused = !isPaused
        if (isPaused) {
            _state.value = PracticeState.Paused
        }
    }

    fun skip() {
        // Signal playback to stop by nulling the reference
        // The playback loop will detect this and stop
        val track = audioTrack
        audioTrack = null
        try {
            track?.stop()
            track?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack in skip", e)
        }
    }

    fun skipToItem(index: Int) {
        if (index in _items.value.indices) {
            practiceJob?.cancel()
            skip() // Clean up current playback
            _currentItemIndex.value = index
            startPractice()
        }
    }

    fun stop() {
        practiceJob?.cancel()
        val track = audioTrack
        audioTrack = null
        try {
            track?.stop()
            track?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioTrack in stop", e)
        }
        _state.value = PracticeState.Ready
    }

    override fun onCleared() {
        super.onCleared()
        practiceJob?.cancel()
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack in onCleared", e)
        }
        audioTrack = null

        // Clean up audio focus callbacks
        mediaControlManager.setOnFocusLostCallback(null)
        mediaControlManager.setOnFocusGainedCallback(null)
    }
}

/**
 * Status of import job for a playlist
 */
enum class ImportJobStatus {
    ACTIVE,      // Import is in progress
    FAILED,      // Import failed
    COMPLETED,   // Import completed successfully
    UNKNOWN      // No import job found (playlist created manually or job deleted)
}
