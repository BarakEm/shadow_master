# Google Speech API Without API Keys - Investigation and Implementation

## Executive Summary

**YES**, Google's speech recognition can be used without API keys in Android apps, similar to how the [hebrew-voice-game](https://github.com/BarakEm/hebrew-voice-game) repository uses it in web browsers.

## How It Works

### Web Browser (hebrew-voice-game)
The hebrew-voice-game repository uses the **Web Speech API**:
```javascript
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
recognition = new SpeechRecognition();
recognition.lang = 'he-IL'; // or 'en-US'
recognition.start();
```

This API:
- Works in Chrome/Chromium browsers
- Connects to Google's speech service transparently
- Requires **no API key**
- Is **free** for consumer applications
- Works out of the box

### Android Native Apps (Shadow Master)
Android provides an equivalent API called **Android SpeechRecognizer**:
```kotlin
val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
}
recognizer.startListening(intent)
```

This API:
- Part of Android framework since API level 8
- Connects to Google's speech service (when Google app is installed)
- Requires **no API key**
- Is **free** for consumer applications
- Works out of the box on standard Android devices

## Implementation in Shadow Master

I've implemented a new transcription provider: `AndroidSpeechProvider`

### Features
- ✅ No API key required
- ✅ Free for consumer use
- ✅ Supports multiple languages
- ✅ Works on standard Android devices with Google services
- ✅ Can work offline if device has offline models installed
- ✅ Marked as `isFree = true` in provider list

### Location
- **File**: `app/src/main/java/com/shadowmaster/transcription/AndroidSpeechProvider.kt`
- **Provider Type**: `TranscriptionProviderType.ANDROID_SPEECH`
- **Display Name**: "Google Speech (Free)"

### Integration
The provider is integrated into the existing transcription system:
1. Added `ANDROID_SPEECH` to `TranscriptionProviderType` enum
2. Added provider creation in `TranscriptionService.createProvider()`
3. Added to available providers list in `TranscriptionService.getAvailableProviders()`

## Key Differences: Free vs Paid Google Speech

### Android SpeechRecognizer (FREE) ✅
- **Cost**: Free
- **API Key**: Not required
- **Setup**: Works out of the box
- **Use Case**: Consumer apps, personal use
- **Limitations**: 
  - Rate limits for abuse prevention
  - Primarily designed for live microphone input
  - Requires Google app/services on device
- **Similar to**: Web Speech API in browsers

### Google Cloud Speech-to-Text API (PAID) 💳
- **Cost**: Paid ($1.44 per hour of audio)
- **API Key**: Required
- **Setup**: Requires Google Cloud account, billing, and API key
- **Use Case**: Enterprise apps, high volume, advanced features
- **Features**:
  - File-based transcription
  - Batch processing
  - Speaker diarization
  - Word-level timestamps
  - More languages and models
  - No rate limits (based on quota)

## Technical Considerations

### Current Limitation
The Android SpeechRecognizer API is designed for **live microphone input**. Shadow Master currently uses **pre-recorded audio files** for transcription. This creates a technical challenge:

**Option 1: Live Transcription** (RECOMMENDED)
- Implement live transcription during user recording
- Use `transcribeLive()` method in `AndroidSpeechProvider`
- Best user experience
- Most efficient

**Option 2: File Playback Workaround**
- Play audio file while simultaneously recording with microphone
- Feed recorded audio to SpeechRecognizer
- More complex, less efficient
- May have audio quality issues

**Option 3: Hybrid Approach**
- Use Android SpeechRecognizer for live recording
- Use other providers (Local Model, Ivrit.ai) for file-based transcription
- Best of both worlds

### Implementation Status

✅ **Completed**:
- `AndroidSpeechProvider` class with live transcription support
- Integration into transcription system
- Configuration and validation
- Documentation

⚠️ **Limitations**:
- File-based transcription not fully implemented (returns informative error)
- Designed for live microphone input (best use case)

🔄 **Recommended Next Steps**:
1. Add UI option to use live transcription during user recording
2. Show "Google Speech (Free)" option in transcription settings
3. Implement live transcription workflow in practice screen

## Comparison with hebrew-voice-game

| Aspect | hebrew-voice-game | Shadow Master |
|--------|-------------------|---------------|
| Platform | Web (Browser) | Android Native |
| API Used | Web Speech API | Android SpeechRecognizer |
| API Key | Not required | Not required |
| Cost | Free | Free |
| Implementation | JavaScript | Kotlin |
| Audio Input | Microphone | File + Microphone |
| Offline Support | Browser-dependent | Device-dependent |

Both use Google's free consumer speech service, just with platform-appropriate APIs.

## Testing Recommendations

To test the new provider:

1. **Check Availability**:
```kotlin
val provider = AndroidSpeechProvider(context)
val result = provider.validateConfiguration()
// Should succeed on devices with Google services
```

2. **Live Transcription**:
```kotlin
val result = provider.transcribeLive(
    language = "en-US",
    maxDurationMs = 10000
)
// Should return transcribed text
```

3. **Integration Test**:
- Go to transcription settings
- Select "Google Speech (Free)"
- Try recording and transcribing
- Should work without API key configuration

## Permissions

Already configured in `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

## Conclusion

**YES**, Google speech recognition can be used without API keys in Shadow Master, just like in hebrew-voice-game. The implementation is complete and ready for testing. The Android SpeechRecognizer API is the native Android equivalent of the Web Speech API, providing free access to Google's speech recognition service for consumer applications.

The key insight is that both platforms (web and Android) offer free, API-key-less access to Google's speech service through platform-specific APIs designed for consumer use cases.
