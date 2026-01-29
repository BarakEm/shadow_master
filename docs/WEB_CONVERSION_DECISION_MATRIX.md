# Web Conversion Decision Matrix

Use this matrix to quickly evaluate which conversion approach is best for your situation.

---

## Feature Comparison

| Feature | Android Native | Web App (PWA) | Browser Extension | Electron Desktop | Hybrid Strategy |
|---------|----------------|---------------|-------------------|------------------|-----------------|
| **Audio Capture** |
| Live capture from apps | ✅ Full | ❌ None | ❌ None | ✅ Full | ✅ Android / ❌ Web |
| Live capture from browser tabs | ✅ Full | ❌ None | ✅ Full | ✅ Full | ✅ Android / ⚠️ Extension |
| Import audio files | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Both |
| Import from URL (server-side) | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Both |
| **Playback** |
| Variable speed playback | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Both |
| Offline playback | ✅ Yes | ⚠️ Cached | ⚠️ Cached | ✅ Yes | ✅ / ⚠️ |
| Background playback | ✅ Yes | ❌ No | ❌ No | ✅ Yes | ✅ / ❌ |
| **Processing** |
| Voice Activity Detection | ✅ Native | ⚠️ WASM | ⚠️ WASM | ✅ Native | ✅ / ⚠️ |
| User recording | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Both |
| Pronunciation assessment | ✅ Yes | ⚠️ Different SDK | ⚠️ Different SDK | ✅ Yes | ✅ Both |
| **Platform Support** |
| Android mobile | ✅ Native | ✅ PWA | ✅ Chrome | ❌ No | ✅ Native / ✅ PWA |
| iOS mobile | ❌ No | ✅ Safari PWA | ❌ No | ❌ No | ❌ / ✅ PWA |
| Windows desktop | ❌ No | ✅ Browser | ✅ Extension | ✅ App | ❌ / ✅ Multiple |
| Mac desktop | ❌ No | ✅ Browser | ✅ Extension | ✅ App | ❌ / ✅ Multiple |
| Linux desktop | ❌ No | ✅ Browser | ✅ Extension | ✅ App | ❌ / ✅ Multiple |
| Smart TV | ❌ No | ✅ Browser | ❌ No | ❌ No | ❌ / ✅ Browser |
| Android Auto | ✅ Yes | ❌ No | ❌ No | ❌ No | ✅ / ❌ |
| **Distribution** |
| Installation required | ✅ Play Store | ❌ No (PWA) | ✅ Store | ✅ Download | Mix |
| App store approval | ✅ Required | ❌ Not needed | ✅ Required | ❌ Not needed | Mix |
| Updates | ⚠️ User action | ✅ Automatic | ⚠️ User action | ⚠️ User action | Mix |
| No-install trial | ❌ No | ✅ Yes | ❌ No | ❌ No | ❌ / ✅ |
| **Performance** |
| Initial load time | ~1-2s | ~2-4s | ~2-4s | ~3-5s | Both |
| Audio latency | ~20ms | ~30-50ms | ~30-50ms | ~20ms | Low / Medium |
| Battery usage | Medium | Medium-High | Medium-High | Medium | Both |
| Memory usage | ~100MB | ~120-150MB | ~120-150MB | ~150-200MB | Both |
| **Development** |
| Codebase | Kotlin/Compose | JS/React/Vue | JS + Extension API | JS + Electron | Both |
| Code reuse from Android | 100% | ~20-30% | ~20-30% | ~30-40% | 100% + new |
| Developer availability | Medium | High | Medium | High | Both |
| Development cost | - | $60-100K | +$20-40K | +$50-80K | $60-100K |
| Maintenance burden | Current | Medium | High (2 codebases) | High (2 codebases) | High (2 codebases) |

---

## Cost Comparison (USD)

| Phase | Android Only | Web App | + Extension | + Electron | Hybrid Strategy |
|-------|--------------|---------|-------------|------------|-----------------|
| **One-Time Development** |
| Initial development | $0 (done) | $60-100K | +$20-40K | +$50-80K | $60-100K |
| Feature parity | $0 (done) | +$15-25K | +$10-20K | +$20-30K | +$15-25K |
| **Total Dev Cost** | **$0** | **$75-125K** | **$105-185K** | **$145-235K** | **$75-125K** |
| **Annual Operating** |
| Hosting | $0 | $0-500 | $0-500 | $0-500 | $0-500 |
| Server processing | $0 | $500-2K | $500-2K | $500-2K | $500-2K |
| CDN & Domain | $0 | $50-200 | $50-200 | $50-200 | $50-200 |
| Cloud sync | $0 | $300-1K | $300-1K | $300-1K | $300-1K |
| **Total Infrastructure** | **$0** | **$850-3.7K** | **$850-3.7K** | **$850-3.7K** | **$850-3.7K** |
| **Annual Maintenance** |
| Bug fixes | Current | $20-40K | $30-50K | $30-50K | $30-50K |
| Feature updates | Current | $20-40K | $20-40K | $20-40K | $30-60K |
| **Total Maintenance** | **$40-80K** | **$40-80K** | **$50-90K** | **$50-90K** | **$60-110K** |

