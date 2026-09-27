package com.minimal.launcher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
) {
    // Pre-lowercased once so search doesn't allocate per keystroke
    val labelLower: String = label.lowercase()
}
