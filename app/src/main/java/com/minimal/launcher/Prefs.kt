package com.minimal.launcher

import android.content.Context

object Prefs {
    private const val PREF = "launcher_prefs"
    private const val MUSIC_BAR = "show_music"
    private const val AUTO_DELAY = "auto_delay"
    private const val USE_24H = "use_24h"
    private const val FIRST_LAUNCH_DONE = "first_launch_done"
    private const val DEFAULT_PROMPT_DISMISSED = "default_prompt_dismissed"

    fun showMusic(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(MUSIC_BAR, false)

    fun setShowMusic(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(MUSIC_BAR, v).apply()
    }

    fun autoDelay(ctx: Context): Long =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(AUTO_DELAY, 600L)

    fun setAutoDelay(ctx: Context, ms: Long) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong(AUTO_DELAY, ms).apply()
    }

    fun use24hClock(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(USE_24H, false)

    fun setUse24hClock(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(USE_24H, v).apply()
    }

    fun isFirstLaunch(ctx: Context): Boolean =
        !ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(FIRST_LAUNCH_DONE, false)

    fun setFirstLaunchDone(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(FIRST_LAUNCH_DONE, true).apply()
    }

    fun isDefaultPromptDismissed(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(DEFAULT_PROMPT_DISMISSED, false)

    fun setDefaultPromptDismissed(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(DEFAULT_PROMPT_DISMISSED, true).apply()
    }
}
