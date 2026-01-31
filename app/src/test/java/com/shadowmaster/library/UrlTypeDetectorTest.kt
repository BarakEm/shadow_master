package com.shadowmaster.library

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for UrlTypeDetector.
 */
class UrlTypeDetectorTest {

    @Test
    fun `test YouTube URL detection - standard format`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.YOUTUBE, type)
    }

    @Test
    fun `test YouTube URL detection - short format`() {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.YOUTUBE, type)
    }

    @Test
    fun `test YouTube URL detection - mobile format`() {
        val url = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.YOUTUBE, type)
    }

    @Test
    fun `test YouTube URL detection - without protocol`() {
        val url = "youtube.com/watch?v=dQw4w9WgXcQ"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.YOUTUBE, type)
    }

    @Test
    fun `test YouTube URL detection - without www`() {
        val url = "https://youtube.com/watch?v=dQw4w9WgXcQ"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.YOUTUBE, type)
    }

    @Test
    fun `test Spotify podcast URL detection`() {
        val url = "https://open.spotify.com/episode/1a2b3c4d5e"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.SPOTIFY_PODCAST, type)
    }

    @Test
    fun `test Spotify podcast URL detection - without protocol`() {
        val url = "open.spotify.com/episode/AbC123"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.SPOTIFY_PODCAST, type)
    }

    @Test
    fun `test direct audio URL detection - mp3`() {
        val url = "https://example.com/audio/file.mp3"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.DIRECT_AUDIO, type)
    }

    @Test
    fun `test direct audio URL detection - m4a`() {
        val url = "https://cdn.example.com/media/podcast.m4a"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.DIRECT_AUDIO, type)
    }

    @Test
    fun `test direct audio URL detection - wav`() {
        val url = "https://server.com/sounds/effect.wav"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.DIRECT_AUDIO, type)
    }

    @Test
    fun `test direct audio URL detection - ogg`() {
        val url = "https://audio.com/track.ogg"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.DIRECT_AUDIO, type)
    }

    @Test
    fun `test direct audio URL detection - aac`() {
        val url = "https://music.example.com/song.aac"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.DIRECT_AUDIO, type)
    }

    @Test
    fun `test direct audio URL detection - flac`() {
        val url = "https://hifi.com/lossless.flac"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.DIRECT_AUDIO, type)
    }

    @Test
    fun `test direct audio URL detection - with query parameters`() {
        val url = "https://example.com/file.mp3?token=abc123&expires=456"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.DIRECT_AUDIO, type)
    }

    @Test
    fun `test direct audio URL detection - case insensitive`() {
        val url = "https://example.com/audio.MP3"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.DIRECT_AUDIO, type)
    }

    @Test
    fun `test unknown URL type`() {
        val url = "https://example.com/page.html"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.UNKNOWN, type)
    }

    @Test
    fun `test unknown URL type - plain domain`() {
        val url = "https://example.com"
        val type = UrlTypeDetector.detectUrlType(url)
        assertEquals(UrlType.UNKNOWN, type)
    }

    @Test
    fun `test extractYouTubeVideoId - standard format`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val videoId = UrlTypeDetector.extractYouTubeVideoId(url)
        assertEquals("dQw4w9WgXcQ", videoId)
    }

    @Test
    fun `test extractYouTubeVideoId - short format`() {
        val url = "https://youtu.be/abc123XYZ"
        val videoId = UrlTypeDetector.extractYouTubeVideoId(url)
        assertEquals("abc123XYZ", videoId)
    }

    @Test
    fun `test extractYouTubeVideoId - mobile format`() {
        val url = "https://m.youtube.com/watch?v=test-video"
        val videoId = UrlTypeDetector.extractYouTubeVideoId(url)
        assertEquals("test-video", videoId)
    }

    @Test
    fun `test extractYouTubeVideoId - non-YouTube URL`() {
        val url = "https://example.com/video"
        val videoId = UrlTypeDetector.extractYouTubeVideoId(url)
        assertNull(videoId)
    }

    @Test
    fun `test extractSpotifyEpisodeId - valid URL`() {
        val url = "https://open.spotify.com/episode/ABC123xyz"
        val episodeId = UrlTypeDetector.extractSpotifyEpisodeId(url)
        assertEquals("ABC123xyz", episodeId)
    }

    @Test
    fun `test extractSpotifyEpisodeId - without protocol`() {
        val url = "open.spotify.com/episode/episode456"
        val episodeId = UrlTypeDetector.extractSpotifyEpisodeId(url)
        assertEquals("episode456", episodeId)
    }

    @Test
    fun `test extractSpotifyEpisodeId - non-Spotify URL`() {
        val url = "https://example.com/podcast"
        val episodeId = UrlTypeDetector.extractSpotifyEpisodeId(url)
        assertNull(episodeId)
    }

    @Test
    fun `test normalizeUrl - with https protocol`() {
        val url = "https://example.com"
        val normalized = UrlTypeDetector.normalizeUrl(url)
        assertEquals("https://example.com", normalized)
    }

    @Test
    fun `test normalizeUrl - with http protocol`() {
        val url = "http://example.com"
        val normalized = UrlTypeDetector.normalizeUrl(url)
        assertEquals("http://example.com", normalized)
    }

    @Test
    fun `test normalizeUrl - without protocol`() {
        val url = "example.com/path"
        val normalized = UrlTypeDetector.normalizeUrl(url)
        assertEquals("https://example.com/path", normalized)
    }

    @Test
    fun `test normalizeUrl - with spaces trimmed`() {
        val url = "  https://example.com  "
        val normalized = UrlTypeDetector.normalizeUrl(url)
        assertEquals("https://example.com", normalized)
    }

    @Test
    fun `test normalizeUrl - empty string`() {
        val url = ""
        val normalized = UrlTypeDetector.normalizeUrl(url)
        assertNull(normalized)
    }

    @Test
    fun `test normalizeUrl - whitespace only`() {
        val url = "   "
        val normalized = UrlTypeDetector.normalizeUrl(url)
        assertNull(normalized)
    }

    @Test
    fun `test normalizeUrl - invalid format`() {
        val url = "not a url"
        val normalized = UrlTypeDetector.normalizeUrl(url)
        assertNull(normalized)
    }

    @Test
    fun `test normalizeUrl - adds https to domain without protocol`() {
        val url = "youtube.com/watch?v=test"
        val normalized = UrlTypeDetector.normalizeUrl(url)
        assertEquals("https://youtube.com/watch?v=test", normalized)
    }
}
