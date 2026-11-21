package com.minimal.launcher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate

/**
 * Dark (default), light, or following the phone (settings > look > theme). The XML colours have a
 * light set in values-notnight; code-drawn colours (dialogs, highlights, the wallpaper dim) read [light].
 */
object Theme {
    /** Whether the light theme is showing. Updated by every activity as it starts or its config changes. */
    @Volatile var light = false
        private set

    private fun mode(c: Context) = when (Prefs.theme(c)) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        else -> AppCompatDelegate.MODE_NIGHT_YES
    }

    /** Before super.onCreate: picks the theme for the activity about to be drawn. */
    fun init(c: Context) {
        Str.app = c.applicationContext
        val m = mode(c)
        if (AppCompatDelegate.getDefaultNightMode() != m) AppCompatDelegate.setDefaultNightMode(m)
    }

    /** After super.onCreate, and on a configuration change: reads which one is in effect. */
    fun update(a: Activity) {
        light = a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
    }

    /** A theme change in settings: AppCompat redraws the open screens. */
    fun apply(a: Activity) { init(a); update(a) }

    val fg get() = if (light) Color.BLACK else Color.WHITE
    val bg get() = if (light) Color.WHITE else Color.BLACK
}
