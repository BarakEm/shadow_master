# Shadow Master TODO

## Development Workflow
- **master** branch is always release-ready
- All work done in feature branches (`feature/`, `fix/`, `improve/`)
- PRs required for merging to master
- Current PR: #19 (devel branch)

---

## Quick Wins (Fast/Easy)

- [ ] **Logo rework** - Use nano banana or similar concept
- [x] **Verify import audio works** - Fixed URI handling, improved error messages
  - Added context/URI method for better content:// compatibility
  - Better error messages with actionable suggestions
- [x] **Reduce play button confusion** - Too many play buttons, improve UX clarity
  - Removed redundant app bar play button
  - Added labeled "Practice" button on playlist cards
  - Added "Start Practice" FAB in detail view

## Medium Priority

- [ ] **Practice mode gradual builder not working** - Investigated, code looks correct, needs device testing
- [ ] **Separate import and segment steps** - Allow re-segmenting same audio with different settings
- [ ] **Persistent audio library** - Survive app reinstalls (external storage or backup/restore)
- [ ] **Playback location controls** - Skip to arbitrary locations, scroll timeline
- [ ] **Original audio playback** - Play original unsegmented audio with navigation

## Major Features

- [ ] **Transcribe and translate support**
  - On-screen text of current segment
  - Translated TTS to desired language after segment playback

- [ ] **Smarter segmentation** - Split by phrase nuances/prosody, not just silence detection

- [ ] **URL import (Spotify/YouTube)** - Extract audio from streaming URLs

- [ ] **PC/Web application** - Partial functionality port for desktop/browser

---

## Notes
- Prioritize by speed: finish quick wins first
- Keep commits atomic and non-breaking
- Test on device before merging to master
