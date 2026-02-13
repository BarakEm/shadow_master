# Shadow Master Web App - Features Documentation

## Network Discovery

### Overview
The Shadow Master web app can automatically discover Python backends running on your local network using multiple approaches. This feature is **opt-in** to respect user privacy and avoid intrusive network access requests.

### Backend Setup (Ubuntu/Linux)

#### 1. Install Dependencies
```bash
cd shadow_cli
pip3 install -r requirements.txt
```

#### 2. Start Backend with Discovery Enabled
```bash
# Enable network discovery (opt-in)
python3 server.py --discovery

# Or with custom port
python3 server.py --port 8765 --discovery
```

When discovery is enabled, the backend:
- Registers an mDNS/Zeroconf service on the local network
- Broadcasts its availability as `_shadowmaster._tcp.local.`
- Shows its IP address and hostname in the console
- Does NOT require manual configuration on client devices

#### 3. Without Discovery (Default)
```bash
# Start without network discovery (localhost only)
python3 server.py
```

This mode only accepts connections from `localhost` and does not broadcast on the network.

### Client-Side Discovery

#### Automatic Discovery (Opt-in)
1. Open Shadow Master web app in browser
2. Go to **Settings** → **Backend**
3. Enable **"Enable Network Discovery"** checkbox
4. The app will automatically scan for backends on next load

#### Manual Discovery
1. Go to **Settings** → **Backend**
2. Click **"🔍 Scan for Backends"** button
3. Wait for scan to complete (~10-30 seconds)
4. Select discovered backend from the list

### Discovery Methods

The web app uses multiple fallback strategies:

1. **Localhost Check** (fastest)
   - Tries `http://localhost:8765` first
   - Works when backend is on same machine

2. **Common Local IPs**
   - Scans common router IPs: `192.168.1.1`, `192.168.0.1`, etc.
   - Checks first 10 IPs in common ranges
   - Limited scope to avoid being intrusive

3. **mDNS/Zeroconf** (future)
   - Browser-based mDNS discovery
   - Currently not widely supported in browsers
   - Placeholder for future enhancement

### Network Requirements

#### For Backend
- Python 3.10+
- `zeroconf` package installed (`pip install zeroconf`)
- Network access allowed on port 8765 (or custom port)
- Ubuntu: May need to allow Python through firewall

#### For Client
- Modern browser (Chrome, Firefox, Edge, Safari)
- JavaScript enabled
- On same local network as backend (192.168.x.x or 10.x.x.x)

### Security & Privacy

#### Opt-in Design
- Discovery is **disabled by default**
- User must explicitly enable it in settings
- No automatic network scanning without permission
- Respects user privacy

#### Non-intrusive for Non-users
- Users who don't enable discovery won't see any network requests
- App works fully offline with local file import
- Backend is optional - only needed for YouTube import

#### Firewall Considerations
Ubuntu users may need to:
```bash
# Allow Python through firewall (if enabled)
sudo ufw allow 8765/tcp

# Or add specific rule for Shadow Master
sudo ufw allow from 192.168.1.0/24 to any port 8765
```

---

## Speech-to-Text (STT)

### Overview
Shadow Master supports speech recognition for transcribing segments during practice. This uses the browser's built-in Web Speech API (no backend required).

### Browser Support
- ✅ Chrome/Edge (excellent support)
- ✅ Safari (good support)
- ⚠️ Firefox (limited support, may require flags)

### How to Use

STT is **enabled by default** in supported browsers. You can disable it in Settings if needed.

#### During Practice
1. Start practicing a playlist
2. Click **"🎤 Listen & Transcribe"** button
3. Speak the segment content
4. Transcription appears automatically
5. Click again to stop listening

### Features
- Real-time speech recognition
- Multi-language support (matches target language)
- Confidence scores
- Automatically saves transcriptions to segments

### Language Codes
Supported languages:
- English: `en-US`
- Spanish: `es-ES`
- French: `fr-FR`
- German: `de-DE`
- Japanese: `ja-JP`
- Chinese: `zh-CN`
- Hebrew: `he-IL`

---

## Text-to-Speech (TTS)

### Overview
Shadow Master can speak transcriptions and translations aloud using the browser's built-in Speech Synthesis API.

### Browser Support
- ✅ All modern browsers (Chrome, Firefox, Edge, Safari)
- ✅ Works offline
- ✅ Multiple voices available

### How to Use

TTS is **enabled by default** in all browsers. You can disable it in Settings if needed, or select a preferred voice.

#### During Practice
1. Add transcriptions to segments (manual or via STT)
2. Click **"🔊 Speak Transcription"** to hear the segment text
3. Click **"🌍 Speak Translation"** to hear the translation (if available)

### Voice Selection
- Each browser provides different voices
- Some voices are better quality than others
- Select target language voice for best pronunciation
- Voices are loaded from your system

### Features
- Adjustable speech rate (matches playback speed)
- Pause/resume support
- Multiple language voices
- No internet connection required

