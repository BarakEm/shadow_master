package com.shadowmaster.library

import android.net.Uri
import com.shadowmaster.data.local.*
import com.shadowmaster.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for accessing and managing the shadow library.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val shadowItemDao: ShadowItemDao,
    private val shadowPlaylistDao: ShadowPlaylistDao,
    private val importJobDao: ImportJobDao,
    private val practiceSessionDao: PracticeSessionDao,
    private val audioImporter: AudioImporter,
    private val urlAudioImporter: UrlAudioImporter,
    private val audioExporter: AudioExporter,
    private val importedAudioDao: com.shadowmaster.data.local.ImportedAudioDao,
    private val segmentationConfigDao: com.shadowmaster.data.local.SegmentationConfigDao
) {
    // Playlists
    fun getAllPlaylists(): Flow<List<ShadowPlaylist>> = shadowPlaylistDao.getAllPlaylists()

    suspend fun getPlaylist(id: String): ShadowPlaylist? = shadowPlaylistDao.getPlaylistById(id)

    suspend fun deletePlaylist(playlist: ShadowPlaylist) {
        // Delete all items in playlist first
        shadowItemDao.deleteByPlaylist(playlist.id)
        shadowPlaylistDao.delete(playlist)
    }

    suspend fun updatePlaylist(playlist: ShadowPlaylist) = shadowPlaylistDao.update(playlist)

    // Items
    fun getAllItems(): Flow<List<ShadowItem>> = shadowItemDao.getAllItems()

    fun getItemsByPlaylist(playlistId: String): Flow<List<ShadowItem>> =
        shadowItemDao.getItemsByPlaylist(playlistId)

    fun getFavorites(): Flow<List<ShadowItem>> = shadowItemDao.getFavorites()

    suspend fun getItem(id: String): ShadowItem? = shadowItemDao.getItemById(id)

    suspend fun updateItem(item: ShadowItem) = shadowItemDao.update(item)

    suspend fun markItemPracticed(itemId: String) = shadowItemDao.markPracticed(itemId)

    suspend fun toggleFavorite(itemId: String, isFavorite: Boolean) =
        shadowItemDao.setFavorite(itemId, isFavorite)

    suspend fun updateItemTranscription(itemId: String, transcription: String?) =
        shadowItemDao.updateTranscription(itemId, transcription)

    suspend fun updateItemTranslation(itemId: String, translation: String?) =
        shadowItemDao.updateTranslation(itemId, translation)

    suspend fun renamePlaylist(playlistId: String, name: String) =
        shadowPlaylistDao.updateName(playlistId, name)

    // Segment operations
    suspend fun splitSegment(item: ShadowItem, splitPointMs: Long): List<ShadowItem>? =
        audioImporter.splitSegment(item, splitPointMs)

    suspend fun mergeSegments(items: List<ShadowItem>): ShadowItem? =
        audioImporter.mergeSegments(items)

    suspend fun getItemCount(): Int = shadowItemDao.getItemCount()

    suspend fun getPlaylistItemCount(playlistId: String): Int =
        shadowItemDao.getItemCountByPlaylist(playlistId)

    suspend fun getTranscribedItemCount(playlistId: String): Int =
        shadowItemDao.getTranscribedItemCountByPlaylist(playlistId)

    // Import
    suspend fun importAudioFile(
        uri: Uri,
        playlistName: String? = null,
        language: String = "auto",
        enableTranscription: Boolean = false
    ): Result<String> {
        return audioImporter.importAudioFile(uri, playlistName, language, enableTranscription)
    }

    fun getActiveImports(): Flow<List<ImportJob>> = importJobDao.getActiveJobs()

    fun getAllImports(): Flow<List<ImportJob>> = importJobDao.getAllJobs()

    fun getRecentFailedImports(): Flow<List<ImportJob>> = importJobDao.getRecentFailedJobs()

    suspend fun getImportJobForPlaylist(playlistId: String): ImportJob? =
        importJobDao.getJobByPlaylistId(playlistId)

    suspend fun deleteImportJob(jobId: String) {
        importJobDao.getJobById(jobId)?.let { job ->
            importJobDao.delete(job)
        }
    }

    // URL Import
    suspend fun importFromUrl(
        url: String,
        playlistName: String? = null,
        language: String = "auto"
    ): Result<String> {
        return urlAudioImporter.importFromUrl(url, playlistName, language)
    }

    fun getUrlImportProgress() = urlAudioImporter.importProgress

    fun detectUrlType(url: String) = UrlTypeDetector.detectUrlType(url)

    fun clearUrlImportProgress() = urlAudioImporter.clearProgress()

    // Practice sessions
    suspend fun startPracticeSession(playlistId: String?): PracticeSession {
        val session = PracticeSession(playlistId = playlistId)
        practiceSessionDao.insert(session)
        return session
    }

    suspend fun endPracticeSession(session: PracticeSession, itemsPracticed: Int, durationMs: Long) {
        val updated = session.copy(
            endedAt = System.currentTimeMillis(),
            itemsPracticed = itemsPracticed,
            totalDurationMs = durationMs
        )
        practiceSessionDao.update(updated)

        // Update playlist last practiced
        session.playlistId?.let {
            shadowPlaylistDao.markPracticed(it)
        }
    }

    fun getRecentSessions(): Flow<List<PracticeSession>> = practiceSessionDao.getRecentSessions()

    suspend fun getTotalPracticeTime(): Long = practiceSessionDao.getTotalPracticeTime() ?: 0L

    suspend fun getTotalItemsPracticed(): Int = practiceSessionDao.getTotalItemsPracticed() ?: 0

    // Export
    suspend fun exportPlaylist(
        playlistId: String,
        playlistName: String,
        config: ShadowingConfig,
        includeYourTurnSilence: Boolean = true,
        format: ExportFormat = ExportFormat.AAC
    ): Result<String> = audioExporter.exportPlaylist(playlistId, playlistName, config, includeYourTurnSilence, format)

    fun getExportProgress(): StateFlow<ExportProgress> = audioExporter.exportProgress

    fun clearExportProgress() = audioExporter.clearProgress()

    fun cancelExport() = audioExporter.cancelExport()

    // Two-phase import API
    suspend fun importAudioOnly(uri: Uri, language: String = "auto"): Result<ImportedAudio> =
        audioImporter.importAudioOnly(uri, language)

    suspend fun segmentImportedAudio(
        importedAudioId: String,
        playlistName: String? = null,
        config: SegmentationConfig,
        enableTranscription: Boolean = false,
        providerOverride: String? = null
    ): Result<String> {
        // Create an ImportJob so the UI shows a progress bar
        val importedAudio = importedAudioDao.getById(importedAudioId)
        val job = ImportJob(
            sourceUri = importedAudio?.sourceUri ?: importedAudioId,
            fileName = playlistName ?: importedAudio?.sourceFileName ?: "Segmenting...",
            status = ImportStatus.DETECTING_SEGMENTS,
            progress = 0,
            language = importedAudio?.language ?: "auto",
            enableTranscription = enableTranscription
        )
        importJobDao.insert(job)

        val result = audioImporter.segmentImportedAudio(
            importedAudioId, playlistName, config, enableTranscription,
            jobId = job.id, providerOverride = providerOverride
        )

        // Mark job completed or failed
        val existing = importJobDao.getJobById(job.id)
        if (existing != null) {
            if (result.isSuccess) {
                importJobDao.update(existing.copy(
                    status = ImportStatus.COMPLETED,
                    progress = 100,
                    completedAt = System.currentTimeMillis()
                ))
            } else {
                importJobDao.update(existing.copy(
                    status = ImportStatus.FAILED,
                    errorMessage = result.exceptionOrNull()?.message
                ))
            }
        }
        return result
    }

    // Convenience method for re-segmentation
    suspend fun resegmentAudio(
        importedAudioId: String,
        newConfig: SegmentationConfig,
        playlistName: String? = null
    ): Result<String> = segmentImportedAudio(
        importedAudioId = importedAudioId,
        playlistName = playlistName,
        config = newConfig,
        enableTranscription = false
    )

    // Convenience method for creating playlist from imported audio
    suspend fun createPlaylistFromImportedAudio(
        importedAudioId: String,
        playlistName: String,
        configId: String
    ): Result<String> {
        val config = segmentationConfigDao.getById(configId)
            ?: return Result.failure(IllegalArgumentException("Segmentation config not found: $configId"))
        
        return segmentImportedAudio(
            importedAudioId = importedAudioId,
            playlistName = playlistName,
            config = config,
            enableTranscription = false
        )
    }

    // Imported audio management
    fun getAllImportedAudio(): Flow<List<ImportedAudio>> =
        importedAudioDao.getAllImportedAudio()

    suspend fun getImportedAudio(id: String): ImportedAudio? =
        importedAudioDao.getById(id)

    suspend fun updateImportedAudioLanguage(audioId: String, language: String) {
        val audio = importedAudioDao.getById(audioId) ?: return
        importedAudioDao.update(audio.copy(language = language))
    }

    suspend fun deleteImportedAudio(audio: ImportedAudio) {
        File(audio.pcmFilePath).delete()
        importedAudioDao.delete(audio)
    }

    // Segmentation config management
    fun getAllSegmentationConfigs(): Flow<List<SegmentationConfig>> =
        segmentationConfigDao.getAllConfigs()

    suspend fun getSegmentationConfig(id: String): SegmentationConfig? =
        segmentationConfigDao.getById(id)

    suspend fun saveSegmentationConfig(config: SegmentationConfig) =
        segmentationConfigDao.insert(config)

    suspend fun deleteSegmentationConfig(config: SegmentationConfig) =
        segmentationConfigDao.delete(config)

    // Storage tracking
    suspend fun getTotalImportedAudioStorage(): Long =
        importedAudioDao.getTotalStorageUsed() ?: 0L
}
