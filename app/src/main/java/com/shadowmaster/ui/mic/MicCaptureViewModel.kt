package com.shadowmaster.ui.mic

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.audio.recording.UserRecordingManager
import com.shadowmaster.data.model.AudioSegment
import com.shadowmaster.library.AudioImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MicCaptureState {
    IDLE,
    RECORDING,
    PROCESSING,
    SAVED,
    ERROR
}

@HiltViewModel
class MicCaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRecordingManager: UserRecordingManager,
    private val audioImporter: AudioImporter
) : ViewModel() {

    private val _captureState = MutableStateFlow(MicCaptureState.IDLE)
    val captureState: StateFlow<MicCaptureState> = _captureState.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _savedCount = MutableStateFlow(0)
    val savedCount: StateFlow<Int> = _savedCount.asStateFlow()

    private var recordingStartTime: Long = 0
    private var pendingSegment: AudioSegment? = null

    init {
        // Update duration while recording
        viewModelScope.launch {
            while (true) {
                if (_captureState.value == MicCaptureState.RECORDING && recordingStartTime > 0) {
                    _recordingDuration.value = System.currentTimeMillis() - recordingStartTime
                }
                delay(100)
            }
        }
    }

    fun startRecording() {
        if (_captureState.value == MicCaptureState.RECORDING) {
            return
        }

        _captureState.value = MicCaptureState.RECORDING
        _errorMessage.value = null
        recordingStartTime = System.currentTimeMillis()
        _recordingDuration.value = 0

        userRecordingManager.startRecording { segment ->
            viewModelScope.launch {
                if (segment != null) {
                    pendingSegment = segment
                    _recordingDuration.value = segment.durationMs
                    saveRecording()
                } else {
                    _captureState.value = MicCaptureState.ERROR
                    _errorMessage.value = "Recording failed or was too short"
                }
            }
        }
    }

    fun stopRecording() {
        if (_captureState.value != MicCaptureState.RECORDING) {
            return
        }
        userRecordingManager.stopRecording()
        // The callback in startRecording will handle the result
    }

    private fun saveRecording() {
        val segment = pendingSegment ?: return

        viewModelScope.launch {
            _captureState.value = MicCaptureState.PROCESSING

            try {
                val savedItem = audioImporter.saveCapturedSegment(segment)
                if (savedItem != null) {
                    _savedCount.value += 1
                    _captureState.value = MicCaptureState.SAVED
                    pendingSegment = null
                } else {
                    _captureState.value = MicCaptureState.ERROR
                    _errorMessage.value = "Failed to save recording"
                }
            } catch (e: Exception) {
                _captureState.value = MicCaptureState.ERROR
                _errorMessage.value = e.message ?: "Unknown error"
            }
        }
    }

    fun resetCapture() {
        _captureState.value = MicCaptureState.IDLE
        _recordingDuration.value = 0
        _errorMessage.value = null
        pendingSegment = null
        recordingStartTime = 0
    }

    fun clearError() {
        _errorMessage.value = null
        _captureState.value = MicCaptureState.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        userRecordingManager.release()
    }
}
