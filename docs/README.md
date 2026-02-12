# Shadow Master Web App

Welcome to the **Shadow Master Web App** - a browser-based language learning tool for mastering pronunciation through audio shadowing.

**[🌐 Launch Web App](https://barakem.github.io/shadow_master/)**

## Features

### Multiple VAD Algorithms
Choose from three voice activity detection algorithms to automatically segment your audio:

1. **Energy-based VAD** (Recommended)
   - Fast and reliable
   - Works in all browsers
   - Best for most use cases
   - Uses RMS energy and zero-crossing rate

2. **WebRTC VAD**
   - Browser-native implementation
   - Moderate accuracy
   - Good for standard speech

3. **Silero VAD**
   - Highest accuracy
   - Uses deep learning model
   - Slower but most precise
   - Requires ONNX Runtime Web

### Configurable Segmentation

Customize how your audio is split into practice segments:

- **Segment Length Modes**:
  - Word Mode: 0.5-2 seconds (for word-level practice)
  - Sentence Mode: 1-8 seconds (for full sentences)
  - Custom: Define your own min/max range

- **Detection Settings**:
  - **Silence Threshold**: Adjust sensitivity (0.005-0.05)
    - Lower = detects quieter speech
    - Higher = only loud speech
  - **Minimum Silence Duration**: How long silence must last to separate segments (0.1-1.0s)
  - **Speech Padding**: Extra time added before/after each segment (0-0.5s)

### Practice Features

- **Adjustable Playback Speed**: 0.5x to 2.0x
- **Playback Repeats**: Hear each segment 1-5 times
- **User Recording**: Practice with automatic recording
- **Bus Mode**: Listen-only mode (no recording)
- **Progress Tracking**: See how many segments you've practiced
- **Audio Feedback**: Beeps for state awareness
- **Buildup Mode**: Backward buildup technique (coming soon)

## Quick Start

### 1. Import Audio
- Click "Shadow Library"
- Go to "Imported Audio" tab
- Click "Import Audio File"
- Select an audio file from your device

### 2. Create Segmented Playlist
- Click "Create Playlist" on your imported audio
- Configure VAD settings:
  - Choose VAD algorithm (Energy-based recommended)
  - Select segment length (Sentence Mode is good for beginners)
  - Adjust silence threshold if needed (default 0.01 works well)
- Click "Create Playlist"
- Wait for segmentation to complete
- Name your playlist

### 3. Practice
- Go to "Playlists" tab
- Click on your playlist
- Press "Start" to begin
- Listen to each segment
- Record your pronunciation
- Progress automatically

## Record from Microphone

You can also record audio directly from your microphone:

1. Click "Record from Mic" on the home screen
2. Click "Start Recording"
3. Speak into your microphone
4. Click "Stop Recording"
5. Choose to segment automatically or save as-is
6. If segmenting, configure VAD settings
7. Start practicing!

## Settings

Access settings from any screen using the ⚙️ button.

### Playback Settings
- **Playback Speed**: Slow down or speed up audio (0.5x - 2.0x)
- **Playback Repeats**: How many times to play each segment (1-5)
- **User Recording Repeats**: How many times to practice each segment (1-3)

### Practice Modes
- **Bus Mode**: Listen-only, no recording (great for commuting)
- **Practice Mode**: Standard or Buildup (standard recommended)

### Audio Feedback
- **Enable Audio Feedback**: Turn beeps on/off
- **Beep Volume**: Adjust beep loudness (0-100%)

### Language
- **Target Language**: Select the language you're learning
  - Affects future features like transcription and pronunciation assessment

## Tips for Best Results

### Choosing VAD Algorithm
- Start with **Energy-based VAD** - it's fast and works well for most audio
- Try **Silero VAD** if you have very quiet audio or need highest accuracy
- **WebRTC VAD** is a middle ground option

### Adjusting Silence Threshold
- If segments are **too short** or **cut off words**: Lower the threshold (e.g., 0.005)
- If you get **too much silence** or **background noise**: Raise the threshold (e.g., 0.02)
- Default 0.01 works for most clear recordings

### Segment Length
- **New learners**: Start with Sentence Mode (1-8s)
- **Advanced practice**: Try Word Mode (0.5-2s) for quick repetition
- **Custom**: Adjust based on your content (e.g., 2-5s for phrases)

### Practice Strategy
1. Start with **slower playback speed** (0.7x - 0.8x)
2. Use **2-3 playback repeats** to hear clearly
3. Enable **audio feedback** for hands-free awareness
4. Gradually increase speed as you improve
5. Create multiple playlists with different segment lengths from the same audio

## Browser Compatibility

Shadow Master Web App works on:

- ✅ Chrome/Chromium (Desktop & Mobile)
- ✅ Firefox (Desktop & Mobile)
- ✅ Safari (Desktop & Mobile)
- ✅ Edge (Desktop & Mobile)

**Requirements:**
- Modern browser with Web Audio API support
- Microphone access (only for recording features)
- JavaScript enabled

## Storage

All your data is stored locally in your browser:

- **Playlists**: Saved in localStorage
- **Settings**: Persisted across sessions
- **Imported Audio**: Stored as data URLs

**Note**: Clearing browser data will delete your playlists. Export important playlists if needed.

## Limitations

Compared to the Android app, the web version:

- ❌ Cannot capture audio from other apps (no "Live Shadow" mode)
- ❌ No Android Auto integration
- ❌ No pronunciation assessment (Azure Speech Services)
- ❌ Limited offline support (requires browser caching)
- ✅ But: Works on any device with a browser!
- ✅ But: No installation required!
- ✅ But: Cross-platform (Windows, Mac, Linux, iOS, Android)

## Privacy

Shadow Master Web App:

- 🔒 Runs entirely in your browser
- 🔒 No data sent to servers
- 🔒 No tracking or analytics
- 🔒 No account required
- 🔒 Your audio stays on your device

## Troubleshooting

### Segmentation Not Working
- Try Energy-based VAD instead of Silero
- Adjust silence threshold (try 0.015 or 0.02)
- Check that audio file is not corrupted
- Ensure audio has actual speech content

### Segments Too Short
- Lower silence threshold (e.g., 0.005)
- Increase minimum silence duration (e.g., 0.5s)
- Use Sentence Mode instead of Word Mode

### Segments Too Long
- Raise silence threshold (e.g., 0.02)
- Decrease minimum silence duration (e.g., 0.2s)
- Use Word Mode or Custom with lower max duration

### Microphone Not Working
- Check browser permissions (click lock icon in address bar)
- Ensure microphone is not in use by another app
- Try refreshing the page
- Check browser compatibility

### Audio Playback Issues
- Ensure audio format is supported (MP3, WAV, OGG, M4A, etc.)
- Check file is not corrupted
- Try converting to MP3 format
- Reduce playback speed if audio is choppy

## Advanced: VAD Algorithm Details

### Energy-based VAD
- **How it works**: Calculates RMS energy and zero-crossing rate for each audio frame
- **Pros**: Fast, reliable, works offline, no external dependencies
- **Cons**: Less accurate with noisy audio or very quiet speech
- **Best for**: Clean recordings, podcasts, audiobooks

### WebRTC VAD
- **How it works**: Uses browser's built-in speech detection with resampling to 16kHz
- **Pros**: Native browser support, moderate accuracy
- **Cons**: Can miss quiet speech, less configurable
- **Best for**: Standard speech recordings, balanced approach

### Silero VAD
- **How it works**: Deep learning model (ONNX) running in browser
- **Pros**: Highest accuracy, handles noise and quiet speech well
- **Cons**: Slower processing, larger download, fallback to energy-based if model fails
- **Best for**: Noisy audio, quiet speech, maximum accuracy needed

## Development

The web app is built with vanilla JavaScript for simplicity and performance:

```
docs/
├── index.html       # Main HTML structure
├── app.js          # Application logic
├── vad.js          # VAD processing module
├── styles.css      # Styling
└── README.md       # This file
```

### Local Development

1. Clone the repository
2. Navigate to `docs/` directory
3. Serve with any static file server:

```bash
# Python
python -m http.server 8000

# Node.js
npx http-server

# PHP
php -S localhost:8000
```

4. Open `http://localhost:8000` in your browser

### Contributing

Contributions welcome! Areas for improvement:

- [ ] Waveform visualization
- [ ] Better mobile UI/UX
- [ ] Export playlists as audio files
- [ ] Import from YouTube/URL
- [ ] Offline PWA support
- [ ] More VAD algorithm options
- [ ] Transcription integration
- [ ] Translation display

## Links

- **Web App**: [https://barakem.github.io/shadow_master/](https://barakem.github.io/shadow_master/)
- **GitHub**: [https://github.com/BarakEm/shadow_master](https://github.com/BarakEm/shadow_master)
- **Android App**: [Download APK](https://github.com/BarakEm/shadow_master/releases)
- **Issues**: [Report a bug](https://github.com/BarakEm/shadow_master/issues)

## License

MIT License - See [LICENSE](../LICENSE) file for details.
