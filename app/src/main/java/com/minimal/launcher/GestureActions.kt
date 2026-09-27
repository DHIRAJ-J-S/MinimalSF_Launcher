package com.minimal.launcher

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.View
import android.widget.Toast

/**
 * Everything a gesture can do. Actions are stored as short codes in Prefs:
 *   ""                              nothing
 *   "lock", "notifications", ...    built-in actions (constants below)
 *   "app:<pkg>"                     open an app (a bare package name is read the same way, from v1.1)
 *   "call:<number>|<name>"          call a contact
 *   "shortcut:<pkg>|<id>|<label>"   an app shortcut (built-in, or pinned to us e.g. by Activity Launcher)
 *   "activity:<pkg>/<cls>|<label>"  a specific screen inside an app (Activity Launcher style)
 */
object GestureActions {

    const val LOCK = "lock"
    const val NOTIFICATIONS = "notifications"
    const val QUICK_SETTINGS = "quick_settings"
    const val ALL_APPS = "all_apps"
    const val FLASHLIGHT = "flashlight"
    const val CAMERA = "camera"
    const val PLAY_PAUSE = "media_play_pause"
    const val NEXT_TRACK = "media_next"

    /** Things only the home screen can do. */
    interface Host {
        fun lockScreen()
        fun openAllApps()
        fun expandNotifications()
        fun expandQuickSettings()
        fun launchApp(pkg: String)
    }

    fun app(pkg: String) = "app:$pkg"
    fun call(number: String, name: String) = "call:$number|$name"
    fun shortcut(pkg: String, id: String, label: String) = "shortcut:$pkg|$id|$label"
    fun activity(cn: ComponentName, label: String) = "activity:${cn.flattenToString()}|$label"

    private fun rest(code: String) = code.substringAfter(':')

    fun label(ctx: Context, code: String): String = when {
        code.isEmpty() -> "none"
        code == LOCK -> "lock screen"
        code == NOTIFICATIONS -> "notifications"
        code == QUICK_SETTINGS -> "quick settings"
        code == ALL_APPS -> "all apps"
        code == FLASHLIGHT -> "flashlight"
        code == CAMERA -> "camera"
        code == PLAY_PAUSE -> "play / pause"
        code == NEXT_TRACK -> "next track"
        code.startsWith("call:") -> "call ${rest(code).substringAfter('|')}"
        code.startsWith("shortcut:") -> rest(code).split('|').let { "${AppRepository.labelFor(ctx, it[0])} · ${it.getOrElse(2) { "shortcut" }}" }
        code.startsWith("activity:") -> rest(code).let {
            val pkg = it.substringBefore('/')
            "${AppRepository.labelFor(ctx, pkg)} · ${it.substringAfter('|')}"
        }
        else -> AppRepository.labelFor(ctx, if (code.startsWith("app:")) rest(code) else code)
    }

    fun icon(code: String): Int = when {
        code.isEmpty() -> R.drawable.ic_none
        code == LOCK -> R.drawable.ic_lock
        code == NOTIFICATIONS -> R.drawable.ic_notifications
        code == QUICK_SETTINGS -> R.drawable.ic_quick_settings
        code == ALL_APPS -> R.drawable.ic_grid
        code == FLASHLIGHT -> R.drawable.ic_flashlight
        code == CAMERA -> R.drawable.ic_camera
        code == PLAY_PAUSE -> R.drawable.ic_play
        code == NEXT_TRACK -> R.drawable.ic_next
        code.startsWith("call:") -> R.drawable.ic_call
        code.startsWith("shortcut:") -> R.drawable.ic_shortcut
        code.startsWith("activity:") -> R.drawable.ic_activity
        else -> R.drawable.ic_app
    }

    /** Runs [code]. Returns false if there was nothing to do. */
    fun run(activity: Activity, code: String, host: Host, source: View? = null): Boolean {
        when {
            code.isEmpty() -> return false
            code == LOCK -> host.lockScreen()
            code == NOTIFICATIONS -> host.expandNotifications()
            code == QUICK_SETTINGS -> host.expandQuickSettings()
            code == ALL_APPS -> host.openAllApps()
            code == FLASHLIGHT -> Torch.toggle(activity)
            code == CAMERA -> start(activity, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            code == PLAY_PAUSE -> mediaKey(activity, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            code == NEXT_TRACK -> mediaKey(activity, KeyEvent.KEYCODE_MEDIA_NEXT)
            code.startsWith("call:") -> callNumber(activity, rest(code).substringBefore('|'))
            code.startsWith("shortcut:") -> rest(code).split('|').let { p ->
                if (!AppShortcuts.start(activity, p[0], p.getOrElse(1) { "" }, source))
                    toast(activity, "shortcut no longer available")
            }
            code.startsWith("activity:") -> ComponentName.unflattenFromString(rest(code).substringBefore('|'))?.let {
                start(activity, Intent().setComponent(it))
            }
            else -> host.launchApp(if (code.startsWith("app:")) rest(code) else code)
        }
        return true
    }

    private fun start(ctx: Context, intent: Intent) {
        try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { toast(ctx, "can't open that — the app may have changed") }
    }

    private fun callNumber(ctx: Context, number: String) {
        // Direct call if the user allowed it when setting up the gesture, otherwise open the dialer
        val direct = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        start(ctx, Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    }

    // Media keys go to whichever app is playing; needs no permission (works in the lite build)
    private fun mediaKey(ctx: Context, key: Int) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    /** Flashlight via the camera service; needs no permission. */
    private object Torch {
        private var on = false
        private var cameraId: String? = null
        private var registered = false

        fun toggle(ctx: Context) {
            val cm = ctx.getSystemService(CameraManager::class.java) ?: return
            if (!registered) {
                registered = true
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(id: String, enabled: Boolean) { if (id == cameraId) on = enabled }
                }, Handler(Looper.getMainLooper()))
            }
            val id = cameraId ?: cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }?.also { cameraId = it }
            if (id == null) { toast(ctx, "no flashlight on this phone"); return }
            try { cm.setTorchMode(id, !on); on = !on } catch (_: Exception) { toast(ctx, "flashlight is busy") }
        }
    }
}

/**
 * App shortcuts ("new chat", "navigate home", …) through LauncherApps.
 * Only the default launcher is allowed to read and start them, which also means other apps'
 * "add to home screen" requests (and Activity Launcher's shortcuts) come to us.
 */
object AppShortcuts {

    private fun la(ctx: Context) = ctx.getSystemService(LauncherApps::class.java)

    fun available(ctx: Context) = try { la(ctx)?.hasShortcutHostPermission() == true } catch (_: Exception) { false }

    fun forPackage(ctx: Context, pkg: String): List<ShortcutInfo> {
        if (!available(ctx)) return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
            )
            (la(ctx)?.getShortcuts(q, Process.myUserHandle()) ?: emptyList())
                .filter { it.isEnabled }
                .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
        } catch (_: Exception) { emptyList() }
    }

    fun label(s: ShortcutInfo) = (s.shortLabel ?: s.longLabel ?: s.id).toString().lowercase()

    fun start(ctx: Context, pkg: String, id: String, source: View? = null): Boolean = try {
        val bounds = source?.let { v -> IntArray(2).also { v.getLocationOnScreen(it) }.let { Rect(it[0], it[1], it[0] + v.width, it[1] + v.height) } }
        la(ctx)?.startShortcut(pkg, id, bounds, null, Process.myUserHandle())
        la(ctx) != null
    } catch (_: Exception) { false }
}
