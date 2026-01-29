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

## Gemini CLI Agent

This project is being developed using the Gemini CLI. The CLI provides a set of tools and agents to assist with software development.

### Quota Limits
Gemini CLI offers different quota limits based on your authentication method:
*   **Free Usage:** Varies by authentication (Google account for Gemini Code Assist, unpaid Gemini API Key, or Vertex AI Express Mode). For instance, individual Google account users get 1000 requests/day, and unpaid API key users get 250 requests/day.
*   **Paid Tier:** Offers higher limits through subscriptions like Google AI Pro/Ultra or Gemini Code Assist subscriptions (Standard/Enterprise editions with increased daily and minute limits).
*   **Pay-As-You-Go:** Provides the most flexibility via a Gemini API key or Vertex AI (Regular Mode), where costs are based on token/model usage, bypassing fixed daily limits.

### `/stats` Command
The `/stats` command provides a summary of your model usage. This summary is also automatically displayed when you exit a session.

To avoid hitting limits, especially in Pay-As-You-Go models, it's recommended to be mindful of your usage and be intentional with prompts and commands.

### `delegate_to_agent` Tool
The `delegate_to_agent` tool can be used to delegate tasks to specialized agents.

#### `cli_help` Agent
The `cli_help` agent is specialized in answering questions about the Gemini CLI's features, documentation, and current runtime configuration.

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

## Version 1.1 Features Implementation Guide

### Rename Feature
- **Location**: `ui/library/LibraryScreen.kt`
- **DAO methods**: `updatePlaylistName()`, `updateItemTranscription()` in DAOs
- **UI**: Long-press context menu or edit icon in detail view

### Transcription Feature
- **Data field**: `ShadowItem.transcription` (already exists, nullable String)
- **Display**: Show in practice screen and library detail
- **Manual entry**: Edit dialog in segment detail view
- **Future**: Add Speech-to-Text API integration (Google Cloud, Azure, Whisper)

### Translation Feature
- **Data field**: `ShadowItem.translation` (already exists, nullable String)
- **Display**: Below transcription in practice screen
- **Manual entry**: Edit dialog in segment detail view
- **Future**: Add Translation API (Google Translate, Azure, DeepL)

### Pronunciation Feedback Display
- **Data**: `AssessmentResult` with overallScore, pronunciationScore, fluencyScore, completenessScore
- **Display**: Score bars or percentage in practice screen
- **Current**: Mock scores returned - real Azure integration planned

### Segment Split Feature
- **Location**: `ui/library/SegmentDetailScreen.kt` (new)
- **Logic**:
  1. Load segment audio samples
  2. User selects split point(s) via timeline
  3. Create new ShadowItem entries with adjusted sourceStartMs/sourceEndMs
  4. Save new segment audio files
- **Audio**: Use `AudioImporter.extractSegment()` pattern

### Segment Merge Feature
- **Location**: `ui/library/LibraryScreen.kt`
- **Logic**:
  1. Select multiple segments (checkbox mode)
  2. Combine audio samples in order
  3. Create new ShadowItem with combined audio
  4. Optionally delete originals
- **Constraint**: Only merge segments from same source file

### Audio Beeps Enhancement
- **Current implementation**: `feedback/AudioFeedbackSystem.kt`
- **Existing beeps**: segment detected, playback start, recording start, good/bad scores, pause/resume
- **Enhancement**: Add volume control, different tone sets, custom sounds

### Export Playlist as Audio
- **Location**: `library/AudioExporter.kt`
- **Output**: WAV file saved to Music/ShadowMaster folder
- **Format**: 16kHz mono PCM wrapped in WAV header
- **Features**:
  1. Beeps between segments (playback start, your turn, segment end)
  2. Playback repeats according to settings
  3. Optional silence gaps for user to practice
  4. Compatible with any audio player for passive practice
- **UI**: Export button on playlist card, export dialog with options
- **Progress**: ExportProgress state flow shows status and progress
