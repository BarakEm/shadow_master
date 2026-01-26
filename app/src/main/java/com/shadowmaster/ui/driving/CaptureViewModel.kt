package com.shadowmaster.ui.driving

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.library.LibraryRepository
import com.shadowmaster.service.AudioCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _captureState = MutableStateFlow(CaptureState.IDLE)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _capturedDuration = MutableStateFlow(0L)
    val capturedDuration: StateFlow<Long> = _capturedDuration.asStateFlow()

    private val _importProgress = MutableStateFlow(0)
    val importProgress: StateFlow<Int> = _importProgress.asStateFlow()

    private var capturedFile: File? = null
    private var captureStartTime: Long = 0

    init {
        // Observe service state
        viewModelScope.launch {
            AudioCaptureService.captureState.collect { state ->
                when (state) {
                    AudioCaptureService.CaptureServiceState.IDLE -> {
                        if (_captureState.value == CaptureState.CAPTURING) {
                            // Capture was stopped externally
                            _isCapturing.value = false
                        }
                    }
                    AudioCaptureService.CaptureServiceState.CAPTURING -> {
                        _captureState.value = CaptureState.CAPTURING
                        _isCapturing.value = true
                    }
                    AudioCaptureService.CaptureServiceState.STOPPED -> {
                        _isCapturing.value = false
                        val file = AudioCaptureService.capturedFile
                        if (file != null && file.exists() && file.length() > 0) {
                            capturedFile = file
                            _captureState.value = CaptureState.CAPTURED
                        }
                    }
                    AudioCaptureService.CaptureServiceState.ERROR -> {
                        _isCapturing.value = false
                        _captureState.value = CaptureState.ERROR
                    }
                }
            }
        }

        // Update duration while capturing
        viewModelScope.launch {
            while (true) {
                if (_isCapturing.value && captureStartTime > 0) {
                    _capturedDuration.value = System.currentTimeMillis() - captureStartTime
                }
                delay(100)
            }
        }
    }

    fun getMediaProjectionIntent(): Intent {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return projectionManager.createScreenCaptureIntent()
    }

    fun startCapture(resultCode: Int, resultData: Intent) {
        captureStartTime = System.currentTimeMillis()
        _capturedDuration.value = 0

        val serviceIntent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START
            putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(AudioCaptureService.EXTRA_RESULT_DATA, resultData)
        }
        context.startForegroundService(serviceIntent)
    }

    fun stopCapture() {
        val serviceIntent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun saveToLibrary() {
        val file = capturedFile ?: return

        viewModelScope.launch {
            _captureState.value = CaptureState.IMPORTING

            try {
                val result = libraryRepository.importAudioFile(
                    uri = Uri.fromFile(file),
                    playlistName = "Captured ${formatTimestamp()}",
                    language = "auto",
                    enableTranscription = false
                )

                result.onSuccess {
                    _captureState.value = CaptureState.SAVED
                    // Clean up captured file
                    file.delete()
                    capturedFile = null
                }

                result.onFailure {
                    _captureState.value = CaptureState.ERROR
                }
            } catch (e: Exception) {
                _captureState.value = CaptureState.ERROR
            }
        }
    }

    fun discardCapture() {
        capturedFile?.delete()
        capturedFile = null
        _capturedDuration.value = 0
        _captureState.value = CaptureState.IDLE
    }

    fun resetCapture() {
        _capturedDuration.value = 0
        _captureState.value = CaptureState.IDLE
        capturedFile = null
    }

    private fun formatTimestamp(): String {
        val sdf = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}
