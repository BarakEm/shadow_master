# Web Conversion Quick Start Guide

This guide provides actionable next steps if you decide to proceed with the web conversion.

---

## Pre-Decision Checklist

Before committing resources, validate these assumptions:

### User Research (1-2 weeks)
```
[ ] Survey existing users about feature usage
    - How often do they use Live Shadow vs Library?
    - Would they accept URL-based import instead?
    - Is cross-platform access valuable to them?
    - Would they pay for web version?

[ ] Identify target web users
    - Desktop users who want language practice
    - iOS users (no Android app alternative)
    - Users who want TV/large screen practice
    - Users who prefer no installation

[ ] Test concept with 10-20 users
    - Show mockup of URL import workflow
    - Gauge reaction to "no live capture" limitation
    - Collect feedback on must-have features
```

### Technical Validation (1-2 weeks)
```
[ ] Build proof-of-concept
    - React + TypeScript basic app
    - ONNX Runtime with Silero VAD model
    - Web Audio API playback with speed control
    - IndexedDB storage for segments
    
[ ] Performance testing
    - VAD latency on target browsers
    - Audio processing performance
    - Memory usage with large playlists
    - Mobile browser responsiveness

[ ] Browser compatibility check
    - Chrome 90+ (desktop & mobile)
    - Safari 15+ (desktop & iOS)
    - Firefox 88+ (desktop)
    - Edge 90+ (desktop)
```

### Business Planning (1 week)
```
[ ] Define success metrics
    - Target number of users (Year 1)
    - Conversion rate from free to paid
    - Acceptable cost per user
    - Revenue targets

[ ] Plan monetization
    - Free tier: Library mode with limits
    - Premium tier: Unlimited, URL import, assessment
    - Or keep Android paid, web free?

[ ] Estimate costs
    - Development: $60K-$100K minimum
    - Infrastructure: $1K-$5K annually
    - Maintenance: $40K-$80K annually
```

---

## Development Roadmap (if proceeding)

### Phase 0: Setup (Week 1-2)

**Repository Setup**
```bash
# Create new repo or separate directory
mkdir shadow-master-web
cd shadow-master-web

# Initialize project with Vite + React + TypeScript
npm create vite@latest . -- --template react-ts

# Install core dependencies
npm install @mui/material @emotion/react @emotion/styled
npm install zustand react-router-dom
npm install dexie
npm install onnxruntime-web
npm install workbox-precaching workbox-routing
```

**Development Tools**
```bash
# Install dev dependencies
npm install -D @types/node
npm install -D vitest @testing-library/react
npm install -D @playwright/test
npm install -D eslint prettier
```

**Project Structure**
```
shadow-master-web/
├── public/
│   ├── manifest.json          # PWA manifest
│   └── silero_vad.onnx        # VAD model
├── src/
│   ├── components/            # React components
│   │   ├── library/
│   │   ├── player/
│   │   └── settings/
│   ├── stores/                # Zustand state stores
│   ├── services/              # Business logic
│   │   ├── audio/
│   │   ├── vad/
│   │   └── storage/
│   ├── types/                 # TypeScript types
│   └── utils/
├── tests/
└── package.json
```

### Phase 1: Core Audio (Week 3-4)

**Milestone: Play audio with speed control**

```typescript
// src/services/audio/WebAudioEngine.ts
export class WebAudioEngine {
    private audioContext: AudioContext
    private sourceNode: AudioBufferSourceNode | null = null
    private gainNode: GainNode
    
    constructor() {
        this.audioContext = new AudioContext({ sampleRate: 16000 })
        this.gainNode = this.audioContext.createGain()
        this.gainNode.connect(this.audioContext.destination)
    }
    
    async playAudio(samples: Float32Array, speed: number = 1.0): Promise<void> {
        // Convert Float32Array to AudioBuffer
        const buffer = this.audioContext.createBuffer(1, samples.length, 16000)
        buffer.copyToChannel(samples, 0)
        
        // Create source and configure
        this.sourceNode = this.audioContext.createBufferSource()
        this.sourceNode.buffer = buffer
        this.sourceNode.playbackRate.value = speed
        this.sourceNode.connect(this.gainNode)
        
        return new Promise((resolve) => {
            this.sourceNode!.onended = () => resolve()
            this.sourceNode!.start()
        })
    }
    
    stop(): void {
        this.sourceNode?.stop()
    }
}
```

