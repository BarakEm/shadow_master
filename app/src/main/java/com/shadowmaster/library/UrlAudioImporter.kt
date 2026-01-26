package com.shadowmaster.library

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shadowmaster.data.local.ImportJobDao
import com.shadowmaster.data.local.ShadowPlaylistDao
import com.shadowmaster.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports audio from URLs (YouTube, Spotify podcasts, direct audio links).
 */
@Singleton
class UrlAudioImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shadowPlaylistDao: ShadowPlaylistDao,
    private val importJobDao: ImportJobDao,
    private val audioImporter: AudioImporter
) {
    companion object {
        private const val TAG = "UrlAudioImporter"

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
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadDir: File by lazy {
        File(context.cacheDir, "url_downloads").also { it.mkdirs() }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _importProgress = MutableStateFlow<UrlImportProgress?>(null)
    val importProgress: StateFlow<UrlImportProgress?> = _importProgress.asStateFlow()

    /**
     * Detect the type of URL and extract relevant info.
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

        // Unknown - might be supported by NewPipe
        return UrlType.UNKNOWN
    }

    /**
     * Import audio from a URL.
     */
    suspend fun importFromUrl(
        url: String,
        playlistName: String? = null,
        language: String = "auto"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            _importProgress.value = UrlImportProgress(
                url = url,
                status = UrlImportStatus.ANALYZING,
                progress = 0
            )

            val urlType = detectUrlType(url)
            Log.i(TAG, "Detected URL type: $urlType for $url")

            val result = when (urlType) {
                UrlType.YOUTUBE -> importFromYouTube(url, playlistName, language)
                UrlType.SPOTIFY_PODCAST -> importFromSpotify(url, playlistName, language)
                UrlType.DIRECT_AUDIO -> importFromDirectUrl(url, playlistName, language)
                UrlType.UNKNOWN -> handleUnknownUrl(url, playlistName, language)
            }

            _importProgress.value = null
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import from URL", e)
            _importProgress.value = UrlImportProgress(
                url = url,
                status = UrlImportStatus.FAILED,
                progress = 0,
                error = e.message
            )
            Result.failure(e)
        }
    }

    /**
     * Import from YouTube.
     * Note: Direct YouTube import requires additional setup.
     * For now, guide users to use the Capture feature.
     */
    private suspend fun importFromYouTube(
        url: String,
        playlistName: String?,
        language: String
    ): Result<String> {
        // YouTube videos require special handling due to their streaming format
        // Guide users to use the Capture feature instead
        return Result.failure(
            Exception(
                "YouTube videos cannot be directly imported. " +
                "Please use 'Capture Playing Audio' while the video plays, " +
                "or download the audio separately and import the file."
            )
        )
    }

    /**
     * Import from Spotify podcast episode.
     * Note: Spotify requires authentication for direct download.
     * This provides a user-friendly error message.
     */
    private suspend fun importFromSpotify(
        url: String,
        playlistName: String?,
        language: String
    ): Result<String> {
        // Spotify podcasts are DRM-protected and require OAuth
        // We could integrate with spotify-dl or spotdl in the future
        return Result.failure(
            Exception(
                "Spotify podcasts require authentication. " +
                "Please download the podcast episode manually and import the audio file, " +
                "or use the 'Capture Playing Audio' feature while listening."
            )
        )
    }

    /**
     * Import from a direct audio URL.
     */
    private suspend fun importFromDirectUrl(
        url: String,
        playlistName: String?,
        language: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            _importProgress.value = _importProgress.value?.copy(
                status = UrlImportStatus.DOWNLOADING
            )

            // Extract filename from URL
            val fileName = Uri.parse(url).lastPathSegment ?: "audio_${System.currentTimeMillis()}"
            val title = playlistName ?: fileName.substringBeforeLast(".")

            // Download the file
            val audioFile = downloadAudio(url, title)
            if (audioFile == null) {
                return@withContext Result.failure(Exception("Failed to download audio"))
            }

            _importProgress.value = _importProgress.value?.copy(
                status = UrlImportStatus.PROCESSING,
                title = title
            )

            val uri = Uri.fromFile(audioFile)
            val result = audioImporter.importAudioFile(
                uri = uri,
                playlistName = title,
                language = language,
                enableTranscription = false
            )

            // Clean up
            scope.launch {
                delay(5000)
                audioFile.delete()
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Direct URL import failed", e)
            Result.failure(e)
        }
    }

    /**
     * Handle unknown URL types.
     */
    private suspend fun handleUnknownUrl(
        url: String,
        playlistName: String?,
        language: String
    ): Result<String> {
        // For unknown URLs, guide users to alternatives
        return Result.failure(
            Exception(
                "This URL type is not supported for direct import. " +
                "Please use 'Capture Playing Audio' while the content plays, " +
                "or download the audio file and import it directly."
            )
        )
    }

    /**
     * Download audio from URL to a temporary file.
     */
    private suspend fun downloadAudio(url: String, title: String): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: ${response.code}")
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()

            // Determine file extension
            val contentType = response.header("Content-Type") ?: "audio/mp4"
            val extension = when {
                contentType.contains("mp3") -> "mp3"
                contentType.contains("mp4") || contentType.contains("m4a") -> "m4a"
                contentType.contains("ogg") -> "ogg"
                contentType.contains("wav") -> "wav"
                contentType.contains("webm") -> "webm"
                else -> "m4a"
            }

            val safeTitle = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50)
            val outputFile = File(downloadDir, "${safeTitle}_${System.currentTimeMillis()}.$extension")

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Update progress
                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            _importProgress.value = _importProgress.value?.copy(
                                progress = progress
                            )
                        }
                    }
                }
            }

            Log.i(TAG, "Downloaded ${outputFile.length()} bytes to ${outputFile.name}")
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Download error", e)
            null
        }
    }

    fun clearProgress() {
        _importProgress.value = null
    }

    fun release() {
        scope.cancel()
    }
}

enum class UrlType {
    YOUTUBE,
    SPOTIFY_PODCAST,
    DIRECT_AUDIO,
    UNKNOWN
}

enum class UrlImportStatus {
    ANALYZING,
    EXTRACTING_INFO,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class UrlImportProgress(
    val url: String,
    val status: UrlImportStatus,
    val progress: Int,
    val title: String? = null,
    val error: String? = null
)
