# Shadow Master: Web App Conversion Feasibility Analysis

**Date:** January 2026  
**Current Platform:** Android (minSdk 29 / Android 10+)  
**Target Platforms:** Web (Mobile, Desktop, TV)

---

## Executive Summary

Converting Shadow Master from a native Android app to a web application is **technically feasible but challenging**, with a recommended difficulty level of **7/10**. The conversion would require significant architectural changes and feature trade-offs, particularly around the app's core functionality: live audio capture from other applications.

### Key Findings

✅ **Feasible Components (60% of codebase):**
- UI layer (Jetpack Compose → React/Vue/Web Components)
- Business logic and state management
- Database (Room → IndexedDB/SQLite WASM)
- Audio playback and user recording
- Settings and preferences

❌ **Major Challenges (40% of codebase):**
- **Live audio capture from other apps** (MediaProjection API) - Not possible in web browsers
- **Foreground service architecture** - Different paradigm needed
- **Android Auto integration** - N/A for web
- **Silero VAD library** - Needs WASM port or JavaScript alternative
- **Azure Speech SDK** - Different SDK required

---

## Detailed Analysis

### 1. Architecture Overview

#### Current Android Architecture
```
Android App (Kotlin + Compose)
├── UI Layer (Jetpack Compose)
├── Business Logic (Kotlin Coroutines + Flow)
├── Audio Subsystem
│   ├── MediaProjection Capture (Android-specific)
│   ├── AudioRecord (User mic)
│   ├── AudioTrack (Playback)
│   └── Silero VAD (JNI/Native)
├── Data Layer
│   ├── Room Database
│   └── DataStore Preferences
└── Services
    ├── Foreground Service
    └── MediaBrowserService (Android Auto)
```

#### Proposed Web Architecture
```
Progressive Web App (TypeScript + Framework)
├── UI Layer (React/Vue/Svelte + Tailwind)
├── Business Logic (TypeScript + RxJS/State Management)
├── Audio Subsystem
│   ├── Web Audio API (Playback)
│   ├── MediaRecorder API (User mic)
│   ├── WebRTC VAD or ML-based VAD
│   └── NO live capture from other apps
├── Data Layer
│   ├── IndexedDB or SQLite WASM
│   └── LocalStorage/IndexedDB for settings
└── Service Workers (Background sync, notifications)
```

---

## 2. Component-by-Component Analysis

### ✅ UI Layer - **EASY** (Difficulty: 3/10)

**Current:** Jetpack Compose (Kotlin)  
**Target:** React, Vue, or Svelte with TypeScript

#### Conversion Strategy
- Compose's declarative UI maps well to modern web frameworks
- State management patterns (StateFlow/MutableState) → useState/signals/stores
- Navigation Compose → React Router/Vue Router
- Material3 → Material UI / Tailwind CSS / Shoelace

#### Estimated Effort
- ~2-3 weeks for complete UI rewrite
- Screens map 1:1 to web components
- Design system already follows Material guidelines

**Code Example Mapping:**
```kotlin
// Android (Compose)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val playlists by viewModel.playlists.collectAsState()
    LazyColumn {
        items(playlists) { playlist ->
            PlaylistCard(playlist)
        }
    }
}
```

```typescript
// Web (React)
function LibraryScreen() {
    const { playlists } = useLibraryViewModel()
    return (
        <div className="library-screen">
            {playlists.map(playlist => (
                <PlaylistCard key={playlist.id} playlist={playlist} />
            ))}
        </div>
    )
}
```

---

### ✅ Business Logic - **MODERATE** (Difficulty: 5/10)

**Current:** Kotlin with Coroutines and Flow  
**Target:** TypeScript with async/await and RxJS

#### Conversion Strategy
- Kotlin coroutines → JavaScript Promises/async-await
- StateFlow/SharedFlow → RxJS Subjects or custom observables
- Sealed classes → TypeScript discriminated unions
- Hilt DI → Context API / Dependency injection containers

#### Estimated Effort
- ~3-4 weeks for core logic port
- State machine is pure logic and ports easily
- Event-driven architecture translates well

