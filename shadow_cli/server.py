#!/usr/bin/env python3
"""FastAPI backend for Shadow Master web app."""

import os
import sys
import uuid
import tempfile
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

from segmenter import load_audio_as_pcm, segment_audio, align_subtitles
from practice_builder import build_practice_audio
from exporter import change_speed, export_mp3, export_wav
from downloader import download_audio, download_subtitles

app = FastAPI(title='Shadow Master Backend')

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

    # Align subtitles
    if req.subtitle_lang and req.subtitle_lang in subtitles:
        segments = align_subtitles(segments, subtitles[req.subtitle_lang])

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

    return {
        'output_file': filename,
        'segments': [
            {'start': s.start_ms, 'end': s.end_ms, 'text': s.text}
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


if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host='0.0.0.0', port=8765)
