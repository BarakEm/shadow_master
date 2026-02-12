#!/usr/bin/env python3
"""Shadow Master CLI - Language learning practice audio generator."""

import argparse
import os
import sys
import glob as globmod

# Add this directory to path for local imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from segmenter import load_audio_as_pcm, segment_audio, align_subtitles, Segment
from practice_builder import build_practice_audio
from exporter import change_speed, export_mp3, export_wav
from downloader import download_audio


def youtube_mode(args):
    """Download from YouTube and generate practice audio."""
    print(f'Downloading: {args.url}')

    # Download audio + subtitles
    dl_dir = os.path.join(args.output, '_downloads')
    result = download_audio(args.url, dl_dir, start=args.start, end=args.end)
    print(f'Downloaded: {result.title} ({result.duration:.0f}s)')

    if result.subtitles:
        print(f'Subtitles: {", ".join(result.subtitles.keys())}')

    # Load and segment
    pcm_data = load_audio_as_pcm(result.audio_path)
    segments = segment_audio(pcm_data, preset=args.preset)
    print(f'Found {len(segments)} segments')

    # Align subtitles if available
    if args.subtitles and result.subtitles:
        for lang in args.subtitles.split(','):
            lang = lang.strip()
            if lang in result.subtitles:
                segments = align_subtitles(segments, result.subtitles[lang])
                print(f'Aligned subtitles: {lang}')
                break

    # Apply speed change
    if args.speed != 1.0:
        print(f'Applying speed: {args.speed}x')
        pcm_data = change_speed(pcm_data, args.speed)
        # Adjust segment times for speed change
        for seg in segments:
            seg.start_ms = int(seg.start_ms / args.speed)
            seg.end_ms = int(seg.end_ms / args.speed)

    # Build practice audio
    print('Building practice audio...')
    practice_pcm = build_practice_audio(
        pcm_data, segments,
        playback_repeats=args.playback_repeats,
        user_repeats=args.user_repeats
    )

    # Export
    os.makedirs(args.output, exist_ok=True)
    safe_title = result.title[:50].replace('/', '_')
    output_path = os.path.join(args.output, f'{safe_title}_practice')

    if args.format == 'mp3':
        output_path = export_mp3(practice_pcm, output_path + '.mp3')
    else:
        output_path = export_wav(practice_pcm, output_path + '.wav')

    print(f'Output: {output_path}')
    _print_segments(segments)


def local_mode(args):
    """Process local audio files into practice audio."""
    files = []
    for pattern in args.files:
        files.extend(globmod.glob(pattern))

    if not files:
        print('No audio files found.')
        sys.exit(1)

    print(f'Processing {len(files)} file(s)')
    os.makedirs(args.output, exist_ok=True)

    for file_path in sorted(files):
        name = os.path.splitext(os.path.basename(file_path))[0]
        print(f'\n--- {name} ---')

        # Load and segment
        pcm_data = load_audio_as_pcm(file_path)
        segments = segment_audio(pcm_data, preset=args.preset)
        print(f'Found {len(segments)} segments')

        # Apply speed change
        if args.speed != 1.0:
            pcm_data = change_speed(pcm_data, args.speed)
            for seg in segments:
                seg.start_ms = int(seg.start_ms / args.speed)
                seg.end_ms = int(seg.end_ms / args.speed)

        # Build practice audio
        practice_pcm = build_practice_audio(
            pcm_data, segments,
            playback_repeats=args.playback_repeats,
            user_repeats=args.user_repeats
        )

        # Export
        output_path = os.path.join(args.output, f'{name}_practice')
        if args.format == 'mp3':
            output_path = export_mp3(practice_pcm, output_path + '.mp3')
        else:
            output_path = export_wav(practice_pcm, output_path + '.wav')

        print(f'Output: {output_path}')
        _print_segments(segments)


def _print_segments(segments: list[Segment]):
    """Print segment summary."""
    for i, seg in enumerate(segments):
        text = f' "{seg.text}"' if seg.text else ''
        print(f'  [{i+1}] {seg.start_ms/1000:.1f}s - {seg.end_ms/1000:.1f}s ({seg.duration_ms}ms){text}')


def main():
    parser = argparse.ArgumentParser(
        description='Shadow Master - Generate practice audio for language learning'
    )
    subparsers = parser.add_subparsers(dest='mode', required=True)

    # YouTube mode
    yt_parser = subparsers.add_parser('youtube', help='Download from YouTube')
    yt_parser.add_argument('url', help='YouTube URL')
    yt_parser.add_argument('--start', help='Start time (e.g., 0:30)')
    yt_parser.add_argument('--end', help='End time (e.g., 2:00)')
    yt_parser.add_argument('--subtitles', default='he,en', help='Subtitle languages (comma-separated)')
    _add_common_args(yt_parser)

    # Local file mode
    local_parser = subparsers.add_parser('local', help='Process local audio files')
    local_parser.add_argument('files', nargs='+', help='Audio file paths or glob patterns')
    _add_common_args(local_parser)

    # Server mode
    server_parser = subparsers.add_parser('server', help='Start web backend server')
    server_parser.add_argument('--host', default='0.0.0.0', help='Host to bind')
    server_parser.add_argument('--port', type=int, default=8765, help='Port to bind')

    args = parser.parse_args()

    if args.mode == 'youtube':
        youtube_mode(args)
    elif args.mode == 'local':
        local_mode(args)
    elif args.mode == 'server':
        import uvicorn
        from server import app
        uvicorn.run(app, host=args.host, port=args.port)


def _add_common_args(parser):
    parser.add_argument('--speed', type=float, default=0.8, help='Playback speed (default: 0.8)')
    parser.add_argument('--playback-repeats', type=int, default=2, help='Playback repeats (default: 2)')
    parser.add_argument('--user-repeats', type=int, default=1, help='User turn repeats (default: 1)')
    parser.add_argument('--preset', default='sentences',
                        choices=['sentences', 'short', 'long', 'words'],
                        help='Segmentation preset (default: sentences)')
    parser.add_argument('--format', default='mp3', choices=['mp3', 'wav'],
                        help='Output format (default: mp3)')
    parser.add_argument('--output', default=os.path.expanduser('~/downloads/practice_output'),
                        help='Output directory')


if __name__ == '__main__':
    main()
