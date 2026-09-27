package com.minimal.launcher

import android.content.ComponentName
import android.content.Context

/**
 * "lite" build: no accessibility service or notification listener, so Google Play Protect
 * doesn't block sideloaded installs. Double-tap lock and the now playing bar are unavailable.
 */
object SystemFeatures {
    const val AVAILABLE = false

    val lockSupported get() = false
    fun lockEnabled(ctx: Context) = false
    fun lock(): Boolean = false

    fun musicAccessEnabled(ctx: Context) = false
    fun musicListener(ctx: Context): ComponentName? = null
}
