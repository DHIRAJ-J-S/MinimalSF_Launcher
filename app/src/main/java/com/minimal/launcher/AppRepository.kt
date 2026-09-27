package com.minimal.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import java.util.concurrent.Executors

/**
 * Process-wide cache of launchable apps.
 * Loads labels + icons off the main thread and reloads only when packages change,
 * instead of re-querying PackageManager on every resume.
 */
object AppRepository {

    @Volatile
    var apps: List<AppInfo> = emptyList()
        private set

    var isLoaded = false
        private set

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private var callbackRegistered = false
    private var pendingReload = false
    private var loading = false

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** Starts watching package changes and does the first load if needed. */
    fun init(ctx: Context) {
        val app = ctx.applicationContext
        if (!callbackRegistered) {
            callbackRegistered = true
            app.getSystemService(LauncherApps::class.java)?.registerCallback(object : LauncherApps.Callback() {
                override fun onPackageRemoved(pkg: String, user: UserHandle) = reload(app)
                override fun onPackageAdded(pkg: String, user: UserHandle) = reload(app)
                override fun onPackageChanged(pkg: String, user: UserHandle) = reload(app)
                override fun onPackagesAvailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) = reload(app)
                override fun onPackagesUnavailable(pkgs: Array<out String>, user: UserHandle, replacing: Boolean) = reload(app)
            }, main)
        }
        if (!isLoaded) reload(app)
    }

    fun reload(ctx: Context) {
        if (loading) { pendingReload = true; return }
        loading = true
        val app = ctx.applicationContext
        executor.execute {
            val list = try { query(app) } catch (_: Exception) { apps }
            main.post {
                apps = list
                isLoaded = true
                loading = false
                listeners.toList().forEach { it() }
                if (pendingReload) { pendingReload = false; reload(app) }
            }
        }
    }

    private fun query(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .asSequence()
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .sortedBy { it.labelLower }
            .toList()
    }

    fun labelFor(ctx: Context, pkg: String): String =
        apps.firstOrNull { it.packageName == pkg }?.label
            ?: try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }
            catch (_: Exception) { pkg }
}