**Code Example Mapping:**
```kotlin
// Android
sealed class ShadowingState {
    object Idle : ShadowingState()
    data class Playback(val segment: AudioSegment) : ShadowingState()
    object Recording : ShadowingState()
}

class ShadowingStateMachine {
    private val _state = MutableStateFlow<ShadowingState>(Idle)
    val state: StateFlow<ShadowingState> = _state.asStateFlow()
}
```

```typescript
// Web
type ShadowingState = 
    | { type: 'idle' }
    | { type: 'playback', segment: AudioSegment }
    | { type: 'recording' }

class ShadowingStateMachine {
    private state$ = new BehaviorSubject<ShadowingState>({ type: 'idle' })
    
    get state(): Observable<ShadowingState> {
        return this.state$.asObservable()
    }
}
```

---

### ❌ Audio Capture (Live Mode) - **IMPOSSIBLE** (Difficulty: 10/10)

**Current:** MediaProjection API with AudioPlaybackCaptureConfiguration  
**Target:** **Not available in web browsers**

#### The Problem
The app's **Live Shadow** mode captures audio output from other apps (YouTube, podcasts, etc.) using Android's `MediaProjection` API. **This is impossible in web browsers** due to security and privacy restrictions.

#### Web Browser Limitations
- No API to capture audio from other browser tabs
- No API to capture system audio
- No API to capture audio from other applications
- Chrome/Firefox explicitly prevent this for security reasons

#### Alternative Approaches

**Option 1: Server-Side URL Processing** ⭐ **RECOMMENDED**
- User provides URL (YouTube, Spotify, podcast RSS)
- Server downloads audio, segments it, sends to client
- Pros: Works cross-platform, no permissions needed
- Cons: Server infrastructure required, legal concerns with DRM content

**Option 2: Browser Extension (Chrome/Firefox)**
- Extension can capture tab audio using `chrome.tabCapture` API
- Only works for audio within the browser
- Pros: Enables capture of web-based content
- Cons: Requires extension installation, limited to browser tabs

**Option 3: Desktop App (Electron/Tauri)**
- Hybrid approach: Web UI + native audio capture
- Uses desktop APIs for system audio capture
- Pros: Full feature parity with Android
- Cons: Not truly cross-platform, separate installations

**Option 4: Remove Live Mode**
- Focus on Library mode only (import audio files)
- User manually downloads audio from YouTube/podcasts
- Pros: Simpler architecture, no capture needed
- Cons: Less convenient than Android version

#### Recommendation
For web-first approach: **Remove Live Shadow mode** and focus on Shadow Library with **Option 1 (URL processing)** as an enhanced import feature. This provides 90% of functionality without requiring native audio capture.

---

### ⚠️ Voice Activity Detection (VAD) - **CHALLENGING** (Difficulty: 7/10)

**Current:** Silero VAD (JNI-based Android library)  
**Target:** WebAssembly port or JavaScript alternative

#### Conversion Options

**Option 1: ONNX Runtime Web** ⭐ **RECOMMENDED**
- Silero VAD model available as ONNX format
- Run in browser using ONNX Runtime for Web
- Near-native performance with WebAssembly
- ~3MB model size (acceptable for web)

**Option 2: WebRTC VAD**
- Built-in browser VAD (simpler but less accurate)
- No external dependencies
- Lower quality than Silero

**Option 3: TensorFlow.js VAD Models**
- Train custom model or use pre-trained
- More flexibility, bigger bundle size

#### Implementation Estimate
- ONNX Runtime integration: ~1-2 weeks
- Testing and tuning: ~1 week
- Performance optimization: ~1 week

**Code Example:**
```typescript
// Web (ONNX Runtime)
import * as ort from 'onnxruntime-web'

class SileroVadWeb {
    private session: ort.InferenceSession
    
    async initialize() {
        this.session = await ort.InferenceSession.create('silero_vad.onnx')
    }
    
    async detectSpeech(audioFrame: Float32Array): Promise<number> {
        const tensor = new ort.Tensor('float32', audioFrame, [1, audioFrame.length])
        const outputs = await this.session.run({ input: tensor })
        return outputs.output.data[0] as number
    }
}
```

---

### ✅ Audio Playback - **EASY** (Difficulty: 2/10)

**Current:** AudioTrack (Android)  
**Target:** Web Audio API

#### Conversion Strategy
- Direct mapping from AudioTrack to Web Audio API
- Web Audio API is mature and well-supported
- Variable playback speed built-in

