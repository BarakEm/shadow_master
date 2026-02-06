package com.shadowmaster.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.shadowmaster.data.model.BeepToneType
import com.shadowmaster.data.model.PracticeMode
import com.shadowmaster.data.model.SegmentMode
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.model.SupportedLanguage
import com.shadowmaster.data.model.TranslationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val SEGMENT_MODE = stringPreferencesKey("segment_mode")
        val SILENCE_THRESHOLD_MS = intPreferencesKey("silence_threshold_ms")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_REPEATS = intPreferencesKey("playback_repeats")
        val USER_REPEATS = intPreferencesKey("user_repeats")
        val ASSESSMENT_ENABLED = booleanPreferencesKey("assessment_enabled")
        val PAUSE_FOR_NAVIGATION = booleanPreferencesKey("pause_for_navigation")
        val BUS_MODE = booleanPreferencesKey("bus_mode")
        val AUDIO_FEEDBACK_ENABLED = booleanPreferencesKey("audio_feedback_enabled")
        val BEEP_VOLUME = intPreferencesKey("beep_volume")
        val BEEP_TONE_TYPE = stringPreferencesKey("beep_tone_type")
        val BEEP_DURATION_MS = intPreferencesKey("beep_duration_ms")
        val PLAYBACK_USER_RECORDING = booleanPreferencesKey("playback_user_recording")
        val SILENCE_BETWEEN_REPEATS_MS = intPreferencesKey("silence_between_repeats_ms")
        val PRACTICE_MODE = stringPreferencesKey("practice_mode")
        val BUILDUP_CHUNK_MS = intPreferencesKey("buildup_chunk_ms")
        
        // Transcription settings
        val TRANSCRIPTION_DEFAULT_PROVIDER = stringPreferencesKey("transcription_default_provider")
        val TRANSCRIPTION_AUTO_ON_IMPORT = booleanPreferencesKey("transcription_auto_on_import")
        val TRANSCRIPTION_IVRIT_API_KEY = stringPreferencesKey("transcription_ivrit_api_key")
        val TRANSCRIPTION_GOOGLE_API_KEY = stringPreferencesKey("transcription_google_api_key")
        val TRANSCRIPTION_AZURE_API_KEY = stringPreferencesKey("transcription_azure_api_key")
        val TRANSCRIPTION_AZURE_REGION = stringPreferencesKey("transcription_azure_region")
        val TRANSCRIPTION_WHISPER_API_KEY = stringPreferencesKey("transcription_whisper_api_key")
        val TRANSCRIPTION_CUSTOM_URL = stringPreferencesKey("transcription_custom_url")
        val TRANSCRIPTION_CUSTOM_API_KEY = stringPreferencesKey("transcription_custom_api_key")
        val TRANSCRIPTION_LOCAL_MODEL_PATH = stringPreferencesKey("transcription_local_model_path")
        val TRANSCRIPTION_LOCAL_MODEL_NAME = stringPreferencesKey("transcription_local_model_name")

        // Translation config keys
        val TRANSLATION_DEFAULT_PROVIDER = stringPreferencesKey("translation_default_provider")
        val TRANSLATION_TARGET_LANGUAGE = stringPreferencesKey("translation_target_language")
        val TRANSLATION_AUTO_TRANSLATE = booleanPreferencesKey("translation_auto_translate")
        val TRANSLATION_GOOGLE_API_KEY = stringPreferencesKey("translation_google_api_key")
        val TRANSLATION_GOOGLE_ENABLED = booleanPreferencesKey("translation_google_enabled")
        val TRANSLATION_DEEPL_API_KEY = stringPreferencesKey("translation_deepl_api_key")
        val TRANSLATION_DEEPL_ENABLED = booleanPreferencesKey("translation_deepl_enabled")
        val TRANSLATION_CUSTOM_URL = stringPreferencesKey("translation_custom_url")
        val TRANSLATION_CUSTOM_API_KEY = stringPreferencesKey("translation_custom_api_key")
        val TRANSLATION_CUSTOM_ENABLED = booleanPreferencesKey("translation_custom_enabled")
    }

    // Blocking access for StateFlow initialValue only
    // Reads actual config from DataStore synchronously using runBlocking
    // ONLY use for StateFlow's initialValue parameter during ViewModel initialization
    // For all other cases, use config.first() within a coroutine context
    val configBlocking: ShadowingConfig
        get() = runBlocking { config.first() }

    val config: Flow<ShadowingConfig> = context.dataStore.data.map { preferences ->
        ShadowingConfig(
            language = SupportedLanguage.fromCode(
                preferences[Keys.LANGUAGE] ?: SupportedLanguage.ENGLISH_US.code
            ),
            segmentMode = try {
                SegmentMode.valueOf(preferences[Keys.SEGMENT_MODE] ?: SegmentMode.SENTENCE.name)
            } catch (e: IllegalArgumentException) {
                SegmentMode.SENTENCE
            },
            silenceThresholdMs = preferences[Keys.SILENCE_THRESHOLD_MS] ?: 700,
            playbackSpeed = preferences[Keys.PLAYBACK_SPEED] ?: 0.8f,
            playbackRepeats = preferences[Keys.PLAYBACK_REPEATS] ?: 1,
            userRepeats = preferences[Keys.USER_REPEATS] ?: 1,
            assessmentEnabled = preferences[Keys.ASSESSMENT_ENABLED] ?: true,
            pauseForNavigation = preferences[Keys.PAUSE_FOR_NAVIGATION] ?: true,
            busMode = preferences[Keys.BUS_MODE] ?: false,
            audioFeedbackEnabled = preferences[Keys.AUDIO_FEEDBACK_ENABLED] ?: true,
            beepVolume = preferences[Keys.BEEP_VOLUME] ?: 80,
            beepToneType = try {
                BeepToneType.valueOf(preferences[Keys.BEEP_TONE_TYPE] ?: BeepToneType.SOFT.name)
            } catch (e: IllegalArgumentException) {
                BeepToneType.SOFT
            },
            beepDurationMs = preferences[Keys.BEEP_DURATION_MS] ?: 150,
            playbackUserRecording = preferences[Keys.PLAYBACK_USER_RECORDING] ?: false,
            silenceBetweenRepeatsMs = preferences[Keys.SILENCE_BETWEEN_REPEATS_MS] ?: 1000,
            practiceMode = try {
                PracticeMode.valueOf(preferences[Keys.PRACTICE_MODE] ?: PracticeMode.STANDARD.name)
            } catch (e: IllegalArgumentException) {
                PracticeMode.STANDARD
            },
            buildupChunkMs = preferences[Keys.BUILDUP_CHUNK_MS] ?: 1500,
            transcription = com.shadowmaster.data.model.TranscriptionConfig(
                defaultProvider = preferences[Keys.TRANSCRIPTION_DEFAULT_PROVIDER] ?: "ivrit",
                autoTranscribeOnImport = preferences[Keys.TRANSCRIPTION_AUTO_ON_IMPORT] ?: false,
                ivritApiKey = preferences[Keys.TRANSCRIPTION_IVRIT_API_KEY],
                googleApiKey = preferences[Keys.TRANSCRIPTION_GOOGLE_API_KEY],
                azureApiKey = preferences[Keys.TRANSCRIPTION_AZURE_API_KEY],
                azureRegion = preferences[Keys.TRANSCRIPTION_AZURE_REGION],
                whisperApiKey = preferences[Keys.TRANSCRIPTION_WHISPER_API_KEY],
                customEndpointUrl = preferences[Keys.TRANSCRIPTION_CUSTOM_URL],
                customEndpointApiKey = preferences[Keys.TRANSCRIPTION_CUSTOM_API_KEY],
                localModelPath = preferences[Keys.TRANSCRIPTION_LOCAL_MODEL_PATH],
                localModelName = preferences[Keys.TRANSCRIPTION_LOCAL_MODEL_NAME]
            ),
            translationConfig = TranslationConfig(
                defaultProvider = preferences[Keys.TRANSLATION_DEFAULT_PROVIDER] ?: "mock",
                targetLanguage = preferences[Keys.TRANSLATION_TARGET_LANGUAGE] ?: "en",
                autoTranslateOnTranscribe = preferences[Keys.TRANSLATION_AUTO_TRANSLATE] ?: false,
                googleApiKey = preferences[Keys.TRANSLATION_GOOGLE_API_KEY] ?: "",
                googleEnabled = preferences[Keys.TRANSLATION_GOOGLE_ENABLED] ?: false,
                deeplApiKey = preferences[Keys.TRANSLATION_DEEPL_API_KEY] ?: "",
                deeplEnabled = preferences[Keys.TRANSLATION_DEEPL_ENABLED] ?: false,
                customEndpointUrl = preferences[Keys.TRANSLATION_CUSTOM_URL] ?: "",
                customEndpointApiKey = preferences[Keys.TRANSLATION_CUSTOM_API_KEY] ?: "",
                customEnabled = preferences[Keys.TRANSLATION_CUSTOM_ENABLED] ?: false
            )
        )
    }

    suspend fun updateLanguage(language: SupportedLanguage) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun updateSegmentMode(mode: SegmentMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SEGMENT_MODE] = mode.name
        }
    }

    suspend fun updateSilenceThreshold(thresholdMs: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SILENCE_THRESHOLD_MS] = thresholdMs.coerceIn(
                ShadowingConfig.MIN_SILENCE_THRESHOLD_MS,
                ShadowingConfig.MAX_SILENCE_THRESHOLD_MS
            )
        }
    }

    suspend fun updatePlaybackSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PLAYBACK_SPEED] = speed.coerceIn(
                ShadowingConfig.MIN_PLAYBACK_SPEED,
                ShadowingConfig.MAX_PLAYBACK_SPEED
            )
        }
    }

    suspend fun updatePlaybackRepeats(repeats: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PLAYBACK_REPEATS] = repeats.coerceIn(
                ShadowingConfig.MIN_PLAYBACK_REPEATS,
                ShadowingConfig.MAX_PLAYBACK_REPEATS
            )
        }
    }

    suspend fun updateUserRepeats(repeats: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.USER_REPEATS] = repeats.coerceIn(
                ShadowingConfig.MIN_USER_REPEATS,
                ShadowingConfig.MAX_USER_REPEATS
            )
        }
    }

    suspend fun updateAssessmentEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.ASSESSMENT_ENABLED] = enabled
        }
    }

    suspend fun updatePauseForNavigation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PAUSE_FOR_NAVIGATION] = enabled
        }
    }

    suspend fun updateBusMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BUS_MODE] = enabled
        }
    }

    suspend fun updateAudioFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.AUDIO_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun updateBeepVolume(volume: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BEEP_VOLUME] = volume.coerceIn(
                ShadowingConfig.MIN_BEEP_VOLUME,
                ShadowingConfig.MAX_BEEP_VOLUME
            )
        }
    }

    suspend fun updateBeepToneType(toneType: BeepToneType) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BEEP_TONE_TYPE] = toneType.name
        }
    }

    suspend fun updateBeepDurationMs(durationMs: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BEEP_DURATION_MS] = durationMs.coerceIn(
                ShadowingConfig.MIN_BEEP_DURATION_MS,
                ShadowingConfig.MAX_BEEP_DURATION_MS
            )
        }
    }

    suspend fun updateConfig(config: ShadowingConfig) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LANGUAGE] = config.language.code
            preferences[Keys.SEGMENT_MODE] = config.segmentMode.name
            preferences[Keys.SILENCE_THRESHOLD_MS] = config.silenceThresholdMs
            preferences[Keys.PLAYBACK_SPEED] = config.playbackSpeed
            preferences[Keys.PLAYBACK_REPEATS] = config.playbackRepeats
            preferences[Keys.USER_REPEATS] = config.userRepeats
            preferences[Keys.ASSESSMENT_ENABLED] = config.assessmentEnabled
            preferences[Keys.PAUSE_FOR_NAVIGATION] = config.pauseForNavigation
            preferences[Keys.BUS_MODE] = config.busMode
            preferences[Keys.AUDIO_FEEDBACK_ENABLED] = config.audioFeedbackEnabled
            preferences[Keys.BEEP_VOLUME] = config.beepVolume
            preferences[Keys.BEEP_TONE_TYPE] = config.beepToneType.name
            preferences[Keys.BEEP_DURATION_MS] = config.beepDurationMs
            preferences[Keys.PLAYBACK_USER_RECORDING] = config.playbackUserRecording
            preferences[Keys.SILENCE_BETWEEN_REPEATS_MS] = config.silenceBetweenRepeatsMs
            preferences[Keys.PRACTICE_MODE] = config.practiceMode.name
            preferences[Keys.BUILDUP_CHUNK_MS] = config.buildupChunkMs
            
            // Translation config
            preferences[Keys.TRANSLATION_DEFAULT_PROVIDER] = config.translationConfig.defaultProvider
            preferences[Keys.TRANSLATION_TARGET_LANGUAGE] = config.translationConfig.targetLanguage
            preferences[Keys.TRANSLATION_AUTO_TRANSLATE] = config.translationConfig.autoTranslateOnTranscribe
            preferences[Keys.TRANSLATION_GOOGLE_API_KEY] = config.translationConfig.googleApiKey
            preferences[Keys.TRANSLATION_GOOGLE_ENABLED] = config.translationConfig.googleEnabled
            preferences[Keys.TRANSLATION_DEEPL_API_KEY] = config.translationConfig.deeplApiKey
            preferences[Keys.TRANSLATION_DEEPL_ENABLED] = config.translationConfig.deeplEnabled
            preferences[Keys.TRANSLATION_CUSTOM_URL] = config.translationConfig.customEndpointUrl
            preferences[Keys.TRANSLATION_CUSTOM_API_KEY] = config.translationConfig.customEndpointApiKey
            preferences[Keys.TRANSLATION_CUSTOM_ENABLED] = config.translationConfig.customEnabled
        }
    }

    suspend fun updatePlaybackUserRecording(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PLAYBACK_USER_RECORDING] = enabled
        }
    }

    suspend fun updateSilenceBetweenRepeats(ms: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SILENCE_BETWEEN_REPEATS_MS] = ms.coerceIn(500, 3000)
        }
    }

    suspend fun updatePracticeMode(mode: PracticeMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.PRACTICE_MODE] = mode.name
        }
    }

    suspend fun updateBuildupChunkMs(ms: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.BUILDUP_CHUNK_MS] = ms.coerceIn(500, 3000)
        }
    }

    // ==================== Transcription Settings ====================

    suspend fun updateTranscriptionDefaultProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSCRIPTION_DEFAULT_PROVIDER] = provider
        }
    }

    suspend fun updateTranscriptionAutoOnImport(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSCRIPTION_AUTO_ON_IMPORT] = enabled
        }
    }

    suspend fun updateTranscriptionIvritApiKey(apiKey: String?) {
        context.dataStore.edit { preferences ->
            if (apiKey.isNullOrBlank()) {
                preferences.remove(Keys.TRANSCRIPTION_IVRIT_API_KEY)
            } else {
                preferences[Keys.TRANSCRIPTION_IVRIT_API_KEY] = apiKey
            }
        }
    }

    suspend fun updateTranscriptionGoogleApiKey(apiKey: String?) {
        context.dataStore.edit { preferences ->
            if (apiKey.isNullOrBlank()) {
                preferences.remove(Keys.TRANSCRIPTION_GOOGLE_API_KEY)
            } else {
                preferences[Keys.TRANSCRIPTION_GOOGLE_API_KEY] = apiKey
            }
        }
    }

    suspend fun updateTranscriptionAzureApiKey(apiKey: String?) {
        context.dataStore.edit { preferences ->
            if (apiKey.isNullOrBlank()) {
                preferences.remove(Keys.TRANSCRIPTION_AZURE_API_KEY)
            } else {
                preferences[Keys.TRANSCRIPTION_AZURE_API_KEY] = apiKey
            }
        }
    }

    suspend fun updateTranscriptionAzureRegion(region: String?) {
        context.dataStore.edit { preferences ->
            if (region.isNullOrBlank()) {
                preferences.remove(Keys.TRANSCRIPTION_AZURE_REGION)
            } else {
                preferences[Keys.TRANSCRIPTION_AZURE_REGION] = region
            }
        }
    }

    suspend fun updateTranscriptionWhisperApiKey(apiKey: String?) {
        context.dataStore.edit { preferences ->
            if (apiKey.isNullOrBlank()) {
                preferences.remove(Keys.TRANSCRIPTION_WHISPER_API_KEY)
            } else {
                preferences[Keys.TRANSCRIPTION_WHISPER_API_KEY] = apiKey
            }
        }
    }

    suspend fun updateTranscriptionCustomUrl(url: String?) {
        context.dataStore.edit { preferences ->
            if (url.isNullOrBlank()) {
                preferences.remove(Keys.TRANSCRIPTION_CUSTOM_URL)
            } else {
                preferences[Keys.TRANSCRIPTION_CUSTOM_URL] = url
            }
        }
    }

    suspend fun updateTranscriptionCustomApiKey(apiKey: String?) {
        context.dataStore.edit { preferences ->
            if (apiKey.isNullOrBlank()) {
                preferences.remove(Keys.TRANSCRIPTION_CUSTOM_API_KEY)
            } else {
                preferences[Keys.TRANSCRIPTION_CUSTOM_API_KEY] = apiKey
            }
        }
    }

    suspend fun updateTranscriptionLocalModelPath(path: String?) {
        context.dataStore.edit { preferences ->
            if (path.isNullOrBlank()) {
                preferences.remove(Keys.TRANSCRIPTION_LOCAL_MODEL_PATH)
            } else {
                preferences[Keys.TRANSCRIPTION_LOCAL_MODEL_PATH] = path
            }
        }
    }

    suspend fun updateTranscriptionLocalModelName(name: String?) {
        context.dataStore.edit { preferences ->
            if (name.isNullOrBlank()) {
                preferences.remove(Keys.TRANSCRIPTION_LOCAL_MODEL_NAME)
            } else {
                preferences[Keys.TRANSCRIPTION_LOCAL_MODEL_NAME] = name
            }
        }
    }

    // ==================== Translation Settings ====================

    suspend fun updateTranslationDefaultProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_DEFAULT_PROVIDER] = provider
        }
    }

    suspend fun updateTranslationTargetLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_TARGET_LANGUAGE] = language
        }
    }

    suspend fun updateTranslationAutoTranslate(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_AUTO_TRANSLATE] = enabled
        }
    }

    suspend fun updateTranslationGoogleApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_GOOGLE_API_KEY] = apiKey
        }
    }

    suspend fun updateTranslationGoogleEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_GOOGLE_ENABLED] = enabled
        }
    }

    suspend fun updateTranslationDeeplApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_DEEPL_API_KEY] = apiKey
        }
    }

    suspend fun updateTranslationDeeplEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_DEEPL_ENABLED] = enabled
        }
    }

    suspend fun updateTranslationCustomUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_CUSTOM_URL] = url
        }
    }

    suspend fun updateTranslationCustomApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_CUSTOM_API_KEY] = apiKey
        }
    }

    suspend fun updateTranslationCustomEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TRANSLATION_CUSTOM_ENABLED] = enabled
        }
    }
}
