package com.shadowmaster

import android.app.Application
import com.shadowmaster.crash.CrashReporter
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ShadowMasterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.initialize(this)
    }
}
