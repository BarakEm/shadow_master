package com.shadowmaster.data.model

import androidx.room.*
import java.util.UUID

/**
 * A shadow item is a single audio segment ready for shadowing practice.
 * Contains the audio data, optional transcription, and metadata.
 */
@Entity(tableName = "shadow_items")
data class ShadowItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    // Source info
    val sourceFileUri: String,           // Original file URI
    val sourceFileName: String,          // Display name
    val sourceStartMs: Long,             // Start position in source file
    val sourceEndMs: Long,               // End position in source file

    // Audio data (stored as file path to processed segment)
    val audioFilePath: String,           // Path to extracted audio segment
    val durationMs: Long,                // Duration of this segment

    // Transcription (optional)
    val transcription: String? = null,   // Original language text
    val translation: String? = null,     // Translated text
    val language: String = "unknown",    // Detected/specified language

    // Metadata
    val createdAt: Long = System.currentTimeMillis(),
    val lastPracticedAt: Long? = null,
    val practiceCount: Int = 0,
    val isFavorite: Boolean = false,

    // Playlist membership
    val playlistId: String? = null,
    val orderInPlaylist: Int = 0
)

/**
 * A playlist/collection of shadow items for organized practice.
 */
@Entity(tableName = "shadow_playlists")
data class ShadowPlaylist(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val name: String,
    val description: String? = null,
    val language: String = "unknown",
    val coverImagePath: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val lastPracticedAt: Long? = null,

    // Source tracking
    val sourceType: SourceType = SourceType.IMPORTED,
    val sourceUri: String? = null        // Original file/folder URI
)

enum class SourceType {
    IMPORTED,      // Imported from file picker
    RECORDED,      // Recorded live via MediaProjection
    DOWNLOADED     // Downloaded from online source (future)
}

/**
 * Import job tracking - for processing large files in background.
 */
@Entity(tableName = "import_jobs")
data class ImportJob(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val sourceUri: String,
    val fileName: String,
    val status: ImportStatus = ImportStatus.PENDING,
    val progress: Int = 0,              // 0-100
    val totalSegments: Int = 0,
    val processedSegments: Int = 0,
    val errorMessage: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,

    // Settings used for this import
    val language: String = "auto",
    val enableTranscription: Boolean = false,
    val targetPlaylistId: String? = null
)

enum class ImportStatus {
    PENDING,
    EXTRACTING_AUDIO,
    DETECTING_SEGMENTS,
    TRANSCRIBING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Practice session - tracks a shadowing session for statistics.
 */
@Entity(tableName = "practice_sessions")
data class PracticeSession(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val playlistId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,

    val itemsPracticed: Int = 0,
    val totalDurationMs: Long = 0
)

// Room type converters
class Converters {
    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromImportStatus(value: ImportStatus): String = value.name

    @TypeConverter
    fun toImportStatus(value: String): ImportStatus = ImportStatus.valueOf(value)
}
