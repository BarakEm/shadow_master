package com.shadowmaster.transcription

import android.content.Context
import com.shadowmaster.library.AudioFileUtility
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Unit tests for TranscriptionService.
 *
 * Tests cover:
 * - Provider creation and instantiation
 * - Provider validation
 * - Available providers list
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptionServiceTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val audioFileUtility = AudioFileUtility()
    private val service = TranscriptionService(mockContext, audioFileUtility)

    // ==================== Provider Creation Tests ====================

    @Test
    fun `createProvider creates GoogleSpeechProvider with valid config`() {
        // Given
        val config = ProviderConfig(googleApiKey = "test-key")

        // When
        val provider = service.createProvider(TranscriptionProviderType.GOOGLE, config)

        // Then
        assertNotNull(provider)
        assertTrue(provider is GoogleSpeechProvider)
        assertEquals("Google Speech-to-Text", provider?.name)
    }

    @Test
    fun `createProvider creates AzureSpeechProvider with valid config`() {
        // Given
        val config = ProviderConfig(azureApiKey = "test-key", azureRegion = "eastus")

        // When
        val provider = service.createProvider(TranscriptionProviderType.AZURE, config)

        // Then
        assertNotNull(provider)
        assertTrue(provider is AzureSpeechProvider)
        assertEquals("Azure Speech Services", provider?.name)
    }

    @Test
    fun `createProvider creates WhisperAPIProvider with valid config`() {
        // Given
        val config = ProviderConfig(whisperApiKey = "test-key")

        // When
        val provider = service.createProvider(TranscriptionProviderType.WHISPER, config)

        // Then
        assertNotNull(provider)
        assertTrue(provider is WhisperAPIProvider)
        assertEquals("OpenAI Whisper", provider?.name)
    }

    @Test
    fun `createProvider creates CustomEndpointProvider with valid config`() {
        // Given
        val config = ProviderConfig(
            customEndpointUrl = "https://api.example.com/transcribe",
            customEndpointApiKey = "test-key"
        )

        // When
        val provider = service.createProvider(TranscriptionProviderType.CUSTOM, config)

        // Then
        assertNotNull(provider)
        assertTrue(provider is CustomEndpointProvider)
        assertEquals("Custom Endpoint", provider?.name)
    }

    @Test
    fun `createProvider returns null for LOCAL provider (not yet implemented)`() {
        // Given
        val config = ProviderConfig(localModelPath = "/path/to/model")

        // When
        val provider = service.createProvider(TranscriptionProviderType.LOCAL, config)

        // Then - Now LOCAL provider should be created with valid config
        assertNotNull(provider)
        assertTrue(provider is LocalModelProvider)
    }

    @Test
    fun `createProvider creates AndroidSpeechProvider with valid config`() {
        // Given
        val config = ProviderConfig() // No config needed for Android Speech

        // When
        val provider = service.createProvider(TranscriptionProviderType.ANDROID_SPEECH, config)

        // Then
        assertNotNull(provider)
        assertTrue(provider is AndroidSpeechProvider)
        assertEquals("Google Speech (Free)", provider?.name)
        assertFalse(provider?.requiresApiKey ?: true)
    }

    // ==================== Provider Validation Tests ====================

    @Test
    fun `validateProvider succeeds for Google with valid API key`() = runTest {
        // Given
        val config = ProviderConfig(googleApiKey = "test-key")

        // When
        val result = service.validateProvider(TranscriptionProviderType.GOOGLE, config)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `validateProvider fails for Google without API key`() = runTest {
        // Given
        val config = ProviderConfig(googleApiKey = null)

        // When
        val result = service.validateProvider(TranscriptionProviderType.GOOGLE, config)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is TranscriptionError.ApiKeyMissing)
    }

    @Test
    fun `validateProvider succeeds for Azure with valid API key and region`() = runTest {
        // Given
        val config = ProviderConfig(azureApiKey = "test-key", azureRegion = "eastus")

        // When
        val result = service.validateProvider(TranscriptionProviderType.AZURE, config)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `validateProvider fails for Azure without API key`() = runTest {
        // Given
        val config = ProviderConfig(azureApiKey = null, azureRegion = "eastus")

        // When
        val result = service.validateProvider(TranscriptionProviderType.AZURE, config)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is TranscriptionError.ApiKeyMissing)
    }

    @Test
    fun `validateProvider fails for Azure without region`() = runTest {
        // Given
        val config = ProviderConfig(azureApiKey = "test-key", azureRegion = null)

        // When
        val result = service.validateProvider(TranscriptionProviderType.AZURE, config)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is TranscriptionError.ProviderError)
    }

    @Test
    fun `validateProvider succeeds for Custom with valid URL`() = runTest {
        // Given
        val config = ProviderConfig(customEndpointUrl = "https://api.example.com/transcribe")

        // When
        val result = service.validateProvider(TranscriptionProviderType.CUSTOM, config)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `validateProvider fails for Custom without URL`() = runTest {
        // Given
        val config = ProviderConfig(customEndpointUrl = null)

        // When
        val result = service.validateProvider(TranscriptionProviderType.CUSTOM, config)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is TranscriptionError.ProviderError)
    }

    @Test
    fun `validateProvider fails for Custom with invalid URL`() = runTest {
        // Given
        val config = ProviderConfig(customEndpointUrl = "invalid-url")

        // When
        val result = service.validateProvider(TranscriptionProviderType.CUSTOM, config)

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is TranscriptionError.ProviderError)
    }

    // ==================== Available Providers Tests ====================

    @Test
    fun `getAvailableProviders returns expected providers`() {
        // When
        val providers = service.getAvailableProviders()

        // Then
        assertTrue(providers.contains(TranscriptionProviderType.IVRIT_AI))
        assertTrue(providers.contains(TranscriptionProviderType.GOOGLE))
        assertTrue(providers.contains(TranscriptionProviderType.AZURE))
        assertTrue(providers.contains(TranscriptionProviderType.WHISPER))
        assertTrue(providers.contains(TranscriptionProviderType.LOCAL))
        assertTrue(providers.contains(TranscriptionProviderType.ANDROID_SPEECH))
        assertTrue(providers.contains(TranscriptionProviderType.CUSTOM))
    }

    @Test
    fun `getAvailableProviders returns correct count`() {
        // When
        val providers = service.getAvailableProviders()

        // Then
        assertEquals(7, providers.size)
    }

    // ==================== Transcription Tests ====================

    @Test
    fun `transcribe returns failure when provider not configured`() = runTest {
        // Given
        val audioFile = File.createTempFile("test", ".wav")
        val config = ProviderConfig() // Empty config

        // When
        val result = service.transcribe(
            audioFile,
            "en-US",
            TranscriptionProviderType.GOOGLE,
            config
        )

        // Then
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is TranscriptionError.ApiKeyMissing)

        // Cleanup
        audioFile.delete()
    }
}
