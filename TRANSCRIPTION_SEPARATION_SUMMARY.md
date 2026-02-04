# Transcription Service Separation & Hebrew Support Implementation

## Overview

This document describes the implementation of separated import and transcription functionality, along with the addition of Hebrew transcription support via ivrit.ai.

## Problem Statement

Previously, transcription was tightly coupled with the audio import process:
- Users had to decide on transcription during import
- Transcription provider couldn't be changed after import
- No easy way to transcribe existing segments
- Paid API services were prominently displayed, discouraging free usage

## Solution

### 1. Added ivrit.ai Provider for Hebrew

**New File:** `app/src/main/java/com/shadowmaster/transcription/IvritAIProvider.kt`

- Free-tier Hebrew transcription service
- No API key required for basic usage
- Optional premium API key support for higher limits
- Specialized for Hebrew language with automatic detection
- Network error handling with user-friendly messages

**Features:**
- Accepts multiple audio formats (WAV, MP3, M4A, OGG, FLAC)
- 25MB file size limit
- 60-second timeout for API requests
- Automatic Hebrew language parameter when language is "he" or "auto"

### 2. Reorganized Settings UI

**Modified:** `app/src/main/java/com/shadowmaster/ui/settings/SettingsScreen.kt`

**Changes:**
- Separated services into "Free Services" and "Paid API Services" sections
- Free services (ivrit.ai, Local Model) shown prominently first
- Paid services (Google, Azure, Whisper, Custom) hidden in collapsible section
- Updated provider selector to list free providers first
- Added ivrit.ai configuration dialog with optional API key

**UI Improvements:**
- Clear "Free tier (no key needed)" indication for ivrit.ai
- Warning text "Requires API keys and may incur costs" for paid section
- Collapsible card with expand/collapse icon for paid services
- Enhanced ApiKeyDialog to support custom description text

### 3. Manual Transcription UI

**Modified:** `app/src/main/java/com/shadowmaster/ui/library/LibraryScreen.kt`

**Changes:**
- Added "Transcribe" button (Subtitles icon) to playlist cards
- Created provider selection dialog for manual transcription
- Separated free and paid provider buttons in dialog
- Progress indicator during batch transcription
- Clear distinction between free and paid services in UI

**Transcription Dialog Features:**
- Shows free services first (ivrit.ai, Local Model)
- Shows paid services below with clear indication
- Real-time progress tracking (segment X of Y)
- Progress bar during transcription
- Cannot dismiss during transcription (prevents accidental cancellation)

### 4. Transcription Methods in ViewModel

**Modified:** `app/src/main/java/com/shadowmaster/ui/library/LibraryViewModel.kt`

**New Methods:**
- `transcribeSegment()` - Transcribe a single segment with provider selection
- `transcribeAllSegments()` - Batch transcribe all segments in playlist
- Progress tracking via `transcriptionInProgress` and `transcriptionProgress` state flows
- Error handling with specific error types and user-friendly messages

**Error Handling:**
- API key missing errors
- Network connection errors
- Provider-specific errors
- File not found errors
- Generic fallback errors

### 5. Updated Data Models

**Modified Files:**
- `app/src/main/java/com/shadowmaster/transcription/TranscriptionProvider.kt`
  - Added `isFree` boolean to `TranscriptionProviderType` enum
  - Reorganized providers: IVRIT_AI, LOCAL first (free), then paid services

- `app/src/main/java/com/shadowmaster/transcription/TranscriptionService.kt`
  - Added IVRIT_AI case to provider factory
  - Updated provider list to include ivrit.ai first

- `app/src/main/java/com/shadowmaster/data/model/ShadowingConfig.kt`
  - Added `ivritApiKey` to `TranscriptionConfig`
  - Changed default provider from "google" to "ivrit"

- `app/src/main/java/com/shadowmaster/data/repository/SettingsRepository.kt`
  - Added `TRANSCRIPTION_IVRIT_API_KEY` preference key
  - Added `updateTranscriptionIvritApiKey()` method
  - Updated config loading to include ivrit settings

## User Workflow

### Importing Audio (Unchanged)
1. User imports audio file via File Picker or URL
2. Audio is segmented using VAD
3. Segments are saved to library
4. **NEW:** Transcription is NOT automatically triggered (unless enabled in settings)

### Manual Transcription (New)
1. User navigates to Library tab
2. Clicks "Transcribe" button (Subtitles icon) on playlist card
3. Provider selection dialog appears with options:
   - **Free Services:**
     - ivrit.ai (Hebrew) - recommended for Hebrew audio
     - Local Model - works offline with downloaded model
   - **Paid Services (collapsed):**
     - Google Speech-to-Text
     - Azure Speech Services
     - OpenAI Whisper
     - Custom Endpoint
4. User selects provider
5. Progress indicator shows transcription progress
6. Transcriptions are saved to each segment
7. Success message displayed when complete

### Hebrew Audio Workflow
For Hebrew audio files:
1. Import audio as normal
2. Click "Transcribe" button on playlist
3. Select "ivrit.ai (Hebrew)" from free services
4. ivrit.ai automatically detects Hebrew language
5. Transcriptions appear in Hebrew

## Benefits

### For Users
- **Free First:** Free transcription services prominently featured
- **Flexibility:** Can try different providers without re-importing
- **Hebrew Support:** High-quality Hebrew transcription via ivrit.ai
- **No Lock-in:** Not forced to choose provider during import
- **Batch Processing:** Transcribe entire playlists with one click
- **Clear Costs:** Paid services clearly marked and separated

