package com.minimal.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import java.util.concurrent.Executors

/**
 * Free fonts from Google Fonts, fetched through Google Play services' font provider.
 * Play services does the download, so this app still needs no INTERNET permission.
 * The chosen font is copied into app storage (same as an imported file), so it works offline after.
 * Devices without Play services (de-Googled ROMs) can still import a .ttf/.otf file.
 */
object GoogleFonts {

    // Curated to fit the launcher: monospace first, then clean sans. All SIL Open Font License.
    val MONO = listOf(
        "JetBrains Mono", "IBM Plex Mono", "Space Mono", "Roboto Mono", "Source Code Pro", "Fira Code",
        "Inconsolata", "DM Mono", "Ubuntu Mono", "Red Hat Mono", "Martian Mono", "Azeret Mono"
    )
    val SANS = listOf(
        "Inter", "Manrope", "Space Grotesk", "DM Sans", "IBM Plex Sans", "Outfit",
        "Work Sans", "Rubik", "Karla", "Lexend"
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Downloads [name] (regular weight) and installs it as the custom font. [onDone] runs on the main thread. */
    fun download(ctx: Context, name: String, onDone: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        executor.execute {
            val ok = try {
                val request = FontRequest(
                    "com.google.android.gms.fonts", "com.google.android.gms",
                    "name=$name&weight=400&italic=0", R.array.com_google_android_gms_fonts_certs
                )
                val result = FontsContractCompat.fetchFonts(app, null, request)
                val font = result.fonts
                    .filter { it.resultCode == FontsContractCompat.Columns.RESULT_CODE_OK }
                    .minByOrNull { (if (it.isItalic) 1000 else 0) + kotlin.math.abs(it.weight - 400) }
                result.statusCode == FontsContractCompat.FontFamilyResult.STATUS_OK && font != null &&
                    FontManager.copyFontToInternal(app, font.uri)
            } catch (_: Exception) { false }
            if (ok) Prefs.setCustomFontName(app, name)
            main.post { onDone(ok) }
        }
    }
}
