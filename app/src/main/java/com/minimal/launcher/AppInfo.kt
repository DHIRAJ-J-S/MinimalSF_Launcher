package com.minimal.launcher

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val component: ComponentName,
    val user: UserHandle,
    // Identifies this icon in saved settings (keywords, hidden apps, gestures). It's the package name
    // for an app's main icon, so settings saved before apps could have several icons still match.
    val key: String,
    val otherProfile: Boolean,
    val canUninstall: Boolean
) {
    // Pre-lowercased once so search doesn't allocate per keystroke
    val labelLower: String = label.lowercase()
}
