package com.minimal.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object Prefs {
    private const val P = "launcher_prefs"

    private fun p(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE)

    fun registerListener(c: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p(c).registerOnSharedPreferenceChangeListener(l)
    fun unregisterListener(c: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p(c).unregisterOnSharedPreferenceChangeListener(l)

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

    // --- Gestures: each stores a GestureActions code ("" = none) ---
    enum class Gesture(val key: String, val title: String) {
        DOUBLE_TAP("double_tap_action", "double tap"),
        LONG_PRESS("long_press_action", "long press"),
        SWIPE_LEFT("swipe_left_action", "swipe left"),
        SWIPE_RIGHT("swipe_right_action", "swipe right"),
        SWIPE_UP("swipe_up_action", "swipe up"),
        SWIPE_DOWN("swipe_down_action", "swipe down"),
    }

    private fun defaultGesture(g: Gesture) = when (g) {
        Gesture.DOUBLE_TAP -> GestureActions.LOCK
        Gesture.SWIPE_DOWN -> GestureActions.NOTIFICATIONS
        else -> ""
    }

    fun gesture(c: Context, g: Gesture): String = p(c).getString(g.key, defaultGesture(g)) ?: ""
    fun setGesture(c: Context, g: Gesture, v: String) { p(c).edit().putString(g.key, v).apply() }

    fun doubleTapAction(c: Context) = gesture(c, Gesture.DOUBLE_TAP)

    // --- Shortcuts other apps pinned to us ("add to home screen", Activity Launcher): "pkg|id|label" ---
    private const val PINNED_KEY = "pinned_shortcuts"

    fun pinnedShortcuts(c: Context): List<String> =
        (p(c).getString(PINNED_KEY, "") ?: "").split('\n').filter { it.isNotBlank() }

    fun addPinnedShortcut(c: Context, entry: String) {
        val list = pinnedShortcuts(c).filterNot { it.substringBeforeLast('|') == entry.substringBeforeLast('|') } + entry
        p(c).edit().putString(PINNED_KEY, list.joinToString("\n")).apply()
    }

    fun removePinnedShortcut(c: Context, entry: String) {
        p(c).edit().putString(PINNED_KEY, pinnedShortcuts(c).filterNot { it == entry }.joinToString("\n")).apply()
    }

    fun homeTipShown(c: Context) = p(c).getBoolean("home_tip_shown", false)
    fun setHomeTipShown(c: Context) { p(c).edit().putBoolean("home_tip_shown", true).apply() }

    fun musicTipShown(c: Context) = p(c).getBoolean("music_tip_shown", false)
    fun setMusicTipShown(c: Context) { p(c).edit().putBoolean("music_tip_shown", true).apply() }

    // --- Custom keywords: package -> keyword ---
    // Parsed once and cached; search reads this on every keystroke.
    private const val KEYWORDS_KEY = "app_keywords"
    private var keywordsCache: Map<String, String>? = null

    fun getKeywords(c: Context): Map<String, String> {
        keywordsCache?.let { return it }
        val json = p(c).getString(KEYWORDS_KEY, "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(json)
            obj.keys().forEach { map[it] = obj.getString(it) }
        } catch (_: Exception) {}
        keywordsCache = map
        return map
    }

    fun setKeyword(c: Context, pkg: String, keyword: String) {
        val map = getKeywords(c).toMutableMap()
        val k = keyword.trim().lowercase()
        if (k.isEmpty()) map.remove(pkg) else map[pkg] = k
        keywordsCache = map
        p(c).edit().putString(KEYWORDS_KEY, JSONObject(map as Map<*, *>).toString()).apply()
    }

    /** Package that already owns [keyword], if any (ignoring [exceptPkg]). */
    fun keywordOwner(c: Context, keyword: String, exceptPkg: String): String? =
        getKeywords(c).entries.firstOrNull { it.value == keyword.trim().lowercase() && it.key != exceptPkg }?.key

    // --- Hidden apps: set of package names ---
    private const val HIDDEN_KEY = "hidden_apps"
    private var hiddenCache: Set<String>? = null

    fun getHiddenApps(c: Context): Set<String> {
        hiddenCache?.let { return it }
        // Copy: the set returned by getStringSet must not be modified or kept
        val set = HashSet(p(c).getStringSet(HIDDEN_KEY, emptySet()) ?: emptySet())
        hiddenCache = set
        return set
    }

    fun setAppHidden(c: Context, pkg: String, hidden: Boolean) {
        val set = HashSet(getHiddenApps(c))
        if (hidden) set.add(pkg) else set.remove(pkg)
        hiddenCache = set
        p(c).edit().putStringSet(HIDDEN_KEY, set).apply()
    }

    // --- Font ---
    // "mono" (default), "clean", "system" (phone's font), "custom"
    fun fontStyle(c: Context): String = p(c).getString("font_style", "mono") ?: "mono"
    fun setFontStyle(c: Context, v: String) { p(c).edit().putString("font_style", v).apply() }

    fun fontBold(c: Context) = p(c).getBoolean("font_bold", false)
    fun setFontBold(c: Context, v: Boolean) { p(c).edit().putBoolean("font_bold", v).apply() }

    fun customFontPath(c: Context): String = p(c).getString("custom_font_path", "") ?: ""
    fun setCustomFontPath(c: Context, v: String) { p(c).edit().putString("custom_font_path", v).apply() }

    // Display name of the stored custom font ("JetBrains Mono", or "" for an imported file)
    fun customFontName(c: Context): String = p(c).getString("custom_font_name", "") ?: ""
    fun setCustomFontName(c: Context, v: String) { p(c).edit().putString("custom_font_name", v).apply() }

    fun showScreenTime(c: Context) = p(c).getBoolean("show_screen_time", false)
    fun setShowScreenTime(c: Context, v: Boolean) { p(c).edit().putBoolean("show_screen_time", v).apply() }

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
