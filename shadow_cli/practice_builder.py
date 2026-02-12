"""Assemble practice audio: beeps + segments + silence + repeats."""

from segmenter import Segment, extract_segment_pcm, SAMPLE_RATE
from beep_generator import playback_beep, your_turn_beep, done_beep

PAUSE_AFTER_BEEP_MS = 300
PAUSE_AFTER_SEGMENT_MS = 300
PAUSE_AFTER_DONE_MS = 500


def _silence(duration_ms: int) -> bytes:
    """Generate silence of given duration."""
    num_samples = int(SAMPLE_RATE * duration_ms / 1000)
    return b'\x00\x00' * num_samples


def build_practice_audio(
    pcm_data: bytes,
    segments: list[Segment],
    playback_repeats: int = 2,
    user_repeats: int = 1,
    beep_volume: float = 0.8
) -> bytes:
    """Build complete practice audio from segments.

    Structure per segment (from PlaylistExporter.kt):
    1. [880Hz beep] -> 300ms pause -> [segment at speed] -> 300ms pause (x playback_repeats)
    2. [1047Hz beep beep] -> 300ms pause -> [silence = segment duration] -> 300ms pause (x user_repeats)
    3. [660Hz beep] -> 500ms pause -> next segment
    """
    output = bytearray()
    pause_after_beep = _silence(PAUSE_AFTER_BEEP_MS)
    pause_after_segment = _silence(PAUSE_AFTER_SEGMENT_MS)
    pause_after_done = _silence(PAUSE_AFTER_DONE_MS)

    pb_beep = playback_beep(beep_volume)
    yt_beep = your_turn_beep(beep_volume)
    dn_beep = done_beep(beep_volume)

    for seg in segments:
        segment_pcm = extract_segment_pcm(pcm_data, seg.start_ms, seg.end_ms)
        segment_silence = _silence(seg.duration_ms)

        # Playback phase
        for _ in range(playback_repeats):
            output.extend(pb_beep)
            output.extend(pause_after_beep)
            output.extend(segment_pcm)
            output.extend(pause_after_segment)

        # User turn phase
        for _ in range(user_repeats):
            output.extend(yt_beep)
            output.extend(pause_after_beep)
            output.extend(segment_silence)
            output.extend(pause_after_segment)

        # Done
        output.extend(dn_beep)
        output.extend(pause_after_done)

    return bytes(output)
