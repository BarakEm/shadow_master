package com.shadowmaster.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.data.model.SegmentMode
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.model.SupportedLanguage
import com.shadowmaster.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val config: StateFlow<ShadowingConfig> = settingsRepository.config
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ShadowingConfig()
        )

    fun updateLanguage(language: SupportedLanguage) {
        viewModelScope.launch {
            settingsRepository.updateLanguage(language)
        }
    }

    fun updateSegmentMode(mode: SegmentMode) {
        viewModelScope.launch {
            settingsRepository.updateSegmentMode(mode)
        }
    }

    fun updateSilenceThreshold(thresholdMs: Int) {
        viewModelScope.launch {
            settingsRepository.updateSilenceThreshold(thresholdMs)
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepository.updatePlaybackSpeed(speed)
        }
    }

    fun updatePlaybackRepeats(repeats: Int) {
        viewModelScope.launch {
            settingsRepository.updatePlaybackRepeats(repeats)
        }
    }

    fun updateUserRepeats(repeats: Int) {
        viewModelScope.launch {
            settingsRepository.updateUserRepeats(repeats)
        }
    }

    fun updateAssessmentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAssessmentEnabled(enabled)
        }
    }

    fun updatePauseForNavigation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updatePauseForNavigation(enabled)
        }
    }
}
