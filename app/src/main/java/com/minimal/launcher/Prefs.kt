package com.minimal.launcher

import android.content.Context

object Prefs {
    private const val P = "launcher_prefs"

    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun showMusic(c: Context) = p(c).getBoolean("show_music", false)
    fun setShowMusic(c: Context, v: Boolean) { p(c).edit().putBoolean("show_music", v).apply() }

    fun autoDelay(c: Context) = p(c).getLong("auto_delay", 200L)
    fun setAutoDelay(c: Context, v: Long) { p(c).edit().putLong("auto_delay", v).apply() }

    fun use24hClock(c: Context) = p(c).getBoolean("use_24h", false)
    fun setUse24hClock(c: Context, v: Boolean) { p(c).edit().putBoolean("use_24h", v).apply() }

    fun isFirstLaunch(c: Context) = !p(c).getBoolean("first_launch_done", false)
    fun setFirstLaunchDone(c: Context) { p(c).edit().putBoolean("first_launch_done", true).apply() }

    fun isDefaultPromptDismissed(c: Context) = p(c).getBoolean("default_prompt_dismissed", false)
    fun setDefaultPromptDismissed(c: Context) { p(c).edit().putBoolean("default_prompt_dismissed", true).apply() }

    fun searchAtBottom(c: Context) = p(c).getBoolean("search_bottom", false)
    fun setSearchAtBottom(c: Context, v: Boolean) { p(c).edit().putBoolean("search_bottom", v).apply() }

    // Gesture: double tap — "lock" (default), or package name to launch app
    fun doubleTapAction(c: Context): String = p(c).getString("double_tap_action", "lock") ?: "lock"
    fun setDoubleTapAction(c: Context, v: String) { p(c).edit().putString("double_tap_action", v).apply() }

    // Gesture: long press — "" (empty/disabled, default), or package name to launch app
    fun longPressAction(c: Context): String = p(c).getString("long_press_action", "") ?: ""
    fun setLongPressAction(c: Context, v: String) { p(c).edit().putString("long_press_action", v).apply() }

    // Tips shown flags
    fun homeTipShown(c: Context) = p(c).getBoolean("home_tip_shown", false)
    fun setHomeTipShown(c: Context) { p(c).edit().putBoolean("home_tip_shown", true).apply() }

    fun musicTipShown(c: Context) = p(c).getBoolean("music_tip_shown", false)
    fun setMusicTipShown(c: Context) { p(c).edit().putBoolean("music_tip_shown", true).apply() }

    // Lock method: "admin" (default) or "accessibility"
    fun lockMethod(c: Context): String = p(c).getString("lock_method", "") ?: ""
    fun setLockMethod(c: Context, v: String) { p(c).edit().putString("lock_method", v).apply() }
}
