package com.minimal.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Opens the system page for accessibility / notification access. On Android 13+ those are "restricted
 * settings": for a sideloaded app, Android refuses with "app was denied access" until the user allows
 * it from app info, and that menu item only appears after the first refusal. People who install from
 * GitHub hit this with no explanation, so tell them before sending them there.
 */
object SpecialAccess {

    // Apps can't read whether they're restricted (the app op needs a system permission), and which
    // installers Android exempts varies by version, so warn for anything not from a recognised store
    private fun maybeBlocked(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            ctx.packageManager.getInstallSourceInfo(ctx.packageName).packageSource != PackageInstaller.PACKAGE_SOURCE_STORE
        } catch (_: Exception) { false }
    }

    /** [action] is the Settings action to open, e.g. Settings.ACTION_ACCESSIBILITY_SETTINGS. */
    fun open(ctx: Context, action: String) {
        if (!maybeBlocked(ctx)) { start(ctx, Intent(action)); return }
        MinimalDialog.confirm(ctx, title = str(R.string.android_may_block_this),
            message = str(R.string.android_can_restrict_this_for),
            positiveText = str(R.string.open_settings), negativeText = str(R.string.app_info),
            onPositive = { start(ctx, Intent(action)) },
            onNegative = {
                start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            })
    }

    private fun start(ctx: Context, intent: Intent) {
        try { ctx.startActivity(intent) } catch (_: Exception) {}
    }
}
