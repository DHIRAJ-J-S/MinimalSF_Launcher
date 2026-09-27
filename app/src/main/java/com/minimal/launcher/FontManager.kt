package com.minimal.launcher

import android.content.Context
import android.graphics.Typeface
import java.io.File

object FontManager {

    private var cachedTypeface: Typeface? = null
    private var cachedStyle: String = ""

    fun getTypeface(ctx: Context): Typeface {
        val style = Prefs.fontStyle(ctx)
        if (cachedTypeface != null && cachedStyle == style) return cachedTypeface!!

        cachedStyle = style
        cachedTypeface = when (style) {
            "clean" -> Typeface.SANS_SERIF
            "custom" -> loadCustomFont(ctx) ?: Typeface.MONOSPACE
            else -> Typeface.MONOSPACE
        }
        return cachedTypeface!!
    }

    private fun loadCustomFont(ctx: Context): Typeface? {
        val path = Prefs.customFontPath(ctx)
        if (path.isEmpty()) return null
        return try {
            // Try loading from internal storage copy
            val internalFile = File(ctx.filesDir, "custom_font")
            if (internalFile.exists()) {
                Typeface.createFromFile(internalFile)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun copyFontToInternal(ctx: Context, uri: android.net.Uri): Boolean {
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return false
            val outFile = File(ctx.filesDir, "custom_font")
            outFile.outputStream().use { out -> input.copyTo(out) }
            input.close()
            Prefs.setCustomFontPath(ctx, outFile.absolutePath)
            // Clear cache so it reloads
            cachedTypeface = null
            cachedStyle = ""
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns size multiplier based on font size pref.
     * Clock size is independent — not affected by this.
     */
    fun sizeMultiplier(ctx: Context): Float {
        return when (Prefs.fontSize(ctx)) {
            "small" -> 0.85f
            "large" -> 1.2f
            else -> 1.0f
        }
    }

    fun clockSizeSp(ctx: Context): Float {
        return when (Prefs.clockSize(ctx)) {
            "small" -> 38f
            "large" -> 64f
            else -> 52f
        }
    }

    fun clearCache() {
        cachedTypeface = null
        cachedStyle = ""
    }
}
