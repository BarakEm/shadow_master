"""Export practice audio: speed change + MP3/WAV via ffmpeg."""

import subprocess
import tempfile
import os
import wave
import io

SAMPLE_RATE = 16000


def change_speed(pcm_data: bytes, speed: float) -> bytes:
    """Change playback speed of PCM audio using ffmpeg atempo filter."""
    if speed == 1.0:
        return pcm_data

    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as in_tmp, \
         tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as out_tmp:
        in_path = in_tmp.name
        out_path = out_tmp.name

    try:
        # Write PCM as WAV
        with wave.open(in_path, 'wb') as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(SAMPLE_RATE)
            wf.writeframes(pcm_data)

        # atempo filter supports 0.5-2.0; chain for wider range
        filters = _build_atempo_chain(speed)

        subprocess.run([
            'ffmpeg', '-y', '-i', in_path,
            '-filter:a', filters,
            '-ar', str(SAMPLE_RATE), '-ac', '1',
            out_path
        ], capture_output=True, check=True)

        # Read back as PCM
        with wave.open(out_path, 'rb') as wf:
            return wf.readframes(wf.getnframes())
    finally:
        for p in (in_path, out_path):
            if os.path.exists(p):
                os.unlink(p)


def _build_atempo_chain(speed: float) -> str:
    """Build chained atempo filters for speeds outside 0.5-2.0 range."""
    filters = []
    remaining = speed
    while remaining > 2.0:
        filters.append('atempo=2.0')
        remaining /= 2.0
    while remaining < 0.5:
        filters.append('atempo=0.5')
        remaining /= 0.5
    filters.append(f'atempo={remaining:.4f}')
    return ','.join(filters)


def export_mp3(pcm_data: bytes, output_path: str, bitrate: str = '192k') -> str:
    """Export PCM data as MP3."""
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        tmp_path = tmp.name

    try:
        with wave.open(tmp_path, 'wb') as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(SAMPLE_RATE)
            wf.writeframes(pcm_data)

        subprocess.run([
            'ffmpeg', '-y', '-i', tmp_path,
            '-b:a', bitrate, output_path
        ], capture_output=True, check=True)

        return output_path
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


def export_wav(pcm_data: bytes, output_path: str) -> str:
    """Export PCM data as WAV."""
    with wave.open(output_path, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm_data)
    return output_path
