package com.minimal.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val delaySteps = longArrayOf(0, 100, 200, 300, 404, 500, 600)
    private lateinit var setDefaultLabel: TextView
    private lateinit var setDefaultArrow: TextView
    private lateinit var doubleTapState: TextView
    private lateinit var longPressState: TextView
    private var allApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setDefaultLabel = findViewById(R.id.setDefaultLabel)
        setDefaultArrow = findViewById(R.id.setDefaultArrow)
        doubleTapState = findViewById(R.id.doubleTapState)
        longPressState = findViewById(R.id.longPressState)

        loadApps()

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }

        findViewById<View>(R.id.setDefaultBtn).setOnClickListener {
            if (!isDefaultLauncher()) {
                try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
            }
        }

        findViewById<View>(R.id.unsetDefaultBtn).setOnClickListener {
            MinimalDialog.confirm(this, title = "change default launcher",
                message = "this will open android's home app settings where you can select a different launcher.",
                positiveText = "open", negativeText = "cancel",
                onPositive = { try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {} }
            )
        }

        val toggleState = findViewById<TextView>(R.id.musicToggleState)
        updateToggle(toggleState)
        findViewById<View>(R.id.musicToggle).setOnClickListener {
            val enabling = !Prefs.showMusic(this)
            Prefs.setShowMusic(this, enabling); updateToggle(toggleState)
            if (enabling && !isNotificationListenerEnabled()) {
                MinimalDialog.confirm(this, title = "notification access needed",
                    message = "enable notification access for MinimalSF to read now playing info.\n\nno data is tracked.",
                    positiveText = "open settings", negativeText = "skip",
                    onPositive = { try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {} }
                )
            }
        }

        val delayValue = findViewById<TextView>(R.id.delayValue)
        updateDelay(delayValue)
        findViewById<View>(R.id.delayBtn).setOnClickListener {
            MinimalDialog.stepSlider(this, title = "auto-launch delay", steps = delaySteps,
                currentValue = Prefs.autoDelay(this),
                onSelect = { ms -> Prefs.setAutoDelay(this, ms); updateDelay(delayValue) }
            )
        }

        val clockState = findViewById<TextView>(R.id.clockToggleState)
        updateClockToggle(clockState)
        findViewById<View>(R.id.clockToggle).setOnClickListener {
            Prefs.setUse24hClock(this, !Prefs.use24hClock(this)); updateClockToggle(clockState)
        }

        val searchPosState = findViewById<TextView>(R.id.searchPosState)
        updateSearchPos(searchPosState)
        findViewById<View>(R.id.searchPosToggle).setOnClickListener {
            Prefs.setSearchAtBottom(this, !Prefs.searchAtBottom(this)); updateSearchPos(searchPosState)
        }

        // Search mode
        val searchModeState = findViewById<TextView>(R.id.searchModeState)
        updateSearchMode(searchModeState)
        findViewById<View>(R.id.searchModeToggle).setOnClickListener {
            Prefs.setSearchFromStart(this, !Prefs.searchFromStart(this)); updateSearchMode(searchModeState)
        }

        // Font style
        val fontStyleState = findViewById<TextView>(R.id.fontStyleState)
        updateFontStyle(fontStyleState)
        findViewById<View>(R.id.fontStyleBtn).setOnClickListener {
            MinimalDialog.options(this, title = "font",
                items = arrayOf("monospace (code)", "clean (sans-serif)", "import custom font")
            ) { which ->
                when (which) {
                    0 -> { Prefs.setFontStyle(this, "mono"); FontManager.clearCache(); updateFontStyle(fontStyleState) }
                    1 -> { Prefs.setFontStyle(this, "clean"); FontManager.clearCache(); updateFontStyle(fontStyleState) }
                    2 -> pickFontFile(fontStyleState)
                }
            }
        }

        // Font size
        val fontSizeState = findViewById<TextView>(R.id.fontSizeState)
        updateFontSize(fontSizeState)
        findViewById<View>(R.id.fontSizeBtn).setOnClickListener {
            MinimalDialog.options(this, title = "font size",
                items = arrayOf("small", "default", "large")
            ) { which ->
                val size = arrayOf("small", "default", "large")[which]
                Prefs.setFontSize(this, size); updateFontSize(fontSizeState)
            }
        }

        // Clock size
        val clockSizeState = findViewById<TextView>(R.id.clockSizeState)
        updateClockSize(clockSizeState)
        findViewById<View>(R.id.clockSizeBtn).setOnClickListener {
            MinimalDialog.options(this, title = "clock size",
                items = arrayOf("small", "default", "large")
            ) { which ->
                val size = arrayOf("small", "default", "large")[which]
                Prefs.setClockSize(this, size); updateClockSize(clockSizeState)
            }
        }

        // Double tap action
        updateDoubleTap()
        findViewById<View>(R.id.doubleTapBtn).setOnClickListener {
            MinimalDialog.options(this, title = "double tap action",
                items = arrayOf("lock screen", "open app")
            ) { which ->
                when (which) {
                    0 -> { Prefs.setDoubleTapAction(this, "lock"); updateDoubleTap() }
                    1 -> showAppPicker { pkg -> Prefs.setDoubleTapAction(this, pkg); updateDoubleTap() }
                }
            }
        }

        // Long press action
        updateLongPress()
        findViewById<View>(R.id.longPressBtn).setOnClickListener {
            MinimalDialog.options(this, title = "long press action",
                items = arrayOf("none (disabled)", "open app")
            ) { which ->
                when (which) {
                    0 -> { Prefs.setLongPressAction(this, ""); updateLongPress() }
                    1 -> showAppPicker { pkg -> Prefs.setLongPressAction(this, pkg); updateLongPress() }
                }
            }
        }

        updateDefaultStatus()
    }

    override fun onResume() {
        super.onResume()
        updateDefaultStatus()
    }

    private fun showAppPicker(onSelect: (String) -> Unit) {
        MinimalDialog.appList(this, "choose app", allApps,
            onTap = { app -> onSelect(app.packageName) },
            onLongPress = {}
        )
    }

    private fun loadApps() {
        val pm = packageManager
        allApps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun getAppLabel(pkg: String): String {
        return try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
        catch (_: Exception) { pkg }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    private fun updateDefaultStatus() {
        if (isDefaultLauncher()) {
            setDefaultLabel.text = "✓  MinimalSF is your default launcher"
            setDefaultLabel.setTextColor(0xFFFFFFFF.toInt()); setDefaultArrow.text = ""
        } else {
            setDefaultLabel.text = "set as default launcher"
            setDefaultLabel.setTextColor(resources.getColor(R.color.grey_text, null)); setDefaultArrow.text = "→"
        }
    }

    private fun updateToggle(tv: TextView) { tv.text = if (Prefs.showMusic(this)) "[on]" else "[off]" }
    private fun updateDelay(tv: TextView) { tv.text = "${Prefs.autoDelay(this)}ms" }
    private fun updateClockToggle(tv: TextView) { tv.text = if (Prefs.use24hClock(this)) "[24h]" else "[12h]" }
    private fun updateSearchPos(tv: TextView) { tv.text = if (Prefs.searchAtBottom(this)) "[bottom]" else "[top]" }
    private fun updateSearchMode(tv: TextView) { tv.text = if (Prefs.searchFromStart(this)) "[starts with]" else "[contains]" }

    private fun updateFontStyle(tv: TextView) {
        tv.text = when (Prefs.fontStyle(this)) {
            "clean" -> "[clean]"
            "custom" -> "[custom]"
            else -> "[mono]"
        }
    }

    private fun updateFontSize(tv: TextView) { tv.text = "[${Prefs.fontSize(this)}]" }
    private fun updateClockSize(tv: TextView) { tv.text = "[${Prefs.clockSize(this)}]" }

    private var fontStyleStateRef: TextView? = null

    private val fontPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ok = FontManager.copyFontToInternal(this, uri)
            if (ok) {
                Prefs.setFontStyle(this, "custom")
                FontManager.clearCache()
                fontStyleStateRef?.let { updateFontStyle(it) }
                Toast.makeText(this, "font imported", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "failed to import font", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickFontFile(stateView: TextView) {
        fontStyleStateRef = stateView
        fontPickerLauncher.launch("*/*")
    }

    private fun updateDoubleTap() {
        val action = Prefs.doubleTapAction(this)
        doubleTapState.text = if (action == "lock") "[lock]" else "[${getAppLabel(action)}]"
    }

    private fun updateLongPress() {
        val action = Prefs.longPressAction(this)
        longPressState.text = if (action.isEmpty()) "[none]" else "[${getAppLabel(action)}]"
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(packageName)
    }
}
