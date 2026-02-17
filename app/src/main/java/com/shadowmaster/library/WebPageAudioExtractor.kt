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
        private val AUDIO_MIME_TYPES = listOf("audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a",
            "audio/wav", "audio/ogg", "audio/aac", "audio/flac", "audio/opus", "audio/webm")
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
        private val ENCLOSURE_WITH_TYPE_PATTERN = Regex(
            """<enclosure[^>]+url\s*=\s*["']([^"']+)["'][^>]+type\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val TITLE_PATTERN = Regex(
            """<title[^>]*>([^<]+)</title>""",
            RegexOption.IGNORE_CASE
        )
        private val RSS_LINK_PATTERN = Regex(
            """<link[^>]+type\s*=\s*["']application/rss\+xml["'][^>]+href\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val RSS_LINK_PATTERN_ALT = Regex(
            """<link[^>]+href\s*=\s*["']([^"']+)["'][^>]+type\s*=\s*["']application/rss\+xml["']""",
            RegexOption.IGNORE_CASE
        )
        private val RSS_ITEM_TITLE_PATTERN = Regex(
            """<item>\s*<title>([^<]*)</title>""",
            RegexOption.IGNORE_CASE
        )
        private val BUZZSPROUT_IFRAME_PATTERN = Regex(
            """https?://www\.buzzsprout\.com/(\d+)/(\d+)[^"']*""",
            RegexOption.IGNORE_CASE
        )
        private val JS_AUDIO_SRC_PATTERN = Regex(
            """"src"\s*:\s*"(https?://[^"]+\.mp3[^"]*)"""",
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

        // Extract from RSS/podcast enclosure tags (if page itself is RSS)
        ENCLOSURE_PATTERN.findAll(html).forEach { match ->
            resolveUrl(baseUri, match.groupValues[1])?.let { audioUrls.add(it) }
        }

        // Extract from podcast embed iframes (Buzzsprout)
        BUZZSPROUT_IFRAME_PATTERN.findAll(html).forEach { match ->
            val podcastId = match.groupValues[1]
            val episodeId = match.groupValues[2]
            audioUrls.add("https://www.buzzsprout.com/$podcastId/$episodeId.mp3")
        }

        // Extract from JSON/JS configs with audio source URLs
        JS_AUDIO_SRC_PATTERN.findAll(html).forEach { match ->
            audioUrls.add(match.groupValues[1])
        }

        // Scan entire page for any URLs with audio extensions
        AUDIO_URL_PATTERN.findAll(html).forEach { match ->
            audioUrls.add(match.value)
        }

        val pageTitle = extractPageTitle(html)

        // If no audio found in page HTML, try RSS feed discovery
        if (audioUrls.isEmpty()) {
            Log.i(TAG, "No audio in page HTML, trying RSS feed discovery for $url")
            val rssUrls = discoverAudioFromRssFeed(html, baseUri, pageTitle)
            audioUrls.addAll(rssUrls)
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

    private fun extractPageTitle(html: String): String? {
        return TITLE_PATTERN.find(html)?.groupValues?.get(1)?.trim()?.let { title ->
            title.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"")
        }
    }

    /** Discovers audio URLs by finding and fetching the page's RSS feed. */
    private fun discoverAudioFromRssFeed(
        html: String,
        baseUri: URI,
        pageTitle: String?
    ): List<String> {
        val feedUrl = findRssFeedUrl(html, baseUri) ?: return emptyList()
        Log.i(TAG, "Found RSS feed: $feedUrl")

        val feedXml = fetchHtml(feedUrl) ?: return emptyList()
        return extractAudioFromRssFeed(feedXml, pageTitle)
    }

    private fun findRssFeedUrl(html: String, baseUri: URI): String? {
        // Check for <link rel="alternate" type="application/rss+xml"> in page
        RSS_LINK_PATTERN.find(html)?.groupValues?.get(1)?.let { href ->
            return resolveUrl(baseUri, href)
        }
        RSS_LINK_PATTERN_ALT.find(html)?.groupValues?.get(1)?.let { href ->
            return resolveUrl(baseUri, href)
        }

        // Try common WordPress feed path
        val wpFeedUrl = "${baseUri.scheme}://${baseUri.host}/feed/"
        Log.i(TAG, "No RSS link tag found, trying WordPress feed path: $wpFeedUrl")
        return wpFeedUrl
    }

    private fun extractAudioFromRssFeed(feedXml: String, pageTitle: String?): List<String> {
        // Extract all enclosure URLs with audio MIME types
        val audioEnclosures = mutableListOf<Pair<String, Int>>() // url to item index

        // Split feed into items
        val items = feedXml.split(Regex("<item>", RegexOption.IGNORE_CASE))
            .drop(1) // Skip content before first <item>

        items.forEachIndexed { index, itemXml ->
            ENCLOSURE_WITH_TYPE_PATTERN.findAll(itemXml).forEach { match ->
                val encUrl = match.groupValues[1]
                val mimeType = match.groupValues[2].lowercase()
                if (AUDIO_MIME_TYPES.any { mimeType.startsWith(it) } || hasAudioExtension(encUrl)) {
                    audioEnclosures.add(encUrl to index)
                }
            }
            // Also try enclosures without type attribute but with audio extension
            if (audioEnclosures.none { it.second == index }) {
                ENCLOSURE_PATTERN.findAll(itemXml).forEach { match ->
                    val encUrl = match.groupValues[1]
                    if (hasAudioExtension(encUrl)) {
                        audioEnclosures.add(encUrl to index)
                    }
                }
            }
        }

        if (audioEnclosures.isEmpty()) {
            Log.i(TAG, "No audio enclosures found in RSS feed")
            return emptyList()
        }

        Log.i(TAG, "Found ${audioEnclosures.size} audio enclosure(s) in RSS feed")

        // Try to match by page title
        if (pageTitle != null) {
            val normalizedPageTitle = normalizeForComparison(pageTitle)
            items.forEachIndexed { index, itemXml ->
                val itemTitle = RSS_ITEM_TITLE_PATTERN.find("<item>$itemXml")
                    ?.groupValues?.get(1)?.trim() ?: return@forEachIndexed
                val normalizedItemTitle = normalizeForComparison(itemTitle)

                if (normalizedPageTitle.contains(normalizedItemTitle) ||
                    normalizedItemTitle.contains(normalizedPageTitle)) {
                    val matched = audioEnclosures.filter { it.second == index }.map { it.first }
                    if (matched.isNotEmpty()) {
                        Log.i(TAG, "Matched RSS item by title: \"$itemTitle\"")
                        return matched
                    }
                }
            }
        }

        // No title match — return the first (latest) episode's audio
        val firstItemIndex = audioEnclosures.minOf { it.second }
        return audioEnclosures.filter { it.second == firstItemIndex }.map { it.first }
    }

    private fun hasAudioExtension(url: String): Boolean {
        val ext = url.substringAfterLast(".").substringBefore("?").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    private fun normalizeForComparison(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
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
