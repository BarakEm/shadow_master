# Feature Request: Add free tier transcription option (no API keys required)

> **To create this as a GitHub issue:** Copy the content below and paste it when creating a new issue at https://github.com/BarakEm/shadow_master/issues/new

---

## Problem
Currently, all transcription providers require users to paste their own API keys (Google, Azure, OpenAI Whisper, or custom endpoint). Not everyone is comfortable with managing API keys, and this creates a barrier for users who just want basic transcription functionality.

## Proposed Solution
Add a free tier transcription option that works out of the box without requiring users to configure API keys.

### Options to consider:

1. **Local on-device transcription using Whisper.cpp** (Recommended)
   - Use [Whisper.cpp](https://github.com/ggerganov/whisper.cpp) with a small model (tiny/base)
   - Pros: Completely offline, no API costs, privacy-friendly
   - Cons: Larger app size (~40-75MB for model), CPU/memory intensive
   - Could be implemented via the already planned `LocalModelProvider`

2. **Vosk offline speech recognition**
   - Lightweight offline speech recognition library
   - Pros: Smaller models available (~50MB), works offline
   - Cons: May have lower accuracy than Whisper

3. **Rate-limited free tier with app-provided API key**
   - App ships with a rate-limited API key for basic usage
   - Pros: Simple implementation, good quality
   - Cons: Cost management, abuse potential, usage limits

## Recommended Approach
Implement **Option 1 (Whisper.cpp local model)** as it:
- Provides the best quality for offline use
- Respects user privacy (no data leaves device)
- No ongoing API costs
- Aligns with the already planned `LocalModelProvider` in the codebase

### Implementation Notes
- The `LocalModelProvider` is already stubbed in the codebase (returns null currently)
- Could offer model size selection: tiny (~40MB) vs base (~75MB) for quality/size tradeoff
- Model download could be on-demand to keep initial app size small
- Path: `app/src/main/java/com/shadowmaster/transcription/LocalModelProvider.kt`

## Related Files
- `app/src/main/java/com/shadowmaster/transcription/TranscriptionService.kt`
- `app/src/main/java/com/shadowmaster/transcription/TranscriptionProvider.kt`
- `docs/TRANSCRIPTION_DEVELOPER_GUIDE.md` (has local model implementation notes)

## Acceptance Criteria
- [ ] Users can transcribe audio without configuring any API keys
- [ ] Free tier option is clearly marked as "Local/Offline" or "Free"
- [ ] Works offline after initial setup
- [ ] Reasonable transcription quality for common languages (at minimum: English)
- [ ] Model download is on-demand (not bundled in APK)
- [ ] Clear UI indication when using local vs cloud transcription
