package com.shadowmaster.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shadowmaster.MainActivity
import com.shadowmaster.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Foreground service that captures audio from other apps and saves to a WAV file.
 */
@AndroidEntryPoint
class AudioCaptureService : Service() {

    companion object {
        private const val TAG = "AudioCaptureService"
        const val ACTION_START = "com.shadowmaster.action.START_CAPTURE"
        const val ACTION_STOP = "com.shadowmaster.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "audio_capture_channel"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Shared state for UI
        private val _captureState = MutableStateFlow(CaptureServiceState.IDLE)
        val captureState: StateFlow<CaptureServiceState> = _captureState.asStateFlow()

        var capturedFile: File? = null
            private set
    }

    enum class CaptureServiceState {
        IDLE,
        CAPTURING,
        STOPPED,
        ERROR
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var outputFile: File? = null
    private var outputStream: FileOutputStream? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultData != null) {
                    startCapturing(resultCode, resultData)
                } else {
                    Log.e(TAG, "No result data provided")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopCapturing()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCapturing(resultCode: Int, resultData: Intent) {
        try {
            // Start foreground service with notification
            startForeground(
                NOTIFICATION_ID,
                createNotification("Preparing to capture audio..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

            // Get media projection
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            if (mediaProjection == null) {
                Log.e(TAG, "Failed to get media projection")
                _captureState.value = CaptureServiceState.ERROR
                stopSelf()
                return
            }

            // Register callback for projection stop
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "Media projection stopped")
                    stopCapturing()
                }
            }, null)

            // Create output file
            val captureDir = File(cacheDir, "captured_audio")
            captureDir.mkdirs()
            outputFile = File(captureDir, "capture_${System.currentTimeMillis()}.wav")
            capturedFile = outputFile

            // Configure audio playback capture
            val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AUDIO_FORMAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .build()

            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                _captureState.value = CaptureServiceState.ERROR
                stopSelf()
                return
            }

            // Start recording
            audioRecord?.startRecording()
            isRecording = true
            _captureState.value = CaptureServiceState.CAPTURING

            // Update notification
            updateNotification("Recording audio...")

            // Start capture loop
            scope.launch {
                writeWavHeader()
                captureLoop()
            }

            Log.i(TAG, "Audio capture started")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture", e)
            _captureState.value = CaptureServiceState.ERROR
            stopSelf()
        }
    }

    private suspend fun writeWavHeader() = withContext(Dispatchers.IO) {
        try {
            outputStream = FileOutputStream(outputFile)
            // Write placeholder WAV header (will update later with actual size)
            val header = ByteArray(44)
            // RIFF header
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            // File size - 8 (placeholder)
            header[4] = 0
            header[5] = 0
            header[6] = 0
            header[7] = 0
            // WAVE
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            // fmt chunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            // Subchunk1 size (16 for PCM)
            header[16] = 16
            header[17] = 0
            header[18] = 0
            header[19] = 0
            // Audio format (1 = PCM)
            header[20] = 1
            header[21] = 0
            // Num channels (1 = mono)
            header[22] = 1
            header[23] = 0
            // Sample rate (16000)
            header[24] = (SAMPLE_RATE and 0xff).toByte()
            header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
            header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
            header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
            // Byte rate (sample rate * channels * bits per sample / 8)
            val byteRate = SAMPLE_RATE * 1 * 16 / 8
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            // Block align (channels * bits per sample / 8)
            header[32] = 2
            header[33] = 0
            // Bits per sample
            header[34] = 16
            header[35] = 0
            // data chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            // Data size (placeholder)
            header[40] = 0
            header[41] = 0
            header[42] = 0
            header[43] = 0

            outputStream?.write(header)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write WAV header", e)
        }
    }

    private suspend fun captureLoop() = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val buffer = ByteArray(bufferSize)

        while (isRecording) {
            val read = audioRecord?.read(buffer, 0, bufferSize) ?: -1
            if (read > 0) {
                outputStream?.write(buffer, 0, read)
            } else if (read < 0) {
                Log.e(TAG, "AudioRecord read error: $read")
                break
            }
        }
    }

    private fun stopCapturing() {
        isRecording = false

        scope.launch {
            // Stop recording
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            // Close output stream
            outputStream?.close()
            outputStream = null

            // Update WAV header with actual size
            updateWavHeader()

            // Stop media projection
            mediaProjection?.stop()
            mediaProjection = null

            Log.i(TAG, "Audio capture stopped, file: ${outputFile?.absolutePath}")

            _captureState.value = CaptureServiceState.STOPPED

            // Stop the service
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateWavHeader() {
        try {
            val file = outputFile ?: return
            if (!file.exists()) return

            val fileSize = file.length()
            val dataSize = fileSize - 44 // Subtract header size

            RandomAccessFile(file, "rw").use { raf ->
                // Update RIFF chunk size (file size - 8)
                raf.seek(4)
                raf.write(((fileSize - 8) and 0xff).toInt())
                raf.write((((fileSize - 8) shr 8) and 0xff).toInt())
                raf.write((((fileSize - 8) shr 16) and 0xff).toInt())
                raf.write((((fileSize - 8) shr 24) and 0xff).toInt())

                // Update data chunk size
                raf.seek(40)
                raf.write((dataSize and 0xff).toInt())
                raf.write(((dataSize shr 8) and 0xff).toInt())
                raf.write(((dataSize shr 16) and 0xff).toInt())
                raf.write(((dataSize shr 24) and 0xff).toInt())
            }

            Log.i(TAG, "WAV header updated, file size: $fileSize, data size: $dataSize")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update WAV header", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when audio is being captured"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, AudioCaptureService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shadow Master")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        isRecording = false
        audioRecord?.release()
        mediaProjection?.stop()
        _captureState.value = CaptureServiceState.IDLE
    }
}