### Use Cases
- Verify transcription accuracy
- Practice pronunciation
- Learn translations
- Accessibility support

---

## Backend API Endpoints

### Health Check
```
GET /api/health
Response: {"status": "ok"}
```

### Discovery Info
```
GET /api/discovery/info
Response: {
  "ip": "192.168.1.100",
  "port": 8765,
  "discovery_enabled": true,
  "zeroconf_available": true,
  "hostname": "ubuntu-desktop",
  "url": "http://192.168.1.100:8765"
}
```

### Speech-to-Text (Placeholder)
```
POST /api/stt
Body: {
  "audio_id": "abc123",
  "provider": "browser",
  "language": "en-US"
}
Response: {
  "provider": "browser",
  "note": "Use Web Speech API in the browser for STT",
  "text": "",
  "confidence": 0.0
}
```

### Text-to-Speech (Placeholder)
```
POST /api/tts
Body: {
  "text": "Hello world",
  "language": "en-US",
  "provider": "browser"
}
Response: {
  "provider": "browser",
  "note": "Use Web Speech API in the browser for TTS",
  "audio_url": ""
}
```

*Note: Backend STT/TTS endpoints are placeholders for future integration with cloud providers (Google Cloud Speech, Whisper, Azure TTS, etc.)*

---

## Architecture

### Client-Only Mode (No Backend)
```
┌─────────────────┐
│  Web Browser    │
│  ┌───────────┐  │
│  │  HTML/JS  │  │
│  │  + VAD    │  │
│  │  + STT    │  │
│  │  + TTS    │  │
│  └───────────┘  │
└─────────────────┘
```
Features available:
- File import
- Browser-based VAD segmentation
- Speech recognition (STT)
- Text-to-speech (TTS)
- Practice mode

### With Backend (Full Features)
```
┌─────────────────┐          ┌──────────────────┐
│  Web Browser    │◄────────►│  Python Backend  │
│  ┌───────────┐  │  HTTP    │  ┌────────────┐  │
│  │  HTML/JS  │  │  REST    │  │  FastAPI   │  │
│  │  + STT    │  │  API     │  │  + yt-dlp  │  │
│  │  + TTS    │  │          │  │  + ffmpeg  │  │
│  └───────────┘  │          │  │  + VAD     │  │
│        ↕         │          │  └────────────┘  │
│  ┌───────────┐  │          │        ↕          │
│  │ Discovery │  │  mDNS    │  ┌────────────┐  │
│  │  Client   │◄─┼──────────┼─→│  Zeroconf  │  │
│  └───────────┘  │          │  └────────────┘  │
└─────────────────┘          └──────────────────┘
```
Additional features:
- YouTube import
- Subtitle extraction
- Backend-based segmentation
- MP3/WAV export
- Network discovery

---

## Troubleshooting

### Backend Not Found
1. Check backend is running: `python3 server.py --discovery`
2. Verify both devices on same network
3. Check firewall allows port 8765
4. Try manual IP entry in settings
5. Use "Test Connection" button

### STT Not Working
1. Check browser support indicator
2. Grant microphone permissions
3. Try Chrome/Edge for best support
4. Check correct language selected

### TTS Not Working
1. Check browser support (should work everywhere)
2. Verify segment has transcription text
3. Try different voice in settings
4. Check browser volume not muted

### Network Discovery Slow
1. First scan always takes ~10-30 seconds
2. Results are cached
3. Use manual IP entry for faster connection
4. Localhost always checked first (instant)

---

## Future Enhancements

### Planned Features
- [ ] Cloud STT providers (Google, Whisper, Azure)
- [ ] Cloud TTS providers (Google, Azure, ElevenLabs)
- [ ] Real-time translation
- [ ] Better mDNS browser support
- [ ] Background discovery
- [ ] Multiple backend support
- [ ] Backend load balancing

### Backend Providers (Stubs)
The backend has placeholder endpoints for:
- Google Cloud Speech-to-Text
- OpenAI Whisper
- Google Cloud Text-to-Speech
- Azure Cognitive Services TTS

These will be implemented in future releases.

---

## Development Notes

### File Structure
```
docs/
├── index.html           # Main webapp UI
├── app.js              # Core application logic
├── styles.css          # Styling
├── vad.js             # Voice activity detection
├── discovery.js       # Network discovery client
├── speech.js          # STT/TTS integration
└── WEBAPP_FEATURES.md # This documentation
```

### Backend Structure
```
shadow_cli/
├── server.py          # FastAPI backend + discovery
├── segmenter.py       # Audio segmentation
├── downloader.py      # YouTube download
├── requirements.txt   # Python dependencies
└── README.md         # Backend documentation
```

### Testing Discovery
```bash
# Terminal 1: Start backend with discovery
cd shadow_cli
python3 server.py --discovery

# Terminal 2: Check mDNS registration (Linux)
avahi-browse _shadowmaster._tcp --resolve

# Browser: Open webapp and test auto-discovery
```
