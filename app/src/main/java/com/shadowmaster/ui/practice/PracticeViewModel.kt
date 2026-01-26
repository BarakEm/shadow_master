package com.shadowmaster.ui.practice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.feedback.AudioFeedbackSystem
import com.shadowmaster.data.model.ShadowItem
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    data object Transcribing : PracticeState()
}

@HiltViewModel
class PracticeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val audioFeedbackSystem: AudioFeedbackSystem,
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

    // Loop mode for sentence-by-sentence practice
    private val _isLoopMode = MutableStateFlow(false)
    val isLoopMode: StateFlow<Boolean> = _isLoopMode.asStateFlow()

    // Transcription state
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    // Thread-safe AudioTrack management
    private val audioTrackMutex = Mutex()
    private var audioTrack: AudioTrack? = null
    private var practiceJob: Job? = null
    private var singlePlayJob: Job? = null
    private var isPaused = false

    // Flag to stop current playback
    @Volatile
    private var stopPlayback = false

    private val config = settingsRepository.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = settingsRepository.configBlocking
        )

    init {
        audioFeedbackSystem.initialize()
        loadPlaylist()
    }

    private fun loadPlaylist() {
        viewModelScope.launch {
            libraryRepository.getItemsByPlaylist(playlistId)
                .first()
                .let { loadedItems ->
                    _items.value = loadedItems
                    if (loadedItems.isNotEmpty()) {
                        _state.value = PracticeState.Ready
                    }
                }
        }
    }

    fun startPractice() {
        if (_items.value.isEmpty()) return

        practiceJob?.cancel()
        singlePlayJob?.cancel()
        practiceJob = viewModelScope.launch(Dispatchers.IO) {
            runPracticeLoop()
        }
    }

    /**
     * Play only the current segment (for sentence mode).
     * Loops if loop mode is enabled.
     */
    fun playCurrentSegment() {
        val items = _items.value
        val index = _currentItemIndex.value
        if (index !in items.indices) return

        practiceJob?.cancel()
        singlePlayJob?.cancel()
        stopPlayback = false

        singlePlayJob = viewModelScope.launch(Dispatchers.IO) {
            do {
                if (!isActive || stopPlayback) break

                _state.value = PracticeState.Playing(index, 1)
                playAudioFile(items[index].audioFilePath)

                if (_isLoopMode.value && isActive && !stopPlayback) {
                    delay(500) // Brief pause between loops
                }
            } while (_isLoopMode.value && isActive && !stopPlayback)

            if (isActive && !stopPlayback) {
                _state.value = PracticeState.Ready
            }
        }
    }

    /**
     * Toggle loop mode for current segment.
     */
    fun toggleLoopMode() {
        _isLoopMode.value = !_isLoopMode.value
    }

    /**
     * Navigate to previous segment.
     */
    fun previousSegment() {
        val currentIndex = _currentItemIndex.value
        if (currentIndex > 0) {
            stopCurrentPlayback()
            _currentItemIndex.value = currentIndex - 1
            _progress.value = _currentItemIndex.value.toFloat() / _items.value.size
        }
    }

    /**
     * Navigate to next segment.
     */
    fun nextSegment() {
        val items = _items.value
        val currentIndex = _currentItemIndex.value
        if (currentIndex < items.size - 1) {
            stopCurrentPlayback()
            _currentItemIndex.value = currentIndex + 1
            _progress.value = _currentItemIndex.value.toFloat() / items.size
        }
    }

    /**
     * Transcribe the current segment using Azure Speech SDK.
     */
    fun transcribeCurrentSegment() {
        val items = _items.value
        val index = _currentItemIndex.value
        if (index !in items.indices) return

        val item = items[index]
        // Skip if already transcribed
        if (!item.transcription.isNullOrBlank()) return

        viewModelScope.launch {
            _isTranscribing.value = true
            val previousState = _state.value
            _state.value = PracticeState.Transcribing

            try {
                val transcription = transcribeAudioFile(item.audioFilePath)
                if (transcription != null) {
                    // Update the item with transcription
                    libraryRepository.updateTranscription(item.id, transcription)
                    // Refresh the items list
                    libraryRepository.getItemsByPlaylist(playlistId)
                        .first()
                        .let { loadedItems ->
                            _items.value = loadedItems
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
            } finally {
                _isTranscribing.value = false
                _state.value = previousState
            }
        }
    }

    private suspend fun transcribeAudioFile(filePath: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Log.e(TAG, "Audio file not found for transcription: $filePath")
                    return@withContext null
                }

                // Read the PCM audio data
                val audioData = file.readBytes()
                if (audioData.isEmpty()) {
                    Log.e(TAG, "Empty audio file: $filePath")
                    return@withContext null
                }

                // Use Azure Speech SDK for transcription
                // Note: Requires AZURE_SPEECH_KEY and AZURE_SPEECH_REGION in BuildConfig
                transcribeWithAzure(audioData)
            } catch (e: Exception) {
                Log.e(TAG, "Error transcribing audio", e)
                null
            }
        }
    }

    private fun transcribeWithAzure(audioData: ByteArray): String? {
        return try {
            // Azure Speech SDK transcription
            // The SDK is already included as a dependency
            val speechKey = com.shadowmaster.BuildConfig.AZURE_SPEECH_KEY
            val speechRegion = com.shadowmaster.BuildConfig.AZURE_SPEECH_REGION

            if (speechKey.isBlank() || speechKey == "your_azure_speech_key_here") {
                Log.w(TAG, "Azure Speech key not configured")
                return null
            }

            val speechConfig = com.microsoft.cognitiveservices.speech.SpeechConfig.fromSubscription(
                speechKey,
                speechRegion
            )

            // Configure for the audio format (16kHz, mono, 16-bit PCM)
            val audioFormat = com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat.getWaveFormatPCM(
                SAMPLE_RATE.toLong(),
                16.toShort(),
                1.toShort()
            )

            val pushStream = com.microsoft.cognitiveservices.speech.audio.PushAudioInputStream.create(audioFormat)
            pushStream.write(audioData)
            pushStream.close()

            val audioConfig = com.microsoft.cognitiveservices.speech.audio.AudioConfig.fromStreamInput(pushStream)
            val recognizer = com.microsoft.cognitiveservices.speech.SpeechRecognizer(speechConfig, audioConfig)

            val result = recognizer.recognizeOnceAsync().get()

            recognizer.close()
            audioConfig.close()
            speechConfig.close()

            when (result.reason) {
                com.microsoft.cognitiveservices.speech.ResultReason.RecognizedSpeech -> {
                    Log.d(TAG, "Transcription successful: ${result.text}")
                    result.text
                }
                com.microsoft.cognitiveservices.speech.ResultReason.NoMatch -> {
                    Log.w(TAG, "No speech recognized")
                    null
                }
                else -> {
                    Log.e(TAG, "Transcription failed: ${result.reason}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Azure transcription error", e)
            null
        }
    }

    private fun stopCurrentPlayback() {
        stopPlayback = true
        singlePlayJob?.cancel()
        viewModelScope.launch {
            releaseAudioTrackSafely()
        }
    }

    private suspend fun runPracticeLoop() {
        val itemsList = _items.value
        val cfg = config.value
        val repeats = cfg.playbackRepeats
        val busMode = cfg.busMode
        val silenceBetweenRepeats = cfg.silenceBetweenRepeatsMs.toLong()

        var index = _currentItemIndex.value
        while (index < itemsList.size) {
            if (!coroutineContext.isActive) break

            _currentItemIndex.value = index
            _progress.value = index.toFloat() / itemsList.size

            val item = itemsList[index]

            for (repeat in 1..repeats) {
                if (!coroutineContext.isActive) break

                // Wait if paused
                while (isPaused && coroutineContext.isActive) {
                    delay(100)
                }

                // Play the audio segment
                _state.value = PracticeState.Playing(index, repeat)

                if (cfg.audioFeedbackEnabled) {
                    withContext(Dispatchers.Main) {
                        audioFeedbackSystem.playPlaybackStart()
                    }
                    delay(150) // Brief pause after beep before audio starts
                }

                playAudioFile(item.audioFilePath)

                if (!coroutineContext.isActive) break

                // In bus mode: just play audio with silences and gentle beeps
                if (busMode) {
                    // Comfortable silence between repeats
                    delay(silenceBetweenRepeats)

                    // If this is the last repeat for this item, play a softer transition beep
                    if (repeat == repeats && cfg.audioFeedbackEnabled) {
                        withContext(Dispatchers.Main) {
                            audioFeedbackSystem.playListening() // Gentle acknowledgment
                        }
                    }
                    continue
                }

                // Regular practice mode: user shadows the audio

                // Brief pause before "your turn" beep
                delay(300)

                // Play "your turn" beep - gentle double beep
                _state.value = PracticeState.UserRecording(index, repeat)

                if (cfg.audioFeedbackEnabled) {
                    withContext(Dispatchers.Main) {
                        audioFeedbackSystem.playRecordingStart()
                    }
                    delay(200) // Brief pause after beep
                }

                // Silent period for user to shadow (same duration as the audio)
                delay(item.durationMs)

                // Small buffer after shadowing
                delay(300)

                // Mark item as practiced after user had chance to shadow
                libraryRepository.markItemPracticed(item.id)

                // Pause between repeats if more repeats coming
                if (repeat < repeats) {
                    delay(silenceBetweenRepeats)
                }
            }

            // In loop mode, stay on current segment
            if (_isLoopMode.value) {
                delay(500)
                continue
            }

            // Brief pause between items
            delay(500)

            // Play transition beep for next item
            if (index < itemsList.size - 1 && cfg.audioFeedbackEnabled) {
                withContext(Dispatchers.Main) {
                    audioFeedbackSystem.playListening()
                }
                delay(300)
            }

            index++
        }

        // Session complete
        if (config.value.audioFeedbackEnabled) {
            withContext(Dispatchers.Main) {
                audioFeedbackSystem.playGoodScore() // Celebration tone
            }
        }

        _state.value = PracticeState.Finished
        _progress.value = 1f
    }

    private suspend fun playAudioFile(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "Audio file not found: $filePath")
                return
            }

            val audioData = file.readBytes()
            if (audioData.isEmpty()) {
                Log.e(TAG, "Empty audio file: $filePath")
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

            // Use 2x minimum buffer for stability
            val bufferSize = minBufferSize * 2

            val track = AudioTrack.Builder()
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

            // Store reference with mutex protection
            audioTrackMutex.withLock {
                audioTrack = track
            }

            track.play()

            // Write audio data in chunks
            var offset = 0
            val chunkSize = bufferSize
            while (offset < audioData.size && !stopPlayback) {
                // Check track state safely
                val currentTrack = audioTrackMutex.withLock { audioTrack }
                if (currentTrack == null || currentTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    break
                }

                // Check for pause
                while (isPaused && !stopPlayback) {
                    audioTrackMutex.withLock {
                        audioTrack?.pause()
                    }
                    delay(100)
                    if (!isPaused) {
                        audioTrackMutex.withLock {
                            audioTrack?.play()
                        }
                    }
                }

                if (stopPlayback) break

                val bytesToWrite = minOf(chunkSize, audioData.size - offset)
                val written = currentTrack.write(audioData, offset, bytesToWrite)
                if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: $written")
                    break
                }
                offset += written
            }

            // Wait for playback to complete
            if (!stopPlayback) {
                delay(100)
            }

            // Release the track
            releaseAudioTrackSafely()

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
            releaseAudioTrackSafely()
        }
    }

    private suspend fun releaseAudioTrackSafely() {
        audioTrackMutex.withLock {
            try {
                audioTrack?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AudioTrack", e)
            }
            try {
                audioTrack?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing AudioTrack", e)
            }
            audioTrack = null
        }
    }

    fun pauseResume() {
        isPaused = !isPaused
        if (isPaused) {
            _state.value = PracticeState.Paused
        }
    }

    fun skip() {
        // Stop current playback and move to next
        stopCurrentPlayback()
    }

    fun skipToItem(index: Int) {
        if (index in _items.value.indices) {
            stopCurrentPlayback()
            practiceJob?.cancel()
            _currentItemIndex.value = index
            _progress.value = index.toFloat() / _items.value.size
            startPractice()
        }
    }

    fun stop() {
        stopPlayback = true
        practiceJob?.cancel()
        singlePlayJob?.cancel()
        viewModelScope.launch {
            releaseAudioTrackSafely()
            _state.value = PracticeState.Ready
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback = true
        practiceJob?.cancel()
        singlePlayJob?.cancel()
        runBlocking {
            releaseAudioTrackSafely()
        }
    }
}
