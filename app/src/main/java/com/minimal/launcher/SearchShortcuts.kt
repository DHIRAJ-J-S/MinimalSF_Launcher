package com.minimal.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import java.util.concurrent.Executors

/**
 * App shortcuts as search results (issue #2), ranked below apps and never in all apps:
 *  - saved shortcuts (other apps' "add to home screen"), always. They can have a keyword.
 *  - every app's built-in shortcuts ("new chat", "new incognito tab"), only with the
 *    "all_shortcuts" toggle of MinimalSF Extended on.
 * Both need MinimalSF to be the default launcher to open; Android refuses otherwise.
 */
object SearchShortcuts {

    private const val PREFIX = "shortcut:"

    /** The key a shortcut result is saved under (keywords): "shortcut:<pkg>|<id>". */
    fun key(pkg: String, id: String) = "$PREFIX$pkg|$id"
    fun isKey(key: String) = key.startsWith(PREFIX)

    // --- Saved shortcuts ("pkg|id|label" lines in Prefs) ---

    private var savedSource: List<String>? = null
    private var savedCache: List<AppInfo> = emptyList()
    private var savedAppsVersion: List<AppInfo>? = null

    fun saved(c: Context): List<AppInfo> {
        val entries = Prefs.pinnedShortcuts(c)
        val apps = AppRepository.apps
        if (entries == savedSource && apps === savedAppsVersion) return savedCache
        savedSource = entries; savedAppsVersion = apps
        savedCache = entries.mapNotNull { e ->
            val p = e.split('|')
            if (p.size < 3 || p[1].isEmpty()) null else result(c, p[0], p[1], p.drop(2).joinToString("|"))
        }
        return savedCache
    }

    /** The saved shortcut behind [key], as its "pkg|id|label" line. */
    fun savedEntry(c: Context, key: String): String? =
        Prefs.pinnedShortcuts(c).firstOrNull { PREFIX + it.substringBeforeLast('|') == key }

    fun label(c: Context, key: String): String? = savedEntry(c, key)?.substringAfterLast('|')

    // --- Built-in shortcuts (MinimalSF Extended toggle) ---

    @Volatile var builtIn: List<AppInfo> = emptyList()
        private set
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * Re-reads every app's built-in shortcuts in the background, then calls [done] on the main thread.
     * Off clears the list straight away.
     */
    fun refreshBuiltIn(c: Context, on: Boolean, done: () -> Unit) {
        if (!on) { if (builtIn.isNotEmpty()) { builtIn = emptyList(); done() }; return }
        val app = c.applicationContext
        executor.execute {
            val list = try { queryBuiltIn(app) } catch (_: Exception) { builtIn }
            main.post { builtIn = list; done() }
        }
    }

    private fun queryBuiltIn(c: Context): List<AppInfo> {
        if (!AppShortcuts.available(c)) return emptyList()
        val la = c.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val q = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        val ours = c.packageName
        return (la.getShortcuts(q, Process.myUserHandle()) ?: emptyList())
            .filter { it.isEnabled && it.`package` != ours }
            .sortedWith(compareBy({ it.`package` }, { !it.isDeclaredInManifest }, { it.rank }))
            .map { result(c, it.`package`, it.id, AppShortcuts.label(it)) }
    }

    /** Saved first, then built-in ones that aren't also saved. Shortcuts of hidden apps are left out. */
    fun all(c: Context, withBuiltIn: Boolean, hidden: Set<String>): List<AppInfo> {
        val saved = saved(c)
        val list = if (!withBuiltIn || builtIn.isEmpty()) saved
            else saved + builtIn.filter { b -> saved.none { it.key == b.key } }
        return if (hidden.isEmpty()) list else list.filter { it.packageName !in hidden && it.key !in hidden }
    }

    private fun result(c: Context, pkg: String, id: String, label: String): AppInfo {
        val app = AppRepository.apps.firstOrNull { it.key == pkg }
        val appLabel = (app?.label ?: AppRepository.labelFor(c, pkg)).lowercase()
        val icon = app?.icon ?: try { c.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { ColorDrawable(0) }
        return AppInfo(label = "$label · $appLabel", packageName = pkg, icon = icon,
            component = app?.component ?: ComponentName(pkg, ""), user = Process.myUserHandle(),
            key = key(pkg, id), otherProfile = false, canUninstall = false, shortcutId = id, matchLabel = label)
    }
}

/** The "set keyword" prompt, shared by the long press menu and settings → saved shortcuts. */
object KeywordPrompt {
    fun show(a: Activity, key: String, name: String, onDone: () -> Unit = {}) {
        val current = Prefs.getKeywords(a)[key] ?: ""
        MinimalDialog.textInput(a,
            title = "keyword for $name",
            hint = "e.g. wa for whatsapp (empty = remove)",
            prefill = current,
            onSubmit = { keyword ->
                val owner = if (keyword.isEmpty()) null else Prefs.keywordOwner(a, keyword, key)
                if (owner != null) {
                    MinimalDialog.confirm(a, title = "keyword in use",
                        message = "\"${keyword.lowercase()}\" is already used by ${AppRepository.labelFor(a, owner)}.\n\nmove it to $name?",
                        positiveText = "move", negativeText = "cancel",
                        onPositive = {
                            Prefs.setKeyword(a, owner, "")
                            Prefs.setKeyword(a, key, keyword)
                            onDone()
                        })
                } else {
                    Prefs.setKeyword(a, key, keyword)
                    onDone()
                }
            }
        )
    }
}