#### Implementation
```typescript
class WebAudioPlayback {
    private audioContext: AudioContext
    private sourceNode: AudioBufferSourceNode
    
    async play(audioData: Float32Array, speed: number) {
        this.audioContext = new AudioContext()
        const buffer = this.audioContext.createBuffer(1, audioData.length, 16000)
        buffer.copyToChannel(audioData, 0)
        
        this.sourceNode = this.audioContext.createBufferSource()
        this.sourceNode.buffer = buffer
        this.sourceNode.playbackRate.value = speed
        this.sourceNode.connect(this.audioContext.destination)
        this.sourceNode.start()
    }
}
```

---

### ✅ User Audio Recording - **EASY** (Difficulty: 3/10)

**Current:** AudioRecord (Android mic)  
**Target:** MediaRecorder API / Web Audio API

#### Conversion Strategy
- Browser's `getUserMedia()` for microphone access
- Web Audio API for recording and processing
- Requires user permission (like Android)

#### Implementation
```typescript
class WebAudioRecorder {
    private mediaRecorder: MediaRecorder
    private chunks: Blob[] = []
    
    async startRecording() {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        this.mediaRecorder = new MediaRecorder(stream)
        
        this.mediaRecorder.ondataavailable = (e) => {
            if (e.data.size > 0) this.chunks.push(e.data)
        }
        
        this.mediaRecorder.start()
    }
    
    async stopRecording(): Promise<Blob> {
        return new Promise((resolve) => {
            this.mediaRecorder.onstop = () => {
                const blob = new Blob(this.chunks, { type: 'audio/webm' })
                this.chunks = []
                resolve(blob)
            }
            this.mediaRecorder.stop()
        })
    }
}
```

---

### ✅ Database & Storage - **MODERATE** (Difficulty: 4/10)

**Current:** Room Database (SQLite) + DataStore  
**Target:** IndexedDB or SQLite WASM

#### Conversion Options

**Option 1: IndexedDB with Wrapper** ⭐ **RECOMMENDED**
- Native browser storage, no external dependencies
- Use Dexie.js for easier API
- ~10MB storage limit (can request more)

**Option 2: SQLite WASM**
- sql.js or wa-sqlite
- Exact SQL compatibility
- Larger bundle size (~800KB)

#### Migration Strategy
```typescript
// Using Dexie.js (IndexedDB wrapper)
import Dexie from 'dexie'

class ShadowLibraryDB extends Dexie {
    playlists: Dexie.Table<Playlist, number>
    shadowItems: Dexie.Table<ShadowItem, number>
    
    constructor() {
        super('ShadowLibraryDB')
        this.version(1).stores({
            playlists: '++id, name, createdAt',
            shadowItems: '++id, playlistId, sourceStartMs, sourceEndMs'
        })
    }
}
```

---

### ⚠️ Azure Speech Services - **MODERATE** (Difficulty: 5/10)

**Current:** Azure Speech SDK for Android  
**Target:** Azure Speech SDK for JavaScript

#### Changes Required
- Different SDK package (`microsoft-cognitiveservices-speech-sdk`)
- API calls are similar but not identical
- Requires CORS configuration on Azure side
- May need proxy server to hide API keys

#### Implementation
```typescript
import * as sdk from 'microsoft-cognitiveservices-speech-sdk'

class AzureSpeechWeb {
    private speechConfig: sdk.SpeechConfig
    
    constructor(key: string, region: string) {
        this.speechConfig = sdk.SpeechConfig.fromSubscription(key, region)
    }
    
    async assessPronunciation(audioBlob: Blob): Promise<AssessmentResult> {
        const audioConfig = sdk.AudioConfig.fromWavFileInput(audioBlob)
        const recognizer = new sdk.SpeechRecognizer(this.speechConfig, audioConfig)
        
        return new Promise((resolve, reject) => {
            recognizer.recognizeOnceAsync(result => {
                if (result.reason === sdk.ResultReason.RecognizedSpeech) {
                    resolve(this.parseScores(result))
                } else {
                    reject('Recognition failed')
                }
            })
        })
    }
}
```

---

### ❌ Foreground Services - **N/A for Web** (Difficulty: N/A)

**Current:** Android Foreground Service with notification  
**Target:** Service Workers + Background Sync

