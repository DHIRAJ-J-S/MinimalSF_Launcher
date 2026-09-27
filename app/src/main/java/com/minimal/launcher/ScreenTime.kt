package com.minimal.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * Today's screen time, computed from the same UsageStats events Digital Wellbeing reads
 * (Digital Wellbeing itself has no public API). Needs "usage access", granted by the user.
 * Totals are usually within a few minutes of Digital Wellbeing's, which uses its own session rules.
 */
object ScreenTime {

    /** Latest result: package -> foreground ms today. Empty until the first refresh. */
    @Volatile
    var today: Map<String, Long> = emptyMap()
        private set
    val totalToday: Long get() = today.values.sum()

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Suppress("DEPRECATION") // the non-deprecated variants don't exist on every supported API level
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openAccessSettings(ctx: Context) {
        // Some OEMs open straight to our entry with the package uri; others only accept the plain action
        try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
        catch (_: Exception) {
            try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Exception) {}
        }
    }

    /** Recomputes today's usage off the main thread, then calls [onDone] on the main thread. */
    fun refresh(ctx: Context, onDone: () -> Unit) {
        val app = ctx.applicationContext
        executor.execute {
            val result = try { compute(app) } catch (_: Exception) { null }
            main.post { if (result != null) today = result; onDone() }
        }
    }

    private fun compute(ctx: Context): Map<String, Long> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()

        val totals = HashMap<String, Long>()
        val sessionStart = HashMap<String, Long>()        // package -> when it came to the foreground
        val resumed = HashMap<String, MutableSet<String>>() // package -> activities currently resumed
        val seen = HashSet<String>()

        fun close(pkg: String, at: Long) {
            val s = sessionStart.remove(pkg) ?: return
            if (at > s) totals[pkg] = (totals[pkg] ?: 0L) + (at - s)
        }

        val events = usm.queryEvents(start, now)
        val e = UsageEvents.Event()
        var lastTs = start
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            // Reboot / power loss: nothing ran while the phone was off. After an unclean shutdown Android
            // logs the shutdown at the *next* startup time, so end open sessions at the last real event.
            if (e.eventType == UsageEvents.Event.DEVICE_SHUTDOWN || e.eventType == UsageEvents.Event.DEVICE_STARTUP) {
                sessionStart.keys.toList().forEach { close(it, lastTs) }
                resumed.clear()
                continue
            }
            lastTs = e.timeStamp
            val pkg = e.packageName ?: continue
            val cls = e.className ?: ""
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    seen += pkg
                    val set = resumed.getOrPut(pkg) { HashSet() }
                    if (set.isEmpty()) sessionStart[pkg] = e.timeStamp
                    set += cls
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val set = resumed[pkg]
                    if (set == null && pkg !in seen) {
                        // Was already open at midnight
                        totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - start)
                    } else if (set != null && set.remove(cls) && set.isEmpty()) {
                        close(pkg, e.timeStamp)
                    }
                    seen += pkg
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // Screen off ends every session
                    sessionStart.keys.toList().forEach { close(it, e.timeStamp) }
                    resumed.clear()
                }
            }
        }
        // Whatever is still open runs until now
        sessionStart.keys.toList().forEach { close(it, now) }

        totals.remove(ctx.packageName) // the home screen itself isn't "screen time"
        return totals.filterValues { it >= 1_000 }
    }

    fun format(ms: Long): String {
        val mins = ms / 60_000
        return when {
            mins < 1 -> "<1m"
            mins < 60 -> "${mins}m"
            else -> "${mins / 60}h ${mins % 60}m"
        }
    }

    /**
     * The system Digital Wellbeing dashboard, if this device has one (Google or Samsung).
     * Falls back to the app's own launch intent, which exists when "show icon in app list" is on.
     */
    fun wellbeingIntent(ctx: Context): Intent? {
        val pm = ctx.packageManager
        val candidates = listOf(
            Intent().setComponent(ComponentName("com.google.android.apps.wellbeing",
                "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.forest",
                "com.samsung.android.forest.launcher.LauncherActivity")),
        ) + listOfNotNull(
            pm.getLaunchIntentForPackage("com.google.android.apps.wellbeing"),
            pm.getLaunchIntentForPackage("com.samsung.android.forest")
        )
        return candidates.firstOrNull { it.resolveActivityInfo(pm, 0)?.exported == true }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
