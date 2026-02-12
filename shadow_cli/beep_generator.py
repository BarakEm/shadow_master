"""Generate beep tones for practice audio."""

import struct
import math
import wave
import io

SAMPLE_RATE = 16000
SAMPLE_WIDTH = 2  # 16-bit

# Beep frequencies from Android app
FREQ_PLAYBACK = 880    # playback start
FREQ_YOUR_TURN = 1047  # your turn (double beep)
FREQ_DONE = 660        # segment done

BEEP_DURATION_MS = 150
DOUBLE_BEEP_GAP_MS = 100
FADE_PERCENT = 0.10  # 10% fade-in/fade-out


def _generate_tone(frequency: float, duration_ms: int, volume: float = 0.8) -> bytes:
    """Generate a sine wave tone with fade-in/fade-out envelope."""
    num_samples = int(SAMPLE_RATE * duration_ms / 1000)
    fade_samples = int(num_samples * FADE_PERCENT)
    samples = []

    for i in range(num_samples):
        t = i / SAMPLE_RATE
        value = math.sin(2.0 * math.pi * frequency * t)

        # Apply envelope
        if i < fade_samples:
            value *= i / fade_samples
        elif i > num_samples - fade_samples:
            value *= (num_samples - i) / fade_samples

        value *= volume
        samples.append(int(value * 32767))

    return struct.pack(f'<{len(samples)}h', *samples)


def _silence(duration_ms: int) -> bytes:
    """Generate silence."""
    num_samples = int(SAMPLE_RATE * duration_ms / 1000)
    return b'\x00\x00' * num_samples


def playback_beep(volume: float = 0.8) -> bytes:
    """Single 880Hz beep signaling playback start."""
    return _generate_tone(FREQ_PLAYBACK, BEEP_DURATION_MS, volume)


def your_turn_beep(volume: float = 0.8) -> bytes:
    """Double 1047Hz beep signaling user's turn."""
    beep = _generate_tone(FREQ_YOUR_TURN, BEEP_DURATION_MS, volume)
    gap = _silence(DOUBLE_BEEP_GAP_MS)
    return beep + gap + beep


def done_beep(volume: float = 0.8) -> bytes:
    """Single 660Hz beep signaling segment done."""
    return _generate_tone(FREQ_DONE, BEEP_DURATION_MS, volume)


def pcm_to_wav_bytes(pcm_data: bytes) -> bytes:
    """Wrap raw PCM data in a WAV container."""
    buf = io.BytesIO()
    with wave.open(buf, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(SAMPLE_WIDTH)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(pcm_data)
    return buf.getvalue()
