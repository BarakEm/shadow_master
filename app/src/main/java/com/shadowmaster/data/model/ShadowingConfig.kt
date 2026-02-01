package com.shadowmaster.data.model

data class ShadowingConfig(
    val language: SupportedLanguage = SupportedLanguage.ENGLISH_US,
    val segmentMode: SegmentMode = SegmentMode.SENTENCE,
    val silenceThresholdMs: Int = 700,
    val playbackSpeed: Float = 0.8f,
    val playbackRepeats: Int = 1,
    val userRepeats: Int = 1,
    val assessmentEnabled: Boolean = true,
    val pauseForNavigation: Boolean = true,
    val busMode: Boolean = false,  // Passive listening - no user recording required
    val audioFeedbackEnabled: Boolean = true,  // Beeps for state transitions
    val beepVolume: Int = 80,  // Beep volume (0-100)
    val beepToneType: BeepToneType = BeepToneType.SOFT,  // Tone type for beeps
    val beepDurationMs: Int = 150,  // Duration of beeps in milliseconds
    val playbackUserRecording: Boolean = false,  // Play back user's recording after shadowing
    val silenceBetweenRepeatsMs: Int = 1000,  // Silence between repeats in bus mode
    val practiceMode: PracticeMode = PracticeMode.STANDARD,  // Learning approach
    val buildupChunkMs: Int = 1500,  // Target chunk size for buildup mode
    val transcription: TranscriptionConfig = TranscriptionConfig()  // Transcription service settings
) {
    companion object {
        const val MIN_SILENCE_THRESHOLD_MS = 300
        const val MAX_SILENCE_THRESHOLD_MS = 1500
        const val MIN_PLAYBACK_SPEED = 0.5f
        const val MAX_PLAYBACK_SPEED = 2.0f
        const val MIN_PLAYBACK_REPEATS = 1
        const val MAX_PLAYBACK_REPEATS = 5
        const val MIN_USER_REPEATS = 1
        const val MAX_USER_REPEATS = 3
        const val MIN_BEEP_VOLUME = 0
        const val MAX_BEEP_VOLUME = 100
        const val MIN_BEEP_DURATION_MS = 50
        const val MAX_BEEP_DURATION_MS = 500
    }
}

enum class SegmentMode {
    WORD,
    SENTENCE
}

/**
 * Practice mode determines how segments are presented to the learner.
 */
enum class PracticeMode {
    STANDARD,   // Play full segment, user repeats
    BUILDUP     // Backward buildup: play last chunk, then last 2 chunks, etc. until full segment
}

/**
 * Tone type for audio feedback beeps.
 * Maps to Android ToneGenerator tone constants.
 */
enum class BeepToneType(
    val displayName: String,
    val toneConstant: Int
) {
    SOFT("Soft", android.media.ToneGenerator.TONE_PROP_BEEP),
    STANDARD("Standard", android.media.ToneGenerator.TONE_PROP_ACK),
    ALERT("Alert", android.media.ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE),
    CONFIRM("Confirm", android.media.ToneGenerator.TONE_SUP_CONFIRM),
    ERROR("Error", android.media.ToneGenerator.TONE_SUP_ERROR)
}

enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val azureLocale: String
) {
    ENGLISH_US("en-US", "English (US)", "en-US"),
    GERMAN("de-DE", "German", "de-DE"),
    SPANISH("es-ES", "Spanish", "es-ES"),
    RUSSIAN("ru-RU", "Russian", "ru-RU"),
    HEBREW("he-IL", "Hebrew", "he-IL"),
    ARABIC("ar-SA", "Arabic", "ar-SA"),
    FRENCH("fr-FR", "French", "fr-FR"),
    ITALIAN("it-IT", "Italian", "it-IT"),
    PORTUGUESE("pt-BR", "Portuguese", "pt-BR"),
    MANDARIN("zh-CN", "Mandarin", "zh-CN");

    companion object {
        fun fromCode(code: String): SupportedLanguage {
            return entries.find { it.code == code } ?: ENGLISH_US
        }
    }
}

/**
 * Configuration for automatic transcription services.
 */
data class TranscriptionConfig(
    val defaultProvider: String = "google",  // google, azure, whisper, custom
    val autoTranscribeOnImport: Boolean = false,
    
    // Provider-specific settings
    val googleApiKey: String? = null,
    val azureApiKey: String? = null,
    val azureRegion: String? = null,
    val whisperApiKey: String? = null,
    val customEndpointUrl: String? = null,
    val customEndpointApiKey: String? = null,
    val customEndpointHeaders: Map<String, String> = emptyMap()
)
