package com.minimal.launcher

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

/**
 * The optional MinimalSF Extended plugin: a separate app that runs force stop, clear data / cache,
 * freeze and freeform through Shizuku. MinimalSF itself never gets those rights; it only asks the
 * plugin, and only apps signed with the same key can (the EXTENDED signature permission).
 */
object Extended {
    const val PACKAGE = "io.github.dhiraj_j_s.minimalsf.extended"
    private const val ACTION = "io.github.dhiraj_j_s.minimalsf.extended.ACTION"
    private const val PERMISSION = "io.github.dhiraj_j_s.minimalsf.permission.EXTENDED"

    private val ACTIONS_URI = Uri.parse("content://io.github.dhiraj_j_s.minimalsf.extended.actions/actions")
    private val SETTINGS_URI = Uri.parse("content://io.github.dhiraj_j_s.minimalsf.extended.actions/settings")
    private val ID = Regex("^[a-z_]{1,32}$")
    private val ICON = Regex("^[a-z0-9_]{1,64}$")

    class Confirm(val title: String, val message: String, val button: String)
    class Action(val id: String, val label: String, val icon: Drawable?, val confirm: Confirm?)

    /**
     * The plugin's current menu, read fresh each time so a plugin update shows up straight away.
     * Text is length-capped and icons are only looked up as drawables inside the plugin.
     */
    fun actions(c: Context): List<Action> = if (!available(c)) emptyList() else try {
        val res = c.packageManager.getResourcesForApplication(PACKAGE)
        c.contentResolver.query(ACTIONS_URI, null, null, null, null)?.use { cur ->
            fun col(name: String) = cur.getColumnIndex(name).takeIf { it >= 0 }?.let { cur.getString(it) }
            buildList {
                while (cur.moveToNext()) {
                    val id = col("id")?.takeIf { ID.matches(it) } ?: continue
                    val label = col("label")?.take(40)?.takeIf { it.isNotBlank() } ?: continue
                    val icon = col("icon")?.takeIf { ICON.matches(it) }?.let { name ->
                        @Suppress("DiscouragedApi")
                        val resId = res.getIdentifier(name, "drawable", PACKAGE)
                        if (resId == 0) null else try { res.getDrawable(resId, null) } catch (_: Exception) { null }
                    }
                    val t = col("confirm_title"); val m = col("confirm_message"); val b = col("confirm_button")
                    val confirm = if (t != null && m != null && b != null) Confirm(t.take(60), m.take(400), b.take(20)) else null
                    add(Action(id, label, icon, confirm))
                }
            }
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    /** One row of the plugin's "extended menu": a page of more rows, an on/off toggle, or "open the plugin". */
    class Setting(val id: String, val parent: String, val kind: String, val label: String, val icon: Drawable?, val on: Boolean)

    const val PAGE = "page"
    const val TOGGLE = "toggle"
    const val OPEN = "open"

    /**
     * The plugin's "extended menu" (settings > extended menu), read fresh so a plugin update can add
     * options. Empty with a plugin older than 1.1. Toggle values are copied into [mirror] as they're read.
     */
    fun settings(c: Context): List<Setting> = if (!available(c)) emptyList() else try {
        val res = c.packageManager.getResourcesForApplication(PACKAGE)
        c.contentResolver.query(SETTINGS_URI, null, null, null, null)?.use { cur ->
            fun col(name: String) = cur.getColumnIndex(name).takeIf { it >= 0 }?.let { cur.getString(it) }
            buildList {
                while (cur.moveToNext()) {
                    val id = col("id")?.takeIf { ID.matches(it) } ?: continue
                    val parent = col("parent")?.takeIf { it.isEmpty() || ID.matches(it) } ?: continue
                    val kind = col("kind")?.takeIf { it == PAGE || it == TOGGLE || it == OPEN } ?: continue
                    val label = col("label")?.take(48)?.takeIf { it.isNotBlank() } ?: continue
                    val icon = col("icon")?.takeIf { ICON.matches(it) }?.let { name ->
                        @Suppress("DiscouragedApi")
                        val resId = res.getIdentifier(name, "drawable", PACKAGE)
                        if (resId == 0) null else try { res.getDrawable(resId, null) } catch (_: Exception) { null }
                    }
                    add(Setting(id, parent, kind, label, icon, col("value") == "1"))
                }
            }
        }.orEmpty().also { rows ->
            mirror(c).edit().clear().apply { rows.filter { it.kind == TOGGLE }.forEach { putBoolean(it.id, it.on) } }.apply()
        }
    } catch (_: Exception) { emptyList() }

    /** Saves a toggle in the plugin, and in [mirror]. False if the plugin refused or isn't there. */
    fun set(c: Context, id: String, on: Boolean): Boolean = available(c) && try {
        val done = c.contentResolver.update(SETTINGS_URI,
            ContentValues().apply { put("id", id); put("value", if (on) 1 else 0) }, null, null) > 0
        if (done) mirror(c).edit().putBoolean(id, on).apply()
        done
    } catch (_: Exception) { false }

    /**
     * MinimalSF's copy of the plugin's toggles, so drawing the home screen never has to wake the plugin.
     * Refreshed whenever the extended menu is opened.
     */
    private fun mirror(c: Context) = c.getSharedPreferences("extended", Context.MODE_PRIVATE)

    /** What the plugin's toggles change in MinimalSF. Without the plugin, everything is as it is out of the box. */
    class Flags(val hideSearch: Boolean, val colourIcons: Boolean, val autoLaunch: Boolean,
                private val hiddenMenu: Set<String>, val anyAction: Boolean) {
        /** Whether MinimalSF's own long press entry [id] (e.g. "menu_app_info") is shown. */
        fun shows(id: String) = id !in hiddenMenu
    }

    val DEFAULT = Flags(hideSearch = false, colourIcons = false, autoLaunch = true, hiddenMenu = emptySet(), anyAction = true)

    fun flags(c: Context): Flags {
        if (!available(c)) return DEFAULT
        val all = mirror(c).all.mapNotNull { (k, v) -> (v as? Boolean)?.let { k to it } }.toMap()
        val actions = all.filterKeys { it.startsWith("action_") }
        return Flags(
            hideSearch = all["hide_search"] ?: false,
            colourIcons = all["colour_icons"] ?: false,
            autoLaunch = all["auto_launch"] ?: true,
            hiddenMenu = all.filter { (k, v) -> k.startsWith("menu_") && !v }.keys,
            // Before the menu was ever opened nothing is copied yet: assume the actions are on
            anyAction = actions.isEmpty() || actions.values.any { it })
    }

    /**
     * Installed and signed with our key. The key check matters: an app could take the plugin's package
     * name on a phone without the real plugin; without it, it would get to see which app you long
     * pressed and fill this menu. With a different key it's simply ignored.
     */
    fun available(c: Context): Boolean {
        if (c.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            c.packageManager.getPackageInfo(PACKAGE, 0)
            c.packageManager.checkSignatures(c.packageName, PACKAGE) == PackageManager.SIGNATURE_MATCH
        } catch (_: Exception) { false }
    }

    /** Asks the plugin to run [action] on [app]. False if the plugin couldn't be opened. */
    fun run(c: Context, action: String, app: AppInfo): Boolean = if (!available(c)) false else try {
        c.startActivity(Intent(ACTION).setPackage(PACKAGE)
            .putExtra("action", action)
            .putExtra("package", app.packageName)
            .putExtra("component", app.component.flattenToString())
            // UserHandle's hash code is its user id (0 = main, others = work profile / dual apps)
            .putExtra("user", app.user.hashCode())
            .putExtra("label", app.label)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) { false }
}