**Testing Checklist**
```
[ ] Audio plays correctly
[ ] Speed control works (0.5x - 1.5x)
[ ] Can stop/restart audio
[ ] No memory leaks on repeated playback
```

### Phase 2: Voice Activity Detection (Week 5-6)

**Milestone: Detect speech in audio files**

```typescript
// src/services/vad/SileroVadWeb.ts
import * as ort from 'onnxruntime-web'

export class SileroVadWeb {
    private session: ort.InferenceSession | null = null
    private readonly THRESHOLD = 0.5
    
    async initialize(): Promise<void> {
        this.session = await ort.InferenceSession.create('/silero_vad.onnx', {
            executionProviders: ['wasm'],
        })
    }
    
    async detectSpeech(audioSamples: Float32Array): Promise<number> {
        if (!this.session) throw new Error('VAD not initialized')
        
        // Silero VAD expects 512 samples at 16kHz
        const input = new ort.Tensor('float32', audioSamples, [1, audioSamples.length])
        const outputs = await this.session.run({ input })
        const speechProb = outputs.output.data[0] as number
        
        return speechProb
    }
    
    isSpeech(probability: number): boolean {
        return probability > this.THRESHOLD
    }
}
```

**Integration with Audio Processing**
```typescript
// src/services/audio/AudioSegmenter.ts
export class AudioSegmenter {
    private vad: SileroVadWeb
    private readonly FRAME_SIZE = 512
    private readonly MIN_SPEECH_DURATION_MS = 300
    private readonly MIN_SILENCE_DURATION_MS = 500
    
    async segmentAudio(audioData: Float32Array): Promise<AudioSegment[]> {
        const segments: AudioSegment[] = []
        let speechStart: number | null = null
        let lastSpeechEnd: number = 0
        
        for (let i = 0; i < audioData.length; i += this.FRAME_SIZE) {
            const frame = audioData.slice(i, i + this.FRAME_SIZE)
            const prob = await this.vad.detectSpeech(frame)
            const isSpeech = this.vad.isSpeech(prob)
            
            if (isSpeech && speechStart === null) {
                speechStart = i
            } else if (!isSpeech && speechStart !== null) {
                const duration = (i - speechStart) / 16 // samples to ms
                if (duration >= this.MIN_SPEECH_DURATION_MS) {
                    segments.push({
                        start: speechStart / 16,
                        end: i / 16,
                        samples: audioData.slice(speechStart, i)
                    })
                }
                speechStart = null
            }
        }
        
        return segments
    }
}
```

**Testing Checklist**
```
[ ] VAD model loads correctly
[ ] Detects speech segments accurately
[ ] Filters out short segments
[ ] Performance is acceptable (<100ms per second of audio)
[ ] Works on mobile browsers
```

### Phase 3: Database & Storage (Week 7)

**Milestone: Store and retrieve playlists and segments**

```typescript
// src/services/storage/ShadowLibraryDB.ts
import Dexie, { Table } from 'dexie'

export interface Playlist {
    id?: number
    name: string
    createdAt: number
    updatedAt: number
}

export interface ShadowItem {
    id?: number
    playlistId: number
    sourceFile: string
    sourceStartMs: number
    sourceEndMs: number
    audioData: Float32Array
    transcription?: string
    translation?: string
    practiceCount: number
    isFavorite: boolean
}

export class ShadowLibraryDB extends Dexie {
    playlists!: Table<Playlist, number>
    items!: Table<ShadowItem, number>
    
    constructor() {
        super('ShadowLibraryDB')
        this.version(1).stores({
            playlists: '++id, name, createdAt',
            items: '++id, playlistId, sourceStartMs, isFavorite'
        })
    }
}

export const db = new ShadowLibraryDB()
```

**Usage Example**
```typescript
// Create playlist
const playlistId = await db.playlists.add({
    name: 'Spanish Podcast',
    createdAt: Date.now(),
    updatedAt: Date.now()
})

// Add segment
await db.items.add({
    playlistId,
    sourceFile: 'podcast-ep1.mp3',
    sourceStartMs: 1000,
    sourceEndMs: 3500,
    audioData: segmentSamples,
    practiceCount: 0,
    isFavorite: false
})
```

