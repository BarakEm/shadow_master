package com.shadowmaster.ui.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowmaster.data.model.*
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.library.ExportProgress
import com.shadowmaster.library.LibraryRepository
import com.shadowmaster.library.UrlImportProgress
import com.shadowmaster.translation.TranslationService
import com.shadowmaster.translation.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val translationService: TranslationService,
    private val transcriptionService: com.shadowmaster.transcription.TranscriptionService
) : ViewModel() {

    val playlists: StateFlow<List<ShadowPlaylist>> = libraryRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val importedAudio: StateFlow<List<ImportedAudio>> = libraryRepository.getAllImportedAudio()
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

    private var playlistItemsJob: Job? = null

    fun selectPlaylist(playlist: ShadowPlaylist) {
        _selectedPlaylist.value = playlist
        playlistItemsJob?.cancel()
        playlistItemsJob = viewModelScope.launch {
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
            // Get transcription setting from config
            val config = settingsRepository.config.first()
            val enableTranscription = config.transcription.autoTranscribeOnImport
            
            val result = libraryRepository.importAudioFile(
                uri = uri,
                language = language,
                enableTranscription = enableTranscription
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
            
            // Check if this is a URL (http/https) and handle accordingly
            // Use safe call and case-insensitive comparison per RFC 3986
            if (uri.scheme?.lowercase() == "http" || uri.scheme?.lowercase() == "https") {
                // This is a URL, not a local file URI - delegate to URL import method
                // Pass original uriString to avoid double decoding
                importFromUrl(uriString, language)
            } else {
                // This is a local file URI (content://, file://, etc.)
                // Get transcription setting from config
                val config = settingsRepository.config.first()
                val enableTranscription = config.transcription.autoTranscribeOnImport
                
                val result = libraryRepository.importAudioFile(
                    uri = uri,
                    language = language,
                    enableTranscription = enableTranscription
                )
                result.onSuccess {
                    _importSuccess.value = "Audio import started"
                }
                result.onFailure { error ->
                    _importError.value = error.message ?: "Import failed"
                }
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

    fun exportPlaylist(playlist: ShadowPlaylist, includeYourTurnSilence: Boolean = true, format: com.shadowmaster.library.ExportFormat = com.shadowmaster.library.ExportFormat.AAC) {
        viewModelScope.launch {
            val config = settingsRepository.config.first()
            val result = libraryRepository.exportPlaylist(
                playlistId = playlist.id,
                playlistName = playlist.name,
                config = config,
                includeYourTurnSilence = includeYourTurnSilence,
                format = format
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

    fun resegmentImportedAudio(
        importedAudioId: String,
        preset: com.shadowmaster.data.model.SegmentationConfig,
        playlistName: String? = null
    ) {
        viewModelScope.launch {
            val result = libraryRepository.resegmentAudio(
                importedAudioId = importedAudioId,
                newConfig = preset,
                playlistName = playlistName
            )
            result.onSuccess { newPlaylistId ->
                _importSuccess.value = "Re-segmentation complete! New playlist created."
            }
            result.onFailure { error ->
                _importError.value = "Re-segmentation failed: ${error.message}"
            }
        }
    }

    fun createPlaylistFromImportedAudio(
        importedAudioId: String,
        playlistName: String,
        config: com.shadowmaster.data.model.SegmentationConfig,
        enableTranscription: Boolean = false,
        language: String? = null
    ) {
        viewModelScope.launch {
            if (language != null) {
                libraryRepository.updateImportedAudioLanguage(importedAudioId, language)
            }
            val result = libraryRepository.segmentImportedAudio(
                importedAudioId = importedAudioId,
                playlistName = playlistName,
                config = config,
                enableTranscription = enableTranscription
            )
            result.onSuccess { playlistId ->
                _importSuccess.value = "Playlist created successfully!"
            }
            result.onFailure { error ->
                _importError.value = "Failed to create playlist: ${error.message}"
            }
        }
    }

    fun deleteImportedAudio(audio: ImportedAudio) {
        viewModelScope.launch {
            libraryRepository.deleteImportedAudio(audio)
        }
    }

    // Translation functionality

    private val _translationInProgress = MutableStateFlow(false)
    val translationInProgress: StateFlow<Boolean> = _translationInProgress.asStateFlow()

    private val _translationProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val translationProgress: StateFlow<Pair<Int, Int>?> = _translationProgress.asStateFlow()

    /**
     * Translate a single segment using the specified provider.
     *
     * @param item The segment to translate
     * @param providerType Provider to use (mock, google, deepl, custom)
     * @param targetLanguage Target language ISO code (e.g., "en", "es")
     */
    fun translateSegment(
        item: ShadowItem,
        providerType: TranslationService.ProviderType,
        targetLanguage: String? = null
    ) {
        viewModelScope.launch {
            // Check if transcription exists
            if (item.transcription.isNullOrBlank()) {
                _importError.value = "Please transcribe this segment first before translating"
                return@launch
            }

            _translationInProgress.value = true

            try {
                val config = settingsRepository.config.first()
                val translationConfig = config.translationConfig
                val targetLang = targetLanguage ?: translationConfig.targetLanguage
                val sourceLanguage = normalizeLanguageCode(item.language)

                // Create provider with API keys from config
                val provider = when (providerType) {
                    TranslationService.ProviderType.GOOGLE ->
                        translationService.createProvider(
                            providerType,
                            apiKey = translationConfig.googleApiKey
                        )
                    TranslationService.ProviderType.DEEPL ->
                        translationService.createProvider(
                            providerType,
                            apiKey = translationConfig.deeplApiKey
                        )
                    TranslationService.ProviderType.CUSTOM ->
                        translationService.createProvider(
                            providerType,
                            apiKey = translationConfig.customEndpointApiKey,
                            customUrl = translationConfig.customEndpointUrl
                        )
                    else -> translationService.createProvider(providerType)
                }

                val result = translationService.translate(
                    text = item.transcription!!,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLang,
                    provider = provider
                )

                result.onSuccess { translatedText ->
                    updateItemTranslation(item.id, translatedText)
                    _importSuccess.value = "Translation complete"
                }

                result.onFailure { error ->
                    val errorMessage = if (error is com.shadowmaster.translation.TranslationError) {
                        error.toUserMessage()
                    } else {
                        error.message ?: "Translation failed"
                    }
                    _importError.value = errorMessage
                }
            } finally {
                _translationInProgress.value = false
            }
        }
    }

    /**
     * Batch translate all segments in the current playlist.
     *
     * @param providerType Provider to use
     * @param targetLanguage Target language ISO code
     */
    fun translateAllSegments(
        providerType: TranslationService.ProviderType,
        targetLanguage: String? = null
    ) {
        viewModelScope.launch {
            val items = _playlistItems.value
            if (items.isEmpty()) {
                _importError.value = "No segments to translate"
                return@launch
            }

            // Filter items that have transcription
            val itemsToTranslate = items.filter { !it.transcription.isNullOrBlank() }
            if (itemsToTranslate.isEmpty()) {
                _importError.value = "No transcribed segments found. Please transcribe segments first."
                return@launch
            }

            _translationInProgress.value = true
            _translationProgress.value = 0 to itemsToTranslate.size

            try {
                val config = settingsRepository.config.first()
                val translationConfig = config.translationConfig
                val targetLang = targetLanguage ?: translationConfig.targetLanguage

                // Create provider
                val provider = when (providerType) {
                    TranslationService.ProviderType.GOOGLE ->
                        translationService.createProvider(
                            providerType,
                            apiKey = translationConfig.googleApiKey
                        )
                    TranslationService.ProviderType.DEEPL ->
                        translationService.createProvider(
                            providerType,
                            apiKey = translationConfig.deeplApiKey
                        )
                    TranslationService.ProviderType.CUSTOM ->
                        translationService.createProvider(
                            providerType,
                            apiKey = translationConfig.customEndpointApiKey,
                            customUrl = translationConfig.customEndpointUrl
                        )
                    else -> translationService.createProvider(providerType)
                }

                var successCount = 0
                var failureCount = 0

                itemsToTranslate.forEachIndexed { index, item ->
                    val sourceLanguage = normalizeLanguageCode(item.language)

                    val result = translationService.translate(
                        text = item.transcription!!,
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLang,
                        provider = provider
                    )

                    result.onSuccess { translatedText ->
                        updateItemTranslation(item.id, translatedText)
                        successCount++
                    }

                    result.onFailure {
                        failureCount++
                    }

                    _translationProgress.value = (index + 1) to itemsToTranslate.size
                }

                _importSuccess.value = if (failureCount > 0) {
                    "Translated $successCount segments ($failureCount failed)"
                } else {
                    "Translated $successCount segments"
                }

            } catch (e: Exception) {
                _importError.value = "Batch translation failed: ${e.message}"
            } finally {
                _translationInProgress.value = false
                _translationProgress.value = null
            }
        }
    }

    fun clearTranslationProgress() {
        _translationProgress.value = null
    }

    // Transcription functionality

    private val _transcriptionInProgress = MutableStateFlow(false)
    val transcriptionInProgress: StateFlow<Boolean> = _transcriptionInProgress.asStateFlow()

    private val _transcriptionProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val transcriptionProgress: StateFlow<Pair<Int, Int>?> = _transcriptionProgress.asStateFlow()
    
    private val _transcriptionComplete = MutableStateFlow(false)
    val transcriptionComplete: StateFlow<Boolean> = _transcriptionComplete.asStateFlow()

    /**
     * Transcribe a single segment using the specified provider.
     *
     * @param item The segment to transcribe
     * @param providerType Provider to use (ivrit, local, google, azure, whisper, custom)
     */
    fun transcribeSegment(
        item: ShadowItem,
        providerType: com.shadowmaster.transcription.TranscriptionProviderType
    ) {
        viewModelScope.launch {
            _transcriptionInProgress.value = true
            // Note: transcribeSegment doesn't use the transcription dialog,
            // so we don't need to set _transcriptionComplete

            try {
                val config = settingsRepository.config.first()
                val transcriptionConfig = config.transcription

                // Create provider config
                val providerConfig = com.shadowmaster.transcription.ProviderConfig(
                    ivritApiKey = transcriptionConfig.ivritApiKey,
                    googleApiKey = transcriptionConfig.googleApiKey,
                    azureApiKey = transcriptionConfig.azureApiKey,
                    azureRegion = transcriptionConfig.azureRegion,
                    whisperApiKey = transcriptionConfig.whisperApiKey,
                    localModelPath = transcriptionConfig.localModelPath,
                    customEndpointUrl = transcriptionConfig.customEndpointUrl,
                    customEndpointApiKey = transcriptionConfig.customEndpointApiKey,
                    customEndpointHeaders = transcriptionConfig.customEndpointHeaders
                )

                // Get audio file
                val audioFile = java.io.File(item.audioFilePath)
                if (!audioFile.exists()) {
                    _importError.value = "Audio file not found"
                    return@launch
                }

                val result = transcriptionService.transcribe(
                    audioFile = audioFile,
                    language = item.language,
                    providerType = providerType,
                    config = providerConfig
                )

                result.onSuccess { transcribedText ->
                    updateItemTranscription(item.id, transcribedText)
                    _importSuccess.value = "Transcription complete"
                }

                result.onFailure { error ->
                    val errorMessage = if (error is com.shadowmaster.transcription.TranscriptionError) {
                        when (error) {
                            is com.shadowmaster.transcription.TranscriptionError.ApiKeyMissing ->
                                "API key required for ${error.provider}"
                            is com.shadowmaster.transcription.TranscriptionError.NetworkError ->
                                "Network error: ${error.cause.message}"
                            is com.shadowmaster.transcription.TranscriptionError.ProviderError ->
                                "${error.provider}: ${error.message}"
                            is com.shadowmaster.transcription.TranscriptionError.UnknownError ->
                                "Transcription failed: ${error.cause?.message ?: "Unknown error"}"
                            is com.shadowmaster.transcription.TranscriptionError.QuotaExceeded ->
                                "API quota exceeded for ${error.provider}"
                            is com.shadowmaster.transcription.TranscriptionError.UnsupportedLanguage ->
                                "Language '${error.language}' not supported by ${error.provider}"
                            is com.shadowmaster.transcription.TranscriptionError.AudioTooLong ->
                                "Audio too long: ${error.durationMs}ms (max: ${error.maxMs}ms)"
                            is com.shadowmaster.transcription.TranscriptionError.InvalidAudioFormat ->
                                "Invalid audio format: ${error.format}"
                        }
                    } else {
                        error.message ?: "Transcription failed"
                    }
                    _importError.value = errorMessage
                }
            } finally {
                _transcriptionInProgress.value = false
            }
        }
    }

    /**
     * Batch transcribe all segments in the current playlist.
     *
     * @param providerType Provider to use
     * @param languageOverride Optional language code to override item languages (e.g., "he-IL", "en-US")
     */
    fun transcribeAllSegments(
        providerType: com.shadowmaster.transcription.TranscriptionProviderType,
        languageOverride: String? = null
    ) {
        viewModelScope.launch {
            val items = _playlistItems.value
            if (items.isEmpty()) {
                _importError.value = "No segments to transcribe"
                return@launch
            }

            _transcriptionInProgress.value = true
            _transcriptionProgress.value = 0 to items.size
            _transcriptionComplete.value = false

            try {
                val config = settingsRepository.config.first()
                val transcriptionConfig = config.transcription

                // Create provider config
                val providerConfig = com.shadowmaster.transcription.ProviderConfig(
                    ivritApiKey = transcriptionConfig.ivritApiKey,
                    googleApiKey = transcriptionConfig.googleApiKey,
                    azureApiKey = transcriptionConfig.azureApiKey,
                    azureRegion = transcriptionConfig.azureRegion,
                    whisperApiKey = transcriptionConfig.whisperApiKey,
                    localModelPath = transcriptionConfig.localModelPath,
                    customEndpointUrl = transcriptionConfig.customEndpointUrl,
                    customEndpointApiKey = transcriptionConfig.customEndpointApiKey,
                    customEndpointHeaders = transcriptionConfig.customEndpointHeaders
                )

                var successCount = 0
                var failureCount = 0

                items.forEachIndexed { index, item ->
                    val audioFile = java.io.File(item.audioFilePath)
                    if (!audioFile.exists()) {
                        failureCount++
                    } else {
                        val result = transcriptionService.transcribe(
                            audioFile = audioFile,
                            language = languageOverride ?: item.language,
                            providerType = providerType,
                            config = providerConfig
                        )

                        result.onSuccess { transcribedText ->
                            updateItemTranscription(item.id, transcribedText)
                            successCount++
                            Log.d("LibraryViewModel", "Transcription success for ${item.id}: $transcribedText")
                        }

                        result.onFailure { error ->
                            failureCount++
                            Log.e("LibraryViewModel", "Transcription failed for ${item.id}: ${error.message}", error)
                        }
                    }

                    _transcriptionProgress.value = (index + 1) to items.size
                }

                _importSuccess.value = if (failureCount > 0) {
                    "Transcribed $successCount segments ($failureCount failed)"
                } else {
                    "Transcribed $successCount segments"
                }
                
                // Mark transcription as complete - dialog will auto-close
                // Note: We set this even with partial failures so user gets immediate feedback
                _transcriptionComplete.value = true

            } catch (e: Exception) {
                _importError.value = "Batch transcription failed: ${e.message}"
                // Don't set _transcriptionComplete - keep dialog open so user can retry
            } finally {
                _transcriptionInProgress.value = false
                _transcriptionProgress.value = null
            }
        }
    }

    fun clearTranscriptionProgress() {
        _transcriptionProgress.value = null
    }
    
    fun clearTranscriptionComplete() {
        _transcriptionComplete.value = false
    }

    /**
     * Normalize language code from ShadowItem format to ISO 639-1.
     * Converts "en-US" -> "en", "zh-CN" -> "zh", etc.
     */
    private fun normalizeLanguageCode(language: String): String {
        return when {
            language.contains("-") -> language.split("-").first()
            else -> language
        }.lowercase()
    }
}
