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

    private var audioTrack: AudioTrack? = null
    private var practiceJob: Job? = null
    private var isPaused = false

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
        practiceJob = viewModelScope.launch(Dispatchers.IO) {
            runPracticeLoop()
        }
    }

    private suspend fun runPracticeLoop() {
        val itemsList = _items.value
        val cfg = config.value
        val repeats = cfg.playbackRepeats
        val busMode = cfg.busMode
        val silenceBetweenRepeats = cfg.silenceBetweenRepeatsMs.toLong()

        for (index in itemsList.indices) {
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

            // Brief pause between items
            delay(500)

            // Play transition beep for next item
            if (index < itemsList.size - 1 && cfg.audioFeedbackEnabled) {
                withContext(Dispatchers.Main) {
                    audioFeedbackSystem.playListening()
                }
                delay(300)
            }
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

            // Use 2x minimum buffer for stability (same as PlaybackEngine)
            val bufferSize = minBufferSize * 2

            audioTrack = AudioTrack.Builder()
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

            audioTrack?.play()

            // Write audio data in chunks
            var offset = 0
            val chunkSize = bufferSize
            while (offset < audioData.size && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                // Check for pause
                while (isPaused && audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack?.pause()
                    delay(100)
                    if (!isPaused) {
                        audioTrack?.play()
                    }
                }

                val bytesToWrite = minOf(chunkSize, audioData.size - offset)
                audioTrack?.write(audioData, offset, bytesToWrite)
                offset += bytesToWrite
            }

            // Wait for playback to complete
            delay(100)

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio", e)
        }
    }

    fun pauseResume() {
        isPaused = !isPaused
        if (isPaused) {
            _state.value = PracticeState.Paused
        }
    }

    fun skip() {
        // Cancel current playback and release resources
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun skipToItem(index: Int) {
        if (index in _items.value.indices) {
            practiceJob?.cancel()
            _currentItemIndex.value = index
            startPractice()
        }
    }

    fun stop() {
        practiceJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        _state.value = PracticeState.Ready
    }

    override fun onCleared() {
        super.onCleared()
        practiceJob?.cancel()
        audioTrack?.release()
    }
}
