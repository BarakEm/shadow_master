#!/usr/bin/env python3
"""FastAPI backend for Shadow Master web app."""

import os
import sys
import uuid
import tempfile
import wave
import socket
from pathlib import Path
from contextlib import asynccontextmanager
from typing import Optional

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

from segmenter import load_audio_as_pcm, segment_audio, align_subtitles, align_all_subtitles, extract_segment_pcm
from practice_builder import build_practice_audio
from exporter import change_speed, export_mp3, export_wav
from downloader import download_audio, download_subtitles

# mDNS/Zeroconf for network discovery (optional)
try:
    from zeroconf import ServiceInfo, Zeroconf
    ZEROCONF_AVAILABLE = True
except ImportError:
    ZEROCONF_AVAILABLE = False
    Zeroconf = None
    ServiceInfo = None

# Global state for zeroconf
zeroconf_instance: Optional[Zeroconf] = None
service_info: Optional[ServiceInfo] = None


def get_local_ip():
    """Get the local IP address of this machine."""
    try:
        # Create a socket to determine local IP
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
        s.close()
        return local_ip
    except Exception:
        return "127.0.0.1"


def register_mdns_service(port: int, enable_discovery: bool = False):
    """Register mDNS service for network discovery."""
    global zeroconf_instance, service_info

    if not enable_discovery or not ZEROCONF_AVAILABLE:
        return

    try:
        local_ip = get_local_ip()
        hostname = socket.gethostname()

        service_info = ServiceInfo(
            "_shadowmaster._tcp.local.",
            f"Shadow Master Backend ({hostname})._shadowmaster._tcp.local.",
            addresses=[socket.inet_aton(local_ip)],
            port=port,
            properties={
                "version": "1.0",
                "api": "v1",
                "features": "youtube,stt,tts,processing"
            },
            server=f"{hostname}.local.",
        )

        zeroconf_instance = Zeroconf()
        zeroconf_instance.register_service(service_info)
        print(f"✓ mDNS service registered: {service_info.name}")
        print(f"  Discoverable at: http://{local_ip}:{port}")
    except Exception as e:
        print(f"⚠ mDNS registration failed: {e}")


def unregister_mdns_service():
    """Unregister mDNS service on shutdown."""
    global zeroconf_instance, service_info

    if zeroconf_instance and service_info:
        try:
            zeroconf_instance.unregister_service(service_info)
            zeroconf_instance.close()
            print("✓ mDNS service unregistered")
        except Exception as e:
            print(f"⚠ mDNS unregister failed: {e}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown events for FastAPI."""
    # Startup: Register mDNS if enabled
    enable_discovery = os.environ.get('SHADOWMASTER_DISCOVERY', 'false').lower() == 'true'
    port = int(os.environ.get('SHADOWMASTER_PORT', '8765'))

    if enable_discovery:
        register_mdns_service(port, enable_discovery)

    yield

    # Shutdown: Unregister mDNS
    unregister_mdns_service()


app = FastAPI(title='Shadow Master Backend', lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=['*'],
    allow_methods=['*'],
    allow_headers=['*'],
)

# Storage for downloaded/uploaded audio
WORK_DIR = Path(tempfile.gettempdir()) / 'shadow_master'
WORK_DIR.mkdir(exist_ok=True)

# In-memory registry of audio files
audio_registry: dict[str, dict] = {}


class YouTubeRequest(BaseModel):
    url: str
    start: str | None = None
    end: str | None = None


class ProcessRequest(BaseModel):
    audio_id: str | None = None
    local_file: str | None = None
    preset: str = 'sentences'
    speed: float = 1.0
    playback_repeats: int = 2
    user_repeats: int = 1
    format: str = 'mp3'
    subtitle_lang: str | None = None


@app.get('/api/health')
def health():
    return {'status': 'ok'}


@app.post('/api/youtube/download')
def youtube_download(req: YouTubeRequest):
    audio_id = str(uuid.uuid4())[:8]
    dl_dir = str(WORK_DIR / audio_id)

    try:
        result = download_audio(req.url, dl_dir, start=req.start, end=req.end)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f'Download failed: {e}')

    # Flatten subtitles for JSON response
    subtitles_text = {}
    for lang, entries in result.subtitles.items():
        subtitles_text[lang] = '\n'.join(e['text'] for e in entries)

    audio_registry[audio_id] = {
        'path': result.audio_path,
        'title': result.title,
        'duration': result.duration,
        'subtitles': result.subtitles,
    }

    return {
        'id': audio_id,
        'title': result.title,
        'duration': result.duration,
        'subtitles': subtitles_text,
        'audio_file': result.audio_path,
    }


