package com.shadowmaster

import android.app.Application
import android.util.Log
import com.shadowmaster.crash.CrashReporter
import com.shadowmaster.data.repository.SettingsRepository
import com.shadowmaster.transcription.LocalModelProvider
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ShadowMasterApplication : Application() {
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize(this)
        
        // Initialize transcription models on startup
        initializeTranscriptionModels()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Cancel coroutine scope to prevent resource leaks
        // Note: onTerminate() is rarely called in production (only in emulator)
        applicationScope.cancel()
    }
    
    /**
     * Auto-detect and restore Vosk model path if not configured.
     * This ensures models persist across app reinstalls when backed up.
     */
    private fun initializeTranscriptionModels() {
        applicationScope.launch {
            try {
                val config = settingsRepository.config.first()
                val transcriptionConfig = config.transcription
                
                // Check if local model path is not set but models exist
                if (transcriptionConfig.localModelPath.isNullOrBlank()) {
                    val detectedPath = LocalModelProvider.autoDetectModel(this@ShadowMasterApplication)
                    if (detectedPath != null) {
                        Log.i(TAG, "Auto-detected Vosk model at: $detectedPath")
                        
                        // Determine model name from path
                        // Check BASE_MODEL_NAME first to avoid false matches with paths containing 'base'
                        val modelName = when {
                            detectedPath.contains(LocalModelProvider.BASE_MODEL_NAME) -> 
                                LocalModelProvider.BASE_MODEL_NAME
                            detectedPath.contains(LocalModelProvider.TINY_MODEL_NAME) -> 
                                LocalModelProvider.TINY_MODEL_NAME
                            else -> null
                        }
                        
                        // Update settings with detected model
                        settingsRepository.updateTranscriptionLocalModelPath(detectedPath)
                        if (modelName != null) {
                            settingsRepository.updateTranscriptionLocalModelName(modelName)
                            Log.i(TAG, "Restored Vosk model settings: $modelName at $detectedPath")
                        }
                    } else {
                        Log.d(TAG, "No Vosk models found on startup - user will need to download")
                    }
                } else {
                    Log.d(TAG, "Vosk model path already configured: ${transcriptionConfig.localModelPath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing transcription models", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "ShadowMasterApp"
    }
}
