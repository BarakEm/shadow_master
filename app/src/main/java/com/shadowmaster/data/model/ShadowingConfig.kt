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
    val playbackUserRecording: Boolean = false,  // Play back user's recording after shadowing
    val silenceBetweenRepeatsMs: Int = 1000  // Silence between repeats in bus mode
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
    }
}

enum class SegmentMode {
    WORD,
    SENTENCE
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
