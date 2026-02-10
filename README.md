
<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="Shadow Master Icon - colorful parrot with headphones" width="150" height="150"/>
</p>

<h1 align="center">Shadow Master</h1>

<p align="center">
  <em>Listen. Repeat. Master.</em>
</p>

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
- **Multiple segmentation modes** - create different playlists from same audio (word-level or sentence-level)
- **Re-segmentation** - adjust segmentation without re-importing
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

# Build release APK (requires keystore configuration)
# See RELEASE_BUILD_GUIDE.md for detailed instructions
./gradlew assembleRelease
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
4. View your imported audio in the "Imported Audio" tab
5. Tap "Create Playlist" on an imported audio item
6. Choose a segmentation mode:
   - **Word Mode**: Shorter segments (500ms-2000ms) for word-level practice
   - **Sentence Mode**: Longer segments (1000ms-8000ms) for full sentences
7. Name your playlist and tap "Create"
8. Switch to "Playlists" tab and tap your new playlist
9. Tap Play to start practice
10. The app will:
    - Play each segment
    - Wait for you to repeat (or skip in Bus Mode)
    - Track your practice progress

**Tip**: Create multiple playlists from the same audio with different segmentation modes for varied practice!

### Live Shadow Mode
1. Open Shadow Master
2. Tap "Live Shadow" on the home screen
3. Start your podcast/video in another app
4. Grant screen capture permission when prompted
5. The app will capture and segment audio in real-time

### Settings

- **Language**: Select target language for pronunciation assessment
- **Playback Speed**: 0.5x - 2.0x (slower for difficult content)
- **Playback Repeats**: 1-5 times before your turn
- **User Repeats**: How many times you practice each segment
- **Bus Mode**: Listen-only, no speaking practice
- **Practice Mode**: Standard or Buildup (backward buildup technique)
- **Audio Feedback**: Beeps for state awareness (customizable volume and tone)

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

## Roadmap

### ✅ Version 1.0 (Current - Stable)
- [x] Shadow Library with audio import and segmentation
- [x] Live Shadow with real-time capture
- [x] VAD-based speech detection (Silero)
- [x] Playback speed control and repeat options
- [x] User recording with silence detection
- [x] Audio feedback beeps for hands-free use
- [x] Bus mode (listen-only)
- [x] Settings persistence

### ✅ Version 1.1 (Complete)
- [x] Rename playlists and segments
- [x] Transcription display and manual entry
- [x] Translation display and manual entry
- [x] Pronunciation feedback display (scores visualization)
- [x] Split long segments into smaller parts
- [x] Merge short segments together
- [x] Export playlist as practice audio (WAV with beeps and silence gaps)
- [x] Enhanced audio beep customization (volume, tone type, duration)

### 📋 Version 1.2 (Planned)
- [ ] Automatic transcription (Speech-to-Text API)
- [ ] Automatic translation (Translation API)
- [ ] Real pronunciation assessment (Azure Speech Services)
- [ ] Import from URL (YouTube, podcast links)

### 🔮 Future Ideas
- [ ] Full Android Auto integration
- [ ] Spaced repetition for difficult segments
- [ ] Social features (share playlists)
- [ ] Waveform visualization
- [ ] A/B comparison (original vs your recording)

## Development

### Automated Task Delegation to GitHub Copilot

This project uses **GitHub Copilot automation** to handle development tasks. When you create an issue with the `copilot` label, GitHub Copilot automatically:
1. Receives the task notification
2. Analyzes the requirements
3. Creates an implementation
4. Submits a pull request for review

**Everything is already set up and automated!**

#### Quick Start

Create a new task for Copilot:
```bash
gh issue create \
  --label copilot \
  --title "Your task title" \
  --body "Detailed description"
```

Copilot will be automatically notified and will start working on it.

#### Monitor Progress

```bash
# View active issues
gh issue list --label copilot-working

# View pull requests
gh pr list
```

#### Documentation

- **[COPILOT_AUTOMATION.md](COPILOT_AUTOMATION.md)** - Complete automation guide (you don't need to remember commands!)
- **[scripts/README.md](scripts/README.md)** - Script documentation
- **[COPILOT_TASKS.md](COPILOT_TASKS.md)** - Task list template
- **[.github/copilot-instructions.md](.github/copilot-instructions.md)** - Guidelines for Copilot

**Current Status:** All v1.1 tasks completed. Codebase stabilized (resource leaks, coroutine leaks, naming inconsistencies fixed).

## License

MIT