**Testing Checklist**
```
[ ] Can create playlists
[ ] Can add segments
[ ] Can retrieve by playlist
[ ] Can update segment metadata
[ ] Can delete items
[ ] Audio data stored correctly (Float32Array)
```

### Phase 4: UI Components (Week 8-10)

**Milestone: Functional UI for all screens**

**Library Screen**
```typescript
// src/components/library/LibraryScreen.tsx
export function LibraryScreen() {
    const { playlists, loading } = useLibrary()
    const navigate = useNavigate()
    
    return (
        <Container>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 2 }}>
                <Typography variant="h4">My Playlists</Typography>
                <Button startIcon={<AddIcon />} onClick={() => navigate('/import')}>
                    Import Audio
                </Button>
            </Box>
            
            {loading ? <CircularProgress /> : (
                <Grid container spacing={2}>
                    {playlists.map(playlist => (
                        <Grid item xs={12} sm={6} md={4} key={playlist.id}>
                            <PlaylistCard 
                                playlist={playlist} 
                                onPlay={() => navigate(`/practice/${playlist.id}`)}
                            />
                        </Grid>
                    ))}
                </Grid>
            )}
        </Container>
    )
}
```

**Practice Screen**
```typescript
// src/components/practice/PracticeScreen.tsx
export function PracticeScreen() {
    const { playlistId } = useParams()
    const { state, currentSegment, play, record, skip } = usePractice(playlistId)
    
    return (
        <Container maxWidth="md">
            <PracticeControls state={state} />
            
            {currentSegment && (
                <Card>
                    <CardContent>
                        <Typography variant="h6">
                            Segment {currentSegment.index + 1} of {currentSegment.total}
                        </Typography>
                        
                        {currentSegment.transcription && (
                            <Typography color="text.secondary">
                                {currentSegment.transcription}
                            </Typography>
                        )}
                        
                        <Box sx={{ mt: 2 }}>
                            {state === 'idle' && (
                                <Button variant="contained" onClick={play}>
                                    Play
                                </Button>
                            )}
                            {state === 'playback' && (
                                <CircularProgress />
                            )}
                            {state === 'waiting_for_user' && (
                                <>
                                    <Button variant="contained" color="error" onClick={record}>
                                        Speak Now
                                    </Button>
                                    <Button onClick={skip}>Skip</Button>
                                </>
                            )}
                        </Box>
                    </CardContent>
                </Card>
            )}
        </Container>
    )
}
```

**Testing Checklist**
```
[ ] Library screen shows playlists
[ ] Can navigate to practice
[ ] Practice screen plays audio
[ ] State transitions work correctly
[ ] Settings page functional
[ ] Mobile responsive
[ ] Keyboard shortcuts work
```

### Phase 5: Audio Import (Week 11-12)

**Milestone: Import audio files and URL**

**File Import**
```typescript
// src/services/import/FileImporter.ts
export class FileImporter {
    private audioContext: AudioContext
    private segmenter: AudioSegmenter
    
    async importFile(file: File): Promise<ImportResult> {
        // Decode audio file
        const arrayBuffer = await file.arrayBuffer()
        const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer)
        
        // Convert to 16kHz mono Float32Array
        const samples = this.resampleTo16kHz(audioBuffer)
        
        // Segment audio
        const segments = await this.segmenter.segmentAudio(samples)
        
        return {
            fileName: file.name,
            duration: samples.length / 16000,
            segments,
            totalSegments: segments.length
        }
    }
    
    private resampleTo16kHz(buffer: AudioBuffer): Float32Array {
        // Use offline audio context to resample
        const offlineCtx = new OfflineAudioContext(1, 
            buffer.duration * 16000, 16000)
        const source = offlineCtx.createBufferSource()
        source.buffer = buffer
        source.connect(offlineCtx.destination)
        source.start()
        
        return offlineCtx.startRendering()
            .then(renderedBuffer => renderedBuffer.getChannelData(0))
    }
}
```

