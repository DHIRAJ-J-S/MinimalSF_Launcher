package com.minimal.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Process-wide cache of launchable apps.
 * Loads labels + icons off the main thread and reloads only when packages change,
 * instead of re-querying PackageManager on every resume.
 */
object AppRepository {

    private const val USER_TYPE_CLONE = "android.os.usertype.profile.CLONE"
    private val DUAL_APP_USER_IDS = setOf(95, 999) // Samsung, Xiaomi and others

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
            // A work profile being added, removed or paused changes the list without any package event
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            }
            ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = reload(app)
            }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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

    /** Every launcher icon in every profile: apps can have several, and work profile apps are listed too. */
    private fun query(ctx: Context): List<AppInfo> {
        val la = ctx.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val um = ctx.getSystemService(UserManager::class.java)
        val me = Process.myUserHandle()
        return la.profiles.flatMap { user ->
            // Our own entry opens settings, and is only listed when the settings button is hidden
            val activities = la.getActivityList(null, user).filter {
                it.componentName.packageName != ctx.packageName || (user == me && !Prefs.showSettingsButton(ctx))
            }
            val iconCount = activities.groupingBy { it.componentName.packageName }.eachCount()
            activities.map { info ->
                val cn = info.componentName
                val isMain = user == me && (iconCount[cn.packageName] == 1 ||
                    ctx.packageManager.getLaunchIntentForPackage(cn.packageName)?.component == cn)
                val key = if (isMain) cn.packageName else "${um?.getSerialNumberForUser(user)}/${cn.flattenToShortString()}"
                val otherProfile = user != me
                // Preinstalled apps can't be removed, but an updated one can go back to its factory version
                val flags = info.applicationInfo.flags
                val canUninstall = flags and ApplicationInfo.FLAG_SYSTEM == 0 || flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                val label = when {
                    otherProfile -> "${info.label} · ${profileName(ctx, user)}"
                    cn.packageName == ctx.packageName -> "${info.label} settings"
                    else -> info.label.toString()
                }
                AppInfo(label, cn.packageName, info.getIcon(0), cn, user, key, otherProfile, canUninstall)
            }
        }.sortedBy { it.labelLower }
    }

    /**
     * "dual" for a second copy of an app (Samsung Dual Messenger, Xiaomi Dual Apps...), otherwise "work".
     * The label Android would badge an app with can't tell them apart: Samsung says "Work" for both.
     */
    private fun profileName(ctx: Context, user: UserHandle): String {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try { ctx.getSystemService(LauncherApps::class.java)?.getLauncherUserInfo(user)?.userType } catch (_: Exception) { null }
        } else null
        // Older versions have no public way to ask, but these makers use fixed user ids for it
        val dual = type?.let { it == USER_TYPE_CLONE } ?: (user.hashCode() in DUAL_APP_USER_IDS)
        return if (dual) "dual" else "work"
    }

    /** The app's main personal icon, for things that only know a package (screen time, shortcuts). */
    fun mainApps(): List<AppInfo> = apps.filter { it.key == it.packageName }

    fun labelFor(ctx: Context, key: String): String =
        apps.firstOrNull { it.key == key }?.label
            ?: try { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(key, 0)).toString() }
            catch (_: Exception) { key }
}
