package com.shadowmaster.audio.playback

import android.media.*
import android.util.Log
import com.shadowmaster.audio.capture.AudioCaptureManager
import com.shadowmaster.data.model.AudioSegment
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackEngine @Inject constructor() {
    companion object {
        private const val TAG = "PlaybackEngine"
    }

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isPlaying = false
    private var isPaused = false
    private var onPlaybackComplete: (() -> Unit)? = null

    fun play(
        segment: AudioSegment,
        speed: Float = 1.0f,
        repeatCount: Int = 1,
        onComplete: () -> Unit = {}
    ) {
        if (isPlaying) {
            stop()
        }

        this.onPlaybackComplete = onComplete

        playbackJob = scope.launch {
            try {
                isPlaying = true

                for (repeat in 1..repeatCount) {
                    if (!isActive || !isPlaying) break

                    Log.d(TAG, "Playing segment (repeat $repeat/$repeatCount) at ${speed}x speed")
                    playSegmentOnce(segment, speed)

                    if (repeat < repeatCount && isActive && isPlaying) {
                        delay(300)
                    }
                }

                withContext(Dispatchers.Main) {
                    onPlaybackComplete?.invoke()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Playback cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
                withContext(Dispatchers.Main) {
                    onPlaybackComplete?.invoke()
                }
            } finally {
                isPlaying = false
                releaseAudioTrack()
            }
        }
    }

    private suspend fun playSegmentOnce(segment: AudioSegment, speed: Float) {
        val bufferSize = AudioTrack.getMinBufferSize(
            segment.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(segment.sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.let { track ->
            val playbackParams = PlaybackParams().apply {
                setSpeed(speed)
                setPitch(1.0f)
            }
            track.playbackParams = playbackParams
            track.play()

            val byteBuffer = shortArrayToByteArray(segment.samples)
            var offset = 0
            val chunkSize = bufferSize

            while (offset < byteBuffer.size && isPlaying) {
                // Handle pause state
                if (isPaused) {
                    try { track.pause() } catch (_: Exception) {}
                    while (isPaused && isPlaying) {
                        delay(50)
                    }
                    if (!isPlaying) break
                    try { track.play() } catch (_: Exception) {}
                }

                val bytesToWrite = minOf(chunkSize, byteBuffer.size - offset)
                val written = track.write(byteBuffer, offset, bytesToWrite)

                if (written < 0) {
                    Log.e(TAG, "AudioTrack write error: $written")
                    break
                }
                offset += written
            }

            val estimatedDurationMs = (segment.durationMs / speed).toLong()
            delay(estimatedDurationMs + 100)

            track.stop()
            track.release()
        }
    }

    private fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2)
        for (i in shortArray.indices) {
            byteArray[i * 2] = (shortArray[i].toInt() and 0xFF).toByte()
            byteArray[i * 2 + 1] = (shortArray[i].toInt() shr 8 and 0xFF).toByte()
        }
        return byteArray
    }

    fun stop() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        releaseAudioTrack()
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
    }

    fun pause() {
        isPaused = true
        Log.d(TAG, "Playback paused")
    }

    fun resume() {
        isPaused = false
        Log.d(TAG, "Playback resumed")
    }

    fun isPlaying(): Boolean = isPlaying

    fun isPaused(): Boolean = isPaused
}
