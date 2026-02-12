@echo off
cd /d "%~dp0"

echo Starting Shadow Master backend on http://localhost:8765
echo Press Ctrl+C to stop
python server.py
