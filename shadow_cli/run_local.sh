#!/bin/bash
set -e
cd "$(dirname "$0")"

INPUT="${1:-$HOME/downloads/mp3_clips}"

python3 shadow_master.py local "$INPUT"/*.mp3 \
    --speed 0.8 --playback-repeats 2 --user-repeats 1 \
    --preset sentences --output ~/downloads/practice_output/
