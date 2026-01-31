# GitHub Copilot Instructions for Shadow Master

This file provides context and guidelines for GitHub Copilot when working on this codebase.

## Project Overview

Shadow Master is an Android app for language learning through "shadowing" - a technique where users listen to native speech and immediately repeat it. The app can:
- Import audio files for offline practice (Shadow Library mode)
- Capture live audio from other apps in real-time (Live Shadow mode)
- Automatically detect speech segments using voice activity detection (VAD)
- Orchestrate a listen-repeat-assess loop for pronunciation practice

## Key Technologies

- **Language:** Kotlin with Coroutines and Flow
- **UI:** Jetpack Compose (no XML layouts)
- **Database:** Room for local storage (Shadow Library)
- **DI:** Hilt for dependency injection
- **Settings:** DataStore for preferences
- **Audio:** MediaCodec for decoding, MediaProjection for capture
- **VAD:** Silero VAD library for speech detection
- **Assessment:** Azure Speech SDK for pronunciation evaluation (optional)

## Architecture Patterns

### State Management
- `ShadowingState` (sealed class) - defines all possible states
- `ShadowingStateMachine` - handles state transitions via `ShadowingEvent`
- `ShadowingCoordinator` - main orchestrator, coordinates all components based on state

### Audio Processing Pipeline
1. **Capture** (`AudioCaptureManager`) - MediaProjection API for Android 10+
2. **VAD** (`SileroVadDetector`) - processes 512-sample frames at 16kHz
3. **Processing** (`AudioProcessingPipeline`) - buffers and segments audio
4. **Playback** (`PlaybackEngine`) - AudioTrack for segment playback
5. **Recording** (`UserRecordingManager`) - microphone capture for user speech

### Dependency Injection
- All managers are `@Singleton` with `@Inject constructor`
- Activities/Services use `@AndroidEntryPoint`
- No explicit Hilt modules needed - generated from constructors

## Key File Locations

| Component | Path |
|-----------|------|
| Entry point | `app/src/main/java/com/shadowmaster/MainActivity.kt` |
| Foreground service | `service/ShadowingForegroundService.kt` |
| Main orchestrator | `core/ShadowingCoordinator.kt` |
| State machine | `core/ShadowingStateMachine.kt` |
| Audio capture | `audio/capture/AudioCaptureManager.kt` |
| VAD detector | `audio/vad/SileroVadDetector.kt` |
| Processing pipeline | `audio/processing/AudioProcessingPipeline.kt` |
| Playback engine | `audio/playback/PlaybackEngine.kt` |
| User recording | `audio/recording/UserRecordingManager.kt` |
| Settings repository | `data/repository/SettingsRepository.kt` |
| Library repository | `data/repository/LibraryRepository.kt` |
| Audio importer | `library/AudioImporter.kt` |
| Audio exporter | `library/AudioExporter.kt` |
| UI screens | `ui/home/`, `ui/library/`, `ui/practice/`, `ui/driving/`, `ui/settings/` |

## Common Development Tasks

### Adding a New Setting
1. Add field to `ShadowingConfig` in `data/model/ShadowingConfig.kt`
2. Add DataStore key and flow in `data/repository/SettingsRepository.kt`
3. Add UI control in `ui/settings/SettingsScreen.kt`
4. Update default values in `SettingsRepository`

### Modifying the Shadowing Flow
1. Update `ShadowingState` and `ShadowingEvent` in `data/model/ShadowingState.kt`
2. Update state transitions in `core/ShadowingStateMachine.kt`
3. Handle new states in `ShadowingCoordinator.handleStateChange()`
4. Update UI to reflect new states

### Adding Audio Feedback
1. Add new sound generator to `feedback/AudioFeedbackSystem.kt`
2. Add corresponding `FeedbackEvent` enum value
3. Emit event at appropriate state transition in `ShadowingCoordinator.kt`
4. Test feedback timing with actual audio flow

### Adding a Database Entity
1. Create entity class in `data/local/` package with `@Entity` annotation
2. Add DAO interface with `@Dao` annotation
3. Update `ShadowMasterDatabase.kt` to include new DAO
4. Create and test database migration
5. Increment database version number

## Coding Guidelines

### Kotlin Style
- Use Kotlin coroutines (no RxJava)
- Prefer `sealed class` for state/events
- Use `StateFlow`/`SharedFlow` for reactive streams
- Use `data class` for immutable models
- Prefer `val` over `var` where possible

### Compose UI
- Single-activity architecture with Compose Navigation
- No XML layouts (except resources like strings, colors)
- Use `remember` and `derivedStateOf` for optimization
- Add `@Preview` functions for UI components
- Use Material 3 design system

