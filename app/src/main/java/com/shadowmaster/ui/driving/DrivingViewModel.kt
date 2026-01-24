package com.shadowmaster.ui.driving

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.core.ShadowingCoordinator
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.model.ShadowingState
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.service.ShadowingForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DrivingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowingCoordinator: ShadowingCoordinator,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val state: StateFlow<ShadowingState> = shadowingCoordinator.state

    val config: StateFlow<ShadowingConfig> = settingsRepository.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ShadowingConfig()
        )

    val isSessionActive: StateFlow<Boolean> = state.map { it !is ShadowingState.Idle }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun getMediaProjectionIntent(): Intent {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return projectionManager.createScreenCaptureIntent()
    }

    fun startSession(resultCode: Int, resultData: Intent) {
        val serviceIntent = Intent(context, ShadowingForegroundService::class.java).apply {
            action = ShadowingForegroundService.ACTION_START
            putExtra(ShadowingForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ShadowingForegroundService.EXTRA_RESULT_DATA, resultData)
        }
        context.startForegroundService(serviceIntent)
    }

    fun stopSession() {
        val serviceIntent = Intent(context, ShadowingForegroundService::class.java).apply {
            action = ShadowingForegroundService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun skip() {
        shadowingCoordinator.skip()
    }
}
