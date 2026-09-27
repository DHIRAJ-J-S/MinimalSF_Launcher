package com.minimal.launcher

import android.content.Context
import org.json.JSONObject

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

    fun doubleTapAction(c: Context): String = p(c).getString("double_tap_action", "lock") ?: "lock"
    fun setDoubleTapAction(c: Context, v: String) { p(c).edit().putString("double_tap_action", v).apply() }

    fun longPressAction(c: Context): String = p(c).getString("long_press_action", "") ?: ""
    fun setLongPressAction(c: Context, v: String) { p(c).edit().putString("long_press_action", v).apply() }

    fun homeTipShown(c: Context) = p(c).getBoolean("home_tip_shown", false)
    fun setHomeTipShown(c: Context) { p(c).edit().putBoolean("home_tip_shown", true).apply() }

    fun musicTipShown(c: Context) = p(c).getBoolean("music_tip_shown", false)
    fun setMusicTipShown(c: Context) { p(c).edit().putBoolean("music_tip_shown", true).apply() }

    fun lockMethod(c: Context): String = p(c).getString("lock_method", "") ?: ""
    fun setLockMethod(c: Context, v: String) { p(c).edit().putString("lock_method", v).apply() }

    // --- Custom keywords: package -> keyword ---
    private const val KEYWORDS_KEY = "app_keywords"

    fun getKeywords(c: Context): Map<String, String> {
        val json = p(c).getString(KEYWORDS_KEY, "{}") ?: "{}"
        val obj = JSONObject(json)
        val map = mutableMapOf<String, String>()
        obj.keys().forEach { map[it] = obj.getString(it) }
        return map
    }

    fun setKeyword(c: Context, pkg: String, keyword: String) {
        val map = getKeywords(c).toMutableMap()
        if (keyword.isEmpty()) map.remove(pkg) else map[pkg] = keyword.lowercase()
        p(c).edit().putString(KEYWORDS_KEY, JSONObject(map as Map<*, *>).toString()).apply()
    }

    // --- Hidden apps: set of package names ---
    private const val HIDDEN_KEY = "hidden_apps"

    fun getHiddenApps(c: Context): Set<String> {
        return p(c).getStringSet(HIDDEN_KEY, emptySet()) ?: emptySet()
    }

    fun setAppHidden(c: Context, pkg: String, hidden: Boolean) {
        val set = getHiddenApps(c).toMutableSet()
        if (hidden) set.add(pkg) else set.remove(pkg)
        p(c).edit().putStringSet(HIDDEN_KEY, set).apply()
    }

    // --- Font ---
    // "mono" (default), "clean", "custom"
    fun fontStyle(c: Context): String = p(c).getString("font_style", "mono") ?: "mono"
    fun setFontStyle(c: Context, v: String) { p(c).edit().putString("font_style", v).apply() }

    fun customFontPath(c: Context): String = p(c).getString("custom_font_path", "") ?: ""
    fun setCustomFontPath(c: Context, v: String) { p(c).edit().putString("custom_font_path", v).apply() }

    // "small", "default", "large"
    fun fontSize(c: Context): String = p(c).getString("font_size", "default") ?: "default"
    fun setFontSize(c: Context, v: String) { p(c).edit().putString("font_size", v).apply() }

    // "small", "default", "large"
    fun clockSize(c: Context): String = p(c).getString("clock_size", "default") ?: "default"
    fun setClockSize(c: Context, v: String) { p(c).edit().putString("clock_size", v).apply() }

    // Search mode: true = match from beginning only, false = match anywhere
    fun searchFromStart(c: Context) = p(c).getBoolean("search_from_start", true)
    fun setSearchFromStart(c: Context, v: Boolean) { p(c).edit().putBoolean("search_from_start", v).apply() }
}