@app.post('/api/process')
def process_audio(req: ProcessRequest):
    # Resolve audio source
    if req.audio_id and req.audio_id in audio_registry:
        entry = audio_registry[req.audio_id]
        audio_path = entry['path']
        subtitles = entry.get('subtitles', {})
    elif req.local_file and os.path.exists(req.local_file):
        audio_path = req.local_file
        subtitles = {}
    else:
        raise HTTPException(status_code=400, detail='No valid audio source provided')

    # Load and segment
    pcm_data = load_audio_as_pcm(audio_path)
    segments = segment_audio(pcm_data, preset=req.preset)

    # Align subtitles (all languages)
    if subtitles:
        segments = align_all_subtitles(segments, subtitles)

    # Speed change
    if req.speed != 1.0:
        pcm_data = change_speed(pcm_data, req.speed)
        for seg in segments:
            seg.start_ms = int(seg.start_ms / req.speed)
            seg.end_ms = int(seg.end_ms / req.speed)

    # Build practice audio
    practice_pcm = build_practice_audio(
        pcm_data, segments,
        playback_repeats=req.playback_repeats,
        user_repeats=req.user_repeats
    )

    # Export
    output_id = str(uuid.uuid4())[:8]
    if req.format == 'wav':
        output_path = str(WORK_DIR / f'{output_id}_practice.wav')
        export_wav(practice_pcm, output_path)
    else:
        output_path = str(WORK_DIR / f'{output_id}_practice.mp3')
        export_mp3(practice_pcm, output_path)

    filename = os.path.basename(output_path)

    # Store segments and PCM for per-segment serving
    if req.audio_id and req.audio_id in audio_registry:
        audio_registry[req.audio_id]['segments'] = segments
        audio_registry[req.audio_id]['pcm_data'] = pcm_data

    available_languages = list(subtitles.keys()) if subtitles else []

    return {
        'audio_id': req.audio_id,
        'output_file': filename,
        'available_languages': available_languages,
        'segments': [
            {'start': s.start_ms, 'end': s.end_ms, 'text': s.text, 'texts': s.texts}
            for s in segments
        ]
    }


@app.get('/api/download/{filename}')
def download_file(filename: str):
    file_path = WORK_DIR / filename
    if not file_path.exists():
        raise HTTPException(status_code=404, detail='File not found')
    media_type = 'audio/mpeg' if filename.endswith('.mp3') else 'audio/wav'
    return FileResponse(str(file_path), media_type=media_type, filename=filename)


@app.get('/api/subtitles/{audio_id}')
def get_subtitles(audio_id: str):
    if audio_id not in audio_registry:
        raise HTTPException(status_code=404, detail='Audio not found')

    entry = audio_registry[audio_id]
    subtitles = entry.get('subtitles', {})

    return {
        'languages': list(subtitles.keys()),
        'subtitles': {
            lang: [{'start': e['start_ms'], 'end': e['end_ms'], 'text': e['text']}
                   for e in entries]
            for lang, entries in subtitles.items()
        }
    }


@app.get('/api/segment/{audio_id}/{index}')
def get_segment(audio_id: str, index: int):
    if audio_id not in audio_registry:
        raise HTTPException(status_code=404, detail='Audio not found')

    entry = audio_registry[audio_id]
    segments = entry.get('segments')
    pcm_data = entry.get('pcm_data')
    if not segments or not pcm_data:
        raise HTTPException(status_code=400, detail='Audio not processed yet')
    if index < 0 or index >= len(segments):
        raise HTTPException(status_code=404, detail='Segment index out of range')

    # Check cache
    cache_path = WORK_DIR / f'{audio_id}_seg{index}.wav'
    if not cache_path.exists():
        seg = segments[index]
        seg_pcm = extract_segment_pcm(pcm_data, seg.start_ms, seg.end_ms)
        with wave.open(str(cache_path), 'wb') as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(16000)
            wf.writeframes(seg_pcm)

    return FileResponse(str(cache_path), media_type='audio/wav',
                        filename=f'segment_{index}.wav')


