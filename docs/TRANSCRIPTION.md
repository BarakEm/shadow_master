# Transcription Service Documentation

## Overview

Shadow Master now supports automatic transcription (Speech-to-Text) with multiple provider options. This feature allows users to automatically transcribe audio segments using various commercial and custom transcription services.

## Supported Providers

### 1. Google Speech-to-Text
- **Provider ID:** `google`
- **Requirements:** Google Cloud Speech-to-Text API key
- **Status:** Implementation stub (TODO: Add Google Cloud Speech library integration)
- **Configuration:** API Key only

### 2. Azure Speech Services
- **Provider ID:** `azure`
- **Requirements:** Azure Speech Services API key and region
- **Status:** Implementation stub (TODO: Add Azure Speech SDK integration)
- **Configuration:** API Key + Region (e.g., "eastus")
- **Note:** Azure Speech SDK is already a project dependency

### 3. OpenAI Whisper
- **Provider ID:** `whisper`
- **Requirements:** OpenAI API key
- **Status:** Implementation stub (TODO: Add OpenAI API integration)
- **Configuration:** API Key only
- **API Endpoint:** `https://api.openai.com/v1/audio/transcriptions`

### 4. Custom Endpoint
- **Provider ID:** `custom`
- **Requirements:** HTTP endpoint URL
- **Status:** ✅ Fully implemented
- **Configuration:** URL + Optional API Key + Optional Custom Headers

#### Custom Endpoint API Contract

**Request:**
```http
POST <endpoint-url>
Content-Type: multipart/form-data

file: <audio-file>
language: <language-code>
```

**Response:**
```json
{
  "text": "transcribed text here",
  "language": "en-US",
  "confidence": 0.95
}
```

Alternative response field names: `transcription` can be used instead of `text`.

## Architecture

### Core Components

#### TranscriptionProvider Interface
```kotlin
interface TranscriptionProvider {
    val name: String
    val id: String
    val requiresApiKey: Boolean
    suspend fun transcribe(audioFile: File, language: String): Result<String>
    suspend fun validateConfiguration(): Result<Unit>
}
```

#### TranscriptionService
Manages provider instantiation and routing:
- Creates provider instances based on configuration
- Validates provider settings
- Routes transcription requests to appropriate provider
- Handles provider-specific errors

#### TranscriptionError (Sealed Class)
Structured error handling:
- `ApiKeyMissing` - API key required but not configured
- `NetworkError` - Network/HTTP error during transcription
- `QuotaExceeded` - API quota/rate limit exceeded
- `UnsupportedLanguage` - Language not supported by provider
- `AudioTooLong` - Audio exceeds provider's duration limit
- `InvalidAudioFormat` - Audio format not supported
- `ProviderError` - Provider-specific error
- `UnknownError` - Catch-all for unexpected errors

### Data Layer

#### TranscriptionConfig
```kotlin
data class TranscriptionConfig(
    val defaultProvider: String = "google",
    val autoTranscribeOnImport: Boolean = false,
    val googleApiKey: String? = null,
    val azureApiKey: String? = null,
    val azureRegion: String? = null,
    val whisperApiKey: String? = null,
    val customEndpointUrl: String? = null,
    val customEndpointApiKey: String? = null,
    val customEndpointHeaders: Map<String, String> = emptyMap()
)
```

Stored in DataStore preferences with keys:
- `transcription_default_provider`
- `transcription_auto_on_import`
- `transcription_google_api_key`
- `transcription_azure_api_key`
- `transcription_azure_region`
- `transcription_whisper_api_key`
- `transcription_custom_url`
- `transcription_custom_api_key`

### UI Layer

#### Settings Screen
New "Transcription Services" section with:
- Auto-transcribe toggle
- Default provider selector
- Provider configuration cards showing status
- Dialog-based API key/configuration entry

#### Provider Configuration Dialogs
- **API Key Dialog:** Single text field for API key
- **Azure Config Dialog:** API key + region fields
- **Custom Endpoint Dialog:** URL + optional API key

## Usage

### Configuring a Provider

1. Navigate to Settings → Transcription Services
2. Select a provider configuration card
3. Enter required credentials in the dialog
4. Save configuration

### Using Transcription

#### Manual Transcription (Future)
1. Navigate to Library
2. Long-press a segment
3. Select "Transcribe with..."
4. Choose provider
5. Wait for transcription to complete

#### Batch Transcription (Future)
1. Open a playlist
2. Tap the action menu (⋮)
3. Select "Transcribe all segments"
4. Choose provider
5. Monitor progress

