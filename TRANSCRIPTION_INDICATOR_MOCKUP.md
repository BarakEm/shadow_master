# Transcription Indicator UI Mockup

## Before (No Indicator)

```
┌─────────────────────────────────────────────┐
│ ┌────┐                                      │
│ │2:15│  This is an audio segment           │
│ └────┘  that might have transcription       │
│         Translated text shown here          │
│         ↻ 5x                                │
│                        [⊞] [✎] [♡]          │
└─────────────────────────────────────────────┘
```

## After (With Transcription Indicator)

```
┌─────────────────────────────────────────────┐
│ ┌────┐                                      │
│ │2:15│  This is an audio segment           │
│ └────┘  that might have transcription       │
│         Translated text shown here          │
│         ↻ 5x  📝 Transcribed               │
│                        [⊞] [✎] [♡]          │
└─────────────────────────────────────────────┘
```

## Segment Card Visual Elements

### Segment WITH Transcription
```
╔═══════════════════════════════════════════════════╗
║  ┌─────┐                                          ║
║  │ 3.5s│  Hello, how are you today?              ║
║  └─────┘  Hola, ¿cómo estás hoy?                 ║
║           ↻ 12x  📝 Transcribed                  ║
║                              [⊞] [✎] [♡]          ║
╚═══════════════════════════════════════════════════╝
```

### Segment WITHOUT Transcription
```
╔═══════════════════════════════════════════════════╗
║  ┌─────┐                                          ║
║  │ 2.0s│  Segment 5                               ║
║  └─────┘                                          ║
║           ↻ 3x                                    ║
║                              [⊞] [✎] [♡]          ║
╚═══════════════════════════════════════════════════╝
```

## Practice Screen

### During Practice (With Transcription)
```
┌─────────────────────────────────────────────────┐
│              Shadow Master                      │
│              1 / 15                             │
├─────────────────────────────────────────────────┤
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━ 60%               │
│                                                 │
│                    ●                            │
│                                                 │
│             Playing (2)                         │
│                                                 │
│                                                 │
│ ┌─────────────────────────────────────────────┐ │
│ │            3:35                             │ │
│ │                                             │ │
│ │    Hello, how are you today?               │ │  ← Transcription shown!
│ │                                             │ │
│ │    Hola, ¿cómo estás hoy?                  │ │  ← Translation shown!
│ │                                             │ │
│ │    ↻ Practiced 12x                          │ │
│ └─────────────────────────────────────────────┘ │
│                                                 │
│                                                 │
│     [⏹]      [⏸]      [⏭]                     │
│                                                 │
└─────────────────────────────────────────────────┘
```

### During Practice (Without Transcription)
```
┌─────────────────────────────────────────────────┐
│              Shadow Master                      │
│              1 / 15                             │
├─────────────────────────────────────────────────┤
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━ 60%               │
│                                                 │
│                    ●                            │
│                                                 │
│             Playing (2)                         │
│                                                 │
│                                                 │
│ ┌─────────────────────────────────────────────┐ │
│ │            2:00                             │ │
│ │                                             │ │  ← No transcription text
│ │                                             │ │
│ └─────────────────────────────────────────────┘ │
│                                                 │
│                                                 │
│     [⏹]      [⏸]      [⏭]                     │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Library Screen - Playlist View

```
┌─────────────────────────────────────────────────┐
│  ← Back    Spanish Phrases                     │
│                                                 │
│  [Playlists]  [Imported Audio]                 │
├─────────────────────────────────────────────────┤
│                                                 │
│  ╔════════════════════════════════════════════╗ │
│  ║ ┌────┐                                     ║ │
│  ║ │2:15│ Hola, ¿cómo estás?                 ║ │
│  ║ └────┘ Hello, how are you?                 ║ │
│  ║        ↻ 5x  📝 Transcribed                ║ │ ← NEW indicator
│  ║                      [⊞] [✎] [♡]           ║ │
│  ╚════════════════════════════════════════════╝ │
│                                                 │
│  ╔════════════════════════════════════════════╗ │
│  ║ ┌────┐                                     ║ │
│  ║ │1:30│ Buenos días                         ║ │
│  ║ └────┘ Good morning                        ║ │
│  ║        ↻ 3x  📝 Transcribed                ║ │ ← NEW indicator
│  ║                      [⊞] [✎] [♡]           ║ │
│  ╚════════════════════════════════════════════╝ │
│                                                 │
│  ╔════════════════════════════════════════════╗ │
│  ║ ┌────┐                                     ║ │
│  ║ │2:00│ Segment 3                           ║ │
│  ║ └────┘                                     ║ │
│  ║        ↻ 1x                                ║ │ ← No indicator (no transcription)
│  ║                      [⊞] [✎] [♡]           ║ │
│  ╚════════════════════════════════════════════╝ │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Key Visual Changes

### Indicator Appearance
- **Icon**: 📝 (TextFields icon from Material Icons)
- **Color**: Primary theme color (typically blue/purple)
- **Size**: 12dp (same as practice count icon)
- **Text**: "Transcribed" in label small typography
- **Position**: Right after practice count, before action buttons

### Layout
- Uses `horizontalArrangement = Arrangement.spacedBy(8.dp)` for spacing
- Both practice count and transcription indicator are in the same row
- Each indicator is wrapped in its own Row for proper alignment

### Conditional Visibility
```kotlin
if (item.transcription != null) {
    // Show indicator
}
```

Only appears when transcription exists, keeping UI clean when not needed.

## Color Scheme

### Light Theme
- Transcription indicator: Primary color (e.g., #6200EE)
- Practice count: On-surface variant (e.g., #757575)

### Dark Theme
- Transcription indicator: Primary color (e.g., #BB86FC)
- Practice count: On-surface variant (e.g., #BEBEBE)

## Accessibility

- **Content Description**: "Has transcription" for screen readers
- **Color Contrast**: Primary color meets WCAG AA standards
- **Touch Target**: Part of the card, not separately clickable
- **Text Label**: "Transcribed" provides clear meaning

## Future Enhancements (Not Implemented Yet)

### Playlist Card Statistics
```
╔═══════════════════════════════════════════╗
║  🎵  Spanish Phrases                      ║
║      ES-ES • Last: Dec 1                  ║
║      📝 15/20 transcribed                 ║  ← Future enhancement
║                                           ║
║               [Practice]  [⚙] [✏] [🗑]    ║
╚═══════════════════════════════════════════╝
```

### ImportedAudio Card Statistics
```
╔═══════════════════════════════════════════╗
║  conversation.mp3                         ║
║  10:45 • Dec 1, 2024                     ║
║  📝 Partially transcribed (8/12)         ║  ← Future enhancement
║                     [Create Playlist]     ║
╚═══════════════════════════════════════════╝
```

## Implementation Notes

1. **Performance**: Simple null check, no database queries per item
2. **Consistency**: Matches existing practice count styling
3. **Extensibility**: Easy to add translation indicator later
4. **Maintainability**: Self-contained in ShadowItemCard composable
5. **Testability**: Preview functions exist for both states

## Testing Scenarios

✅ **Segment with transcription**: Indicator shows
✅ **Segment without transcription**: No indicator
✅ **Segment with transcription + practice count**: Both show side-by-side
✅ **Segment without transcription but with practice count**: Only practice count shows
✅ **Fresh segment (no practice, no transcription)**: Neither shows
✅ **Practice screen with transcription**: Text displays correctly
✅ **Practice screen without transcription**: No text, but segment plays correctly
