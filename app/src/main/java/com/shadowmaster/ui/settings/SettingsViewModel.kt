package com.shadowmaster.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.data.model.BeepToneType
import com.shadowmaster.data.model.PracticeMode
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

    fun updateBusMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateBusMode(enabled)
        }
    }

    fun updateAudioFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAudioFeedbackEnabled(enabled)
        }
    }

    fun updateBeepVolume(volume: Int) {
        viewModelScope.launch {
            settingsRepository.updateBeepVolume(volume)
        }
    }

    fun updateBeepToneType(toneType: BeepToneType) {
        viewModelScope.launch {
            settingsRepository.updateBeepToneType(toneType)
        }
    }

    fun updateBeepDurationMs(durationMs: Int) {
        viewModelScope.launch {
            settingsRepository.updateBeepDurationMs(durationMs)
        }
    }

    fun updatePracticeMode(mode: PracticeMode) {
        viewModelScope.launch {
            settingsRepository.updatePracticeMode(mode)
        }
    }

    fun updateBuildupChunkMs(ms: Int) {
        viewModelScope.launch {
            settingsRepository.updateBuildupChunkMs(ms)
        }
    }

    // ==================== Transcription Settings ====================

    fun updateTranscriptionDefaultProvider(provider: String) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionDefaultProvider(provider)
        }
    }

    fun updateTranscriptionAutoOnImport(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionAutoOnImport(enabled)
        }
    }

    fun updateTranscriptionIvritApiKey(apiKey: String?) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionIvritApiKey(apiKey)
        }
    }

    fun updateTranscriptionGoogleApiKey(apiKey: String?) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionGoogleApiKey(apiKey)
        }
    }

    fun updateTranscriptionAzureApiKey(apiKey: String?) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionAzureApiKey(apiKey)
        }
    }

    fun updateTranscriptionAzureRegion(region: String?) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionAzureRegion(region)
        }
    }

    fun updateTranscriptionWhisperApiKey(apiKey: String?) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionWhisperApiKey(apiKey)
        }
    }

    fun updateTranscriptionCustomUrl(url: String?) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionCustomUrl(url)
        }
    }

    fun updateTranscriptionCustomApiKey(apiKey: String?) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionCustomApiKey(apiKey)
        }
    }

    fun updateTranscriptionLocalModelPath(path: String?) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionLocalModelPath(path)
        }
    }

    fun updateTranscriptionLocalModelName(name: String?) {
        viewModelScope.launch {
            settingsRepository.updateTranscriptionLocalModelName(name)
        }
    }

    // ==================== Translation Settings ====================

    fun updateTranslationDefaultProvider(provider: String) {
        viewModelScope.launch {
            settingsRepository.updateTranslationDefaultProvider(provider)
        }
    }

    fun updateTranslationTargetLanguage(language: String) {
        viewModelScope.launch {
            settingsRepository.updateTranslationTargetLanguage(language)
        }
    }

    fun updateTranslationAutoTranslate(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateTranslationAutoTranslate(enabled)
        }
    }

    fun updateTranslationGoogleApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.updateTranslationGoogleApiKey(apiKey)
        }
    }

    fun updateTranslationGoogleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateTranslationGoogleEnabled(enabled)
        }
    }

    fun updateTranslationDeeplApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.updateTranslationDeeplApiKey(apiKey)
        }
    }

    fun updateTranslationDeeplEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateTranslationDeeplEnabled(enabled)
        }
    }

    fun updateTranslationCustomUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.updateTranslationCustomUrl(url)
        }
    }

    fun updateTranslationCustomApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsRepository.updateTranslationCustomApiKey(apiKey)
        }
    }

    fun updateTranslationCustomEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateTranslationCustomEnabled(enabled)
        }
    }
}
