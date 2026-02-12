#!/bin/bash
set -e
cd "$(dirname "$0")"

if [ -z "$1" ]; then
    echo "Usage: $0 <youtube-url> [start] [end]"
    echo "Example: $0 'https://youtube.com/watch?v=XXX' 0:30 2:00"
    exit 1
fi

python3 shadow_master.py youtube "$1" \
    --start "${2:-0:00}" --end "${3:-}" \
    --speed 0.8 --playback-repeats 2 --user-repeats 1 \
    --preset sentences --subtitles he,en \
    --output ~/downloads/practice_output/
