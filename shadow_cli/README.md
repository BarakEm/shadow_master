# Shadow Master CLI + Web Backend

Python backend that adds YouTube download, subtitle extraction, VAD segmentation, speed change, beep generation, and MP3 export to Shadow Master.

## Quick Start

### WSL / Linux Setup

```bash
# 1. Install system dependencies
sudo apt install ffmpeg python3-pip

# 2. Install yt-dlp
pip3 install yt-dlp
# or: sudo apt install yt-dlp

# 3. Install Python dependencies
cd shadow_cli
pip3 install -r requirements.txt

# 4. Start the backend server
python3 server.py
```

The server starts on `http://localhost:8765`.

### Windows Browser + WSL Backend

WSL2 automatically forwards `localhost` ports to Windows, so:

1. Start the server in WSL: `python3 server.py`
2. Open `http://localhost:8765/api/health` in Windows browser to verify
3. Open the web app - it auto-detects the backend and shows the YouTube Import card

**If localhost doesn't work**, find your WSL IP:
```bash
hostname -I
```
Then in the web app Settings, change Backend URL to `http://<wsl-ip>:8765`.

### CLI Usage (no browser needed)

```bash
# YouTube → practice MP3
python3 shadow_master.py youtube "https://youtube.com/watch?v=XXX" \
  --start 0:30 --end 2:00 --speed 0.8 --preset sentences

# Local files → practice MP3
python3 shadow_master.py local ~/downloads/mp3_clips/*.mp3 \
  --speed 0.8 --preset sentences

# One-click scripts
./run_youtube.sh "https://youtube.com/watch?v=XXX" 0:30 2:00
./run_local.sh ~/downloads/mp3_clips
```

### Options

| Option | Default | Description |
|--------|---------|-------------|
| `--speed` | 0.8 | Playback speed (0.5 - 2.0) |
| `--preset` | sentences | Segmentation: `sentences`, `short`, `long`, `words` |
| `--playback-repeats` | 2 | Times to play each segment |
| `--user-repeats` | 1 | Silence gaps for user practice |
| `--format` | mp3 | Output: `mp3` or `wav` |
| `--output` | ~/downloads/practice_output | Output directory |
| `--subtitles` | he,en | Subtitle languages (YouTube mode) |

## Architecture

```
Windows Browser                WSL / Linux
┌──────────────────┐          ┌──────────────────┐
│  Web app (docs/) │◄────────►│  Python backend   │
│  GitHub Pages or │  REST    │  FastAPI :8765    │
│  local file      │  API     │  yt-dlp + ffmpeg  │
└──────────────────┘          │  webrtcvad        │
                              └──────────────────┘
```

The web app works standalone (file import + browser VAD). The backend unlocks YouTube import, subtitle extraction, and MP3 export.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Backend detection |
| POST | `/api/youtube/download` | Download audio + subtitles |
| POST | `/api/process` | Segment + build practice audio |
| GET | `/api/download/{file}` | Download generated file |
| GET | `/api/subtitles/{id}` | Get subtitle data |
| POST | `/api/upload` | Upload local audio file |

## Practice Audio Structure

For each segment:
1. **880Hz beep** → 300ms pause → segment audio → 300ms pause (x playback repeats)
2. **1047Hz double beep** → 300ms pause → silence (= segment duration) → 300ms pause (x user repeats)
3. **660Hz beep** → 500ms pause → next segment

## Dependencies

- Python 3.10+
- ffmpeg (system)
- yt-dlp (system or pip)
- fastapi, uvicorn, webrtcvad-wheels, pydub, numpy, python-multipart (pip)
