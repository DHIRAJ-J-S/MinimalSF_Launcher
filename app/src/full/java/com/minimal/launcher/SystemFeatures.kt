package com.minimal.launcher

import android.content.ComponentName
import android.content.Context

/**
 * "full" build: double-tap lock (accessibility service) and now playing bar (notification listener).
 * The "lite" build has a stub with the same API and none of these services.
 */
object SystemFeatures {
    const val AVAILABLE = true

    val lockSupported get() = LockAccessibilityService.isSupported
    fun lockEnabled(ctx: Context) = LockAccessibilityService.isEnabled(ctx)
    fun lock(): Boolean = LockAccessibilityService.instance?.lock() == true

    fun musicAccessEnabled(ctx: Context) = MusicNotificationListener.isEnabled(ctx)
    fun musicListener(ctx: Context): ComponentName? = ComponentName(ctx, MusicNotificationListener::class.java)
}
