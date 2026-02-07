package com.shadowmaster.transcription

import android.content.Context
import com.shadowmaster.data.model.SupportedLanguage
import com.shadowmaster.library.AudioFileUtility
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Test suite for multi-language transcription support.
 * Validates provider creation, language routing, and configuration
 * for all supported languages including Hebrew, Arabic, and Mandarin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageSupportTest {

    private val mockContext: Context = mockk(relaxed = true)
    private val audioFileUtility = AudioFileUtility()
    private lateinit var service: TranscriptionService

    @Before
    fun setup() {
        service = TranscriptionService(mockContext, audioFileUtility)
    }

    // ==================== SupportedLanguage Enum Tests ====================

    @Test
    fun `all supported languages have valid codes`() {
        SupportedLanguage.entries.forEach { lang ->
            assertTrue(
                "Language ${lang.name} should have a non-blank code",
                lang.code.isNotBlank()
            )
            assertTrue(
                "Language ${lang.name} code should contain hyphen (BCP-47 format)",
                lang.code.contains("-")
            )
        }
    }

    @Test
    fun `all supported languages have display names`() {
        SupportedLanguage.entries.forEach { lang ->
            assertTrue(
                "Language ${lang.name} should have a non-blank display name",
                lang.displayName.isNotBlank()
            )
        }
    }

    @Test
    fun `all supported languages have azure locales`() {
        SupportedLanguage.entries.forEach { lang ->
            assertTrue(
                "Language ${lang.name} should have a non-blank Azure locale",
                lang.azureLocale.isNotBlank()
            )
        }
    }

    @Test
    fun `fromCode returns correct language for Hebrew`() {
        val language = SupportedLanguage.fromCode("he-IL")
        assertEquals(SupportedLanguage.HEBREW, language)
    }

    @Test
    fun `fromCode returns correct language for Arabic`() {
        val language = SupportedLanguage.fromCode("ar-SA")
        assertEquals(SupportedLanguage.ARABIC, language)
    }

    @Test
    fun `fromCode returns correct language for Mandarin`() {
        val language = SupportedLanguage.fromCode("zh-CN")
        assertEquals(SupportedLanguage.MANDARIN, language)
    }

    @Test
    fun `fromCode returns correct language for Russian`() {
        val language = SupportedLanguage.fromCode("ru-RU")
        assertEquals(SupportedLanguage.RUSSIAN, language)
    }

    @Test
    fun `fromCode returns English for unknown code`() {
        val language = SupportedLanguage.fromCode("unknown")
        assertEquals(SupportedLanguage.ENGLISH_US, language)
    }

    @Test
    fun `fromCode roundtrip works for all languages`() {
        SupportedLanguage.entries.forEach { lang ->
            val roundTrip = SupportedLanguage.fromCode(lang.code)
            assertEquals(
                "fromCode(${lang.code}) should return ${lang.name}",
                lang, roundTrip
            )
        }
    }

    // ==================== Hebrew Transcription Tests ====================

    @Test
    fun `ivrit provider is created for Hebrew transcription`() {
        val config = ProviderConfig()
        val provider = service.createProvider(TranscriptionProviderType.IVRIT_AI, config)

        assertNotNull("IvritAI provider should be created without API key", provider)
        assertEquals("ivrit.ai (Hebrew)", provider?.name)
        assertFalse("ivrit.ai should not require API key", provider?.requiresApiKey ?: true)
    }

    @Test
    fun `ivrit provider validates without API key`() = runTest {
        val config = ProviderConfig()
        val result = service.validateProvider(TranscriptionProviderType.IVRIT_AI, config)
        assertTrue("ivrit.ai should validate without API key", result.isSuccess)
    }

    @Test
    fun `ivrit provider is created with optional API key`() {
        val config = ProviderConfig(ivritApiKey = "test-premium-key")
        val provider = service.createProvider(TranscriptionProviderType.IVRIT_AI, config)

        assertNotNull(provider)
        assertTrue(provider is IvritAIProvider)
    }

    @Test
    fun `hebrew language code is correct`() {
        assertEquals("he-IL", SupportedLanguage.HEBREW.code)
        assertEquals("Hebrew", SupportedLanguage.HEBREW.displayName)
        assertEquals("he-IL", SupportedLanguage.HEBREW.azureLocale)
    }

    // ==================== RTL Language Tests (Hebrew, Arabic) ====================

    @Test
    fun `rtl languages are included in supported languages`() {
        val rtlLanguages = listOf(SupportedLanguage.HEBREW, SupportedLanguage.ARABIC)
        rtlLanguages.forEach { lang ->
            assertTrue(
                "${lang.name} should be in SupportedLanguage entries",
                SupportedLanguage.entries.contains(lang)
            )
        }
    }

    @Test
    fun `arabic language code is correct`() {
        assertEquals("ar-SA", SupportedLanguage.ARABIC.code)
        assertEquals("Arabic", SupportedLanguage.ARABIC.displayName)
    }

    // ==================== Provider Language Routing Tests ====================

    @Test
    fun `transcribe fails gracefully for google without API key for any language`() = runTest {
        val audioFile = File.createTempFile("test", ".wav")
        try {
            val languageCodes = listOf("en-US", "he-IL", "ar-SA", "zh-CN", "ru-RU", "de-DE")

            languageCodes.forEach { langCode ->
                val config = ProviderConfig() // No API key
                val result = service.transcribe(
                    audioFile, langCode,
                    TranscriptionProviderType.GOOGLE, config
                )
                assertTrue(
                    "Google transcription should fail without API key for $langCode",
                    result.isFailure
                )
                assertTrue(
                    "Error should be ApiKeyMissing for $langCode",
                    result.exceptionOrNull() is TranscriptionError.ApiKeyMissing
                )
            }
        } finally {
            audioFile.delete()
        }
    }

    @Test
    fun `transcribe fails gracefully for azure without config for various languages`() = runTest {
        val audioFile = File.createTempFile("test", ".wav")
        try {
            val languageCodes = listOf("en-US", "he-IL", "fr-FR", "it-IT", "pt-BR")

            languageCodes.forEach { langCode ->
                val config = ProviderConfig() // No Azure config
                val result = service.transcribe(
                    audioFile, langCode,
                    TranscriptionProviderType.AZURE, config
                )
                assertTrue(
                    "Azure transcription should fail without config for $langCode",
                    result.isFailure
                )
            }
        } finally {
            audioFile.delete()
        }
    }

    @Test
    fun `transcribe fails gracefully for whisper without API key for various languages`() = runTest {
        val audioFile = File.createTempFile("test", ".wav")
        try {
            val languageCodes = listOf("en-US", "he-IL", "de-DE", "es-ES", "zh-CN")

            languageCodes.forEach { langCode ->
                val config = ProviderConfig() // No Whisper API key
                val result = service.transcribe(
                    audioFile, langCode,
                    TranscriptionProviderType.WHISPER, config
                )
                assertTrue(
                    "Whisper transcription should fail without API key for $langCode",
                    result.isFailure
                )
            }
        } finally {
            audioFile.delete()
        }
    }

    // ==================== Provider Type Filtering Tests ====================

    @Test
    fun `implemented providers include ivrit for Hebrew`() {
        val implemented = TranscriptionProviderType.entries.filter { it.isImplemented }
        assertTrue(implemented.contains(TranscriptionProviderType.IVRIT_AI))
    }

    @Test
    fun `implemented providers include android speech for all languages`() {
        val implemented = TranscriptionProviderType.entries.filter { it.isImplemented }
        assertTrue(implemented.contains(TranscriptionProviderType.ANDROID_SPEECH))
    }

    @Test
    fun `stub providers are not implemented`() {
        assertFalse(TranscriptionProviderType.GOOGLE.isImplemented)
        assertFalse(TranscriptionProviderType.AZURE.isImplemented)
    }

    @Test
    fun `whisper provider is implemented`() {
        assertTrue(TranscriptionProviderType.WHISPER.isImplemented)
    }

    @Test
    fun `free providers are marked correctly`() {
        assertTrue(TranscriptionProviderType.IVRIT_AI.isFree)
        assertTrue(TranscriptionProviderType.LOCAL.isFree)
        assertTrue(TranscriptionProviderType.ANDROID_SPEECH.isFree)
        assertFalse(TranscriptionProviderType.CUSTOM.isFree)
    }

    // ==================== Language Code Normalization Tests ====================

    @Test
    fun `language codes follow BCP-47 format`() {
        val expectedCodes = mapOf(
            SupportedLanguage.ENGLISH_US to "en-US",
            SupportedLanguage.GERMAN to "de-DE",
            SupportedLanguage.SPANISH to "es-ES",
            SupportedLanguage.RUSSIAN to "ru-RU",
            SupportedLanguage.HEBREW to "he-IL",
            SupportedLanguage.ARABIC to "ar-SA",
            SupportedLanguage.FRENCH to "fr-FR",
            SupportedLanguage.ITALIAN to "it-IT",
            SupportedLanguage.PORTUGUESE to "pt-BR",
            SupportedLanguage.MANDARIN to "zh-CN"
        )

        expectedCodes.forEach { (lang, expected) ->
            assertEquals(
                "Language code for ${lang.name} should be $expected",
                expected, lang.code
            )
        }
    }

    @Test
    fun `supported languages count is correct`() {
        assertEquals(10, SupportedLanguage.entries.size)
    }

    // ==================== Android Speech Provider Tests ====================

    @Test
    fun `android speech provider is created for multilingual use`() {
        val config = ProviderConfig()
        val provider = service.createProvider(TranscriptionProviderType.ANDROID_SPEECH, config)

        assertNotNull(provider)
        assertTrue(provider is AndroidSpeechProvider)
        assertFalse(provider?.requiresApiKey ?: true)
    }

    @Test
    fun `android speech provider validates without config`() = runTest {
        val config = ProviderConfig()
        val result = service.validateProvider(TranscriptionProviderType.ANDROID_SPEECH, config)
        assertTrue(result.isSuccess)
    }

    // ==================== Custom Endpoint Language Tests ====================

    @Test
    fun `custom endpoint accepts any language code`() = runTest {
        val config = ProviderConfig(
            customEndpointUrl = "https://api.example.com/transcribe"
        )
        val result = service.validateProvider(TranscriptionProviderType.CUSTOM, config)
        assertTrue("Custom endpoint should validate with URL", result.isSuccess)
    }
}