#### Auto-transcribe on Import (Future)
1. Enable in Settings → Transcription Services
2. Import audio file
3. Segments are automatically transcribed after detection
4. Progress shown during import

## Implementation Status

### ✅ Completed
- Core provider infrastructure
- TranscriptionProvider interface
- TranscriptionService with provider management
- CustomEndpointProvider with full HTTP implementation
- TranscriptionConfig data model
- DataStore persistence
- Settings UI with provider configuration
- Unit tests for service and providers
- Error handling with structured types

### 🚧 In Progress / TODO
- Google Speech-to-Text API integration
- Azure Speech Services API integration
- OpenAI Whisper API integration
- Local model provider (Hugging Face wav2vec2)
- Library UI integration (context menus)
- Batch transcription functionality
- Auto-transcribe on import
- Progress tracking and cancellation
- Encrypted API key storage (EncryptedSharedPreferences)

## API Key Security

**Current:** API keys are stored in DataStore (plaintext in private app storage)

**Planned:** Migration to EncryptedSharedPreferences for enhanced security:
```kotlin
val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "transcription_keys",
    MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

## Testing

### Unit Tests

**TranscriptionServiceTest:**
- Provider creation and instantiation
- Configuration validation
- Available providers list
- Error handling for misconfigured providers

**SettingsViewModelTest:**
- All transcription settings update methods
- Null value handling
- Repository interaction verification

### Integration Testing (Future)
- End-to-end transcription flow
- Provider switching
- Error recovery
- Progress tracking
- UI state management

## Dependencies

### Current
- OkHttp (already in project) - for CustomEndpointProvider HTTP requests
- Azure Speech SDK (already in project) - for AzureSpeechProvider

### Future (when implementing providers)
- Google Cloud Speech-to-Text library
- JSON parsing library (org.json already available)
- EncryptedSharedPreferences for secure key storage
- Hugging Face Android library (for local models)

## Performance Considerations

1. **Network:** All API-based providers require internet connection
2. **Quota:** Monitor API usage to avoid quota limits
3. **Latency:** Transcription is async; show loading indicators
4. **Audio Format:** 16kHz mono PCM recommended for best compatibility
5. **File Size:** Be mindful of provider limits (typically 10-25 MB per file)

## Error Handling Best Practices

```kotlin
when (val result = transcriptionService.transcribe(...)) {
    is Result.Success -> {
        val transcription = result.value
        // Update UI with transcription
    }
    is Result.Failure -> {
        when (val error = result.error) {
            is TranscriptionError.ApiKeyMissing -> {
                // Prompt user to configure API key
            }
            is TranscriptionError.NetworkError -> {
                // Show retry option
            }
            is TranscriptionError.QuotaExceeded -> {
                // Inform user about quota limits
            }
            // ... handle other error types
        }
    }
}
```

## Future Enhancements

1. **Provider Fallback:** Automatically try alternative provider if primary fails
2. **Caching:** Cache transcriptions to avoid re-transcribing same audio
3. **Offline Support:** Local model provider for offline transcription
4. **Custom Headers:** Support for authentication headers in custom endpoints
5. **Webhooks:** Support for async transcription with webhook callbacks
6. **Confidence Scores:** Display transcription confidence when available
7. **Language Detection:** Automatic language detection for transcription
8. **Speaker Diarization:** Identify different speakers in audio
9. **Punctuation:** Add automatic punctuation to transcriptions
10. **Custom Vocabulary:** Support for domain-specific terminology

## Troubleshooting

### Provider not working
1. Verify API key is correctly entered
2. Check internet connection
3. Verify provider region/endpoint is correct
4. Check provider service status
5. Review error messages in logs

### Transcription quality issues
1. Ensure audio is clear and high quality
2. Use appropriate language setting
3. Consider different provider if quality is poor
4. Check if audio format is supported

### Performance issues
1. Reduce concurrent transcription requests
2. Consider batch processing during off-peak hours
3. Use local model for offline scenarios
4. Optimize audio file size before transcription

## Contributing

When adding a new provider:

1. Implement `TranscriptionProvider` interface
2. Add provider to `TranscriptionProviderType` enum
3. Update `TranscriptionService.createProvider()` to instantiate provider
4. Add configuration fields to `TranscriptionConfig`
5. Update `SettingsRepository` with new preference keys
6. Add UI configuration in `SettingsScreen.kt`
7. Create unit tests for provider validation
8. Update documentation with provider details
9. Add integration tests for end-to-end flow

## License

This implementation follows the project's main license (see LICENSE file).