**URL Import (Server-Side)**
```typescript
// server/routes/import.ts (Node.js/Express example)
import ytdl from 'ytdl-core'
import ffmpeg from 'fluent-ffmpeg'

app.post('/api/import/url', async (req, res) => {
    const { url } = req.body
    
    try {
        // Download audio from YouTube
        const info = await ytdl.getInfo(url)
        const audio = ytdl(url, { quality: 'highestaudio' })
        
        // Convert to 16kHz mono WAV
        const outputPath = `/tmp/${Date.now()}.wav`
        await new Promise((resolve, reject) => {
            ffmpeg(audio)
                .audioFrequency(16000)
                .audioChannels(1)
                .format('wav')
                .save(outputPath)
                .on('end', resolve)
                .on('error', reject)
        })
        
        // Return download URL
        res.json({
            success: true,
            audioUrl: `/downloads/${path.basename(outputPath)}`,
            title: info.videoDetails.title,
            duration: parseInt(info.videoDetails.lengthSeconds)
        })
    } catch (error) {
        res.status(500).json({ error: error.message })
    }
})
```

**Testing Checklist**
```
[ ] File import works for MP3, WAV, M4A
[ ] Audio resampling is correct
[ ] Segmentation runs on imported files
[ ] Progress indication works
[ ] URL import downloads correctly
[ ] Server handles errors gracefully
```

### Phase 6: PWA Features (Week 13)

**Milestone: Installable Progressive Web App**

**Manifest**
```json
// public/manifest.json
{
  "name": "Shadow Master",
  "short_name": "Shadow Master",
  "description": "Language learning through audio shadowing",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#1976d2",
  "icons": [
    {
      "src": "/icons/icon-192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "/icons/icon-512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ],
  "categories": ["education", "productivity"],
  "screenshots": [
    {
      "src": "/screenshots/library.png",
      "sizes": "1280x720",
      "type": "image/png"
    }
  ]
}
```

**Service Worker (Workbox)**
```typescript
// src/service-worker.ts
import { precacheAndRoute } from 'workbox-precaching'
import { registerRoute } from 'workbox-routing'
import { CacheFirst, NetworkFirst } from 'workbox-strategies'

// Precache app shell
precacheAndRoute(self.__WB_MANIFEST)

// Cache audio files
registerRoute(
  ({ request }) => request.destination === 'audio',
  new CacheFirst({
    cacheName: 'audio-cache',
    plugins: [
      {
        cacheWillUpdate: async ({ response }) => {
          return response.ok ? response : null
        }
      }
    ]
  })
)

// Network-first for API calls
registerRoute(
  ({ url }) => url.pathname.startsWith('/api/'),
  new NetworkFirst({
    cacheName: 'api-cache',
    networkTimeoutSeconds: 10
  })
)
```

**Testing Checklist**
```
[ ] App installs on mobile (iOS & Android)
[ ] App installs on desktop (Chrome, Edge)
[ ] Works offline (cached audio plays)
[ ] Service worker updates correctly
[ ] Push notifications work (if implemented)
```

### Phase 7: Testing & Polish (Week 14-15)

**Testing Strategy**
```bash
# Unit tests
npm run test

# E2E tests
npm run test:e2e

# Performance testing
npm run lighthouse
```

**Performance Targets**
```
[ ] Lighthouse score > 90
[ ] First Contentful Paint < 1.5s
[ ] Time to Interactive < 3s
[ ] VAD processing < 100ms per second
[ ] Audio playback latency < 50ms
[ ] Bundle size < 500KB (gzipped)
```

**Browser Testing**
```
[ ] Chrome 90+ (Windows, Mac, Linux, Android)
[ ] Safari 15+ (macOS, iOS)
[ ] Firefox 88+ (Windows, Mac, Linux)
[ ] Edge 90+ (Windows)
```

**Accessibility**
```
[ ] Keyboard navigation works
[ ] Screen reader compatible
[ ] WCAG 2.1 AA compliance
[ ] Focus indicators visible
[ ] Color contrast sufficient
```

---

## Deployment Checklist

### Hosting Setup

**Option 1: Vercel (Recommended)**
```bash
# Install Vercel CLI
npm i -g vercel

# Deploy
vercel --prod

# Custom domain
vercel domains add shadowmaster.app
```

