package com.minimal.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val delaySteps = longArrayOf(0, 100, 200, 300, 404, 500, 600)
    private val sizes = arrayOf("small", "default", "large")

    private lateinit var settingsRoot: View
    private lateinit var setDefaultLabel: TextView
    private lateinit var setDefaultState: TextView
    private lateinit var musicState: TextView
    private lateinit var delayValue: TextView
    private lateinit var clockState: TextView
    private lateinit var searchPosState: TextView
    private lateinit var searchModeState: TextView
    private lateinit var fontStyleState: TextView
    private lateinit var fontSizeState: TextView
    private lateinit var clockSizeState: TextView
    private var screenTimeStateRef: TextView? = null

    private val fontPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        if (FontManager.copyFontToInternal(this, uri)) {
            Prefs.setCustomFontName(this, "")
            Prefs.setFontStyle(this, "custom")
            FontManager.clearCache()
            refresh()
            Toast.makeText(this, "font imported", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "not a valid font file (.ttf / .otf)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsRoot = findViewById(R.id.settingsRoot)
        setDefaultLabel = findViewById(R.id.setDefaultLabel)
        setDefaultState = findViewById(R.id.setDefaultArrow)
        musicState = findViewById(R.id.musicToggleState)
        delayValue = findViewById(R.id.delayValue)
        clockState = findViewById(R.id.clockToggleState)
        searchPosState = findViewById(R.id.searchPosState)
        searchModeState = findViewById(R.id.searchModeState)
        fontStyleState = findViewById(R.id.fontStyleState)
        fontSizeState = findViewById(R.id.fontSizeState)
        clockSizeState = findViewById(R.id.clockSizeState)

        // Settings can be opened before the launcher loaded apps (e.g. process restart)
        AppRepository.init(this)

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }

        findViewById<TextView>(R.id.versionText).text = "MinimalSF v" + try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) { "?" }

        // One row: sets default when we aren't, lets you switch away when we are
        findViewById<View>(R.id.setDefaultBtn).setOnClickListener {
            if (isDefaultLauncher()) {
                MinimalDialog.confirm(this, title = "change default launcher",
                    message = "this will open android's home app settings where you can select a different launcher.",
                    positiveText = "open", negativeText = "cancel",
                    onPositive = { openHomeSettings() }
                )
            } else openHomeSettings()
        }

        // Now playing bar needs the notification listener, which only the "full" build has
        if (!SystemFeatures.AVAILABLE) {
            findViewById<View>(R.id.musicToggle).visibility = View.GONE
            findViewById<View>(R.id.musicDivider).visibility = View.GONE
        }
        findViewById<View>(R.id.musicToggle).setOnClickListener {
            val enabling = !Prefs.showMusic(this)
            Prefs.setShowMusic(this, enabling); refresh()
            if (enabling && !SystemFeatures.musicAccessEnabled(this)) {
                MinimalDialog.confirm(this, title = "notification access needed",
                    message = "enable notification access for MinimalSF to read now playing info.\n\nno data is tracked.",
                    positiveText = "open settings", negativeText = "skip",
                    onPositive = { try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {} }
                )
            }
        }

        findViewById<View>(R.id.delayBtn).setOnClickListener {
            MinimalDialog.stepSlider(this, title = "auto-launch delay", steps = delaySteps,
                currentValue = Prefs.autoDelay(this),
                onSelect = { ms -> Prefs.setAutoDelay(this, ms); refresh() }
            )
        }

        findViewById<View>(R.id.clockToggle).setOnClickListener {
            Prefs.setUse24hClock(this, !Prefs.use24hClock(this)); refresh()
        }

        findViewById<View>(R.id.searchPosToggle).setOnClickListener {
            Prefs.setSearchAtBottom(this, !Prefs.searchAtBottom(this)); refresh()
        }

        findViewById<View>(R.id.searchModeToggle).setOnClickListener {
            Prefs.setSearchFromStart(this, !Prefs.searchFromStart(this)); refresh()
        }

        findViewById<View>(R.id.fontStyleBtn).setOnClickListener { showFontMenu() }

        val screenTimeState = findViewById<TextView>(R.id.screenTimeState)
        screenTimeStateRef = screenTimeState
        findViewById<View>(R.id.screenTimeToggle).setOnClickListener {
            val enabling = !Prefs.showScreenTime(this)
            Prefs.setShowScreenTime(this, enabling); refresh()
            if (enabling && !ScreenTime.hasAccess(this)) {
                MinimalDialog.confirm(this, title = "usage access needed",
                    message = "to show today's screen time, allow MinimalSF under usage access.\n\nit's read on this device only — nothing is sent anywhere.",
                    positiveText = "open settings", negativeText = "skip",
                    onPositive = { ScreenTime.openAccessSettings(this) }
                )
            }
        }

        findViewById<View>(R.id.fontSizeBtn).setOnClickListener {
            MinimalDialog.singleChoice(this, title = "font size", items = sizes,
                checkedIndex = sizes.indexOf(Prefs.fontSize(this))
            ) { which -> Prefs.setFontSize(this, sizes[which]); refresh() }
        }

        findViewById<View>(R.id.clockSizeBtn).setOnClickListener {
            MinimalDialog.singleChoice(this, title = "clock size", items = sizes,
                checkedIndex = sizes.indexOf(Prefs.clockSize(this))
            ) { which -> Prefs.setClockSize(this, sizes[which]); refresh() }
        }

        findViewById<View>(R.id.gesturesBtn).setOnClickListener {
            startActivity(Intent(this, GesturesActivity::class.java))
        }

        findViewById<View>(R.id.githubBtn).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) }
            catch (_: Exception) { Toast.makeText(this, "no browser found", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions / default launcher may have changed while we were in system settings
        refresh()
    }

    private fun refresh() {
        if (isDefaultLauncher()) {
            setDefaultLabel.text = "default launcher"
            setDefaultState.text = "[✓ set]"
        } else {
            setDefaultLabel.text = "set as default launcher"
            setDefaultState.text = "→"
        }

        musicState.text = when {
            !Prefs.showMusic(this) -> "[off]"
            !SystemFeatures.musicAccessEnabled(this) -> "[on · needs access]"
            else -> "[on]"
        }
        delayValue.text = "[${Prefs.autoDelay(this)}ms]"
        clockState.text = if (Prefs.use24hClock(this)) "[24h]" else "[12h]"
        searchPosState.text = if (Prefs.searchAtBottom(this)) "[bottom]" else "[top]"
        searchModeState.text = if (Prefs.searchFromStart(this)) "[starts with]" else "[contains]"
        val fontName = when (Prefs.fontStyle(this)) {
            "clean" -> "clean"
            "system" -> "phone default"
            "custom" -> Prefs.customFontName(this).lowercase().ifEmpty { "custom" }
            else -> "mono"
        }
        fontStyleState.text = "[$fontName${if (Prefs.fontBold(this)) " · bold" else ""}]"
        screenTimeStateRef?.text = when {
            !Prefs.showScreenTime(this) -> "[off]"
            !ScreenTime.hasAccess(this) -> "[on · needs access]"
            else -> "[on]"
        }
        fontSizeState.text = "[${Prefs.fontSize(this)}]"
        clockSizeState.text = "[${Prefs.clockSize(this)}]"

        // Preview the chosen font right here
        FontManager.applyTo(settingsRoot, FontManager.getTypeface(this), FontManager.sizeMultiplier(this))
    }

    // --- Font menu: built-in, the stored custom font, Google Fonts download, or a file ---

    private fun showFontMenu() {
        val style = Prefs.fontStyle(this)
        val hasCustom = Prefs.customFontPath(this).isNotEmpty()
        val customLabel = Prefs.customFontName(this).lowercase().ifEmpty { "imported font" }

        val items = mutableListOf("monospace", "clean (sans-serif)", "phone default")
        val actions = mutableListOf({ setBuiltInFont("mono") }, { setBuiltInFont("clean") }, { setBuiltInFont("system") })
        var checked = when (style) { "clean" -> 1; "system" -> 2; else -> 0 }
        if (hasCustom) {
            if (style == "custom") checked = items.size
            items += customLabel
            actions += { Prefs.setFontStyle(this, "custom"); FontManager.clearCache(); refresh() }
        }
        items += "download font…"; actions += { showFontDownloads() }
        items += "import from file…"; actions += { fontPickerLauncher.launch("*/*") }

        // Bold applies to whichever font is chosen; settings behind the dialog update live as a preview
        val bold = MinimalDialog.TitleToggle("bold", isOn = { Prefs.fontBold(this) }) {
            Prefs.setFontBold(this, !Prefs.fontBold(this)); FontManager.clearCache(); refresh()
        }
        MinimalDialog.singleChoice(this, title = "font", items = items.toTypedArray(), checkedIndex = checked, toggle = bold) { actions[it]() }
    }

    private fun setBuiltInFont(style: String) {
        Prefs.setFontStyle(this, style); FontManager.clearCache(); refresh()
    }

    private fun showFontDownloads() {
        val fonts = GoogleFonts.MONO + GoogleFonts.SANS
        val current = if (Prefs.fontStyle(this) == "custom") Prefs.customFontName(this) else ""
        MinimalDialog.options(this, title = "google fonts", subtitle = "free · open font license",
            items = fonts.map { it.lowercase() }.toTypedArray(),
            icons = IntArray(fonts.size) { if (fonts[it] == current) R.drawable.ic_radio_on else R.drawable.ic_download },
            trailing = Array(fonts.size) { if (it < GoogleFonts.MONO.size) "mono" else "sans" }
        ) { which ->
            val name = fonts[which]
            Toast.makeText(this, "downloading ${name.lowercase()}…", Toast.LENGTH_SHORT).show()
            GoogleFonts.download(this, name) { ok ->
                if (isDestroyed) return@download
                if (ok) {
                    Prefs.setFontStyle(this, "custom"); FontManager.clearCache(); refresh()
                    Toast.makeText(this, "${name.lowercase()} applied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "couldn't download — needs google play services and internet", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openHomeSettings() {
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    companion object {
        private const val REPO_URL = "https://github.com/DHIRAJ-J-S/MinimalSF_Launcher"
    }
}
