@echo off
cd /d "%~dp0"

if "%~1"=="" (
    echo Usage: %0 ^<youtube-url^> [start] [end]
    echo Example: %0 "https://youtube.com/watch?v=XXX" 0:30 2:00
    exit /b 1
)

set START=%2
if "%START%"=="" set START=0:00

python shadow_master.py youtube "%~1" --start %START% --end %3 --speed 0.8 --playback-repeats 2 --user-repeats 1 --preset sentences --subtitles he,en --output "%USERPROFILE%\downloads\practice_output"
