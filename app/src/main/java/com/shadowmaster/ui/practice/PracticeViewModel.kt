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
        val repeats = config.value.playbackRepeats
        val busMode = config.value.busMode

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

                // Play the segment
                _state.value = PracticeState.Playing(index, repeat)

                if (config.value.audioFeedbackEnabled) {
                    withContext(Dispatchers.Main) {
                        audioFeedbackSystem.playPlaybackStart()
                    }
                }

                playAudioFile(item.audioFilePath)

                if (!coroutineContext.isActive) break

                // In bus mode, skip user recording - just move to next
                if (busMode) {
                    delay(500) // Brief pause between repeats in bus mode
                    continue
                }

                // Wait for user response
                _state.value = PracticeState.WaitingForUser(index, repeat)

                if (config.value.audioFeedbackEnabled) {
                    withContext(Dispatchers.Main) {
                        audioFeedbackSystem.playListening()
                    }
                }

                // Simulate waiting for user recording (duration of original + buffer)
                _state.value = PracticeState.UserRecording(index, repeat)

                if (config.value.audioFeedbackEnabled) {
                    withContext(Dispatchers.Main) {
                        audioFeedbackSystem.playRecordingStart()
                    }
                }

                delay(item.durationMs + 500) // Give user time to shadow

                // Mark item as practiced
                libraryRepository.markItemPracticed(item.id)
            }

            // Brief pause between items
            delay(300)
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
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

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
        // Cancel current and let loop continue
        audioTrack?.stop()
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
