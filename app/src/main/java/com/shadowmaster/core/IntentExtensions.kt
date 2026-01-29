package com.shadowmaster.core

import android.content.Intent
import android.os.Build
import android.os.Parcelable

/**
 * Compatibility extension for getting Parcelable extras that works across API levels.
 * Uses the new type-safe API on Android 13+ and the deprecated API on older versions.
 */
inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
}
