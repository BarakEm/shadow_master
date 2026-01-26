package com.shadowmaster.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.shadowmaster.data.model.SegmentMode
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.model.SupportedLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        val PLAYBACK_USER_RECORDING = booleanPreferencesKey("playback_user_recording")
        val SILENCE_BETWEEN_REPEATS_MS = intPreferencesKey("silence_between_repeats_ms")
    }

    // Blocking access for initial value (use sparingly)
    val configBlocking: ShadowingConfig
        get() = ShadowingConfig()

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
            playbackUserRecording = preferences[Keys.PLAYBACK_USER_RECORDING] ?: false,
            silenceBetweenRepeatsMs = preferences[Keys.SILENCE_BETWEEN_REPEATS_MS] ?: 1000
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
            preferences[Keys.PLAYBACK_USER_RECORDING] = config.playbackUserRecording
            preferences[Keys.SILENCE_BETWEEN_REPEATS_MS] = config.silenceBetweenRepeatsMs
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
}
