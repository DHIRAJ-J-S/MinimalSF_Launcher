package com.minimal.launcher

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

object FontManager {

    private const val FONT_FILE = "custom_font"

    private var cachedTypeface: Typeface? = null
    private var cachedStyle: String = ""

    fun getTypeface(ctx: Context): Typeface {
        val bold = Prefs.fontBold(ctx)
        val style = Prefs.fontStyle(ctx) + if (bold) "+bold" else ""
        cachedTypeface?.let { if (cachedStyle == style) return it }

        // Mono and clean load the actual font files: Samsung's font styles redirect the MONOSPACE and
        // SANS_SERIF typefaces to the chosen style, which made all three options look the same.
        val tf = when (Prefs.fontStyle(ctx)) {
            "system" -> phoneDefault(bold)
            else -> {
                val base = when (Prefs.fontStyle(ctx)) {
                    "clean" -> systemFont("RobotoStatic-Regular.ttf", "Roboto-Regular.ttf") ?: Typeface.SANS_SERIF
                    "custom" -> loadCustomFont(ctx) ?: mono()
                    else -> mono()
                }
                // Single-weight fonts (imported/downloaded) get a synthetic bold
                if (bold) Typeface.create(base, Typeface.BOLD) else base
            }
        }
        cachedStyle = style
        cachedTypeface = tf
        return tf
    }

    private fun loadCustomFont(ctx: Context): Typeface? = buildTypeface(File(ctx.filesDir, FONT_FILE))

    private fun mono() = systemFont("DroidSansMono.ttf", "CutiveMono.ttf") ?: Typeface.MONOSPACE

    /**
     * The font the phone's own UI uses. Samsung's system apps use One UI Sans, but ordinary apps asking
     * for the default font get Roboto, so load One UI Sans directly — unless a custom Samsung font style
     * is active, in which case the default typeface already is that style.
     */
    private fun phoneDefault(bold: Boolean): Typeface {
        val oneUi = File("/system/fonts/OneUISans-VF.ttf")
        if (oneUi.exists() && !customStyleActive()) {
            try {
                // Variable font: real bold weight instead of a synthetic one
                Typeface.Builder(oneUi).setFontVariationSettings("'wght' ${if (bold) 700 else 400}").build()
                    ?.let { return it }
            } catch (_: Exception) {}
        }
        return if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    /** True when the default typeface has been replaced (e.g. a Samsung font style) — it no longer measures like Roboto. */
    private fun customStyleActive(): Boolean {
        val roboto = systemFont("Roboto-Regular.ttf", "RobotoStatic-Regular.ttf") ?: return false
        val sample = "The quick brown fox 0123456789"
        val p = android.graphics.Paint().apply { textSize = 100f }
        p.typeface = Typeface.DEFAULT
        val def = p.measureText(sample)
        p.typeface = roboto
        return kotlin.math.abs(def - p.measureText(sample)) > 1f
    }

    /** First of [names] that exists in /system/fonts, loaded straight from the file. */
    private fun systemFont(vararg names: String): Typeface? =
        names.firstNotNullOfOrNull { buildTypeface(File("/system/fonts", it)) }

    // Typeface.Builder returns null for unreadable files instead of silently falling back
    private fun buildTypeface(file: File): Typeface? =
        if (!file.exists()) null else try { Typeface.Builder(file).build() } catch (_: Exception) { null }

    /**
     * Copies the picked font into app storage. The file is validated first so a bad pick
     * doesn't replace a working custom font.
     */
    fun copyFontToInternal(ctx: Context, uri: Uri): Boolean {
        val tmp = File(ctx.filesDir, "$FONT_FILE.tmp")
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: return false
            if (buildTypeface(tmp) == null) { tmp.delete(); return false }
            val outFile = File(ctx.filesDir, FONT_FILE)
            outFile.delete()
            if (!tmp.renameTo(outFile)) return false
            Prefs.setCustomFontPath(ctx, outFile.absolutePath)
            clearCache()
            true
        } catch (_: Exception) {
            tmp.delete()
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

    /**
     * Applies typeface + size multiplier to every TextView under [root], so the chosen font
     * is used consistently instead of on a handful of hand-picked views.
     * The original XML size is remembered in a tag so repeated calls don't compound.
     * RecyclerView contents are skipped — their adapters apply the font on bind.
     */
    fun applyTo(root: View, tf: Typeface, mult: Float, skip: Set<View> = emptySet()) {
        if (root in skip || root is RecyclerView) return
        if (root is TextView) {
            val basePx = (root.getTag(R.id.tag_base_text_size) as? Float)
                ?: root.textSize.also { root.setTag(R.id.tag_base_text_size, it) }
            // Keep text that's bold in the XML bold, using its original style (not the last one applied)
            val baseStyle = (root.getTag(R.id.tag_base_text_style) as? Int)
                ?: (root.typeface?.style ?: Typeface.NORMAL).also { root.setTag(R.id.tag_base_text_style, it) }
            if (baseStyle and Typeface.BOLD != 0) root.setTypeface(tf, Typeface.BOLD) else root.typeface = tf
            root.setTextSize(TypedValue.COMPLEX_UNIT_PX, basePx * mult)
        }
        if (root is ViewGroup) for (i in 0 until root.childCount) applyTo(root.getChildAt(i), tf, mult, skip)
    }

    fun clearCache() {
        cachedTypeface = null
        cachedStyle = ""
    }
}
