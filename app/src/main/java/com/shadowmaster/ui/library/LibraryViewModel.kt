package com.shadowmaster.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.data.model.*
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
    private val libraryRepository: LibraryRepository
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
}