@app.post('/api/upload')
async def upload_audio(file: UploadFile = File(...)):
    audio_id = str(uuid.uuid4())[:8]
    ext = os.path.splitext(file.filename or 'audio.wav')[1] or '.wav'
    save_path = str(WORK_DIR / f'{audio_id}{ext}')

    content = await file.read()
    with open(save_path, 'wb') as f:
        f.write(content)

    # Get duration via ffprobe
    duration = 0.0
    try:
        import subprocess
        result = subprocess.run(
            ['ffprobe', '-v', 'quiet', '-show_entries', 'format=duration',
             '-of', 'csv=p=0', save_path],
            capture_output=True, text=True
        )
        duration = float(result.stdout.strip())
    except Exception:
        pass

    audio_registry[audio_id] = {
        'path': save_path,
        'title': file.filename,
        'duration': duration,
        'subtitles': {},
    }

    return {'audio_id': audio_id, 'duration': duration}


class STTRequest(BaseModel):
    audio_id: str | None = None
    provider: str = 'browser'  # 'browser', 'google', 'whisper', etc.
    language: str = 'en-US'


class TTSRequest(BaseModel):
    text: str
    language: str = 'en-US'
    provider: str = 'browser'  # 'browser', 'google', etc.


@app.post('/api/stt')
async def speech_to_text(req: STTRequest):
    """
    Speech-to-text endpoint (placeholder for future integration).
    Currently returns a note that browser-based STT should be used.
    """
    if req.provider == 'browser':
        return {
            'provider': 'browser',
            'note': 'Use Web Speech API in the browser for STT',
            'text': '',
            'confidence': 0.0
        }

    # Placeholder for future providers (Google Cloud Speech, Whisper, etc.)
    raise HTTPException(
        status_code=501,
        detail=f'Provider "{req.provider}" not implemented. Use browser-based STT for now.'
    )


@app.post('/api/tts')
async def text_to_speech(req: TTSRequest):
    """
    Text-to-speech endpoint (placeholder for future integration).
    Currently returns a note that browser-based TTS should be used.
    """
    if req.provider == 'browser':
        return {
            'provider': 'browser',
            'note': 'Use Web Speech API in the browser for TTS',
            'audio_url': ''
        }

    # Placeholder for future providers (Google Cloud TTS, Azure TTS, etc.)
    raise HTTPException(
        status_code=501,
        detail=f'Provider "{req.provider}" not implemented. Use browser-based TTS for now.'
    )


@app.get('/api/discovery/info')
def discovery_info():
    """Return network discovery information."""
    local_ip = get_local_ip()
    port = int(os.environ.get('SHADOWMASTER_PORT', '8765'))
    discovery_enabled = os.environ.get('SHADOWMASTER_DISCOVERY', 'false').lower() == 'true'

    return {
        'ip': local_ip,
        'port': port,
        'discovery_enabled': discovery_enabled,
        'zeroconf_available': ZEROCONF_AVAILABLE,
        'hostname': socket.gethostname(),
        'url': f'http://{local_ip}:{port}'
    }


if __name__ == '__main__':
    import argparse
    import uvicorn

    parser = argparse.ArgumentParser(description='Shadow Master Backend Server')
    parser.add_argument('--port', type=int, default=8765, help='Server port (default: 8765)')
    parser.add_argument('--host', default='0.0.0.0', help='Server host (default: 0.0.0.0)')
    parser.add_argument('--discovery', action='store_true',
                        help='Enable mDNS/Zeroconf network discovery (opt-in)')
    args = parser.parse_args()

    # Set environment variables for lifespan context
    os.environ['SHADOWMASTER_PORT'] = str(args.port)
    if args.discovery:
        os.environ['SHADOWMASTER_DISCOVERY'] = 'true'
        print("✓ Network discovery enabled (mDNS)")
        if not ZEROCONF_AVAILABLE:
            print("⚠ Warning: zeroconf not installed. Install with: pip install zeroconf")
            print("  Discovery will not work without it.")
    else:
        print("ℹ Network discovery disabled (use --discovery to enable)")

    uvicorn.run(app, host=args.host, port=args.port)