#### Web Alternatives
- **Service Workers**: Handle background tasks, push notifications
- **Web Workers**: Run JavaScript in background threads
- **Background Sync**: Sync data when network available
- **No true "always running" equivalent**: Browsers aggressively suspend inactive tabs

#### Implications
- App must be in foreground/visible for active practice
- Can use service workers for offline support
- Push notifications possible but require user permission
- No persistent background processing like Android

---

### ❌ Android Auto - **N/A for Web** (Difficulty: N/A)

**Current:** MediaBrowserService for Android Auto  
**Target:** Not applicable

#### TV/Large Screen Alternatives
- Responsive web design for TV browsers
- Smart TV apps (Tizen, webOS) if needed
- Chromecast support via Cast API
- Apple TV via Safari on tvOS

---

## 3. Technology Stack Recommendations

### Frontend Framework Options

#### Option 1: React + TypeScript ⭐ **MOST POPULAR**
**Pros:**
- Largest ecosystem and community
- Excellent TypeScript support
- Many UI libraries (Material-UI, Chakra, shadcn/ui)
- Strong PWA support

**Cons:**
- Larger bundle size
- More boilerplate than alternatives

**Stack:**
```
- React 18+ with TypeScript
- Material-UI or shadcn/ui for components
- Zustand or Redux Toolkit for state
- React Router for navigation
- TanStack Query for data fetching
- Vite for build tooling
```

#### Option 2: Vue 3 + TypeScript
**Pros:**
- Cleaner syntax than React
- Great documentation
- Good performance
- Progressive adoption

**Cons:**
- Smaller ecosystem than React
- Fewer audio-specific libraries

**Stack:**
```
- Vue 3 Composition API + TypeScript
- Vuetify or PrimeVue for components
- Pinia for state management
- Vue Router for navigation
- Vite for build tooling
```

#### Option 3: Svelte + TypeScript
**Pros:**
- Smallest bundle size
- Best performance
- Simple reactive syntax
- Compile-time optimization

**Cons:**
- Smallest ecosystem
- Fewer developers familiar with it
- Less mature tooling

**Stack:**
```
- SvelteKit + TypeScript
- Skeleton UI or DaisyUI
- Built-in stores for state
- SvelteKit routing
- Vite bundler
```

### Recommended: **React + TypeScript**
Reasoning: Largest ecosystem for audio processing libraries, best PWA support, most developers available for maintenance.

---

## 4. Progressive Web App (PWA) Features

### Mobile Installation
- Add to Home Screen support
- App manifest with icons
- Splash screens
- Standalone mode (no browser UI)

### Offline Support
- Service Worker caching
- Background sync for data
- Offline-first architecture
- Cache audio files locally

### Notifications
- Push notifications for practice reminders
- Background sync completion notifications
- Requires user permission

### Platform-Specific Features
- **Mobile:** Touch gestures, mobile-optimized UI
- **Desktop:** Keyboard shortcuts, larger layouts
- **TV:** D-pad navigation, 10-foot UI design

---

## 5. Migration Roadmap

### Phase 1: MVP (3-4 months)
**Goal:** Core functionality without live capture

✅ **Scope:**
- Shadow Library mode only
- Audio import from local files
- Manual URL import (user provides downloaded files)
- Voice Activity Detection (ONNX Runtime)
- Audio playback with speed control
- User recording
- Settings and preferences
- PWA with offline support

❌ **Excluded:**
- Live Shadow mode (audio capture)
- Android Auto
- Azure Speech assessment (add later)

**Team Size:** 2-3 developers

**Deliverables:**
- Web app accessible at shadowmaster.app
- Mobile-responsive UI
- Install as PWA on iOS/Android
- Desktop browser support

### Phase 2: Enhanced Features (2-3 months)
**Goal:** Feature parity with Android (minus live capture)

✅ **Additions:**
- Server-side URL processing (YouTube, Spotify, podcasts)
- Azure Speech pronunciation assessment
- Advanced audio export
- Playlist sharing via URLs
- Cloud sync (Firebase/Supabase)
- TV-optimized UI

**Team Size:** 3-4 developers

### Phase 3: Native Enhancements (Optional, 2-3 months)
**Goal:** Platform-specific features

