
<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" alt="Shadow Master Icon - colorful parrot with headphones" width="150" height="150"/>
</p>

<h1 align="center">Shadow Master</h1>

<p align="center">
  <em>Listen. Repeat. Master.</em>
</p>

<p align="center">
  <strong>Language learning through audio shadowing</strong><br>
  <a href="https://barakem.github.io/shadow_master/">🌐 Try Web App</a> |
  <a href="#android-app---installation">📱 Android App</a>
</p>

---

**Shadow Master** helps you master pronunciation through audio shadowing - available as both a **web app** and **Android app**. Import audio files or capture live audio, then practice repeating speech segments hands-free with intelligent voice activity detection.

## Platforms

### 🌐 Web App
Try Shadow Master instantly in your browser - no installation required!

**[Launch Web App →](https://barakem.github.io/shadow_master/)**

- Works on any modern browser (Chrome, Firefox, Safari, Edge)
- Import audio files or record from microphone
- Multiple VAD algorithms for automatic segmentation
- Configurable segment length and detection sensitivity
- Save playlists in browser storage
- Perfect for desktop and mobile browsers

### 📱 Android App
Full-featured native Android application with additional capabilities:

- Android 10+ (API 29 or higher)
- Live audio capture from other apps (podcasts, YouTube, etc.)
- Silero VAD for highest accuracy segmentation
- Android Auto integration for hands-free practice
- Local database for offline playlist management
- Optional Azure Speech Services for pronunciation assessment

See [installation instructions](#android-app---installation) below to build from source.

## Practice Modes

### Shadow Library (Recommended)
Import audio files for offline, hands-free practice:
- **Intelligent segmentation** - multiple VAD algorithms detect speech boundaries automatically
- **Playlist management** - organize content into playlists
- **Multiple segmentation modes** - word-level (0.5-2s) or sentence-level (1-8s)
- **Configurable detection** - adjust silence threshold and segment length
- **Offline practice** - no internet needed after import
- **Track progress** - see practice counts and favorites (Android)

### Live Shadow (Android Only)
Capture audio from any app in real-time:
- Works with podcasts, YouTube, audiobooks, etc.
- Requires screen recording permission each session
- Best for spontaneous practice

## Key Features

### Intelligent Segmentation
- **Web**: Energy-based VAD, WebRTC VAD, or Silero VAD (via ONNX)
- **Android**: Silero VAD for highest accuracy
- Configurable segment length (word-level or sentence-level)
- Adjustable silence threshold and detection sensitivity
- Automatic speech padding for natural playback

### Playback & Practice
- **Adjustable Speed**: 0.5x - 2.0x playback speed
- **Repeat Control**: Configure playback and user recording repeats
- **Bus Mode**: Listen-only mode without speaking practice
- **Audio Feedback**: Gentle beeps indicate state changes for hands-free use
- **User Recording**: Records your pronunciation with automatic silence detection

### Advanced Features (Android)
- **Pronunciation Assessment**: Optional Azure Speech Services integration
- **Android Auto**: Full support for hands-free driving practice
- **Live Capture**: Record audio from any playing app in real-time

## Getting Started

### Web App - Instant Access
1. Visit **[https://barakem.github.io/shadow_master/](https://barakem.github.io/shadow_master/)**
2. Import an audio file or record from your microphone
3. Configure VAD segmentation settings (or use defaults)
4. Start practicing!

No installation, no permissions required. Works on any modern browser.

### Android App - Installation

**Requirements:**
- Android 10 (API 29) or higher
- Microphone permission (for recording your voice)
- Storage access (for importing audio files)
- Screen capture permission (only for Live Shadow mode)

#### Option 1: Build from Source

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

#### Option 2: Install via ADB

```bash
# Build and install directly to connected device
./gradlew installDebug
```

## Usage Guide

### Web App Usage

#### Import and Segment Audio
1. Click "Shadow Library"
2. Go to "Imported Audio" tab
3. Click "Import Audio File" and select your audio
4. Click "Create Playlist" on the imported audio
5. Configure segmentation:
   - **VAD Algorithm**: Energy-based (fast), WebRTC (moderate), or Silero (best)
   - **Segment Length**: Word Mode (0.5-2s), Sentence Mode (1-8s), or Custom
   - **Silence Threshold**: Adjust sensitivity (lower = more sensitive)
   - **Minimum Silence**: How long silence must last to split segments
   - **Speech Padding**: Extra time before/after each segment
6. Click "Create Playlist" to process
7. Name your playlist and start practicing!

#### Practice Session
1. Select a playlist from the "Playlists" tab
2. Press "Start" to begin
3. Each segment plays according to your settings
4. Record your pronunciation (or enable Bus Mode to skip)
5. Track progress with the progress bar
6. Adjust playback speed in real-time

### Android App Usage

#### Shadow Library Mode
1. Open Shadow Master
2. Tap "Shadow Library" on the home screen
3. Tap + to import an audio file
4. View your imported audio in the "Imported Audio" tab
5. Tap "Create Playlist" on an imported audio item
6. Choose a segmentation mode:
   - **Word Mode**: Shorter segments (0.5-2s) for word-level practice
   - **Sentence Mode**: Longer segments (1-8s) for full sentences
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
    ├── mic/           # Microphone capture screen
    ├── settings/      # Settings screen
    └── navigation/    # Navigation graph
```

## Tech Stack

### Web App
- **Vanilla JavaScript** - No frameworks, lightweight and fast
- **Web Audio API** - Audio processing and playback
- **MediaRecorder API** - Microphone recording
- **localStorage** - Settings and playlist persistence
- **Custom VAD Engine** - Energy-based, WebRTC, and Silero support
- **ONNX Runtime Web** - For Silero VAD model (optional)

### Android App
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
