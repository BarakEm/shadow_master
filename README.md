<p align="center">
  <img src="docs/ninja-logo.svg" width="180" alt="Shadow Master Logo">
</p>

<h1 align="center">Shadow Master</h1>

<p align="center">
  <strong>Language learning through audio shadowing</strong><br>
  <a href="https://barakem.github.io/shadow_master/">Project Website</a>
</p>

---

Android app for language learning through audio shadowing. Import audio files or capture live audio, then practice repeating speech segments hands-free.

## Two Practice Modes

### Shadow Library (Recommended)
Import audio files from your device for offline, hands-free practice:
- **No permissions required** - works with any audio file on your phone
- **Better segmentation** - pre-processes audio for accurate speech boundaries
- **Playlist management** - organize content into playlists
- **Offline practice** - no internet needed after import
- **Track progress** - see practice counts and favorites

### Live Shadow
Capture audio from any app in real-time:
- Works with podcasts, YouTube, audiobooks, etc.
- Requires screen recording permission each session
- Best for spontaneous practice

## Features

- **Voice Activity Detection**: Silero VAD detects speech segments automatically
- **Playback Control**: Adjustable speed (0.5x-1.5x) and repeat count
- **Bus Mode**: Listen-only mode - hear segments without speaking practice
- **Audio Feedback**: Gentle beeps indicate state changes for hands-free use
- **User Recording**: Records your pronunciation with automatic silence detection
- **Pronunciation Assessment**: Optional Azure Speech Services integration
- **Android Auto Ready**: Designed for hands-free use while driving

## Requirements

- Android 10 (API 29) or higher
- Microphone permission (for recording your voice)
- Storage access (for importing audio files)
- Screen capture permission (only for Live Shadow mode)

## Installation

### Option 1: Download APK
Download the latest APK from [GitHub Releases](https://github.com/BarakEm/shadow_master/releases)

### Option 2: Build from source

```bash
# Clone the repo
git clone https://github.com/BarakEm/shadow_master.git
cd shadow_master

# Build debug APK
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Option 3: Install via ADB

```bash
# Build and install directly to connected device
./gradlew installDebug
```

## Usage

### Shadow Library Mode
1. Open Shadow Master
2. Tap "Shadow Library" on the home screen
3. Tap + to import an audio file
4. Wait for processing (speech segmentation)
5. Tap a playlist, then tap Play to start practice
6. The app will:
   - Play each segment
   - Wait for you to repeat (or skip in Bus Mode)
   - Track your practice progress

### Live Shadow Mode
1. Open Shadow Master
2. Tap "Live Shadow" on the home screen
3. Start your podcast/video in another app
4. Grant screen capture permission when prompted
5. The app will capture and segment audio in real-time

### Settings

- **Playback Speed**: 0.5x - 1.5x (slower for difficult content)
- **Playback Repeats**: 1-3 times before your turn
- **Bus Mode**: Listen-only, no speaking practice
- **Audio Feedback**: Beeps for state awareness
- **Silence Threshold**: How long to wait for speech end (500-1500ms)

## Configuration

### Azure Speech (Optional)

For pronunciation assessment, create `local.properties` in the project root:

```properties
AZURE_SPEECH_KEY=your_azure_speech_key
AZURE_SPEECH_REGION=your_azure_region
```

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
│   ├── local/         # Room database
│   ├── model/         # Data classes
│   └── repository/    # DataStore & database access
├── di/                # Hilt dependency injection modules
├── feedback/          # Audio feedback (beeps, tones)
├── library/           # Audio import & library management
├── media/             # MediaSession, navigation detection
├── service/           # Foreground service
└── ui/
    ├── home/          # Mode selection screen
    ├── library/       # Library browser
    ├── practice/      # Offline practice player
    ├── driving/       # Live shadow screen
    ├── settings/      # Settings screen
    └── navigation/    # Navigation graph
```

## Tech Stack

- **Kotlin** with Coroutines and Flow
- **Jetpack Compose** for UI
- **Room** for local database (Shadow Library)
- **Hilt** for dependency injection
- **DataStore** for settings persistence
- **MediaCodec** for audio decoding
- **Silero VAD** for voice activity detection
- **Azure Speech SDK** for pronunciation assessment (optional)

## License

MIT
