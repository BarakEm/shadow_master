# Transcription System - Developer Guide

## Quick Start

This guide helps developers understand and extend the transcription system in Shadow Master.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        User Interface                        │
│  SettingsScreen.kt - Configuration UI                       │
│  LibraryScreen.kt - Transcription actions (TODO)            │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                      ViewModel Layer                         │
│  SettingsViewModel - Settings management                    │
│  LibraryViewModel - Library actions (TODO)                  │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                     Repository Layer                         │
│  SettingsRepository - Persist transcription config          │
│  LibraryRepository - Update transcriptions (TODO)           │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                    Service Layer                             │
│  TranscriptionService - Provider routing                    │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                   Provider Layer                             │
│  TranscriptionProvider interface                            │
│  ├── GoogleSpeechProvider (TODO)                            │
│  ├── AzureSpeechProvider (TODO)                             │
│  ├── WhisperAPIProvider (TODO)                              │
│  ├── CustomEndpointProvider ✓ IMPLEMENTED                   │
│  └── LocalModelProvider (Future)                            │
└─────────────────────────────────────────────────────────────┘
```

## Adding a New Provider

### Step 1: Create Provider Class

```kotlin
package com.shadowmaster.transcription

import java.io.File

class MyNewProvider(
    private val apiKey: String?
) : TranscriptionProvider {
    
    override val name: String = "My New Provider"
    override val id: String = "mynew"  // Unique identifier
    override val requiresApiKey: Boolean = true
    
    override suspend fun validateConfiguration(): Result<Unit> {
        // Check if required config is present
        return if (apiKey.isNullOrBlank()) {
            Result.failure(TranscriptionError.ApiKeyMissing(name))
        } else {
            Result.success(Unit)
        }
    }
    
    override suspend fun transcribe(
        audioFile: File, 
        language: String
    ): Result<String> {
        // Implement transcription logic
        return try {
            // 1. Read audio file
            // 2. Call API
            // 3. Parse response
            // 4. Return transcription
            Result.success("transcribed text")
        } catch (e: Exception) {
            Result.failure(TranscriptionError.UnknownError(name, e))
        }
    }
}
```

### Step 2: Add to TranscriptionProviderType Enum

```kotlin
// In TranscriptionProvider.kt
enum class TranscriptionProviderType(val id: String, val displayName: String) {
    GOOGLE("google", "Google Speech-to-Text"),
    AZURE("azure", "Azure Speech Services"),
    WHISPER("whisper", "OpenAI Whisper"),
    MYNEW("mynew", "My New Provider"),  // ADD THIS
    CUSTOM("custom", "Custom Endpoint")
}
```

### Step 3: Add to ProviderConfig

```kotlin
// In TranscriptionService.kt
data class ProviderConfig(
    val googleApiKey: String? = null,
    val azureApiKey: String? = null,
    val azureRegion: String? = null,
    val whisperApiKey: String? = null,
    val myNewApiKey: String? = null,  // ADD THIS
    // ... other fields
)
```

### Step 4: Update TranscriptionService

```kotlin
// In TranscriptionService.kt
fun createProvider(
    providerType: TranscriptionProviderType,
    config: ProviderConfig
): TranscriptionProvider? {
    return when (providerType) {
        // ... existing cases
        TranscriptionProviderType.MYNEW -> {
            MyNewProvider(config.myNewApiKey)  // ADD THIS
        }
        // ... other cases
    }
}

fun getAvailableProviders(): List<TranscriptionProviderType> {
    return listOf(
        // ... existing providers
        TranscriptionProviderType.MYNEW  // ADD THIS
    )
}
```

### Step 5: Add to TranscriptionConfig

```kotlin
// In ShadowingConfig.kt
data class TranscriptionConfig(
    // ... existing fields
    val myNewApiKey: String? = null  // ADD THIS
)
```

### Step 6: Add to SettingsRepository

```kotlin
// In SettingsRepository.kt

// Add preference key
private object Keys {
    // ... existing keys
    val TRANSCRIPTION_MYNEW_API_KEY = 
        stringPreferencesKey("transcription_mynew_api_key")
}

// Add to config flow
val config: Flow<ShadowingConfig> = context.dataStore.data.map { preferences ->
    ShadowingConfig(
        // ... existing fields
        transcription = TranscriptionConfig(
            // ... existing fields
            myNewApiKey = preferences[Keys.TRANSCRIPTION_MYNEW_API_KEY]
        )
    )
}