---

## Timeline Comparison

| Milestone | Android | Web App | + Extension | + Electron | Hybrid |
|-----------|---------|---------|-------------|------------|--------|
| Project setup | - | 1 week | 1 week | 2 weeks | 1 week |
| Core audio | - | 4 weeks | 4 weeks | 4 weeks | 4 weeks |
| VAD integration | - | 2 weeks | 2 weeks | 1 week | 2 weeks |
| Database/storage | - | 1 week | 1 week | 1 week | 1 week |
| UI implementation | - | 3 weeks | 3 weeks | 3 weeks | 3 weeks |
| Import features | - | 2 weeks | 3 weeks | 2 weeks | 2 weeks |
| PWA features | - | 1 week | 1 week | - | 1 week |
| Extension dev | - | - | 2 weeks | - | Optional |
| Desktop packaging | - | - | - | 2 weeks | Optional |
| Testing & polish | - | 2 weeks | 2 weeks | 2 weeks | 2 weeks |
| **Total Time** | **Done** | **16 weeks** | **19 weeks** | **17 weeks** | **16 weeks** |
| **(Months)** | - | **3.5-4** | **4.5-5** | **4-4.5** | **3.5-4** |

---

## User Impact Score (1-10, higher is better)

| User Type | Android Only | Web App | + Extension | + Electron | Hybrid |
|-----------|--------------|---------|-------------|------------|--------|
| **Current Android users** | 10 | 6 | 6 | 6 | 10 |
| **Potential iOS users** | 0 | 8 | 8 | 0 | 8 |
| **Desktop users** | 0 | 8 | 9 | 10 | 9 |
| **TV users** | 0 | 7 | 7 | 0 | 7 |
| **No-install preference** | 0 | 10 | 6 | 0 | 10 |
| **Live capture priority** | 10 | 0 | 5 | 7 | 10 |
| **Android Auto users** | 10 | 0 | 0 | 0 | 10 |
| **Cross-platform users** | 2 | 9 | 9 | 8 | 10 |
| **Privacy-conscious** | 8 | 10 | 7 | 9 | 9 |
| **Average Score** | **5.6** | **6.4** | **6.3** | **5.6** | **9.2** |

---

## Decision Tree

```
START: Should I convert to web?
│
├─ Do users NEED live audio capture?
│  │
│  ├─ YES → Is browser tab capture enough?
│  │  │
│  │  ├─ YES → Build Web App + Browser Extension
│  │  └─ NO → Keep Android Only OR Build Hybrid Strategy
│  │
│  └─ NO → Do you want cross-platform?
│     │
│     ├─ YES → Build Web App (PWA)
│     └─ NO → Keep Android Only
│
├─ What's your budget?
│  │
│  ├─ < $50K → Keep Android Only
│  ├─ $50-100K → Web App (MVP)
│  ├─ $100-150K → Web App + URL Processing
│  ├─ $150-200K → Web App + Extension OR Electron
│  └─ > $200K → Hybrid Strategy (best of both)
│
├─ What's your timeline?
│  │
│  ├─ < 3 months → Keep Android Only
│  ├─ 3-4 months → Web App (MVP)
│  ├─ 4-5 months → Web App + Extension
│  └─ 6+ months → Hybrid Strategy
│
└─ What's your team size?
   │
   ├─ 1 developer → Keep Android Only
   ├─ 2 developers → Web App
   └─ 3+ developers → Hybrid Strategy
```

---

## Risk vs Reward Matrix

|  | Low Risk | Medium Risk | High Risk |
|---|----------|-------------|-----------|
| **High Reward** | **Web App (PWA)** ✅<br>- Cross-platform reach<br>- 6-9 month timeline<br>- Proven tech | **Hybrid Strategy** ⭐<br>- Best features everywhere<br>- Higher maintenance<br>- 2 codebases | **Electron Desktop**<br>- Full features<br>- Large download<br>- Desktop only |
| **Medium Reward** | Android Only 🔄<br>- Keep improving<br>- Known territory<br>- Growing user base | **Browser Extension**<br>- Tab capture works<br>- Limited audience<br>- Browser-only | - |
| **Low Reward** | - | - | Complete Rewrite<br>- Throw away Android<br>- Lose features<br>- ❌ Not recommended |

