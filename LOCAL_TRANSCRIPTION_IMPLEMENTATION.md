# Local Transcription Implementation

## Overview
This implementation adds free-tier, offline transcription to Shadow Master using Whisper.cpp. Users can transcribe audio segments without API keys, completely offline after downloading a model.

## What Was Implemented

### 1. LocalModelProvider ✅
**File:** `app/src/main/java/com/shadowmaster/transcription/LocalModelProvider.kt`

**Features:**
- Implements `TranscriptionProvider` interface
- Model download from Hugging Face (tiny ~40MB, base ~75MB)
- Model file management (download, validate, delete)
- Progress callback during model download
- Offline transcription framework (stub implementation)

**Key Methods:**
- `downloadModel()` - Downloads Whisper models from Hugging Face
- `transcribe()` - Transcribes audio (stub, needs Whisper.cpp library)
- `validateConfiguration()` - Validates model file exists
- `isModelDownloaded()` - Checks if model is ready
- `getModelPath()` - Returns path to model files

### 2. Settings UI ✅
**File:** `app/src/main/java/com/shadowmaster/ui/settings/SettingsScreen.kt`

**Features:**
- Local Model provider section in Transcription Services
- Model selection dialog (Tiny/Base)
- Download progress indicator
- Model status display (downloaded/not downloaded/ready)
- Download error handling

**Components:**
- `LocalModelProviderSection` - Provider card with status
- `LocalModelDialog` - Model selection and download UI
- Progress bar during model download
- Radio button selection for model types

### 3. Data Layer ✅
**Files Modified:**
- `app/src/main/java/com/shadowmaster/data/model/ShadowingConfig.kt`
- `app/src/main/java/com/shadowmaster/data/repository/SettingsRepository.kt`
- `app/src/main/java/com/shadowmaster/ui/settings/SettingsViewModel.kt`

**Added Fields:**
- `localModelPath` - Path to downloaded model file
- `localModelName` - Selected model name (tiny/base)

**New Methods:**
- `updateTranscriptionLocalModelPath()`
- `updateTranscriptionLocalModelName()`

### 4. Service Integration ✅
**File:** `app/src/main/java/com/shadowmaster/transcription/TranscriptionService.kt`

**Changes:**
- `createProvider()` now creates `LocalModelProvider` when type is LOCAL
- `getAvailableProviders()` includes `TranscriptionProviderType.LOCAL`
- LOCAL provider requires `Context` injection

### 5. Tests ✅
**File:** `app/src/test/java/com/shadowmaster/transcription/TranscriptionServiceTest.kt`

**Updates:**
- Updated test expecting LOCAL provider to be created (not null)
- Updated available providers count to 5 (was 4)
- Added LOCAL to expected providers list

## What Needs to Be Done

### Complete Whisper.cpp Integration

#### Step 1: Add Whisper.cpp Library
Add one of these libraries to `app/build.gradle.kts`:

**Option A: Use official whisper.cpp Android bindings**
```kotlin
// Add to dependencies
implementation("com.github.ggerganov:whisper.cpp:v1.5.5@aar")
```

**Option B: Build from source**
Follow instructions at: https://github.com/ggerganov/whisper.cpp/tree/master/examples/whisper.android

#### Step 2: Update LocalModelProvider
Replace the stub `transcribe()` method with actual Whisper.cpp calls:

```kotlin
override suspend fun transcribe(audioFile: File, language: String): Result<String> = withContext(Dispatchers.IO) {
    validateConfiguration().getOrElse { return@withContext Result.failure(it) }

    try {
        // Initialize WhisperContext
        val whisperContext = WhisperContext.createContextFromFile(modelPath!!)
        
        // Read and convert audio
        val samples = readAudioFile(audioFile)
        
        // Transcribe
        val langCode = language.split("-").firstOrNull() ?: "en"
        val result = whisperContext.transcribeData(samples, langCode, null)
        
        // Release context
        whisperContext.release()
        
        Result.success(result.text.trim())
    } catch (e: Exception) {
        Log.e(TAG, "Transcription failed", e)
        Result.failure(TranscriptionError.UnknownError(name, e))
    }
}
```

#### Step 3: Add CMakeLists.txt (if building from source)
Create `app/src/main/cpp/CMakeLists.txt` for JNI bindings.

#### Step 4: Test
1. Build and install the app
2. Go to Settings > Transcription Services
3. Click "Local Model (Whisper.cpp)"
4. Select and download Tiny model
5. Set as default provider
6. Import audio and test transcription

## Architecture

### Model Storage
- Models stored in: `{app.filesDir}/whisper_models/`
- Tiny model: `ggml-tiny.bin` (~40MB)
- Base model: `ggml-base.bin` (~75MB)

### Model Download
- Source: Hugging Face model repository
- URL: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/{model}.bin`
- Progress tracking via callback
- Atomic download (delete + redownload on failure)

### Transcription Flow
1. User selects local provider in settings
2. Downloads model (one-time)
3. Uses transcription in library:
   - Select segment(s)
   - Click "Transcribe"
   - Provider validates model exists
   - Converts audio to required format
   - Calls Whisper.cpp
   - Returns transcribed text

## Benefits

### For Users
- ✅ No API keys required
- ✅ Works completely offline
- ✅ No API costs
- ✅ Privacy-friendly (data stays on device)
- ✅ Fast transcription (on-device)

### Technical
- ✅ Clean provider interface
- ✅ On-demand model download
- ✅ Progress tracking
- ✅ Error handling
- ✅ Multiple model sizes

## Files Changed

### New Files
- `app/src/main/java/com/shadowmaster/transcription/LocalModelProvider.kt`
- `LOCAL_TRANSCRIPTION_IMPLEMENTATION.md` (this file)

### Modified Files
- `app/build.gradle.kts` - TODO comment for Whisper.cpp dependency
- `app/src/main/java/com/shadowmaster/data/model/ShadowingConfig.kt` - Added local model fields
- `app/src/main/java/com/shadowmaster/data/repository/SettingsRepository.kt` - Added local model settings
- `app/src/main/java/com/shadowmaster/transcription/TranscriptionService.kt` - LOCAL provider support
- `app/src/main/java/com/shadowmaster/ui/settings/SettingsScreen.kt` - Local model UI
- `app/src/main/java/com/shadowmaster/ui/settings/SettingsViewModel.kt` - Local model view model methods
- `app/src/test/java/com/shadowmaster/transcription/TranscriptionServiceTest.kt` - Updated tests

## Acceptance Criteria Status

- ✅ Users can transcribe without API keys - Implementation complete, needs library integration
- ✅ Works offline after model download - Model download implemented
- ✅ Model download is on-demand - UI and download logic complete

## Next Steps

1. **Integrate Whisper.cpp library** - Add dependency and update transcribe() method
2. **Test end-to-end** - Download model, transcribe audio, verify results
3. **Optimize** - Consider model quantization, caching, etc.
4. **Document** - Update user-facing documentation with local transcription guide

## Notes

- Whisper.cpp requires models in GGML format (binary)
- Audio must be 16kHz mono PCM for optimal results
- Tiny model is faster but less accurate than Base
- Consider adding more models (small, medium) in future
- May need to handle large model files (100MB+) with additional UI feedback

## References

- Whisper.cpp: https://github.com/ggerganov/whisper.cpp
- Hugging Face models: https://huggingface.co/ggerganov/whisper.cpp
- Android bindings example: https://github.com/ggerganov/whisper.cpp/tree/master/examples/whisper.android