**Option 2: Netlify**
```bash
# Install Netlify CLI
npm i -g netlify-cli

# Deploy
netlify deploy --prod

# Custom domain in Netlify dashboard
```

### Server Setup (for URL import)

**Backend API (Node.js + Express)**
```bash
# Deploy to Railway, Render, or Fly.io
fly launch
fly deploy

# Environment variables
fly secrets set YOUTUBE_API_KEY=xxx
```

### Database

**Supabase Setup**
```bash
# Create project at supabase.com
# Copy project URL and anon key

# Add to web app environment
VITE_SUPABASE_URL=https://xxx.supabase.co
VITE_SUPABASE_ANON_KEY=eyJxxx
```

### Domain & SSL

```bash
# Purchase domain
# shadowmaster.app

# Point DNS to Vercel/Netlify
# A record: 76.76.21.21
# CNAME: shadowmaster.app -> cname.vercel-dns.com

# SSL automatically provided
```

---

## Post-Launch Monitoring

### Analytics Setup
```typescript
// Google Analytics 4
npm install react-ga4

// Initialize
import ReactGA from 'react-ga4'
ReactGA.initialize('G-XXXXXXXXXX')

// Track page views
ReactGA.send({ hitType: 'pageview', page: window.location.pathname })
```

### Error Tracking
```typescript
// Sentry
npm install @sentry/react

// Initialize
import * as Sentry from '@sentry/react'
Sentry.init({
  dsn: 'https://xxx@sentry.io/xxx',
  environment: process.env.NODE_ENV
})
```

### Performance Monitoring
```typescript
// Web Vitals
npm install web-vitals

import { getCLS, getFID, getFCP, getLCP, getTTFB } from 'web-vitals'

getCLS(console.log)
getFID(console.log)
getFCP(console.log)
getLCP(console.log)
getTTFB(console.log)
```

---

## Resources

### Learning Resources
- [Web Audio API Tutorial](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API)
- [ONNX Runtime Web Docs](https://onnxruntime.ai/docs/tutorials/web/)
- [PWA Documentation](https://web.dev/progressive-web-apps/)
- [IndexedDB Guide](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API)

### Example Projects
- [Voice Activity Detection in Browser](https://github.com/ricky0123/vad)
- [Web Audio Recorder](https://github.com/mattdiamond/Recorderjs)
- [Audio Worklet Examples](https://github.com/GoogleChromeLabs/web-audio-samples)

### Tools
- [Vite](https://vitejs.dev/) - Build tool
- [Vitest](https://vitest.dev/) - Unit testing
- [Playwright](https://playwright.dev/) - E2E testing
- [Lighthouse](https://github.com/GoogleChrome/lighthouse) - Performance audit

---

## Success Criteria

### Technical Metrics
```
✅ Core audio playback works across browsers
✅ VAD accuracy > 90% (compared to Android)
✅ No audio glitches or dropouts
✅ App loads in < 3 seconds
✅ Works offline after first visit
✅ Mobile battery drain < 20%/hour during practice
```

### User Metrics
```
✅ 100+ beta users try the app
✅ 50%+ complete at least one practice session
✅ 20%+ use app weekly
✅ Average session length > 10 minutes
✅ Bug report rate < 5%
```

### Business Metrics
```
✅ Cost per user < $1
✅ Server costs < $100/month
✅ Positive user feedback (>4.0/5.0 rating)
✅ Conversion rate to paid tier > 5%
```

---

## Emergency Rollback Plan

If web version has critical issues:

1. **Immediate:** Take down web app or show maintenance page
2. **Communicate:** Email users, update social media
3. **Analyze:** Identify root cause (logs, Sentry, user reports)
4. **Fix:** Apply hotfix or revert to previous version
5. **Test:** Verify fix in staging environment
6. **Deploy:** Gradual rollout (10% → 50% → 100%)
7. **Monitor:** Watch metrics closely for 24-48 hours

---

**This guide should be used in conjunction with:**
- [WEB_CONVERSION_FEASIBILITY.md](./WEB_CONVERSION_FEASIBILITY.md) - Full analysis
- [WEB_CONVERSION_SUMMARY.md](./WEB_CONVERSION_SUMMARY.md) - Executive summary

**Questions or need help?** Open an issue in the repository or reach out to the development team.