### For Developers
- **Clean Separation:** Import and transcription are independent operations
- **Extensible:** Easy to add new providers
- **Type Safety:** Provider type enum includes isFree flag
- **Error Handling:** Specific error types for different failure modes
- **Progress Tracking:** Built-in progress state for UI updates

## Technical Details

### Provider Selection
```kotlin
enum class TranscriptionProviderType(
    val id: String, 
    val displayName: String,
    val isFree: Boolean = false
) {
    IVRIT_AI("ivrit", "ivrit.ai (Hebrew)", isFree = true),
    LOCAL("local", "Local Model", isFree = true),
    GOOGLE("google", "Google Speech-to-Text", isFree = false),
    AZURE("azure", "Azure Speech Services", isFree = false),
    WHISPER("whisper", "OpenAI Whisper", isFree = false),
    CUSTOM("custom", "Custom Endpoint", isFree = false)
}
```

### ivrit.ai API Integration
```kotlin
// Free tier - no API key
val provider = IvritAIProvider(apiKey = null)

// Premium tier - with API key
val provider = IvritAIProvider(apiKey = "your_key")

// Automatic Hebrew detection
val result = provider.transcribe(audioFile, language = "auto")
```

### Batch Transcription
```kotlin
// In LibraryViewModel
fun transcribeAllSegments(providerType: TranscriptionProviderType) {
    // Progress tracking
    _transcriptionProgress.value = current to total
    
    // Error handling per segment
    items.forEachIndexed { index, item ->
        val result = transcriptionService.transcribe(...)
        result.onSuccess { text -> updateItemTranscription(...) }
        result.onFailure { error -> /* log and continue */ }
    }
    
    // Summary message
    "Transcribed $successCount segments ($failureCount failed)"
}
```

## Configuration

### Settings UI
1. Navigate to Settings
2. Scroll to "Transcription Services"
3. Free Services section shows:
   - ivrit.ai (Hebrew) - Configure optional premium API key
   - Local Model - Download and select model
4. Paid API Services (collapsed) - click to expand
   - Configure API keys for paid providers

### Default Provider
The default provider is set to "ivrit" but users can:
- Change default in Settings > Transcription Services > Default Provider
- Select different provider per transcription action
- Mix providers for different playlists

## Testing Recommendations

### Test Cases
1. **Import without transcription**
   - Import audio file
   - Verify segments created without transcriptions
   - Check that no transcription API calls are made

2. **Manual transcription with ivrit.ai**
   - Import Hebrew audio
   - Click Transcribe button
   - Select ivrit.ai
   - Verify progress indicator
   - Check transcriptions in Hebrew

3. **Manual transcription with Local Model**
   - Download Vosk model first
   - Import audio
   - Click Transcribe button
   - Select Local Model
   - Verify offline transcription works

4. **Provider switching**
   - Transcribe with one provider
   - Transcribe again with different provider
   - Verify transcriptions are overwritten

5. **Error handling**
   - Test with no internet (ivrit.ai should fail gracefully)
   - Test with invalid API key (should show error message)
   - Test with missing model (Local should show error)

6. **UI/UX**
   - Verify free services show first
   - Verify paid services are collapsed by default
   - Verify progress indicator during transcription
   - Verify cannot dismiss dialog during transcription

### Manual Testing Steps
```bash
# Build and install
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# Test Hebrew transcription with ivrit.ai
1. Import Hebrew audio file
2. Navigate to Library tab
3. Click Subtitles icon on playlist
4. Select "ivrit.ai (Hebrew)"
5. Wait for transcription to complete
6. Verify Hebrew text appears in segments
```

## Future Enhancements

1. **Per-segment provider selection**
   - Allow different providers for different segments
   - Useful for mixed-language content

2. **Transcription confidence scores**
   - Show confidence percentage per segment
   - Highlight low-confidence transcriptions for review

3. **Automatic language detection**
   - Detect language per segment
   - Route to appropriate provider automatically

4. **Transcription editing**
   - In-app editor for corrections
   - Save corrections as training data

5. **Offline fallback chain**
   - Try ivrit.ai first
   - Fall back to Local Model if offline
   - Smart provider selection based on connectivity

## References

- **ivrit.ai:** https://ivrit.ai/
- **Vosk Models:** https://alphacephei.com/vosk/models
- **Material Design 3:** https://m3.material.io/
- **Jetpack Compose:** https://developer.android.com/jetpack/compose

## Migration Notes

### For Existing Users
- Existing transcriptions are preserved
- `autoTranscribeOnImport` setting still works but is now optional
- No action required for existing playlists
- Can re-transcribe with new providers anytime

### For Developers
- Import and transcription are now independent
- Always check `isFree` flag before requiring API keys
- Use `TranscriptionProviderType` enum for type safety
- Handle transcription errors gracefully with specific error types
- Progress tracking is built-in via StateFlows

## Conclusion

This implementation successfully separates import and transcription concerns while adding robust Hebrew support through ivrit.ai. The UI clearly distinguishes free and paid services, empowering users to choose the best option for their needs without upfront costs or commitments.

The architecture is extensible, allowing easy addition of new providers while maintaining a clean separation of concerns and excellent user experience.
# Rebased onto latest master