---

## Quick Recommendation by Scenario

### Scenario 1: "I want to reach iOS and desktop users"
**Recommendation:** Web App (PWA)  
**Why:** Works on all platforms, no installation needed, 3-4 month timeline  
**Trade-off:** No live capture, but URL import compensates  
**Cost:** $75-125K

### Scenario 2: "Live capture is my killer feature"
**Recommendation:** Hybrid Strategy  
**Why:** Keep Android for live capture, add web for reach  
**Trade-off:** Maintain two codebases  
**Cost:** $75-125K initial + higher maintenance

### Scenario 3: "I have limited budget (<$50K)"
**Recommendation:** Keep Android Only  
**Why:** Don't spread resources thin, improve what works  
**Trade-off:** Limited to Android users  
**Cost:** $0 for conversion

### Scenario 4: "I need desktop app with full features"
**Recommendation:** Electron Desktop App  
**Why:** Native-like experience, system audio capture possible  
**Trade-off:** Not truly cross-platform, larger download  
**Cost:** $145-235K

### Scenario 5: "I want maximum user reach"
**Recommendation:** Hybrid Strategy  
**Why:** Android (live capture) + Web (cross-platform)  
**Trade-off:** Higher development and maintenance costs  
**Cost:** $75-125K + ongoing maintenance

### Scenario 6: "I want to test web market with minimal investment"
**Recommendation:** Web App MVP (Shadow Library only)  
**Why:** 3-month timeline, $60-80K, validate demand  
**Trade-off:** Limited features initially  
**Cost:** $60-80K

---

## Success Probability Estimates

| Approach | Technical Success | User Adoption | ROI Likelihood |
|----------|-------------------|---------------|----------------|
| Android Only | 95% (proven) | 60% (Android only) | Medium |
| Web App (PWA) | 85% (mature tech) | 75% (broad reach) | High |
| + Browser Extension | 70% (extension friction) | 50% (install barrier) | Medium |
| + Electron Desktop | 80% (proven tech) | 60% (niche audience) | Medium |
| Hybrid Strategy | 90% (leverages both) | 85% (best of both) | High |

---

## Final Recommendations by Priority

### Priority 1: Maximize User Reach 🎯
→ **Hybrid Strategy** (Android + Web PWA)
- Reaches 90% of potential users
- Preserves Android's unique features
- Adds cross-platform accessibility

### Priority 2: Minimize Cost 💰
→ **Web App MVP** (Shadow Library only)
- $60-80K initial investment
- Validate web market
- Can add features later

### Priority 3: Maintain Feature Parity ⚡
→ **Android Only + Continuous Improvement**
- $0 conversion cost
- Focus on making Android app even better
- Wait for web tech to mature

### Priority 4: Desktop Power Users 💻
→ **Electron Desktop App**
- Full system audio capture
- Professional desktop experience
- Complements Android app

---

## Common Questions

**Q: Can't I just use React Native or Flutter for cross-platform?**  
A: These target iOS/Android native apps. They still can't capture system audio on iOS, and you'd need web separately anyway.

**Q: What about Progressive Web App (PWA) on iOS?**  
A: PWAs work on iOS Safari, but still limited—no background audio, no system audio capture, limited notifications.

**Q: Could I use WebAssembly to run the Android app in browser?**  
A: Technically possible but impractical. Would need to rewrite all Android-specific APIs, and still no system audio capture.

**Q: What if browser APIs improve in the future?**  
A: Unlikely. System audio capture is intentionally blocked for security/privacy. May never be available in browsers.

**Q: Can I reuse any Android code?**  
A: Business logic (~20-30%) can be ported. Audio processing logic is portable. UI and platform APIs need full rewrite.

**Q: Should I wait for Compose Multiplatform Web?**  
A: It's experimental (alpha/beta). For production app, use mature React/Vue/Svelte ecosystem.

---

**Use this matrix with:**
1. [WEB_CONVERSION_FEASIBILITY.md](./WEB_CONVERSION_FEASIBILITY.md) - Detailed analysis
2. [WEB_CONVERSION_SUMMARY.md](./WEB_CONVERSION_SUMMARY.md) - Executive overview
3. [WEB_CONVERSION_QUICKSTART.md](./WEB_CONVERSION_QUICKSTART.md) - Implementation guide

---

**Last Updated:** January 29, 2026
