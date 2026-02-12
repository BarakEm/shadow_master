@echo off
cd /d "%~dp0"

set INPUT=%1
if "%INPUT%"=="" set INPUT=%USERPROFILE%\downloads\mp3_clips

python shadow_master.py local "%INPUT%\*.mp3" --speed 0.8 --playback-repeats 2 --user-repeats 1 --preset sentences --output "%USERPROFILE%\downloads\practice_output"
