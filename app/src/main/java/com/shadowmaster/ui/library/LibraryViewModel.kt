package com.shadowmaster.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.data.model.*
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.library.ExportProgress
import com.shadowmaster.library.LibraryRepository
import com.shadowmaster.library.UrlImportProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val playlists: StateFlow<List<ShadowPlaylist>> = libraryRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeImports: StateFlow<List<ImportJob>> = libraryRepository.getActiveImports()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentFailedImports: StateFlow<List<ImportJob>> = libraryRepository.getRecentFailedImports()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val urlImportProgress: StateFlow<UrlImportProgress?> = libraryRepository.getUrlImportProgress()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _selectedPlaylist = MutableStateFlow<ShadowPlaylist?>(null)
    val selectedPlaylist: StateFlow<ShadowPlaylist?> = _selectedPlaylist.asStateFlow()

    private val _playlistItems = MutableStateFlow<List<ShadowItem>>(emptyList())
    val playlistItems: StateFlow<List<ShadowItem>> = _playlistItems.asStateFlow()

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    private val _importSuccess = MutableStateFlow<String?>(null)
    val importSuccess: StateFlow<String?> = _importSuccess.asStateFlow()

    fun selectPlaylist(playlist: ShadowPlaylist) {
        _selectedPlaylist.value = playlist
        viewModelScope.launch {
            libraryRepository.getItemsByPlaylist(playlist.id)
                .collect { items ->
                    _playlistItems.value = items
                }
        }
    }

    fun clearSelection() {
        _selectedPlaylist.value = null
        _playlistItems.value = emptyList()
    }

    fun importAudioFile(uri: Uri, language: String = "auto") {
        viewModelScope.launch {
            val result = libraryRepository.importAudioFile(
                uri = uri,
                language = language,
                enableTranscription = false
            )
            result.onSuccess {
                _importSuccess.value = "Audio import started"
            }
            result.onFailure { error ->
                _importError.value = error.message ?: "Import failed"
            }
        }
    }

    fun importFromUrl(url: String, language: String = "auto") {
        viewModelScope.launch {
            // Decode URL if it was encoded for navigation
            val decodedUrl = try {
                URLDecoder.decode(url, "UTF-8")
            } catch (e: Exception) {
                url
            }

            val result = libraryRepository.importFromUrl(
                url = decodedUrl,
                language = language
            )
            result.onSuccess {
                _importSuccess.value = "Import started successfully"
            }
            result.onFailure { error ->
                _importError.value = error.message ?: "Import failed"
            }
        }
    }

    fun importFromUri(uriString: String, language: String = "auto") {
        viewModelScope.launch {
            val decodedUri = try {
                URLDecoder.decode(uriString, "UTF-8")
            } catch (e: Exception) {
                uriString
            }

            val uri = Uri.parse(decodedUri)
            val result = libraryRepository.importAudioFile(
                uri = uri,
                language = language,
                enableTranscription = false
            )
            result.onSuccess {
                _importSuccess.value = "Audio import started"
            }
            result.onFailure { error ->
                _importError.value = error.message ?: "Import failed"
            }
        }
    }

    fun deletePlaylist(playlist: ShadowPlaylist) {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(playlist)
        }
    }

    fun toggleFavorite(item: ShadowItem) {
        viewModelScope.launch {
            libraryRepository.toggleFavorite(item.id, !item.isFavorite)
        }
    }

    fun clearError() {
        _importError.value = null
    }

    fun clearSuccess() {
        _importSuccess.value = null
    }

    fun clearUrlImportProgress() {
        libraryRepository.clearUrlImportProgress()
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            libraryRepository.renamePlaylist(playlistId, newName)
            // Update selected playlist if it's the one being renamed
            _selectedPlaylist.value?.let { current ->
                if (current.id == playlistId) {
                    _selectedPlaylist.value = current.copy(name = newName)
                }
            }
        }
    }

    fun updateItemTranscription(itemId: String, transcription: String?) {
        viewModelScope.launch {
            libraryRepository.updateItemTranscription(itemId, transcription)
            // Refresh playlist items
            _playlistItems.value = _playlistItems.value.map { item ->
                if (item.id == itemId) item.copy(transcription = transcription) else item
            }
        }
    }

    fun updateItemTranslation(itemId: String, translation: String?) {
        viewModelScope.launch {
            libraryRepository.updateItemTranslation(itemId, translation)
            // Refresh playlist items
            _playlistItems.value = _playlistItems.value.map { item ->
                if (item.id == itemId) item.copy(translation = translation) else item
            }
        }
    }

    fun splitSegment(item: ShadowItem, splitPointMs: Long) {
        viewModelScope.launch {
            val result = libraryRepository.splitSegment(item, splitPointMs)
            if (result != null) {
                _importSuccess.value = "Segment split into 2 parts"
                // Refresh playlist items
                _selectedPlaylist.value?.let { playlist ->
                    selectPlaylist(playlist)
                }
            } else {
                _importError.value = "Failed to split segment"
            }
        }
    }

    private val _selectedForMerge = MutableStateFlow<Set<String>>(emptySet())
    val selectedForMerge: StateFlow<Set<String>> = _selectedForMerge.asStateFlow()

    fun toggleMergeSelection(itemId: String) {
        _selectedForMerge.value = _selectedForMerge.value.let { current ->
            if (current.contains(itemId)) current - itemId else current + itemId
        }
    }

    fun clearMergeSelection() {
        _selectedForMerge.value = emptySet()
    }

    fun mergeSelectedSegments() {
        viewModelScope.launch {
            val selectedIds = _selectedForMerge.value
            if (selectedIds.size < 2) {
                _importError.value = "Select at least 2 segments to merge"
                return@launch
            }

            val itemsToMerge = _playlistItems.value.filter { it.id in selectedIds }
            val result = libraryRepository.mergeSegments(itemsToMerge)

            if (result != null) {
                _importSuccess.value = "Merged ${itemsToMerge.size} segments"
                clearMergeSelection()
                // Refresh playlist items
                _selectedPlaylist.value?.let { playlist ->
                    selectPlaylist(playlist)
                }
            } else {
                _importError.value = "Failed to merge segments"
            }
        }
    }

    // Export functionality
    val exportProgress: StateFlow<ExportProgress> = libraryRepository.getExportProgress()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ExportProgress(com.shadowmaster.library.ExportStatus.IDLE)
        )

    fun exportPlaylist(playlist: ShadowPlaylist, includeYourTurnSilence: Boolean = true) {
        viewModelScope.launch {
            val config = settingsRepository.configBlocking
            val result = libraryRepository.exportPlaylist(
                playlistId = playlist.id,
                playlistName = playlist.name,
                config = config,
                includeYourTurnSilence = includeYourTurnSilence
            )
            result.onSuccess { path ->
                _importSuccess.value = "Exported to $path"
            }
            result.onFailure { error ->
                _importError.value = "Export failed: ${error.message}"
            }
        }
    }

    fun clearExportProgress() {
        libraryRepository.clearExportProgress()
    }

    fun cancelExport() {
        libraryRepository.cancelExport()
    }

    fun dismissFailedImport(jobId: String) {
        viewModelScope.launch {
            libraryRepository.deleteImportJob(jobId)
        }
    }
}
