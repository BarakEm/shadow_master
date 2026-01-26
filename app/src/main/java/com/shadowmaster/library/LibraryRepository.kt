package com.shadowmaster.library

import android.net.Uri
import com.shadowmaster.data.local.*
import com.shadowmaster.data.model.*
import kotlinx.coroutines.flow.Flow
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
    private val urlAudioImporter: UrlAudioImporter
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

    suspend fun updateTranscription(itemId: String, transcription: String) =
        shadowItemDao.updateTranscription(itemId, transcription)

    suspend fun getItemCount(): Int = shadowItemDao.getItemCount()

    suspend fun getPlaylistItemCount(playlistId: String): Int =
        shadowItemDao.getItemCountByPlaylist(playlistId)

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

    // URL Import
    suspend fun importFromUrl(
        url: String,
        playlistName: String? = null,
        language: String = "auto"
    ): Result<String> {
        return urlAudioImporter.importFromUrl(url, playlistName, language)
    }

    fun getUrlImportProgress() = urlAudioImporter.importProgress

    fun detectUrlType(url: String) = urlAudioImporter.detectUrlType(url)

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
}
