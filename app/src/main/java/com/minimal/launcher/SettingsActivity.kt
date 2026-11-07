package com.minimal.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ReplacementSpan
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private val delaySteps = longArrayOf(0, 100, 200, 300, 404, 500, 600)
    private val sizes = arrayOf("small", "default", "large")

    private lateinit var settingsRoot: View
    private lateinit var rows: LinearLayout
    // Run on every refresh, to show the current values
    private val updaters = mutableListOf<() -> Unit>()

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
        rows = findViewById(R.id.settingsRows)

        // Settings can be opened before the launcher loaded apps (e.g. process restart)
        AppRepository.init(this)

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }

        val page = intent.getStringExtra(EXTRA_PAGE)
        findViewById<TextView>(R.id.settingsTitle).text = page ?: "settings"
        when (page) {
            PAGE_SEARCH -> addSearchRows()
            PAGE_HOME -> { addHomeRows(); addButtonRows() }
            else -> addMainRows()
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions / default launcher may have changed while we were in system settings
        refresh()
    }

    private fun refresh() {
        updaters.forEach { it() }
        // Preview the chosen font right here
        FontManager.applyTo(settingsRoot, FontManager.getTypeface(this), FontManager.sizeMultiplier(this))
    }

    // --- Rows ---

    private fun row(label: String, state: () -> String, onClick: () -> Unit): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_setting_row, rows, false)
        val stateView = v.findViewById<TextView>(R.id.rowState)
        v.findViewById<TextView>(R.id.rowLabel).text = label
        updaters += { stateView.text = state() }
        v.setOnClickListener { onClick() }
        rows.addView(v)
        rows.addView(divider())
        return v
    }

    /**
     * One of the line icons at the start of a row's value, inside its brackets ("[✓ set]"), sized to
     * the text. It goes in the text: the value view spans the row, so a compound drawable would sit
     * next to the label instead.
     */
    private fun setStateIcon(stateView: TextView, res: Int) {
        val d = ContextCompat.getDrawable(this, res)?.mutate() ?: return
        val size = (stateView.textSize * 1.3f).toInt()
        d.setBounds(0, 0, size, size)
        d.setTint(stateView.currentTextColor)
        // No space after it: the icon's own padding already spaces it like the "[" before it
        val value = stateView.text.toString().removePrefix("[")
        stateView.text = SpannableStringBuilder("[").append(" ", CenteredIconSpan(d), 0).append(value)
    }

    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.density).toInt().coerceAtLeast(1)).apply {
            val m = (20 * resources.displayMetrics.density).toInt(); marginStart = m; marginEnd = m
        }
        setBackgroundColor(getColor(R.color.grey_border))
    }

    private fun toggle(label: String, get: () -> Boolean, set: (Boolean) -> Unit) =
        row(label, { if (get()) "[on]" else "[off]" }) { set(!get()); refresh() }

    /** A row that opens one of the pages below. */
    private fun pageRow(page: String) = row(page, { "→" }) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page))
    }

    // --- Pages ---

    private fun addMainRows() {
        // One row: sets default when we aren't, lets you switch away when we are
        val defaultRow = row("default launcher", { if (isDefaultLauncher()) "[set]" else "→" }) {
            if (isDefaultLauncher()) {
                MinimalDialog.confirm(this, title = "change default launcher",
                    message = "this will open android's home app settings where you can select a different launcher.",
                    positiveText = "open", negativeText = "cancel",
                    onPositive = { openHomeSettings() }
                )
            } else openHomeSettings()
        }
        updaters += {
            val isDefault = isDefaultLauncher()
            defaultRow.findViewById<TextView>(R.id.rowLabel).text = if (isDefault) "default launcher" else "set as default launcher"
            if (isDefault) setStateIcon(defaultRow.findViewById(R.id.rowState), R.drawable.ic_check)
        }

        pageRow(PAGE_SEARCH)
        pageRow(PAGE_HOME)
        // All apps view and font open a dialog rather than a page
        row("all apps view", {
            when {
                Prefs.allAppsGrid(this) -> "[grid · ${Prefs.allAppsColumns(this)} columns]"
                !Prefs.showAppIcons(this) -> "[list - without icons]"
                else -> "[list]"
            }
        }) {
            MinimalDialog.gridPicker(this, title = "all apps view", isGrid = Prefs.allAppsGrid(this),
                icons = Prefs.showAppIcons(this), columns = Prefs.allAppsColumns(this),
                min = Prefs.MIN_GRID_COLUMNS, max = Prefs.MAX_GRID_COLUMNS
            ) { grid, icons, columns ->
                Prefs.setAllAppsGrid(this, grid); Prefs.setShowAppIcons(this, icons)
                Prefs.setAllAppsColumns(this, columns); refresh()
            }
        }
        toggle("a-z scrubber in all apps", { Prefs.showAlphabetScrubber(this) }) { Prefs.setShowAlphabetScrubber(this, it) }
        row("font", {
            val name = when (Prefs.fontStyle(this)) {
                "clean" -> "clean"
                "system" -> "phone default"
                "custom" -> Prefs.customFontName(this).lowercase().ifEmpty { "custom" }
                else -> "mono"
            }
            val size = Prefs.fontSize(this).takeIf { it != "default" }
            "[" + listOfNotNull(name, "bold".takeIf { Prefs.fontBold(this) }, size).joinToString(" · ") + "]"
        }) { showFontMenu() }
        row("gestures", { "→" }) { startActivity(Intent(this, GesturesActivity::class.java)) }
        // The optional plugin: its menu when it's installed, otherwise what it adds and where to get it
        val extendedRow = row("want more features?", { "→" }) {
            if (Extended.available(this)) { showExtendedMenu(); return@row }
            MinimalDialog.confirm(this, title = "MinimalSF Extended",
                message = "install the MinimalSF Extended plugin to add these to the long press menu " +
                    "(needs Shizuku or root):\n\n" +
                    "• open in freeform\n• force stop\n• clear cache (root)\n• clear data\n• freeze\n\n" +
                    "it also adds an extended menu here, no Shizuku or root needed: coloured icons, " +
                    "hide the search bar, auto-launch on / off and what the long press menu shows.",
                // ↗: opens a website
                negativeText = "close", positiveText = "get it ↗",
                onPositive = {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(EXTENDED_URL))) }
                    catch (_: Exception) { Toast.makeText(this, "no browser found", Toast.LENGTH_SHORT).show() }
                })
        }
        updaters += {
            extendedRow.findViewById<TextView>(R.id.rowLabel).text =
                if (Extended.available(this)) "extended menu" else "want more features?"
        }

        findViewById<View>(R.id.creditsGroup).visibility = View.VISIBLE
        findViewById<TextView>(R.id.versionText).text = "MinimalSF v" + try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) { "?" }
        findViewById<View>(R.id.githubBtn).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) }
            catch (_: Exception) { Toast.makeText(this, "no browser found", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun addSearchRows() {
        row("auto-launch delay", { "[${Prefs.autoDelay(this)}ms]" }) {
            MinimalDialog.stepSlider(this, title = "auto-launch delay", steps = delaySteps,
                currentValue = Prefs.autoDelay(this),
                onSelect = { ms -> Prefs.setAutoDelay(this, ms); refresh() }
            )
        }
        row("search matching", { if (Prefs.searchFromStart(this)) "[starts with]" else "[contains]" }) {
            Prefs.setSearchFromStart(this, !Prefs.searchFromStart(this)); refresh()
        }
        toggle("keyword replaces app name", { Prefs.keywordReplacesName(this) }) { Prefs.setKeywordReplacesName(this, it) }
        toggle("open keyboard on home", { Prefs.autoKeyboard(this) }) { Prefs.setAutoKeyboard(this, it) }
        row("floating keyboard side (landscape)", { if (Prefs.floatingKeyboardLeft(this)) "[left]" else "[right]" }) {
            Prefs.setFloatingKeyboardLeft(this, !Prefs.floatingKeyboardLeft(this)); refresh()
        }
    }

    private fun addHomeRows() {
        toggle("show clock", { Prefs.showClock(this) }) { Prefs.setShowClock(this, it) }
        row("clock format", { if (Prefs.use24hClock(this)) "[24h]" else "[12h]" }) {
            Prefs.setUse24hClock(this, !Prefs.use24hClock(this)); refresh()
        }
        row("clock size", { "[${Prefs.clockSize(this)}]" }) {
            MinimalDialog.singleChoice(this, title = "clock size", items = sizes,
                checkedIndex = sizes.indexOf(Prefs.clockSize(this))
            ) { which -> Prefs.setClockSize(this, sizes[which]); refresh() }
        }
        toggle("show date", { Prefs.showDate(this) }) { Prefs.setShowDate(this, it) }
        toggle("show ram", { Prefs.showRam(this) }) { Prefs.setShowRam(this, it) }
        row("show screen time", {
            when {
                !Prefs.showScreenTime(this) -> "[off]"
                !ScreenTime.hasAccess(this) -> "[on · needs access]"
                else -> "[on]"
            }
        }) {
            val enabling = !Prefs.showScreenTime(this)
            Prefs.setShowScreenTime(this, enabling); refresh()
            if (enabling && !ScreenTime.hasAccess(this)) {
                MinimalDialog.confirm(this, title = "usage access needed",
                    message = "to show today's screen time, allow MinimalSF under usage access.\n\nit's read on this device only - nothing is sent anywhere.",
                    positiveText = "open settings", negativeText = "skip",
                    onPositive = { ScreenTime.openAccessSettings(this) }
                )
            }
        }
        row("show now playing bar", {
            when {
                !Prefs.showMusic(this) -> "[off]"
                !MusicNotificationListener.isEnabled(this) -> "[on · needs access]"
                else -> "[on]"
            }
        }) {
            val enabling = !Prefs.showMusic(this)
            Prefs.setShowMusic(this, enabling); refresh()
            if (enabling && !MusicNotificationListener.isEnabled(this)) {
                MinimalDialog.confirm(this, title = "notification access needed",
                    message = "enable notification access for MinimalSF to read now playing info.\n\nno data is tracked.",
                    positiveText = "open settings", negativeText = "skip",
                    onPositive = { SpecialAccess.open(this, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                )
            }
        }
        toggle("show todo", { Prefs.showTodo(this) }) { Prefs.setShowTodo(this, it) }
        row("search bar position", { if (Prefs.searchAtBottom(this)) "[bottom]" else "[top]" }) {
            Prefs.setSearchAtBottom(this, !Prefs.searchAtBottom(this)); refresh()
        }
    }

    private fun addButtonRows() {
        toggle("show all apps button", { Prefs.showAllAppsButton(this) }) { Prefs.setShowAllAppsButton(this, it) }
        row("show settings button", { if (Prefs.showSettingsButton(this)) "[on]" else "[off]" }) { toggleSettingsButton() }

        val styles = arrayOf("both", "icon", "text")
        val styleNames = arrayOf("icon + text", "icon only", "text only")
        row("button style", { "[${styleNames[styles.indexOf(Prefs.navStyle(this)).coerceAtLeast(0)]}]" }) {
            MinimalDialog.singleChoice(this, title = "button style", items = styleNames,
                checkedIndex = styles.indexOf(Prefs.navStyle(this))
            ) { which -> Prefs.setNavStyle(this, styles[which]); refresh() }
        }
    }

    /** Hiding the button must leave a way back in, so settings then appears in the app list. */
    private fun toggleSettingsButton() {
        fun apply(show: Boolean) {
            Prefs.setShowSettingsButton(this, show)
            AppRepository.reload(this)
            refresh()
        }
        if (!Prefs.showSettingsButton(this)) { apply(true); return }
        MinimalDialog.confirm(this, title = "hide settings button",
            message = "you can still open settings by searching \"minimalsf\" or from all apps.",
            positiveText = "hide", negativeText = "cancel",
            onPositive = { apply(false) })
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
        // Size sits under the title too, so the whole font setting is in one place
        val size = MinimalDialog.Segmented("size", sizes, selected = { sizes.indexOf(Prefs.fontSize(this)) }) {
            Prefs.setFontSize(this, sizes[it]); refresh()
        }
        MinimalDialog.singleChoice(this, title = "font", items = items.toTypedArray(), checkedIndex = checked,
            toggle = bold, segmented = size) { actions[it]() }
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
                    Toast.makeText(this, "couldn't download - needs google play services and internet", Toast.LENGTH_LONG).show()
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

    /**
     * Draws an icon centred on the middle of lowercase letters. ImageSpan centres on the whole line,
     * ascent to descent, which leaves the icon sitting above text like "set".
     */
    private class CenteredIconSpan(private val icon: Drawable) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?) =
            icon.bounds.width()

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float,
                          top: Int, y: Int, bottom: Int, paint: Paint) {
            val xHeight = Rect().also { paint.getTextBounds("x", 0, 1, it) }.height()
            canvas.save()
            canvas.translate(x, y - xHeight / 2f - icon.bounds.height() / 2f)
            icon.draw(canvas)
            canvas.restore()
        }
    }

    /**
     * settings > extended menu. The plugin decides what's listed (pages, toggles), so new options arrive
     * with a plugin update; MinimalSF draws them and saves changes back to the plugin. [page] is a page
     * row whose rows to show, null for the top level.
     */
    private fun showExtendedMenu(page: Extended.Setting? = null) {
        val all = Extended.settings(this)
        // A plugin older than 1.1 has no menu: just open it, as before
        if (all.isEmpty()) { openExtended(); return }
        val rows = all.filter { it.parent == (page?.id ?: "") }.map { s ->
            when (s.kind) {
                Extended.TOGGLE -> {
                    var on = s.on
                    // MinimalSF's own long press entries use its own icons
                    val own = EXTENDED_ICONS[s.id]
                    MinimalDialog.MenuRow(s.label, icon = own, drawable = if (own == null) s.icon else null, isOn = { on }) {
                        if (Extended.set(this, s.id, !on)) on = !on
                        else Toast.makeText(this, "couldn't save - is MinimalSF Extended still installed?", Toast.LENGTH_SHORT).show()
                    }
                }
                Extended.PAGE -> MinimalDialog.MenuRow(s.label, drawable = s.icon) { showExtendedMenu(s) }
                else -> MinimalDialog.MenuRow(s.label, drawable = s.icon) { openExtended() }
            }
        }
        MinimalDialog.toggleMenu(this, title = page?.label ?: "extended menu",
            subtitle = if (page == null) "from MinimalSF Extended" else null, rows = rows)
    }

    private fun openExtended() {
        packageManager.getLaunchIntentForPackage(Extended.PACKAGE)?.let { startActivity(it) }
    }

    companion object {
        private const val REPO_URL = "https://github.com/DHIRAJ-J-S/MinimalSF_Launcher"
        private const val EXTENDED_URL = "https://github.com/DHIRAJ-J-S/MinimalSF_Extended/releases/latest"
        // Icons for MinimalSF's own long press entries in the extended menu, the same as in that menu
        private val EXTENDED_ICONS = mapOf("menu_shortcuts" to R.drawable.ic_shortcut, "menu_app_info" to R.drawable.ic_info,
            "menu_uninstall" to R.drawable.ic_uninstall, "menu_keyword" to R.drawable.ic_keyword, "menu_hide" to R.drawable.ic_eye_off)
        private const val EXTRA_PAGE = "page"
        // Each page's title, and its row on the main menu
        private const val PAGE_SEARCH = "search & keyboard"
        private const val PAGE_HOME = "home screen"
    }
}
