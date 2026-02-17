"""VAD-based speech segmentation using webrtcvad."""

import struct
import subprocess
import tempfile
import os
from dataclasses import dataclass, field

import webrtcvad

SAMPLE_RATE = 16000
FRAME_DURATION_MS = 30  # webrtcvad supports 10, 20, or 30ms frames

# Segmentation presets from Android app
PRESETS = {
    'sentences': {'min_ms': 500, 'max_ms': 8000, 'silence_ms': 700, 'pre_buffer_ms': 200},
    'short':     {'min_ms': 500, 'max_ms': 3000, 'silence_ms': 500, 'pre_buffer_ms': 200},
    'long':      {'min_ms': 1000, 'max_ms': 12000, 'silence_ms': 1000, 'pre_buffer_ms': 300},
    'words':     {'min_ms': 300, 'max_ms': 2000, 'silence_ms': 400, 'pre_buffer_ms': 150},
}


@dataclass
class Segment:
    start_ms: int
    end_ms: int
    text: str = ''
    texts: dict[str, str] = field(default_factory=dict)

    @property
    def duration_ms(self) -> int:
        return self.end_ms - self.start_ms


def load_audio_as_pcm(file_path: str) -> bytes:
    """Convert any audio file to 16kHz mono 16-bit PCM using ffmpeg."""
    with tempfile.NamedTemporaryFile(suffix='.raw', delete=False) as tmp:
        tmp_path = tmp.name

    try:
        subprocess.run([
            'ffmpeg', '-y', '-i', file_path,
            '-ar', str(SAMPLE_RATE), '-ac', '1', '-f', 's16le',
            tmp_path
        ], capture_output=True, check=True)

        with open(tmp_path, 'rb') as f:
            return f.read()
    finally:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)


def segment_audio(pcm_data: bytes, preset: str = 'sentences', aggressiveness: int = 2) -> list[Segment]:
    """Segment PCM audio using webrtcvad.

    Args:
        pcm_data: Raw 16kHz mono 16-bit PCM data
        preset: One of 'sentences', 'short', 'long', 'words'
        aggressiveness: VAD aggressiveness (0-3), higher = more aggressive filtering
    """
    params = PRESETS[preset]
    min_ms = params['min_ms']
    max_ms = params['max_ms']
    silence_ms = params['silence_ms']
    pre_buffer_ms = params['pre_buffer_ms']

    vad = webrtcvad.Vad(aggressiveness)

    frame_size = int(SAMPLE_RATE * FRAME_DURATION_MS / 1000) * 2  # bytes per frame
    frames = []
    for i in range(0, len(pcm_data) - frame_size + 1, frame_size):
        frame = pcm_data[i:i + frame_size]
        if len(frame) == frame_size:
            frames.append(frame)

    # Classify each frame as speech or silence
    is_speech = []
    for frame in frames:
        try:
            is_speech.append(vad.is_speech(frame, SAMPLE_RATE))
        except Exception:
            is_speech.append(False)

    # Find speech regions
    silence_frames = int(silence_ms / FRAME_DURATION_MS)
    pre_buffer_frames = int(pre_buffer_ms / FRAME_DURATION_MS)

    segments = []
    in_speech = False
    speech_start = 0
    silence_count = 0

    for i, speech in enumerate(is_speech):
        if speech:
            if not in_speech:
                speech_start = max(0, i - pre_buffer_frames)
                in_speech = True
            silence_count = 0
        else:
            if in_speech:
                silence_count += 1
                current_duration_ms = (i - speech_start) * FRAME_DURATION_MS

                if silence_count >= silence_frames and current_duration_ms >= min_ms:
                    # End segment
                    end_frame = i - silence_count + 1
                    segments.append(Segment(
                        start_ms=speech_start * FRAME_DURATION_MS,
                        end_ms=end_frame * FRAME_DURATION_MS
                    ))
                    in_speech = False
                    silence_count = 0
                elif current_duration_ms >= max_ms:
                    # Force split at max duration
                    segments.append(Segment(
                        start_ms=speech_start * FRAME_DURATION_MS,
                        end_ms=i * FRAME_DURATION_MS
                    ))
                    in_speech = False
                    silence_count = 0

    # Handle trailing speech
    if in_speech:
        end_ms = len(is_speech) * FRAME_DURATION_MS
        duration_ms = end_ms - speech_start * FRAME_DURATION_MS
        if duration_ms >= min_ms:
            segments.append(Segment(
                start_ms=speech_start * FRAME_DURATION_MS,
                end_ms=end_ms
            ))

    return segments


def extract_segment_pcm(pcm_data: bytes, start_ms: int, end_ms: int) -> bytes:
    """Extract a segment from PCM data by time range."""
    bytes_per_ms = SAMPLE_RATE * 2 // 1000
    start_byte = start_ms * bytes_per_ms
    end_byte = end_ms * bytes_per_ms
    return pcm_data[start_byte:end_byte]


def align_subtitles(segments: list[Segment], subtitles: list[dict]) -> list[Segment]:
    """Align subtitle text to audio segments by timestamp overlap."""
    for seg in segments:
        best_overlap = 0
        best_text = ''
        for sub in subtitles:
            overlap_start = max(seg.start_ms, sub['start_ms'])
            overlap_end = min(seg.end_ms, sub['end_ms'])
            overlap = max(0, overlap_end - overlap_start)
            if overlap > best_overlap:
                best_overlap = overlap
                best_text = sub['text']
        if best_overlap > 0:
            seg.text = best_text
    return segments


def align_all_subtitles(segments: list[Segment], subtitles: dict[str, list[dict]]) -> list[Segment]:
    """Align all available subtitle languages to audio segments."""
    for lang, subs in subtitles.items():
        for seg in segments:
            best_overlap = 0
            best_text = ''
            for sub in subs:
                overlap_start = max(seg.start_ms, sub['start_ms'])
                overlap_end = min(seg.end_ms, sub['end_ms'])
                overlap = max(0, overlap_end - overlap_start)
                if overlap > best_overlap:
                    best_overlap = overlap
                    best_text = sub['text']
            if best_overlap > 0:
                seg.texts[lang] = best_text
    # Set text from first available language for backward compat
    if subtitles:
        first_lang = next(iter(subtitles))
        for seg in segments:
            if first_lang in seg.texts:
                seg.text = seg.texts[first_lang]
    return segments
