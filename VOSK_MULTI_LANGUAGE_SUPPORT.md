# Vosk Multi-Language Support

## Overview

The LocalModelProvider now supports 11 languages through Vosk speech recognition models. All models are free, work offline, and require no API keys.

## Supported Languages

| Language | Model Name | Size | Language Code |
|----------|------------|------|---------------|
| English | vosk-model-small-en-us-0.15 | ~40MB | en, en-US |
| German | vosk-model-small-de-0.15 | ~45MB | de, de-DE |
| Arabic | vosk-model-small-ar-0.22-linto | ~45MB | ar, ar-SA |
| French | vosk-model-small-fr-0.22 | ~39MB | fr, fr-FR |
| Spanish | vosk-model-small-es-0.42 | ~39MB | es, es-ES |
| Chinese | vosk-model-small-cn-0.22 | ~42MB | zh, zh-CN (cn also accepted) |
| Russian | vosk-model-small-ru-0.22 | ~45MB | ru, ru-RU |
| Italian | vosk-model-small-it-0.22 | ~48MB | it, it-IT |
| Portuguese | vosk-model-small-pt-0.3 | ~31MB | pt, pt-BR |
| Turkish | vosk-model-small-tr-0.3 | ~35MB | tr, tr-TR |
| Hebrew | vosk-model-small-he-0.22 | ~38MB | he, he-IL (iw also accepted) |

**Note on Language Codes:**
- **Chinese**: The model filename uses "cn" but the standard ISO 639-1 code is "zh". Both codes are accepted by `getModelForLanguage()` for convenience. When using the `VoskModel` enum, the `languageCode` field is "zh" (standard code).
- **Hebrew**: The old ISO 639-1 code "iw" is still accepted alongside the current "he" code.

## Usage

### Get Model for Language

```kotlin
// Automatically select model based on language code
val modelName = LocalModelProvider.getModelForLanguage("de-DE")
// Returns: "vosk-model-small-de-0.15"

val arabicModel = LocalModelProvider.getModelForLanguage("ar-SA")
// Returns: "vosk-model-small-ar-0.22-linto"
```

### Using VoskModel Enum

```kotlin
// Get all models for a language
val germanModels = LocalModelProvider.VoskModel.getModelsForLanguage("de")
// Returns: [VoskModel.DE_SMALL]

// Get recommended model for a language
val recommendedModel = LocalModelProvider.VoskModel.getRecommendedModelForLanguage("ar")
// Returns: VoskModel.AR_SMALL

// Get all supported language codes
val languages = LocalModelProvider.VoskModel.getSupportedLanguages()
// Returns: ["ar", "de", "en", "es", "fr", "he", "it", "pt", "ru", "tr", "zh"]
```

### Download and Use Model

```kotlin
val context = applicationContext
val language = "de-DE"

// 1. Get model name for language
val modelName = LocalModelProvider.getModelForLanguage(language)
    ?: throw IllegalArgumentException("Language not supported: $language")

// 2. Check if model is downloaded
if (!LocalModelProvider.isModelDownloaded(context, modelName)) {
    // 3. Download model
    val provider = LocalModelProvider(context, null)
    val result = provider.downloadModel(modelName) { progress ->
        println("Download progress: ${(progress * 100).toInt()}%")
    }
    
    if (result.isFailure) {
        println("Download failed: ${result.exceptionOrNull()?.message}")
        return
    }
}

// 4. Create provider with downloaded model
val modelPath = LocalModelProvider.getModelPath(context, modelName).absolutePath
val provider = LocalModelProvider(context, modelPath)

// 5. Transcribe audio
val audioFile = File("/path/to/audio.wav")
val transcription = provider.transcribe(audioFile, language)

if (transcription.isSuccess) {
    println("Transcription: ${transcription.getOrNull()}")
} else {
    println("Error: ${transcription.exceptionOrNull()?.message}")
}
```

## Model Selection Strategy

For each language, we provide the "small" model variant which offers:
- ✅ Reasonable accuracy for most use cases
- ✅ Small download size (31-48MB)
- ✅ Fast processing on mobile devices
- ✅ Low memory footprint

Larger, more accurate models are available from [Vosk Models](https://alphacephei.com/vosk/models) but are not included due to size constraints (some are >1GB).

## Integration with Shadow Master

### Transcription Settings

Users can now:
1. Select "Local Model (Vosk)" as transcription provider
2. Choose their preferred language
3. Download the appropriate model (one-time, cached locally)
4. Transcribe audio offline without API costs

### Automatic Model Selection

When a user selects a language in Shadow Master:
```kotlin
val userLanguage = "ar-SA" // From settings
val modelName = LocalModelProvider.getModelForLanguage(userLanguage)

if (modelName != null && !LocalModelProvider.isModelDownloaded(context, modelName)) {
    // Prompt user to download model
    showModelDownloadDialog(modelName, language)
}
```

## Comparison: Vosk vs Android SpeechRecognizer

| Feature | Vosk (LocalModelProvider) | Android SpeechRecognizer |
|---------|---------------------------|--------------------------|
| **API Key** | Not required | Not required |
| **Cost** | Free | Free |
| **Internet** | Not required (offline) | Required (online) |
| **Input Type** | Pre-recorded files | Live microphone only |
| **Languages** | 11+ (manual download) | Many (automatic) |
| **Accuracy** | Good | Excellent |
| **Privacy** | Complete (on-device) | Limited (cloud processing) |
| **Model Size** | 31-48MB per language | No download needed |

## Use Cases

### When to Use Vosk (LocalModelProvider)
- ✅ Transcribing pre-recorded audio files
- ✅ Offline transcription needed
- ✅ Privacy-sensitive applications
- ✅ Batch processing multiple files
- ✅ Known language requirements

### When to Use Android SpeechRecognizer
- ✅ Live voice recording/transcription
- ✅ Real-time user speech input
- ✅ Unknown language (auto-detection)
- ✅ No storage space for models
- ✅ Best accuracy needed

## Adding New Languages

To add support for additional Vosk languages:

1. Find the model at [Vosk Models](https://alphacephei.com/vosk/models)
2. Add constants to `LocalModelProvider.companion object`:
```kotlin
private const val XX_SMALL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-xx-0.xx.zip"
const val XX_SMALL_NAME = "vosk-model-small-xx-0.xx"
```

3. Add to `getModelUrl()` function:
```kotlin
XX_SMALL_NAME -> XX_SMALL_URL
```

4. Add to `getModelForLanguage()` function:
```kotlin
"xx" -> XX_SMALL_NAME
```

5. Add to `VoskModel` enum:
```kotlin
XX_SMALL(XX_SMALL_NAME, sizeInMB, "Language Name (Small, ~XXMBs)", "Native Name", "xx")
```

## Resources

- [Vosk Official Website](https://alphacephei.com/vosk/)
- [Vosk Models Repository](https://alphacephei.com/vosk/models)
- [Vosk Android Documentation](https://alphacephei.com/vosk/android)
- [GitHub: vosk-api](https://github.com/alphacep/vosk-api)

## License

All Vosk models are provided under Apache 2.0 license and are free for both personal and commercial use.
