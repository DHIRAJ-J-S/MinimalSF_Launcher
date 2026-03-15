package com.minimal.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
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
