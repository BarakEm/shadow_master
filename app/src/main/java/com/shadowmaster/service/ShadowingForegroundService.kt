package com.shadowmaster.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shadowmaster.MainActivity
import com.shadowmaster.R
import com.shadowmaster.core.FeedbackEvent
import com.shadowmaster.core.ShadowingCoordinator
import com.shadowmaster.data.model.ShadowingConfig
import com.shadowmaster.data.model.ShadowingState
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.feedback.AudioFeedbackSystem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class ShadowingForegroundService : Service() {
    companion object {
        private const val TAG = "ShadowingService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "shadowing_channel"

        const val ACTION_START = "com.shadowmaster.action.START"
        const val ACTION_STOP = "com.shadowmaster.action.STOP"
        const val ACTION_SKIP = "com.shadowmaster.action.SKIP"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private var instance: ShadowingForegroundService? = null

        fun isRunning(): Boolean = instance != null

        fun getService(): ShadowingForegroundService? = instance
    }

    @Inject
    lateinit var shadowingCoordinator: ShadowingCoordinator

    @Inject
    lateinit var audioFeedbackSystem: AudioFeedbackSystem

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var mediaProjection: MediaProjection? = null
    private var currentConfig: ShadowingConfig = ShadowingConfig()
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateObserverJob: Job? = null
    private var feedbackObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        audioFeedbackSystem.initialize()
        Log.i(TAG, "Service created")
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startForegroundWithNotification()
                    startCapture(resultCode, resultData)
                } else {
                    Log.e(TAG, "Invalid MediaProjection result")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopSession()
            }
            ACTION_SKIP -> {
                shadowingCoordinator.skip()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification(getString(R.string.notification_text_listening))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Acquire wake lock to keep session alive
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ShadowMaster::ShadowingSession"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // 10 hours max
            }
            Log.i(TAG, "Wake lock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "Wake lock released")
            }
        }
        wakeLock = null
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        mediaProjection?.let { projection ->
            if (shadowingCoordinator.start(projection)) {
                observeState()
                observeFeedback()
                Log.i(TAG, "Shadowing session started")
            } else {
                Log.e(TAG, "Failed to start shadowing session")
                stopSelf()
            }
        } ?: run {
            Log.e(TAG, "Failed to get MediaProjection")
            stopSelf()
        }
    }

    private fun observeState() {
        stateObserverJob = serviceScope.launch {
            shadowingCoordinator.state.collectLatest { state ->
                updateNotification(state)
            }
        }
    }

    private fun observeFeedback() {
        // Observe config changes
        serviceScope.launch {
            settingsRepository.config.collect { config ->
                currentConfig = config
            }
        }

        feedbackObserverJob = serviceScope.launch {
            shadowingCoordinator.feedbackEvents.collect { event ->
                if (!currentConfig.audioFeedbackEnabled) return@collect

                when (event) {
                    is FeedbackEvent.Listening -> audioFeedbackSystem.playListening()
                    is FeedbackEvent.SegmentDetected -> audioFeedbackSystem.playSegmentDetected()
                    is FeedbackEvent.PlaybackStarted -> audioFeedbackSystem.playPlaybackStart()
                    is FeedbackEvent.RecordingStarted -> audioFeedbackSystem.playRecordingStart()
                    is FeedbackEvent.GoodScore -> audioFeedbackSystem.playGoodScore()
                    is FeedbackEvent.BadScore -> audioFeedbackSystem.playBadScore()
                    is FeedbackEvent.Paused -> audioFeedbackSystem.playPaused()
                    else -> { }
                }
            }
        }
    }

    private fun updateNotification(state: ShadowingState) {
        val text = when (state) {
            is ShadowingState.Idle -> getString(R.string.state_idle)
            is ShadowingState.Listening -> getString(R.string.state_listening)
            is ShadowingState.SegmentDetected -> getString(R.string.state_segment_detected)
            is ShadowingState.Playback -> getString(R.string.state_playback)
            is ShadowingState.UserRecording -> getString(R.string.state_user_recording)
            is ShadowingState.Assessment -> getString(R.string.state_assessment)
            is ShadowingState.Feedback -> getString(R.string.state_feedback)
            is ShadowingState.PausedForNavigation -> getString(R.string.state_paused)
        }

        val notification = createNotification(text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ShadowingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val skipIntent = Intent(this, ShadowingForegroundService::class.java).apply {
            action = ACTION_SKIP
        }
        val skipPendingIntent = PendingIntent.getService(
            this,
            2,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Skip", skipPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun stopSession() {
        stateObserverJob?.cancel()
        feedbackObserverJob?.cancel()
        shadowingCoordinator.stop()
        mediaProjection?.stop()
        mediaProjection = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Shadowing session stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        releaseWakeLock()
        serviceScope.cancel()
        shadowingCoordinator.release()
        audioFeedbackSystem.release()
        Log.i(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
