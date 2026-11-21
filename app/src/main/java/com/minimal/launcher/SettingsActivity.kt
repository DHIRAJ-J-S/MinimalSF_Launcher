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
            Toast.makeText(this, str(R.string.font_imported), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, str(R.string.not_a_valid_font_file), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.init(this)
        super.onCreate(savedInstanceState)
        Theme.update(this)
        setContentView(R.layout.activity_settings)

        settingsRoot = findViewById(R.id.settingsRoot)
        rows = findViewById(R.id.settingsRows)

        // Settings can be opened before the launcher loaded apps (e.g. process restart)
        AppRepository.init(this)

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }

        val page = intent.getStringExtra(EXTRA_PAGE)
        findViewById<TextView>(R.id.settingsTitle).text = page ?: str(R.string.settings_2)
        when (page) {
            PAGE_SEARCH -> addSearchRows()
            PAGE_HOME -> { addHomeRows(); addButtonRows() }
            PAGE_LOOK -> addLookRows()
            PAGE_ALL_APPS -> addAllAppsRows()
            else -> addMainRows()
        }
    }

    override fun onResume() {
        super.onResume()
        Theme.update(this)
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
        row(label, { if (get()) str(R.string.on) else str(R.string.off) }) { set(!get()); refresh() }

    /** Hides [row] and its divider while [cond] is false, updating as settings change. */
    private fun shownWhen(row: View, cond: () -> Boolean) {
        val divider = rows.getChildAt(rows.indexOfChild(row) + 1)
        updaters += {
            val vis = if (cond()) View.VISIBLE else View.GONE
            row.visibility = vis; divider?.visibility = vis
        }
    }

    /** A row that opens one of the pages below. */
    private fun pageRow(page: String) = row(page, { "→" }) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page))
    }

    // --- Pages ---

    private fun addMainRows() {
        // One row: sets default when we aren't, lets you switch away when we are
        val defaultRow = row(str(R.string.default_launcher), { if (isDefaultLauncher()) str(R.string.set) else "→" }) {
            if (isDefaultLauncher()) {
                MinimalDialog.confirm(this, title = str(R.string.change_default_launcher),
                    message = str(R.string.this_will_open_android_s),
                    positiveText = str(R.string.open), negativeText = str(R.string.cancel),
                    onPositive = { openHomeSettings() }
                )
            } else openHomeSettings()
        }
        updaters += {
            val isDefault = isDefaultLauncher()
            defaultRow.findViewById<TextView>(R.id.rowLabel).text = if (isDefault) str(R.string.default_launcher) else str(R.string.set_as_default_launcher)
            if (isDefault) setStateIcon(defaultRow.findViewById(R.id.rowState), R.drawable.ic_check)
        }

        pageRow(PAGE_LOOK)
        pageRow(PAGE_HOME)
        pageRow(PAGE_SEARCH)
        pageRow(PAGE_ALL_APPS)
        row(str(R.string.gestures), { "→" }) { startActivity(Intent(this, GesturesActivity::class.java)) }
        row(str(R.string.long_press_menu), { "→" }) { showLongPressMenu() }
        // The optional plugin: its menu when it's installed, otherwise what it adds and where to get it
        val extendedRow = row(str(R.string.want_more_features), { if (Extended.updateAvailable(this)) str(R.string.plugin_update_available) else "→" }) {
            if (Extended.available(this)) { showExtendedMenu(); return@row }
            MinimalDialog.confirm(this, title = str(R.string.minimalsf_extended),
                message = str(R.string.install_the_minimalsf_extended_plugin),
                // ↗: opens a website
                negativeText = str(R.string.close), positiveText = str(R.string.get_it),
                onPositive = { openReleases() })
        }
        updaters += {
            extendedRow.findViewById<TextView>(R.id.rowLabel).text =
                if (Extended.available(this)) str(R.string.extended_menu) else str(R.string.want_more_features)
        }

        findViewById<View>(R.id.creditsGroup).visibility = View.VISIBLE
        findViewById<TextView>(R.id.versionText).text = str(R.string.minimalsf_v) + try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) { "?" }
        findViewById<View>(R.id.githubBtn).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) }
            catch (_: Exception) { Toast.makeText(this, str(R.string.no_browser_found), Toast.LENGTH_SHORT).show() }
        }
    }

    private fun addSearchRows() {
        row(str(R.string.auto_launch_delay), { "[${Prefs.autoDelay(this)}ms]" }) {
            MinimalDialog.stepSlider(this, title = str(R.string.auto_launch_delay), steps = delaySteps,
                currentValue = Prefs.autoDelay(this),
                onSelect = { ms -> Prefs.setAutoDelay(this, ms); refresh() }
            )
        }
        toggle(str(R.string.auto_launch_a_single_match), { Prefs.autoLaunchSingle(this) }) { Prefs.setAutoLaunchSingle(this, it) }
        row(str(R.string.search_matching), { if (Prefs.searchFromStart(this)) str(R.string.starts_with) else str(R.string.contains) }) {
            Prefs.setSearchFromStart(this, !Prefs.searchFromStart(this)); refresh()
        }
        toggle(str(R.string.keyword_replaces_app_name), { Prefs.keywordReplacesName(this) }) { Prefs.setKeywordReplacesName(this, it) }
        // Shortcuts in search: saved ones always, apps' built-in ones with MinimalSF Extended
        row(str(R.string.saved_shortcuts_2), { Prefs.pinnedShortcuts(this).size.let { if (it == 0) str(R.string.none_2) else "[$it]" } }) { showSavedShortcuts() }
        val modes = arrayOf("same", "app", "shortcut", "never")
        val modeNames = arrayOf(str(R.string.same_as_any_match), str(R.string.prefer_the_app), str(R.string.prefer_the_shortcut), str(R.string.never_open_a_shortcut))
        row(str(R.string.app_and_shortcut_both_match), { "[${modeNames[modes.indexOf(Prefs.shortcutAutoLaunch(this)).coerceAtLeast(0)]}]" }) {
            MinimalDialog.singleChoice(this, title = str(R.string.auto_launch_with_shortcuts), items = modeNames,
                checkedIndex = modes.indexOf(Prefs.shortcutAutoLaunch(this)).coerceAtLeast(0)) {
                Prefs.setShortcutAutoLaunch(this, modes[it]); refresh()
            }
        }
        toggle(str(R.string.open_keyboard_on_home), { Prefs.autoKeyboard(this) }) { Prefs.setAutoKeyboard(this, it) }
        row(str(R.string.floating_keyboard_side_landscape), { if (Prefs.floatingKeyboardLeft(this)) str(R.string.left) else str(R.string.right) }) {
            Prefs.setFloatingKeyboardLeft(this, !Prefs.floatingKeyboardLeft(this)); refresh()
        }
    }

    private fun addHomeRows() {
        toggle(str(R.string.show_clock), { Prefs.showClock(this) }) { Prefs.setShowClock(this, it) }
        row(str(R.string.clock_format), { if (Prefs.use24hClock(this)) "[24h]" else "[12h]" }) {
            Prefs.setUse24hClock(this, !Prefs.use24hClock(this)); refresh()
        }
        val cases = arrayOf("phone", "lower", "upper")
        val caseNames = arrayOf(str(R.string.as_the_phone_writes_it), str(R.string.pm), str(R.string.pm_2))
        val amPm = row(str(R.string.am_pm), { "[${caseNames[cases.indexOf(Prefs.amPmCase(this)).coerceAtLeast(0)]}]" }) {
            MinimalDialog.singleChoice(this, title = str(R.string.am_pm), items = caseNames,
                checkedIndex = cases.indexOf(Prefs.amPmCase(this)).coerceAtLeast(0)) { Prefs.setAmPmCase(this, cases[it]); refresh() }
        }
        shownWhen(amPm) { !Prefs.use24hClock(this) }
        row(str(R.string.clock_size), { "[${Prefs.clockSize(this)}]" }) {
            MinimalDialog.singleChoice(this, title = str(R.string.clock_size), items = sizes,
                checkedIndex = sizes.indexOf(Prefs.clockSize(this))
            ) { which -> Prefs.setClockSize(this, sizes[which]); refresh() }
        }
        toggle(str(R.string.show_date), { Prefs.showDate(this) }) { Prefs.setShowDate(this, it) }
        toggle(str(R.string.show_ram), { Prefs.showRam(this) }) { Prefs.setShowRam(this, it) }
        row(str(R.string.show_screen_time), {
            when {
                !Prefs.showScreenTime(this) -> str(R.string.off)
                !ScreenTime.hasAccess(this) -> str(R.string.on_needs_access)
                else -> str(R.string.on)
            }
        }) {
            val enabling = !Prefs.showScreenTime(this)
            Prefs.setShowScreenTime(this, enabling); refresh()
            if (enabling && !ScreenTime.hasAccess(this)) {
                MinimalDialog.confirm(this, title = str(R.string.usage_access_needed),
                    message = str(R.string.to_show_today_s_screen),
                    positiveText = str(R.string.open_settings), negativeText = str(R.string.skip),
                    onPositive = { ScreenTime.openAccessSettings(this) }
                )
            }
        }
        row(str(R.string.show_now_playing_bar), {
            when {
                !Prefs.showMusic(this) -> str(R.string.off)
                !MusicNotificationListener.isEnabled(this) -> str(R.string.on_needs_access)
                Prefs.musicOnlyWhenPlaying(this) -> str(R.string.while_playing)
                else -> str(R.string.always)
            }
        }) {
            val modes = arrayOf("off", "always", "only while music plays")
            val current = when { !Prefs.showMusic(this) -> 0; Prefs.musicOnlyWhenPlaying(this) -> 2; else -> 1 }
            MinimalDialog.singleChoice(this, title = str(R.string.now_playing_bar), items = modes, checkedIndex = current) { pick ->
                val enabling = pick != 0 && !Prefs.showMusic(this)
                Prefs.setShowMusic(this, pick != 0)
                Prefs.setMusicOnlyWhenPlaying(this, pick == 2); refresh()
                if (enabling && !MusicNotificationListener.isEnabled(this)) {
                    MinimalDialog.confirm(this, title = str(R.string.notification_access_needed),
                        message = str(R.string.enable_notification_access_for_minimalsf),
                        positiveText = str(R.string.open_settings), negativeText = str(R.string.skip),
                        onPositive = { SpecialAccess.open(this, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }
                    )
                }
            }
        }
        toggle(str(R.string.show_todo), { Prefs.showTodo(this) }) { Prefs.setShowTodo(this, it) }
        row(str(R.string.search_bar_position), { if (Prefs.searchAtBottom(this)) str(R.string.bottom) else str(R.string.top) }) {
            Prefs.setSearchAtBottom(this, !Prefs.searchAtBottom(this)); refresh()
        }
        toggle(str(R.string.hide_empty_search_bar), { Prefs.hideEmptySearch(this) }) { Prefs.setHideEmptySearch(this, it) }
    }

    private fun addLookRows() {
        val themes = arrayOf("dark", "light", "system")
        val themeNames = arrayOf(str(R.string.dark), str(R.string.light), str(R.string.follow_the_phone))
        row(str(R.string.theme), { "[${themeNames[themes.indexOf(Prefs.theme(this)).coerceAtLeast(0)]}]" }) {
            MinimalDialog.singleChoice(this, title = str(R.string.theme), items = themeNames,
                checkedIndex = themes.indexOf(Prefs.theme(this)).coerceAtLeast(0)) {
                Prefs.setTheme(this, themes[it]); Theme.apply(this)
            }
        }
        row(str(R.string.font), {
            val name = when (Prefs.fontStyle(this)) {
                "clean" -> str(R.string.clean)
                "system" -> str(R.string.phone_default)
                "custom" -> Prefs.customFontName(this).lowercase().ifEmpty { "custom" }
                else -> str(R.string.mono)
            }
            val size = Prefs.fontSize(this).takeIf { it != "default" }
            "[" + listOfNotNull(name, "bold".takeIf { Prefs.fontBold(this) }, size).joinToString(" · ") + "]"
        }) { showFontMenu() }
        toggle(str(R.string.coloured_app_icons), { Prefs.colourIcons(this) }) { Prefs.setColourIcons(this, it) }
        toggle(str(R.string.hide_status_bar), { Prefs.hideStatusBar(this) }) { Prefs.setHideStatusBar(this, it) }
        toggle(str(R.string.show_wallpaper), { Prefs.showWallpaper(this) }) { Prefs.setShowWallpaper(this, it) }
        val dim = row(str(R.string.wallpaper_dim), { "[${Prefs.wallpaperDim(this)}%]" }) {
            MinimalDialog.stepSlider(this, title = str(R.string.wallpaper_dim), steps = longArrayOf(30, 40, 50, 60, 70, 80, 90),
                currentValue = Prefs.wallpaperDim(this), unit = "%",
                onSelect = { v -> Prefs.setWallpaperDim(this, v); refresh() })
        }
        val middle = toggle(str(R.string.lighter_in_the_middle), { Prefs.wallpaperGradient(this) }) { Prefs.setWallpaperGradient(this, it) }
        shownWhen(dim) { Prefs.showWallpaper(this) }
        shownWhen(middle) { Prefs.showWallpaper(this) }
    }

    private fun addAllAppsRows() {
        row(str(R.string.all_apps_view), {
            when {
                Prefs.allAppsGrid(this) -> str(R.string.grid_columns, Prefs.allAppsColumns(this))
                !Prefs.showAppIcons(this) -> str(R.string.list_without_icons)
                else -> str(R.string.list)
            }
        }) {
            MinimalDialog.gridPicker(this, title = str(R.string.all_apps_view), isGrid = Prefs.allAppsGrid(this),
                icons = Prefs.showAppIcons(this), columns = Prefs.allAppsColumns(this),
                min = Prefs.MIN_GRID_COLUMNS, max = Prefs.MAX_GRID_COLUMNS
            ) { grid, icons, columns ->
                Prefs.setAllAppsGrid(this, grid); Prefs.setShowAppIcons(this, icons)
                Prefs.setAllAppsColumns(this, columns); refresh()
            }
        }
        toggle(str(R.string.a_z_scrubber_in_all), { Prefs.showAlphabetScrubber(this) }) { Prefs.setShowAlphabetScrubber(this, it) }
    }

    private fun addButtonRows() {
        toggle(str(R.string.show_all_apps_button), { Prefs.showAllAppsButton(this) }) { Prefs.setShowAllAppsButton(this, it) }
        row(str(R.string.show_settings_button), { if (Prefs.showSettingsButton(this)) str(R.string.on) else str(R.string.off) }) { toggleSettingsButton() }

        val styles = arrayOf("both", "icon", "text")
        val styleNames = arrayOf(str(R.string.icon_text), str(R.string.icon_only), str(R.string.text_only))
        row(str(R.string.button_style), { "[${styleNames[styles.indexOf(Prefs.navStyle(this)).coerceAtLeast(0)]}]" }) {
            MinimalDialog.singleChoice(this, title = str(R.string.button_style), items = styleNames,
                checkedIndex = styles.indexOf(Prefs.navStyle(this))
            ) { which -> Prefs.setNavStyle(this, styles[which]); refresh() }
        }
    }

    /** Hiding the button must leave a way back in, so settings then appears in the app list. */
    /** Saved shortcuts ("add to home screen" from other apps): give one a keyword, or remove it. */
    private fun showSavedShortcuts() {
        val pinned = Prefs.pinnedShortcuts(this)
        if (pinned.isEmpty()) {
            MinimalDialog.confirm(this, title = str(R.string.saved_shortcuts_2),
                message = str(R.string.none_yet_n_nuse_add),
                positiveText = str(R.string.ok), onPositive = {})
            return
        }
        val keywords = Prefs.getKeywords(this)
        fun key(e: String) = e.split('|').let { SearchShortcuts.key(it[0], it.getOrElse(1) { "" }) }
        MinimalDialog.options(this, title = str(R.string.saved_shortcuts_2), subtitle = str(R.string.shown_in_search_below_apps),
            items = pinned.map { it.substringAfterLast('|') }.toTypedArray(),
            icons = IntArray(pinned.size) { R.drawable.ic_shortcut },
            trailing = pinned.map { keywords[key(it)] ?: AppRepository.labelFor(this, it.substringBefore('|')).lowercase() }.toTypedArray()
        ) { i ->
            val entry = pinned[i]
            val name = entry.substringAfterLast('|')
            MinimalDialog.options(this, title = name, subtitle = AppRepository.labelFor(this, entry.substringBefore('|')).lowercase(),
                items = arrayOf(str(R.string.set_keyword), str(R.string.remove)), icons = intArrayOf(R.drawable.ic_keyword, R.drawable.ic_close),
                trailing = arrayOf(keywords[key(entry)], null)) { pick ->
                if (pick == 0) KeywordPrompt.show(this, key(entry), name) { refresh() }
                else MinimalDialog.confirm(this, title = str(R.string.remove_shortcut), message = str(R.string.remove_from_saved_shortcuts, name),
                    positiveText = str(R.string.remove), negativeText = str(R.string.cancel),
                    onPositive = { Prefs.removePinnedShortcut(this, entry); refresh() })
            }
        }
    }

    private fun toggleSettingsButton() {
        fun apply(show: Boolean) {
            Prefs.setShowSettingsButton(this, show)
            AppRepository.reload(this)
            refresh()
        }
        if (!Prefs.showSettingsButton(this)) { apply(true); return }
        MinimalDialog.confirm(this, title = str(R.string.hide_settings_button),
            message = str(R.string.you_can_still_open_settings),
            positiveText = str(R.string.hide), negativeText = str(R.string.cancel),
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
        val bold = MinimalDialog.TitleToggle(str(R.string.bold), isOn = { Prefs.fontBold(this) }) {
            Prefs.setFontBold(this, !Prefs.fontBold(this)); FontManager.clearCache(); refresh()
        }
        // Size sits under the title too, so the whole font setting is in one place
        val size = MinimalDialog.Segmented(str(R.string.size), sizes, selected = { sizes.indexOf(Prefs.fontSize(this)) }) {
            Prefs.setFontSize(this, sizes[it]); refresh()
        }
        MinimalDialog.singleChoice(this, title = str(R.string.font), items = items.toTypedArray(), checkedIndex = checked,
            toggle = bold, segmented = size) { actions[it]() }
    }

    private fun setBuiltInFont(style: String) {
        Prefs.setFontStyle(this, style); FontManager.clearCache(); refresh()
    }

    private fun showFontDownloads() {
        val fonts = GoogleFonts.MONO + GoogleFonts.SANS
        val current = if (Prefs.fontStyle(this) == "custom") Prefs.customFontName(this) else ""
        MinimalDialog.options(this, title = str(R.string.google_fonts), subtitle = str(R.string.free_open_font_license),
            items = fonts.map { it.lowercase() }.toTypedArray(),
            icons = IntArray(fonts.size) { if (fonts[it] == current) R.drawable.ic_radio_on else R.drawable.ic_download },
            trailing = Array(fonts.size) { if (it < GoogleFonts.MONO.size) str(R.string.mono) else str(R.string.sans) }
        ) { which ->
            val name = fonts[which]
            Toast.makeText(this, str(R.string.downloading, name.lowercase()), Toast.LENGTH_SHORT).show()
            GoogleFonts.download(this, name) { ok ->
                if (isDestroyed) return@download
                if (ok) {
                    Prefs.setFontStyle(this, "custom"); FontManager.clearCache(); refresh()
                    Toast.makeText(this, str(R.string.applied, name.lowercase()), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, str(R.string.couldn_t_download_needs_google), Toast.LENGTH_LONG).show()
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
        val update = Extended.updateAvailable(this)
        if (all.isEmpty()) {
            // A plugin older than 1.1 has no menu: say so and offer the update, or just open it
            if (!update) { openExtended(); return }
            MinimalDialog.confirm(this, title = str(R.string.extended_menu),
                message = str(R.string.this_version_of_minimalsf_extended),
                negativeText = str(R.string.open_plugin), onNegative = { openExtended() },
                positiveText = str(R.string.update_now), onPositive = { openReleases() })
            return
        }
        // Options MinimalSF now has itself (look, home screen, search, long press menu): an older plugin still lists them
        val rows = all.filter { it.parent == (page?.id ?: "") && it.id !in MOVED_TO_LAUNCHER }.map { s ->
            when (s.kind) {
                Extended.TOGGLE -> {
                    var on = s.on
                    // MinimalSF's own long press entries use its own icons
                    val own = EXTENDED_ICONS[s.id]
                    MinimalDialog.MenuRow(s.label, icon = own, drawable = if (own == null) s.icon else null, isOn = { on }) {
                        if (Extended.set(this, s.id, !on)) on = !on
                        else Toast.makeText(this, str(R.string.couldn_t_save_is_minimalsf), Toast.LENGTH_SHORT).show()
                    }
                }
                Extended.PAGE -> EXTENDED_ICONS[s.id].let { own ->
                    MinimalDialog.MenuRow(s.label, icon = own, drawable = if (own == null) s.icon else null) { showExtendedMenu(s) }
                }
                else -> MinimalDialog.MenuRow(s.label, drawable = s.icon) { openExtended() }
            }
        }
        MinimalDialog.toggleMenu(this, title = page?.label ?: str(R.string.extended_menu),
            subtitle = if (page == null) str(R.string.from_minimalsf_extended) else null, rows = rows,
            // MinimalSF has no internet: it knows the plugin version that was out when it was released
            button = if (update) MinimalDialog.TitleButton(str(R.string.update_now)) { openReleases() } else null)
    }

    /**
     * What a long press on an app shows: MinimalSF's own entries, and with MinimalSF Extended each of its
     * actions, "[on] [main]": shown at all, and in the main menu or under "extended…".
     */
    private fun showLongPressMenu() {
        val own = Prefs.MENU_ENTRIES.map { (id, label) ->
            MinimalDialog.MenuRow(label, icon = EXTENDED_ICONS[id], isOn = { Prefs.menuShows(this, id) }) {
                Prefs.setMenuShows(this, id, !Prefs.menuShows(this, id))
            }
        }
        val plugin = Extended.settings(this)
        val state = plugin.filter { it.kind == Extended.TOGGLE }.associate { it.id to it.on }.toMutableMap()
        fun flip(id: String) {
            val v = !(state[id] ?: false)
            if (Extended.set(this, id, v)) state[id] = v
            else Toast.makeText(this, str(R.string.couldn_t_save_is_minimalsf), Toast.LENGTH_SHORT).show()
        }
        val usable = Extended.actions(this).map { it.id }.toSet()
        val actions = plugin.filter { it.kind == Extended.TOGGLE && it.id.startsWith("action_") }.map { a ->
            val id = a.id.removePrefix("action_")
            // The plugin only offers what it can run now (clear cache needs root, the rest Shizuku or root)
            val label = a.label.removePrefix("extended · ").let { if (id in usable) it else str(R.string.action_needs_root, it) }
            MinimalDialog.MenuRow(label, drawable = a.icon,
                isOn = { state[a.id] ?: true }, inMain = { state["main_$id"] ?: false },
                onMain = { flip("main_$id") }) { flip(a.id) }
        }
        MinimalDialog.toggleMenu(this, title = str(R.string.long_press_menu),
            subtitle = if (actions.isEmpty()) null else str(R.string.minimalsf_extended_actions_on_off),
            rows = own + actions)
    }

    private fun openExtended() {
        packageManager.getLaunchIntentForPackage(Extended.PACKAGE)?.let { startActivity(it) }
    }

    private fun openReleases() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Extended.RELEASES_URL))) }
        catch (_: Exception) { Toast.makeText(this, str(R.string.no_browser_found), Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val REPO_URL = "https://github.com/DHIRAJ-J-S/MinimalSF_Launcher"
        // Icons for MinimalSF's own long press entries in the extended menu, the same as in that menu,
        // and for the "actions in the main menu" page
        private val MOVED_TO_LAUNCHER = setOf("long_press", "hide_search", "colour_icons", "auto_launch")
        private val EXTENDED_ICONS = mapOf("menu_shortcuts" to R.drawable.ic_shortcut, "menu_app_info" to R.drawable.ic_info,
            "menu_uninstall" to R.drawable.ic_uninstall, "menu_keyword" to R.drawable.ic_keyword, "menu_hide" to R.drawable.ic_eye_off,
            "main_menu" to R.drawable.ic_extended)
        private const val EXTRA_PAGE = "page"
        // Each page's title, and its row on the main menu
        private const val PAGE_SEARCH = "search & keyboard"
        private const val PAGE_HOME = "home screen"
        private const val PAGE_LOOK = "look"
        private const val PAGE_ALL_APPS = "all apps"
    }
}
