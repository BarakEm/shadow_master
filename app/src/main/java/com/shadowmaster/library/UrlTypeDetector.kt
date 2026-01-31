package com.shadowmaster.library

import java.util.regex.Pattern

/**
 * Detects and parses different types of URLs for audio import.
 * Supports YouTube, Spotify podcasts, and direct audio links.
 */
object UrlTypeDetector {
    // URL patterns
    private val YOUTUBE_PATTERNS = listOf(
        Pattern.compile("(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?v=([\\w-]+)"),
        Pattern.compile("(?:https?://)?(?:www\\.)?youtu\\.be/([\\w-]+)"),
        Pattern.compile("(?:https?://)?(?:m\\.)?youtube\\.com/watch\\?v=([\\w-]+)")
    )
    private val SPOTIFY_PODCAST_PATTERN =
        Pattern.compile("(?:https?://)?open\\.spotify\\.com/episode/([\\w]+)")
    private val DIRECT_AUDIO_PATTERN =
        Pattern.compile("(?:https?://).*\\.(mp3|m4a|wav|ogg|aac|flac)(?:\\?.*)?$", Pattern.CASE_INSENSITIVE)
    private val URL_VALIDATION_REGEX = Regex("https?://[^\\s]+")

    /**
     * Detect the type of URL.
     */
    fun detectUrlType(url: String): UrlType {
        // Check YouTube
        for (pattern in YOUTUBE_PATTERNS) {
            if (pattern.matcher(url).find()) {
                return UrlType.YOUTUBE
            }
        }

        // Check Spotify podcast
        if (SPOTIFY_PODCAST_PATTERN.matcher(url).find()) {
            return UrlType.SPOTIFY_PODCAST
        }

        // Check direct audio
        if (DIRECT_AUDIO_PATTERN.matcher(url).find()) {
            return UrlType.DIRECT_AUDIO
        }

        // Unknown - might be supported by other tools
        return UrlType.UNKNOWN
    }

    /**
     * Extract video ID from a YouTube URL.
     * Returns null if URL is not a valid YouTube URL.
     */
    fun extractYouTubeVideoId(url: String): String? {
        for (pattern in YOUTUBE_PATTERNS) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    /**
     * Extract episode ID from a Spotify podcast URL.
     * Returns null if URL is not a valid Spotify podcast URL.
     */
    fun extractSpotifyEpisodeId(url: String): String? {
        val matcher = SPOTIFY_PODCAST_PATTERN.matcher(url)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    /**
     * Validate and normalize a URL.
     * Adds https:// prefix if missing.
     * Returns null if URL format is invalid.
     */
    fun normalizeUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        // Add https:// if no scheme is present
        val normalized = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }

        // Basic validation - check if it looks like a URL
        return if (normalized.matches(URL_VALIDATION_REGEX)) {
            normalized
        } else {
            null
        }
    }
}