✅ **Options:**
- Electron/Tauri desktop app (system audio capture)
- Chrome extension (browser audio capture)
- Native mobile apps (React Native/Capacitor)
- Smart TV apps (Tizen, webOS)

---

## 6. Cost & Resource Estimates

### Development Costs

| Phase | Duration | Team Size | Estimated Cost |
|-------|----------|-----------|----------------|
| Phase 1 (MVP) | 3-4 months | 2-3 devs | $60K - $100K |
| Phase 2 (Enhanced) | 2-3 months | 3-4 devs | $80K - $120K |
| Phase 3 (Native) | 2-3 months | 2-3 devs | $50K - $80K |
| **Total** | **7-10 months** | **2-4 devs** | **$190K - $300K** |

*Based on mid-level developer rates of $80-100/hour*

### Infrastructure Costs (Annual)

| Service | Purpose | Cost/Year |
|---------|---------|-----------|
| Cloud Hosting (Vercel/Netlify) | Web app hosting | $0 - $240 |
| Database (Supabase/Firebase) | User data, sync | $0 - $300 |
| CDN (Cloudflare) | Asset delivery | $0 - $240 |
| Server Processing (AWS/GCP) | URL audio extraction | $500 - $2000 |
| Azure Speech API | Assessment (pay-per-use) | $300 - $1500 |
| Domain & SSL | shadowmaster.app | $20 - $50 |
| **Total** | | **$820 - $4,330** |

### Maintenance Costs (Annual)
- Bug fixes and updates: $20K - $40K
- Feature development: $40K - $80K
- Infrastructure monitoring: $5K - $10K
- **Total:** $65K - $130K/year

---

## 7. Trade-offs & Limitations

### What You Gain ✅
- **Cross-platform:** One codebase for mobile, desktop, TV
- **No installation:** Access via browser, no app store approval
- **Easy updates:** Deploy instantly, no user action required
- **Web ecosystem:** Access to npm packages, web APIs
- **Lower distribution cost:** No Play Store fees
- **Easier testing:** No emulator/device setup needed

### What You Lose ❌
- **Live audio capture:** Cannot capture from other apps/tabs
- **Background processing:** Limited when app not in foreground
- **Android Auto:** No native car integration
- **Native performance:** Slightly slower than native Android
- **Offline-first:** More complex to implement than native
- **System integration:** Less deep integration with OS

### Performance Comparison

| Metric | Android Native | Web App |
|--------|----------------|---------|
| Initial Load Time | ~1-2s | ~2-4s |
| Audio Processing | Native C++ | WASM (90% speed) |
| Memory Usage | ~80-120MB | ~100-150MB |
| Battery Impact | Medium | Medium-High |
| Storage Space | ~50MB | Cache (variable) |

---

## 8. Competitive Analysis

### Similar Web Apps

**Speechling** (speechling.com)
- Web-based language learning with audio
- No live capture, focuses on library mode
- Uses Web Audio API successfully
- Has mobile PWA

**Yabla** (yabla.com)
- Video-based language learning
- Web player with speed control
- Server-side audio processing
- Works on all platforms

**FluentU** (fluentu.com)
- Web and mobile apps
- Video content with interactive transcripts
- Successful cross-platform approach

**Key Takeaway:** Successful language learning apps on web focus on curated/imported content rather than live capture.

---

## 9. Security & Privacy Considerations

### Web-Specific Security
- **No file system access:** More secure but less flexible
- **Sandboxed environment:** Harder for malware but limits features
- **HTTPS required:** For microphone, PWA features
- **CORS restrictions:** Need proxy for some external APIs
- **Content Security Policy:** Restricts third-party scripts

### Privacy Advantages
- **No persistent background:** Better privacy than Android service
- **Granular permissions:** User controls mic access per session
- **No app tracking:** No Play Services tracking
- **Open source:** Easier to audit web code

---

## 10. Recommended Approach

### Strategy: **Hybrid Web-First with Native Fallback**

#### Primary: Progressive Web App
- **Focus:** Shadow Library mode with URL import
- **Target:** 90% of users who don't need live capture
- **Benefits:** Cross-platform, easy distribution, lower cost

#### Secondary: Browser Extension (Optional)
- **Focus:** Enable tab audio capture for power users
- **Target:** 10% of users who want live capture
- **Benefits:** Capture from YouTube, Spotify in browser

