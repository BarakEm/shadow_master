# Shadow Master

**[Project Website](https://barakem.github.io/shadow_master/)**

Android app for language learning through audio shadowing. Captures audio from any app (podcasts, YouTube, etc.), detects speech segments, and prompts you to repeat them.

## Features

- **Audio Capture**: Captures audio output from any app using Android's MediaProjection API
- **Voice Activity Detection**: Uses Silero VAD to detect speech segments automatically
- **Playback Control**: Adjustable playback speed and repeat count
- **User Recording**: Records your pronunciation attempt with automatic silence detection
- **Pronunciation Assessment**: Optional Azure Speech Services integration for scoring (Phase 2)
- **Android Auto Ready**: Designed for hands-free use while driving

## Requirements

- Android 10 (API 29) or higher (required for AudioPlaybackCapture)
- Microphone permission
- Screen capture permission (for audio capture)

## Installation

### Option 1: Build from source

```bash
# Clone the repo
git clone https://github.com/BarakEm/shadow_master.git
cd shadow_master

# Build debug APK
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Option 2: Install via ADB

```bash
# Build and install directly to connected device
./gradlew installDebug

# Or install a pre-built APK
adb install app-debug.apk
```

### Option 3: Manual APK install

1. Build the APK (see above)
2. Transfer `app-debug.apk` to your phone
3. Open the APK file on your phone
4. Allow installation from unknown sources when prompted
5. Install the app

## Configuration

### Azure Speech (Optional)

For pronunciation assessment, create `local.properties` in the project root:

```properties
AZURE_SPEECH_KEY=your_azure_speech_key
AZURE_SPEECH_REGION=your_azure_region
```

## Usage

1. Open Shadow Master
2. Start your podcast/audiobook/video in another app
3. Tap "Start Session" in Shadow Master
4. Grant screen capture permission when prompted
5. The app will:
   - Listen for speech segments
   - Pause other audio when speech is detected
   - Play back the segment for you
   - Record your repetition
   - Provide feedback (with Azure) or continue (without)

### Settings

- **Playback Speed**: 0.5x - 1.5x (slower for difficult content)
- **Playback Repeats**: 1-3 times before your turn
- **Silence Threshold**: How long to wait for speech end (500-1500ms)
- **Pause for Navigation**: Auto-pause during GPS navigation prompts

## Architecture

```
com.shadowmaster/
├── audio/
│   ├── capture/       # MediaProjection audio capture
│   ├── playback/      # AudioTrack playback engine
│   ├── processing/    # VAD pipeline, circular buffer
│   ├── recording/     # User microphone recording
│   └── vad/           # Silero VAD wrapper
├── core/
│   ├── ShadowingCoordinator.kt   # Main orchestrator
│   └── ShadowingStateMachine.kt  # State management
├── data/
│   ├── model/         # Data classes (AudioSegment, State, Config)
│   └── repository/    # DataStore settings persistence
├── feedback/          # Audio feedback (beeps, tones)
├── media/             # MediaSession, navigation detection
├── service/           # Foreground service
└── ui/                # Jetpack Compose UI
    ├── driving/       # Main driving-mode screen
    ├── settings/      # Settings screen
    └── navigation/    # Navigation graph
```

## State Machine

```
Idle → Listening → SegmentDetected → Playback → UserRecording → Assessment → Feedback
  ↑                                                                              │
  └──────────────────────────────────────────────────────────────────────────────┘
```

Navigation can pause the flow at any state, resuming when navigation ends.

## Tech Stack

- **Kotlin** with Coroutines and Flow
- **Jetpack Compose** for UI
- **Hilt** for dependency injection
- **DataStore** for settings persistence
- **Silero VAD** for voice activity detection
- **Azure Speech SDK** for pronunciation assessment (optional)

## License

MIT
