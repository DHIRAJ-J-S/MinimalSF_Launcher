package com.minimal.launcher

import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Receives "add to home screen" shortcut requests from other apps (Chrome, Maps, Activity Launcher, …).
 * MinimalSF has no icon grid, so accepted shortcuts are kept in a list that gestures can use
 * (gestures → pick action → saved shortcuts).
 */
class PinShortcutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val la = getSystemService(LauncherApps::class.java)
        val request = try { la?.getPinItemRequest(intent) } catch (_: Exception) { null }
        val info = request?.shortcutInfo
        if (request == null || !request.isValid || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || info == null) {
            // Widgets aren't supported (no widget area on the home screen)
            Toast.makeText(this, "MinimalSF can't add that to the home screen", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val label = AppShortcuts.label(info)
        val appLabel = AppRepository.labelFor(this, info.`package`)
        MinimalDialog.confirm(this, title = "save shortcut",
            message = "\"$label\" from $appLabel\n\nyou can put it on a gesture in settings → gestures.",
            positiveText = "save", negativeText = "cancel",
            onPositive = {
                if (request.isValid && request.accept()) {
                    Prefs.addPinnedShortcut(this, "${info.`package`}|${info.id}|$label")
                    Toast.makeText(this, "shortcut saved", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { finish() }
        )
    }
}
