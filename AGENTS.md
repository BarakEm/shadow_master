# Agent Instructions for Shadow Master

This file provides context for AI coding agents working on this codebase.

## Project Overview

Shadow Master is an Android app for language learning through "shadowing" - a technique where you listen to native speech and immediately repeat it. The app captures audio from other apps, detects speech segments, and orchestrates a listen-repeat-assess loop.

## Key Technical Decisions

### Audio Capture (Android 10+)
- Uses `MediaProjection` API with `AudioPlaybackCaptureConfiguration`
- Requires foreground service with `mediaProjection` type
- Captures at 16kHz mono PCM for VAD compatibility

### Voice Activity Detection
- Silero VAD library (`com.github.gkonovalov.android-vad:silero`)
- Processes 512-sample frames (~32ms at 16kHz)
- Speech/silence detection drives segment extraction

### State Management
- Sealed class `ShadowingState` defines all possible states
- `ShadowingStateMachine` handles transitions via `ShadowingEvent`
- `ShadowingCoordinator` orchestrates components based on state

### Dependency Injection
- Hilt with `@AndroidEntryPoint` on Activity and Service
- All managers are `@Singleton` with `@Inject constructor`
- No explicit modules needed - Hilt generates from constructors

## File Locations

| Component | Path |
|-----------|------|
| Entry point | `MainActivity.kt` |
| Foreground service | `service/ShadowingForegroundService.kt` |
| Main orchestrator | `core/ShadowingCoordinator.kt` |
| State machine | `core/ShadowingStateMachine.kt` |
| Audio capture | `audio/capture/AudioCaptureManager.kt` |
| VAD | `audio/vad/SileroVadDetector.kt` |
| Processing pipeline | `audio/processing/AudioProcessingPipeline.kt` |
| Playback | `audio/playback/PlaybackEngine.kt` |
| User recording | `audio/recording/UserRecordingManager.kt` |
| Settings | `data/repository/SettingsRepository.kt` |
| UI screens | `ui/driving/`, `ui/settings/` |

## Common Tasks

### Adding a new setting
1. Add field to `ShadowingConfig` in `data/model/ShadowingConfig.kt`
2. Add DataStore key and flow in `SettingsRepository.kt`
3. Add UI control in `SettingsScreen.kt`

### Modifying the shadowing flow
1. Update `ShadowingState` and `ShadowingEvent` in `data/model/ShadowingState.kt`
2. Update transitions in `ShadowingStateMachine.kt`
3. Handle new states in `ShadowingCoordinator.handleStateChange()`

### Adding audio feedback
1. Add new sound to `AudioFeedbackSystem.kt`
2. Add corresponding `FeedbackEvent` in `ShadowingCoordinator.kt`
3. Emit event at appropriate state transition

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Install to device
./gradlew installDebug

# Run tests
./gradlew test

# Clean build
./gradlew clean assembleDebug
```

## Known Limitations / TODOs

1. **Azure Assessment**: Currently returns mock scores. Real Azure Speech integration pending.
2. **Android Auto**: MediaBrowserService is minimal stub. Full Android Auto UI not implemented.
3. **Navigation Detection**: `NavigationAudioDetector` needs testing with various nav apps.

## Recently Fixed Issues

- **Playback Event Race Condition**: Fixed premature PlaybackComplete event in ShadowingCoordinator that caused state transitions before audio finished
- **Frame Buffer Handling**: Fixed audio sample loss in AudioProcessingPipeline and UserRecordingManager frame processing
- **User Recording Flow**: Fixed RecordingComplete event to properly pass recorded audio to Assessment state
- **Thread Blocking**: Replaced Thread.sleep() with coroutine delay() in AudioFeedbackSystem

## Testing Notes

- Test on Android 10+ devices (AudioPlaybackCapture requirement)
- Need to grant both microphone and screen capture permissions
- Audio capture only works when another app is playing audio
- Silero VAD model loads on first use - may have brief delay

## Code Style

- Kotlin with coroutines (no RxJava)
- Sealed classes for state/events
- StateFlow/SharedFlow for reactive streams
- Compose for all UI (no XML layouts except resources)
- Single-activity architecture with Compose Navigation