// Add update method
suspend fun updateTranscriptionMyNewApiKey(apiKey: String?) {
    context.dataStore.edit { preferences ->
        if (apiKey.isNullOrBlank()) {
            preferences.remove(Keys.TRANSCRIPTION_MYNEW_API_KEY)
        } else {
            preferences[Keys.TRANSCRIPTION_MYNEW_API_KEY] = apiKey
        }
    }
}
```

### Step 7: Add to SettingsViewModel

```kotlin
// In SettingsViewModel.kt
fun updateTranscriptionMyNewApiKey(apiKey: String?) {
    viewModelScope.launch {
        settingsRepository.updateTranscriptionMyNewApiKey(apiKey)
    }
}
```

### Step 8: Add UI Configuration

```kotlin
// In SettingsScreen.kt - TranscriptionServicesSection

// Add provider section
ProviderConfigSection(
    title = "My New Provider",
    isConfigured = !config.transcription.myNewApiKey.isNullOrBlank(),
    onConfigureClick = { showMyNewKeyDialog = true }
)

// Add dialog state
var showMyNewKeyDialog by remember { mutableStateOf(false) }

// Add dialog
if (showMyNewKeyDialog) {
    ApiKeyDialog(
        title = "My New Provider API Key",
        currentValue = config.transcription.myNewApiKey ?: "",
        onDismiss = { showMyNewKeyDialog = false },
        onSave = { apiKey ->
            viewModel.updateTranscriptionMyNewApiKey(apiKey.ifBlank { null })
            showMyNewKeyDialog = false
        }
    )
}
```

### Step 9: Add to Provider Selector

```kotlin
// In SettingsScreen.kt - TranscriptionProviderSelector
val providers = mapOf(
    // ... existing providers
    "mynew" to "My New Provider"  // ADD THIS
)
```

### Step 10: Write Tests

```kotlin
// In TranscriptionServiceTest.kt
@Test
fun `createProvider creates MyNewProvider with valid config`() {
    // Given
    val config = ProviderConfig(myNewApiKey = "test-key")
    
    // When
    val provider = service.createProvider(
        TranscriptionProviderType.MYNEW, 
        config
    )
    
    // Then
    assertNotNull(provider)
    assertTrue(provider is MyNewProvider)
    assertEquals("My New Provider", provider?.name)
}

@Test
fun `validateProvider succeeds for MyNew with valid API key`() = runTest {
    // Given
    val config = ProviderConfig(myNewApiKey = "test-key")
    
    // When
    val result = service.validateProvider(
        TranscriptionProviderType.MYNEW, 
        config
    )
    
    // Then
    assertTrue(result.isSuccess)
}

// In SettingsViewModelTest.kt
@Test
fun `updateTranscriptionMyNewApiKey calls repository`() = runTest {
    // Given
    val apiKey = "test-key"
    
    // When
    viewModel.updateTranscriptionMyNewApiKey(apiKey)
    advanceUntilIdle()
    
    // Then
    coVerify { settingsRepository.updateTranscriptionMyNewApiKey(apiKey) }
}
```

## Common Patterns

### Error Handling

```kotlin
when (val result = provider.transcribe(file, language)) {
    is Result.Success -> {
        // Handle success
        val text = result.value
    }
    is Result.Failure -> {
        when (val error = result.error) {
            is TranscriptionError.ApiKeyMissing -> {
                // Show "Configure API key" message
            }
            is TranscriptionError.NetworkError -> {
                // Show "Network error, retry?" message
            }
            is TranscriptionError.QuotaExceeded -> {
                // Show "Quota exceeded" message
            }
            // ... other error types
        }
    }
}
```

### Audio File Preparation

```kotlin
// Most providers expect 16kHz mono PCM WAV
// Shadow Master's audio is already in this format
suspend fun prepareAudioForProvider(
    sourceFile: File
): File {
    // If already in correct format, return as-is
    if (isCorrectFormat(sourceFile)) {
        return sourceFile
    }
    
    // Otherwise, convert
    return convertAudio(sourceFile, 
        sampleRate = 16000,
        channels = 1
    )
}
```

### Making HTTP Requests

```kotlin
// Use OkHttp (already a dependency)
private val client = OkHttpClient()

suspend fun callApi(audioFile: File): String {
    return withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaType())
            )
            .build()
            
        val request = Request.Builder()
            .url("https://api.example.com/transcribe")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()
            
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}")
        }
        
        response.body?.string() ?: ""
    }
}
```

### Progress Tracking (Future)

```kotlin
// When implementing progress tracking:
interface TranscriptionProvider {
    // Add progress callback parameter
    suspend fun transcribe(
        audioFile: File,
        language: String,
        onProgress: (Float) -> Unit = {}
    ): Result<String>
}