#### Tertiary: Electron App (Optional)
- **Focus:** Desktop users who need system audio capture
- **Target:** Advanced users, language coaches
- **Benefits:** Full feature parity with Android

### Development Priority
1. **Month 1-4:** Core web app (Shadow Library)
2. **Month 5-6:** Server-side URL processing
3. **Month 7-8:** Azure Speech integration
4. **Month 9+:** Browser extension (if demand exists)

---

## 11. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Users demand live capture | High | High | Educate on URL import, offer extension |
| VAD performance issues | Medium | High | Test early, optimize WASM, fallback to WebRTC |
| Browser compatibility | Low | Medium | Target modern browsers, feature detection |
| Storage limitations | Medium | Medium | Cloud sync option, warn users of limits |
| Audio processing latency | Medium | Medium | Web Workers, optimize processing pipeline |
| App store preference | High | Medium | Focus on PWA benefits, marketing |

---

## 12. Alternative: Kotlin Multiplatform

### Consideration: Share Code with KMP

Instead of full rewrite, consider **Kotlin Multiplatform Mobile (KMM) + Compose Multiplatform**:

**Pros:**
- Share 80% of business logic between Android/iOS/Web
- Use existing Kotlin code
- Compose Multiplatform for UI (experimental web support)
- Faster than complete rewrite

**Cons:**
- Still need platform-specific audio code
- Compose Web is less mature than React/Vue
- Smaller web ecosystem than JavaScript
- Limited browser support compared to JS frameworks

**Verdict:** Only consider if you plan iOS native app too. For web-only conversion, JavaScript frameworks are more mature.

---

## 13. Conclusion & Recommendation

### Difficulty Rating: **7/10**

**Breakdown:**
- UI Layer: 3/10 (straightforward)
- Business Logic: 5/10 (moderate port)
- Audio Capture: 10/10 (impossible without native)
- Audio Processing: 6/10 (WASM required)
- Overall Implementation: 7/10 (challenging but doable)

### Final Recommendation

**Proceed with web conversion IF:**
1. You can accept losing live audio capture feature
2. You're willing to invest 6-9 months and $150K-250K
3. Your user base values cross-platform over live capture
4. You can pivot to URL-based import as main feature

**Don't convert IF:**
1. Live audio capture is essential to your value proposition
2. You have limited budget/time (<$100K or <6 months)
3. Android-only user base is satisfied
4. You prefer to invest in Android features instead

### Hybrid Approach (Best of Both Worlds)
**Keep Android app** for users who need live capture and Android Auto.  
**Build web app** for cross-platform users who value accessibility over features.  
**Market them together** as complementary products.

### Next Steps

If proceeding:
1. **User research:** Survey users about feature priorities
2. **Prototype:** Build 2-week proof-of-concept with React + ONNX VAD
3. **Test with users:** Validate URL import workflow
4. **Decide:** Commit to full build or iterate on Android

---

## 14. Appendix: Technical Resources

### Web Audio Libraries
- **Tone.js:** Web Audio framework for music/audio apps
- **Howler.js:** Audio library with Web Audio API fallback
- **WaveSurfer.js:** Audio waveform visualization
- **Peaks.js:** Audio waveform UI component

### VAD Options
- **ONNX Runtime Web:** Run Silero VAD in browser
- **@ricky0123/vad-web:** Pre-packaged VAD for browser
- **WebRTC VAD:** Native browser VAD (lower quality)

### State Management
- **Zustand:** Lightweight state management
- **Redux Toolkit:** Full-featured state management
- **Jotai:** Atomic state management
- **RxJS:** Reactive programming (closest to Flow)

### Database/Storage
- **Dexie.js:** IndexedDB wrapper with promises
- **sql.js:** SQLite compiled to WebAssembly
- **wa-sqlite:** Async SQLite for web
- **LocalForage:** Simple key-value store

### Build Tools
- **Vite:** Fast build tool for modern web
- **Turbopack:** Next.js bundler (experimental)
- **esbuild:** Fast bundler written in Go

### Testing
- **Vitest:** Fast unit testing (Vite-native)
- **Playwright:** E2E testing for web
- **Testing Library:** Component testing

---

**Document Version:** 1.0  
**Last Updated:** January 29, 2026  
**Author:** Technical Analysis for Shadow Master Web Conversion