### Threading & Concurrency
- Use `Dispatchers.IO` for disk/network operations
- Use `Dispatchers.Default` for CPU-intensive work
- Use `Dispatchers.Main` for UI updates
- Avoid `Thread.sleep()` - use `delay()` from coroutines
- Use `Mutex` or `synchronized` for thread-safe operations

### Audio Processing
- Always work with 16kHz mono PCM for VAD compatibility
- Use 512-sample frames (~32ms at 16kHz) for Silero VAD
- Handle audio buffer overflow gracefully
- Clean up audio resources in finally blocks
- Test with various audio sources and formats

### Error Handling
- Use sealed classes for structured errors
- Provide user-friendly error messages
- Log errors with context (component, operation, parameters)
- Handle permission denials gracefully
- Don't crash on non-critical errors

## Build & Test Commands

```bash
# Clean and build debug APK
./gradlew clean assembleDebug

# Install to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Lint check
./gradlew lint

# Generate lint report
./gradlew lintDebug
```

## Testing Guidelines

### Requirements
- Test on Android 10+ devices (AudioPlaybackCapture requirement)
- Grant microphone and storage permissions
- Live Shadow requires screen capture permission each session
- Audio capture only works when another app is playing audio
- Silero VAD model loads on first use (brief delay expected)

### Unit Tests
- Use JUnit5 and MockK for mocking
- Test state transitions thoroughly
- Mock Room DAOs for repository tests
- Use `TestCoroutineDispatcher` for coroutine testing
- Test error cases and edge conditions

### UI Tests
- Use Compose testing utilities
- Test navigation flows
- Verify state changes update UI correctly
- Test user interactions and dialogs

## Known Limitations

1. **Azure Assessment:** Currently returns mock scores. Real Azure Speech integration is planned.
2. **Android Auto:** MediaBrowserService is a minimal stub. Full Android Auto UI not implemented.
3. **Navigation Detection:** `NavigationAudioDetector` needs testing with various navigation apps.

## Recent Bug Fixes

- **Playback Event Race Condition:** Fixed premature PlaybackComplete event that caused state transitions before audio finished
- **Frame Buffer Handling:** Fixed audio sample loss in AudioProcessingPipeline and UserRecordingManager
- **User Recording Flow:** Fixed RecordingComplete event to properly pass recorded audio to Assessment state
- **Thread Blocking:** Replaced Thread.sleep() with coroutine delay() in AudioFeedbackSystem

## Version Roadmap

### ✅ Version 1.0 (Released)
- Shadow Library with audio import and segmentation
- Live Shadow with real-time capture
- VAD-based speech detection (Silero)
- Playback speed control and repeat options
- User recording with silence detection
- Audio feedback beeps for hands-free use
- Bus mode (listen-only)

### 🚧 Version 1.1 (In Progress)
- Rename playlists and segments
- Transcription display and manual entry
- Translation display and manual entry
- Pronunciation feedback display
- Split long segments
- Merge short segments
- Export playlist as practice audio

### 📋 Version 1.2 (Planned)
- Automatic transcription (Google Cloud Speech-to-Text, Azure Speech Services, or Whisper API)
- Automatic translation (Google Cloud Translation, Azure Translator, or DeepL API)
- Real Azure pronunciation assessment
- Import from URL (YouTube, podcasts)

## Configuration Files

### Azure Speech (Optional)
Create `local.properties` in project root:
```properties
AZURE_SPEECH_KEY=your_azure_speech_key
AZURE_SPEECH_REGION=your_azure_region
```

### Gradle Configuration
- `build.gradle.kts` - main build configuration
- `app/build.gradle.kts` - app module configuration
- `gradle.properties` - project properties
- `local.properties.template` - template for local configuration

## Additional Resources

- **Project Website:** https://barakem.github.io/shadow_master/
- **GitHub Repository:** https://github.com/BarakEm/shadow_master
- **Issue Tracker:** Use GitHub Issues for bugs and feature requests
- **Copilot Tasks:** See `COPILOT_TASKS.md` for delegatable tasks

## Tips for Copilot

1. **When suggesting audio code:** Always consider 16kHz mono PCM format
2. **When adding UI:** Use Compose, not XML
3. **When handling state:** Update StateMachine and Coordinator together
4. **When adding dependencies:** Check for existing alternatives first
5. **When writing tests:** Follow existing test patterns in the codebase
6. **When handling errors:** Use sealed classes for structured error types
7. **When using coroutines:** Be mindful of dispatcher choice
8. **When modifying database:** Remember to create and test migrations
