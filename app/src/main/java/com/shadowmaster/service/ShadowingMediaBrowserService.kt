package com.shadowmaster.service

import android.content.Intent
import android.os.Bundle
import android.service.media.MediaBrowserService
import androidx.media3.session.MediaSession

/**
 * MediaBrowserService for Android Auto integration.
 * Allows the app to appear in Android Auto's app launcher.
 */
class ShadowingMediaBrowserService : MediaBrowserService() {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        // Return empty root - we don't provide browsable content
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserService.MediaItem>>
    ) {
        // No browsable content - Shadow Master is controlled via UI
        result.sendResult(mutableListOf())
    }
}
