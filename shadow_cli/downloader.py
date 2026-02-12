"""Download audio and subtitles from YouTube using yt-dlp."""

import subprocess
import json
import re
import os
import tempfile
from dataclasses import dataclass, field


@dataclass
class DownloadResult:
    id: str
    title: str
    duration: float
    audio_path: str
    subtitles: dict[str, list[dict]] = field(default_factory=dict)


def download_audio(url: str, output_dir: str, start: str = None, end: str = None) -> DownloadResult:
    """Download audio from YouTube URL.

    Args:
        url: YouTube URL
        output_dir: Directory to save files
        start: Start time (e.g., "0:30" or "30")
        end: End time (e.g., "2:00" or "120")
    """
    os.makedirs(output_dir, exist_ok=True)

    # Get video info first
    info_cmd = ['yt-dlp', '--dump-json', '--no-download', url]
    result = subprocess.run(info_cmd, capture_output=True, text=True, check=True)
    info = json.loads(result.stdout)

    video_id = info.get('id', 'unknown')
    title = info.get('title', 'Untitled')
    duration = info.get('duration', 0)

    # Sanitize title for filename
    safe_title = re.sub(r'[^\w\s-]', '', title)[:50].strip()
    audio_path = os.path.join(output_dir, f'{safe_title}.wav')

    # Download audio as WAV
    dl_cmd = [
        'yt-dlp',
        '-x', '--audio-format', 'wav',
        '--postprocessor-args', 'ffmpeg:-ar 16000 -ac 1',
        '-o', audio_path,
        url
    ]

    # Add time range via ffmpeg postprocessor if specified
    if start or end:
        pp_args = []
        if start:
            pp_args.extend(['-ss', _parse_time(start)])
        if end:
            pp_args.extend(['-to', _parse_time(end)])
        # Use download_ranges for trimming
        section_opts = []
        if start:
            section_opts.append(f'*{_parse_time(start)}')
        else:
            section_opts.append('*0')
        if end:
            section_opts.append(f'{_parse_time(end)}')
        dl_cmd = [
            'yt-dlp',
            '-x', '--audio-format', 'wav',
            '--postprocessor-args', 'ffmpeg:-ar 16000 -ac 1',
            '--download-sections', f'*{_parse_time(start or "0")}-{_parse_time(end or str(duration))}',
            '-o', audio_path,
            url
        ]

    subprocess.run(dl_cmd, capture_output=True, check=True)

    # Download subtitles
    subtitles = download_subtitles(url, output_dir)

    return DownloadResult(
        id=video_id,
        title=title,
        duration=duration,
        audio_path=audio_path,
        subtitles=subtitles
    )


def download_subtitles(url: str, output_dir: str, langs: str = 'he,en,ar') -> dict[str, list[dict]]:
    """Download and parse subtitles from YouTube.

    Returns dict of language -> list of {start_ms, end_ms, text}
    """
    os.makedirs(output_dir, exist_ok=True)

    # Download auto-generated and manual subtitles
    sub_cmd = [
        'yt-dlp',
        '--write-auto-sub', '--write-sub',
        '--sub-lang', langs,
        '--sub-format', 'srt',
        '--skip-download',
        '-o', os.path.join(output_dir, '%(title).50s.%(ext)s'),
        url
    ]
    subprocess.run(sub_cmd, capture_output=True)

    # Parse any .srt files found
    subtitles = {}
    for f in os.listdir(output_dir):
        if f.endswith('.srt'):
            # Extract language from filename like "title.he.srt"
            parts = f.rsplit('.', 2)
            if len(parts) >= 3:
                lang = parts[-2]
                srt_path = os.path.join(output_dir, f)
                subtitles[lang] = parse_srt(srt_path)

    return subtitles


def parse_srt(srt_path: str) -> list[dict]:
    """Parse SRT file into list of {start_ms, end_ms, text}."""
    entries = []
    with open(srt_path, 'r', encoding='utf-8', errors='replace') as f:
        content = f.read()

    blocks = re.split(r'\n\n+', content.strip())

    for block in blocks:
        lines = block.strip().split('\n')
        if len(lines) < 2:
            continue

        # Find the timestamp line
        time_line = None
        text_lines = []
        for line in lines:
            if '-->' in line:
                time_line = line
            elif time_line is not None:
                # Clean HTML tags
                clean = re.sub(r'<[^>]+>', '', line).strip()
                if clean:
                    text_lines.append(clean)

        if not time_line or not text_lines:
            continue

        match = re.match(
            r'(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})',
            time_line.strip()
        )
        if not match:
            continue

        h1, m1, s1, ms1, h2, m2, s2, ms2 = [int(g) for g in match.groups()]
        start_ms = h1 * 3600000 + m1 * 60000 + s1 * 1000 + ms1
        end_ms = h2 * 3600000 + m2 * 60000 + s2 * 1000 + ms2

        entries.append({
            'start_ms': start_ms,
            'end_ms': end_ms,
            'text': ' '.join(text_lines)
        })

    return entries


def _parse_time(time_str: str) -> str:
    """Parse time string to ffmpeg-compatible format."""
    time_str = str(time_str).strip()
    # Already in HH:MM:SS or M:SS format
    if ':' in time_str:
        return time_str
    # Seconds only
    try:
        secs = float(time_str)
        mins = int(secs // 60)
        secs = secs % 60
        return f'{mins}:{secs:05.2f}'
    except ValueError:
        return time_str
