package com.minimal.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private val delayOptions = longArrayOf(300, 400, 500, 600, 800, 1000, 1500)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }

        // Set as default
        findViewById<View>(R.id.setDefaultBtn).setOnClickListener {
            try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
        }

        // Unset / change default
        findViewById<View>(R.id.unsetDefaultBtn).setOnClickListener {
            MinimalDialog.confirm(this,
                title = "change default launcher",
                message = "this will open android's home app settings where you can select a different launcher.",
                positiveText = "open",
                negativeText = "cancel",
                onPositive = {
                    try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                    catch (_: Exception) { try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {} }
                }
            )
        }

        // Music toggle
        val toggleState = findViewById<TextView>(R.id.musicToggleState)
        updateToggle(toggleState)
        findViewById<View>(R.id.musicToggle).setOnClickListener {
            val enabling = !Prefs.showMusic(this)
            Prefs.setShowMusic(this, enabling)
            updateToggle(toggleState)
            if (enabling && !isNotificationListenerEnabled()) {
                MinimalDialog.confirm(this,
                    title = "notification access needed",
                    message = "enable notification access for MinimalSF to read now playing info.\n\nno data is tracked.",
                    positiveText = "open settings",
                    negativeText = "skip",
                    onPositive = {
                        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
                    }
                )
            }
        }

        // Auto-launch delay
        val delayValue = findViewById<TextView>(R.id.delayValue)
        updateDelay(delayValue)
        findViewById<View>(R.id.delayBtn).setOnClickListener {
            val current = Prefs.autoDelay(this)
            val labels = delayOptions.map { "${it}ms" }.toTypedArray()
            val checked = delayOptions.indexOf(current).coerceAtLeast(0)
            MinimalDialog.singleChoice(this,
                title = "auto-launch delay",
                items = labels,
                checkedIndex = checked,
                onSelect = { which ->
                    Prefs.setAutoDelay(this, delayOptions[which])
                    updateDelay(delayValue)
                }
            )
        }

        // Clock format
        val clockState = findViewById<TextView>(R.id.clockToggleState)
        updateClockToggle(clockState)
        findViewById<View>(R.id.clockToggle).setOnClickListener {
            Prefs.setUse24hClock(this, !Prefs.use24hClock(this))
            updateClockToggle(clockState)
        }
    }

    private fun updateToggle(tv: TextView) { tv.text = if (Prefs.showMusic(this)) "[on]" else "[off]" }
    private fun updateDelay(tv: TextView) { tv.text = "${Prefs.autoDelay(this)}ms" }
    private fun updateClockToggle(tv: TextView) { tv.text = if (Prefs.use24hClock(this)) "[24h]" else "[12h]" }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(packageName)
    }
}