// Usage
provider.transcribe(file, language) { progress ->
    // Update UI: progress is 0.0 to 1.0
    println("Progress: ${(progress * 100).toInt()}%")
}
```

## Testing Guidelines

### Unit Testing Providers

```kotlin
@Test
fun `provider returns success with valid input`() = runTest {
    // Arrange
    val provider = MyNewProvider("valid-key")
    val testFile = createTempAudioFile()
    
    // Act
    val result = provider.transcribe(testFile, "en-US")
    
    // Assert
    assertTrue(result.isSuccess)
    assertNotNull(result.getOrNull())
    
    // Cleanup
    testFile.delete()
}

@Test
fun `provider returns error with invalid key`() = runTest {
    // Arrange
    val provider = MyNewProvider(null)
    
    // Act
    val result = provider.validateConfiguration()
    
    // Assert
    assertTrue(result.isFailure)
    val error = result.exceptionOrNull()
    assertTrue(error is TranscriptionError.ApiKeyMissing)
}
```

### Mocking for Integration Tests

```kotlin
// Create a mock provider for testing
class MockTranscriptionProvider : TranscriptionProvider {
    override val name = "Mock"
    override val id = "mock"
    override val requiresApiKey = false
    
    var mockResult: Result<String> = Result.success("mock transcription")
    
    override suspend fun transcribe(
        audioFile: File, 
        language: String
    ): Result<String> = mockResult
    
    override suspend fun validateConfiguration(): Result<Unit> = 
        Result.success(Unit)
}
```

## Debugging

### Enable Logging

```kotlin
// In provider implementation
private val logger = Logger.getLogger(MyNewProvider::class.java.name)

override suspend fun transcribe(...): Result<String> {
    logger.info("Starting transcription for ${audioFile.name}")
    // ...
    logger.info("Transcription complete: ${result.length} chars")
}
```

### Common Issues

**Issue:** API returns 401 Unauthorized
- Check API key is correctly configured
- Verify API key format (some need "Bearer " prefix)
- Check key hasn't expired

**Issue:** Audio file too large
- Implement chunking for large files
- Compress audio before upload
- Use streaming if provider supports it

**Issue:** Language not supported
- Check provider's supported languages
- Map Shadow Master language codes to provider's format
- Return `UnsupportedLanguage` error

## Performance Tips

1. **Batch Processing:** Process multiple files concurrently
   ```kotlin
   val results = files.map { file ->
       async { provider.transcribe(file, language) }
   }.awaitAll()
   ```

2. **Caching:** Cache transcriptions to avoid re-processing
   ```kotlin
   val cacheKey = "${file.path}-${language}"
   transcriptionCache[cacheKey] ?: provider.transcribe(file, language).also {
       transcriptionCache[cacheKey] = it
   }
   ```

3. **Connection Pooling:** Reuse HTTP clients
   ```kotlin
   // Create once, reuse many times
   private val client = OkHttpClient.Builder()
       .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
       .build()
   ```

## Security Best Practices

1. **Never log API keys**
   ```kotlin
   // BAD
   logger.info("Using API key: $apiKey")
   
   // GOOD
   logger.info("API key configured: ${!apiKey.isNullOrBlank()}")
   ```

2. **Validate inputs**
   ```kotlin
   require(audioFile.exists()) { "Audio file does not exist" }
   require(audioFile.canRead()) { "Cannot read audio file" }
   require(language.matches(Regex("[a-z]{2}-[A-Z]{2}"))) {
       "Invalid language code format"
   }
   ```

3. **Handle sensitive data**
   ```kotlin
   // Clear sensitive data after use
   val apiKey = config.apiKey
   try {
       // Use apiKey
   } finally {
       // Java String is immutable, but clear reference
       @Suppress("UNUSED_VALUE")
       apiKey = null
   }
   ```

## Resources

- [Full API Documentation](TRANSCRIPTION.md)
- [UI Mockup](TRANSCRIPTION_UI_MOCKUP.md)
- [Implementation Summary](../TRANSCRIPTION_IMPLEMENTATION_SUMMARY.md)
- [CustomEndpointProvider](../app/src/main/java/com/shadowmaster/transcription/CustomEndpointProvider.kt) - Reference implementation

## Getting Help

- Check existing provider implementations for patterns
- Review unit tests for usage examples
- Consult the main `TRANSCRIPTION.md` documentation
- Open an issue on GitHub for questions
