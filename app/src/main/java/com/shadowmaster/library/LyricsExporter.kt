package com.shadowmaster.library

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.shadowmaster.data.model.ShadowItem
import com.shadowmaster.data.model.ShadowingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates LRC and SRT lyrics/subtitle files from playlist segments.
 * Timestamps mirror the audio layout produced by [PlaylistExporter].
 */
@Singleton
class LyricsExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "LyricsExporter"

        // Must match PlaylistExporter timing constants
        private const val BEEP_DURATION_MS = 150L
        private const val DOUBLE_BEEP_GAP_MS = 100L
        private const val PRE_SEGMENT_PAUSE_MS = 300L
        private const val POST_SEGMENT_PAUSE_MS = 500L
        private const val BETWEEN_REPEATS_PAUSE_MS = 300L
    }

    /**
     * Generate and save .lrc and .srt files alongside an exported audio file.
     *
     * @param items Playlist items in order
     * @param config Shadowing configuration (repeats, bus mode, etc.)
     * @param includeYourTurnSilence Whether "your turn" sections are present in the audio
     * @param baseFileName Base name for the output files (without extension)
     */
    fun exportLyrics(
        items: List<ShadowItem>,
        config: ShadowingConfig,
        includeYourTurnSilence: Boolean,
        baseFileName: String
    ) {
        val transcribedItems = items.filter { !it.transcription.isNullOrBlank() }
        if (transcribedItems.isEmpty()) {
            Log.d(TAG, "No transcribed segments, skipping lyrics export")
            return
        }

        val timestamps = calculateTimestamps(items, config, includeYourTurnSilence)

        val lrcContent = generateLrc(items, timestamps)
        val srtContent = generateSrt(items, timestamps)

        saveLyricsFile(baseFileName, "lrc", lrcContent)
        saveLyricsFile(baseFileName, "srt", srtContent)
    }

    /**
     * Calculate the absolute timestamp (ms) where each segment's audio starts playing
     * for the first playback repeat — after the beep + pre-segment pause.
     */
    private fun calculateTimestamps(
        items: List<ShadowItem>,
        config: ShadowingConfig,
        includeYourTurnSilence: Boolean
    ): List<Long> {
        val timestamps = mutableListOf<Long>()
        var currentMs = 0L

        for (item in items) {
            // First playback repeat starts here: beep + pause, then audio
            val audioStartMs = currentMs + BEEP_DURATION_MS + PRE_SEGMENT_PAUSE_MS
            timestamps.add(audioStartMs)

            // Advance through all playback repeats
            for (repeat in 1..config.playbackRepeats) {
                currentMs += BEEP_DURATION_MS + PRE_SEGMENT_PAUSE_MS
                currentMs += item.durationMs
                currentMs += BETWEEN_REPEATS_PAUSE_MS
            }

            // "Your turn" section
            if (!config.busMode && includeYourTurnSilence) {
                for (userRepeat in 1..config.userRepeats) {
                    // Double beep + pause
                    currentMs += BEEP_DURATION_MS + DOUBLE_BEEP_GAP_MS + BEEP_DURATION_MS + PRE_SEGMENT_PAUSE_MS
                    // Silence for user to shadow
                    currentMs += item.durationMs
                    currentMs += BETWEEN_REPEATS_PAUSE_MS
                }
                // Segment complete beep
                currentMs += BEEP_DURATION_MS
            }

            // Post-segment pause
            currentMs += POST_SEGMENT_PAUSE_MS
        }

        return timestamps
    }

    private fun generateLrc(items: List<ShadowItem>, timestamps: List<Long>): String {
        val sb = StringBuilder()
        items.forEachIndexed { index, item ->
            item.transcription?.let { text ->
                val ms = timestamps[index]
                val minutes = (ms / 60000).toInt()
                val seconds = ((ms % 60000) / 1000.0)
                sb.appendLine("[%02d:%05.2f]%s".format(minutes, seconds, text))
            }
        }
        return sb.toString()
    }

    private fun generateSrt(items: List<ShadowItem>, timestamps: List<Long>): String {
        val sb = StringBuilder()
        var srtIndex = 1
        items.forEachIndexed { index, item ->
            item.transcription?.let { text ->
                val startMs = timestamps[index]
                val endMs = startMs + item.durationMs
                sb.appendLine(srtIndex++)
                sb.appendLine("${formatSrtTime(startMs)} --> ${formatSrtTime(endMs)}")
                sb.appendLine(text)
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

    private fun saveLyricsFile(baseFileName: String, extension: String, content: String) {
        try {
            val sanitizedName = baseFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "ShadowMaster_${sanitizedName}_${System.currentTimeMillis()}.$extension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(fileName, content, extension)
            } else {
                saveToExternalStorage(fileName, content)
            }
            Log.i(TAG, "Saved $extension file: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save $extension file", e)
        }
    }

    private fun saveWithMediaStore(fileName: String, content: String, extension: String) {
        val mimeType = when (extension) {
            "lrc" -> "text/plain"
            "srt" -> "text/plain"
            else -> "text/plain"
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/ShadowMaster")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            ?: return

        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToExternalStorage(fileName: String, content: String) {
        val musicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "ShadowMaster"
        )
        musicDir.mkdirs()

        val outputFile = File(musicDir, fileName)
        FileOutputStream(outputFile).use { it.write(content.toByteArray()) }
    }
}
