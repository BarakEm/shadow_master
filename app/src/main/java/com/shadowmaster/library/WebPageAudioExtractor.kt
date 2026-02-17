package com.shadowmaster.library

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/**
 * Extracts audio URLs from web page HTML by scanning for common audio embedding patterns.
 */
class WebPageAudioExtractor(private val httpClient: OkHttpClient) {

    companion object {
        private const val TAG = "WebPageAudioExtractor"
        private val AUDIO_EXTENSIONS = listOf("mp3", "m4a", "wav", "ogg", "aac", "flac", "opus", "webm")
        private val AUDIO_URL_PATTERN = Regex(
            """(?:https?://[^\s"'<>]+\.(?:${AUDIO_EXTENSIONS.joinToString("|")})(?:\?[^\s"'<>]*)?)""",
            RegexOption.IGNORE_CASE
        )
        private val AUDIO_SRC_PATTERN = Regex(
            """<(?:audio|source)[^>]+src\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val OG_AUDIO_PATTERN = Regex(
            """<meta[^>]+property\s*=\s*["']og:audio["'][^>]+content\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val ENCLOSURE_PATTERN = Regex(
            """<enclosure[^>]+url\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val TITLE_PATTERN = Regex(
            """<title[^>]*>([^<]+)</title>""",
            RegexOption.IGNORE_CASE
        )
    }

    data class ExtractionResult(
        val audioUrls: List<String>,
        val pageTitle: String?
    )

    fun extractAudioUrls(url: String): ExtractionResult {
        val html = fetchHtml(url) ?: return ExtractionResult(emptyList(), null)
        val baseUri = URI(url)
        val audioUrls = mutableSetOf<String>()

        // Extract from <audio src>, <source src>
        AUDIO_SRC_PATTERN.findAll(html).forEach { match ->
            resolveUrl(baseUri, match.groupValues[1])?.let { audioUrls.add(it) }
        }

        // Extract from og:audio meta tag
        OG_AUDIO_PATTERN.findAll(html).forEach { match ->
            resolveUrl(baseUri, match.groupValues[1])?.let { audioUrls.add(it) }
        }

        // Extract from RSS/podcast enclosure tags
        ENCLOSURE_PATTERN.findAll(html).forEach { match ->
            resolveUrl(baseUri, match.groupValues[1])?.let { audioUrls.add(it) }
        }

        // Scan entire page for any URLs with audio extensions
        AUDIO_URL_PATTERN.findAll(html).forEach { match ->
            audioUrls.add(match.value)
        }

        val pageTitle = TITLE_PATTERN.find(html)?.groupValues?.get(1)?.trim()?.let { title ->
            // Decode basic HTML entities
            title.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
        }

        // Sort: prefer direct audio extensions, then by extension priority
        val sorted = audioUrls.toList().sortedWith(
            compareBy { url ->
                val ext = url.substringAfterLast(".").substringBefore("?").lowercase()
                AUDIO_EXTENSIONS.indexOf(ext).let { if (it == -1) AUDIO_EXTENSIONS.size else it }
            }
        )

        Log.i(TAG, "Found ${sorted.size} audio URL(s) on $url")
        return ExtractionResult(sorted, pageTitle)
    }

    private fun fetchHtml(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .addHeader("Accept", "text/html,application/xhtml+xml")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch page: ${response.code}")
                response.close()
                return null
            }
            val body = response.body?.string()
            response.close()
            body
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching page", e)
            null
        }
    }

    private fun resolveUrl(baseUri: URI, href: String): String? {
        return try {
            val resolved = baseUri.resolve(href.trim())
            resolved.toString()
        } catch (e: Exception) {
            null
        }
    }
}
