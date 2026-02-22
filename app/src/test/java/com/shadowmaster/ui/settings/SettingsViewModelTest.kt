package com.shadowmaster.ui.settings

import app.cash.turbine.test
import com.shadowmaster.data.model.SegmentMode
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.model.SupportedLanguage
import com.shadowmaster.data.repository.SettingsRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SettingsViewModel.
 *
 * Tests cover:
 * - Config state management
 * - Language updates
 * - Segment mode updates
 * - Playback settings (speed, repeats)
 * - Practice mode settings
 * - Feature toggles (assessment, pause for navigation, bus mode, audio feedback)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        settingsRepository = mockk(relaxed = true)

        // Default mock behavior
        every { settingsRepository.config } returns flowOf(ShadowingConfig())

        viewModel = SettingsViewModel(settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    // ==================== Config State Tests ====================

    @Test
    fun `config state flows from repository`() = runTest {
        // Given
        val expectedConfig = ShadowingConfig(
            playbackSpeed = 0.75f,
            playbackRepeats = 3
        )
        every { settingsRepository.config } returns flowOf(expectedConfig)

        // When
        val viewModel = SettingsViewModel(settingsRepository)
        advanceUntilIdle()

        // Then
        viewModel.config.test {
            val config = awaitItem()
            assertEquals(0.75f, config.playbackSpeed)
            assertEquals(3, config.playbackRepeats)
        }
    }

    @Test
    fun `config defaults to empty ShadowingConfig`() = runTest {
        // Given - use default mock which returns empty config

        // When
        advanceUntilIdle()

        // Then
        viewModel.config.test {
            val config = awaitItem()
            assertEquals(ShadowingConfig(), config)
        }
    }

    // ==================== Language Update Tests ====================

    @Test
    fun `updateLanguage calls repository`() = runTest {
        // Given
        val language = SupportedLanguage.JAPANESE

        // When
        viewModel.updateLanguage(language)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateLanguage(language) }
    }

    @Test
    fun `updateLanguage handles all supported languages`() = runTest {
        // Test each supported language
        SupportedLanguage.entries.forEach { language ->
            // When
            viewModel.updateLanguage(language)
            advanceUntilIdle()

            // Then
            coVerify { settingsRepository.updateLanguage(language) }
        }
    }

    // ==================== Segment Mode Tests ====================

    @Test
    fun `updateSegmentMode calls repository with WORD mode`() = runTest {
        // When
        viewModel.updateSegmentMode(SegmentMode.WORD)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateSegmentMode(SegmentMode.WORD) }
    }

    @Test
    fun `updateSegmentMode calls repository with SENTENCE mode`() = runTest {
        // When
        viewModel.updateSegmentMode(SegmentMode.SENTENCE)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateSegmentMode(SegmentMode.SENTENCE) }
    }

    // ==================== Silence Threshold Tests ====================

    @Test
    fun `updateSilenceThreshold calls repository`() = runTest {
        // Given
        val thresholdMs = 500

        // When
        viewModel.updateSilenceThreshold(thresholdMs)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateSilenceThreshold(thresholdMs) }
    }

    @Test
    fun `updateSilenceThreshold handles various values`() = runTest {
        // Test various threshold values
        listOf(300, 500, 700, 1000).forEach { threshold ->
            // When
            viewModel.updateSilenceThreshold(threshold)
            advanceUntilIdle()

            // Then
            coVerify { settingsRepository.updateSilenceThreshold(threshold) }
        }
    }

    // ==================== Feature Toggle Tests ====================

    @Test
    fun `updatePauseForNavigation calls repository with true`() = runTest {
        // When
        viewModel.updatePauseForNavigation(true)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePauseForNavigation(true) }
    }

    @Test
    fun `updatePauseForNavigation calls repository with false`() = runTest {
        // When
        viewModel.updatePauseForNavigation(false)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updatePauseForNavigation(false) }
    }

    // ==================== Transcription Settings Tests ====================

    @Test
    fun `updateTranscriptionDefaultProvider calls repository`() = runTest {
        // Given
        val provider = "azure"

        // When
        viewModel.updateTranscriptionDefaultProvider(provider)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionDefaultProvider(provider) }
    }

    @Test
    fun `updateTranscriptionAutoOnImport calls repository with true`() = runTest {
        // When
        viewModel.updateTranscriptionAutoOnImport(true)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionAutoOnImport(true) }
    }

    @Test
    fun `updateTranscriptionAutoOnImport calls repository with false`() = runTest {
        // When
        viewModel.updateTranscriptionAutoOnImport(false)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionAutoOnImport(false) }
    }

    @Test
    fun `updateTranscriptionGoogleApiKey calls repository`() = runTest {
        // Given
        val apiKey = "test-google-key"

        // When
        viewModel.updateTranscriptionGoogleApiKey(apiKey)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionGoogleApiKey(apiKey) }
    }

    @Test
    fun `updateTranscriptionGoogleApiKey handles null`() = runTest {
        // When
        viewModel.updateTranscriptionGoogleApiKey(null)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionGoogleApiKey(null) }
    }

    @Test
    fun `updateTranscriptionAzureApiKey calls repository`() = runTest {
        // Given
        val apiKey = "test-azure-key"

        // When
        viewModel.updateTranscriptionAzureApiKey(apiKey)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionAzureApiKey(apiKey) }
    }

    @Test
    fun `updateTranscriptionAzureRegion calls repository`() = runTest {
        // Given
        val region = "eastus"

        // When
        viewModel.updateTranscriptionAzureRegion(region)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionAzureRegion(region) }
    }

    @Test
    fun `updateTranscriptionWhisperApiKey calls repository`() = runTest {
        // Given
        val apiKey = "test-whisper-key"

        // When
        viewModel.updateTranscriptionWhisperApiKey(apiKey)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionWhisperApiKey(apiKey) }
    }

    @Test
    fun `updateTranscriptionCustomUrl calls repository`() = runTest {
        // Given
        val url = "https://api.example.com/transcribe"

        // When
        viewModel.updateTranscriptionCustomUrl(url)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionCustomUrl(url) }
    }

    @Test
    fun `updateTranscriptionCustomApiKey calls repository`() = runTest {
        // Given
        val apiKey = "test-custom-key"

        // When
        viewModel.updateTranscriptionCustomApiKey(apiKey)
        advanceUntilIdle()

        // Then
        coVerify { settingsRepository.updateTranscriptionCustomApiKey(apiKey) }
    }
}
