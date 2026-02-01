# Transcription Settings UI Mockup

## Settings Screen - Transcription Services Section

```
┌─────────────────────────────────────────────────────┐
│ ← Settings                                          │
├─────────────────────────────────────────────────────┤
│                                                     │
│ [Existing settings sections above]                 │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│ Transcription Services                             │
│                                                     │
│ Auto-transcribe on Import                          │
│ Automatically transcribe audio segments after      │
│ importing                                    ○ OFF │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│ Default Provider                                   │
│ Google Speech-to-Text ▼                            │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│ Google Speech-to-Text                              │
│                                              ⚠️ Not configured │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│ Azure Speech Services                              │
│ Region: eastus                            ✓ Configured │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│ OpenAI Whisper                                     │
│                                              ⚠️ Not configured │
│                                                     │
├─────────────────────────────────────────────────────┤
│                                                     │
│ Custom Endpoint                                    │
│ https://api.example.com/...               ✓ Configured │
│                                                     │
└─────────────────────────────────────────────────────┘
```

## API Key Dialog (Example: Google)

```
┌─────────────────────────────────────────────┐
│ Google API Key                              │
├─────────────────────────────────────────────┤
│                                             │
│ Enter your API key:                         │
│                                             │
│ ┌─────────────────────────────────────┐   │
│ │ API Key                              │   │
│ │ [sk-...]                             │   │
│ └─────────────────────────────────────┘   │
│                                             │
│                           [Cancel] [Save]   │
└─────────────────────────────────────────────┘
```

## Azure Configuration Dialog

```
┌─────────────────────────────────────────────┐
│ Azure Configuration                         │
├─────────────────────────────────────────────┤
│                                             │
│ Enter your Azure Speech Services           │
│ credentials:                                │
│                                             │
│ ┌─────────────────────────────────────┐   │
│ │ API Key                              │   │
│ │ [abc123...]                          │   │
│ └─────────────────────────────────────┘   │
│                                             │
│ ┌─────────────────────────────────────┐   │
│ │ Region (e.g., eastus)                │   │
│ │ [eastus]                             │   │
│ └─────────────────────────────────────┘   │
│                                             │
│                           [Cancel] [Save]   │
└─────────────────────────────────────────────┘
```

## Custom Endpoint Dialog

```
┌─────────────────────────────────────────────┐
│ Custom Endpoint                             │
├─────────────────────────────────────────────┤
│                                             │
│ Configure your custom transcription         │
│ endpoint:                                   │
│                                             │
│ ┌─────────────────────────────────────┐   │
│ │ Endpoint URL                         │   │
│ │ [https://api.example.com/transcribe] │   │
│ └─────────────────────────────────────┘   │
│                                             │
│ ┌─────────────────────────────────────┐   │
│ │ API Key (optional)                   │   │
│ │ [optional-key]                       │   │
│ └─────────────────────────────────────┘   │
│                                             │
│                           [Cancel] [Save]   │
└─────────────────────────────────────────────┘
```

## Default Provider Dropdown Menu

```
┌─────────────────────────────────────────────┐
│ Default Provider                            │
│ Google Speech-to-Text ▼                     │
├─────────────────────────────────────────────┤
│ ✓ Google Speech-to-Text                     │
│   Azure Speech Services                     │
│   OpenAI Whisper                            │
│   Custom Endpoint                           │
└─────────────────────────────────────────────┘
```

## Visual Design Notes

### Colors
- **Configured Status:** Primary color (typically blue/green)
- **Not Configured Status:** Error color (typically red/orange)
- **Section Headers:** Primary color, larger font
- **Dividers:** Light grey separators

### Typography
- **Section Title:** Typography.titleLarge, Primary color
- **Setting Title:** Typography.titleMedium
- **Setting Subtitle:** Typography.bodyMedium, OnSurfaceVariant
- **Status Text:** Typography.bodyMedium, Primary (configured) or Error (not configured)
- **Additional Info:** Typography.bodySmall, OnSurfaceVariant

### Interactions
1. **Tap provider card:** Opens configuration dialog
2. **Tap toggle:** Toggles auto-transcribe on/off
3. **Tap default provider:** Opens dropdown menu
4. **Long press (future):** Show provider details/help

### Status Indicators
- ✓ Configured - Green checkmark
- ⚠️ Not configured - Warning/error indicator
- Additional info line shows relevant details (e.g., Azure region, Custom URL)

### Accessibility
- All interactive elements are tappable with minimum 48dp touch target
- Status is conveyed through both color and text
- Dialog fields have clear labels
- Screen reader friendly with content descriptions

## Responsive Layout
- Settings list scrolls vertically
- Dialogs are centered on screen
- On tablets, dialogs may be larger with more padding
- Portrait and landscape orientations supported

## Material 3 Design
- Uses Material 3 components throughout
- Follows Material Design guidelines
- Consistent with existing Shadow Master UI
- Smooth animations for transitions
- Ripple effects on tap
