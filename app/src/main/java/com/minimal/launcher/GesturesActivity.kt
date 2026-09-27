package com.minimal.launcher

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class GesturesActivity : AppCompatActivity() {

    private lateinit var root: View
    private val stateViews = mutableMapOf<Prefs.Gesture, TextView>()
    private var pendingGesture: Prefs.Gesture? = null

    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val g = pendingGesture ?: return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        // The picker grants one-time read access to the chosen number - no contacts permission needed
        val cols = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val picked = try {
            contentResolver.query(uri, cols, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) to (c.getString(1) ?: c.getString(0)) else null
            }
        } catch (_: Exception) { null }
        if (picked == null) { Toast.makeText(this, "couldn't read that contact", Toast.LENGTH_SHORT).show(); return@registerForActivityResult }
        save(g, GestureActions.call(picked.first, picked.second.lowercase()))
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            MinimalDialog.confirm(this, title = "call directly?",
                message = "allow phone calls so the gesture calls straight away.\n\nwithout it, the dialer opens with the number filled in.",
                positiveText = "allow", negativeText = "use dialer",
                onPositive = { callPermission.launch(Manifest.permission.CALL_PHONE) })
        }
    }

    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gestures)
        root = findViewById(R.id.gesturesRoot)
        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }
        AppRepository.init(this)

        val rows = findViewById<LinearLayout>(R.id.gestureRows)
        Prefs.Gesture.values().forEachIndexed { i, g ->
            if (i > 0) rows.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (resources.displayMetrics.density).toInt().coerceAtLeast(1)).apply {
                    val m = (20 * resources.displayMetrics.density).toInt(); marginStart = m; marginEnd = m
                }
                setBackgroundColor(getColor(R.color.grey_border))
            })
            val row = LayoutInflater.from(this).inflate(R.layout.item_setting_row, rows, false)
            row.findViewById<TextView>(R.id.rowLabel).text = g.title
            stateViews[g] = row.findViewById(R.id.rowState)
            row.setOnClickListener { pickAction(g) }
            rows.addView(row)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        stateViews.forEach { (g, tv) ->
            val code = Prefs.gesture(this, g)
            tv.text = when {
                code == GestureActions.LOCK && !LockAccessibilityService.isEnabled(this) -> "[lock · needs access]"
                else -> "[${GestureActions.label(this, code).lowercase()}]"
            }
        }
        FontManager.applyTo(root, FontManager.getTypeface(this), FontManager.sizeMultiplier(this))
    }

    private fun save(g: Prefs.Gesture, code: String) {
        Prefs.setGesture(this, g, code)
        refresh()
    }

    // --- Action picker ---

    private fun pickAction(g: Prefs.Gesture) {
        val choices = mutableListOf<Pair<String, () -> Unit>>()
        val icons = mutableListOf<Int>()
        fun add(label: String, icon: Int, action: () -> Unit) { choices += label to action; icons += icon }
        fun simple(code: String) = add(GestureActions.label(this, code), GestureActions.icon(code)) { save(g, code) }

        simple("")
        add("lock screen", R.drawable.ic_lock) {
            save(g, GestureActions.LOCK)
            if (LockAccessibilityService.isSupported && !LockAccessibilityService.isEnabled(this)) promptAccessibility()
        }
        add("open app…", R.drawable.ic_app) { pickApp { save(g, GestureActions.app(it.packageName)) } }
        add("call contact…", R.drawable.ic_call) { pendingGesture = g; pickContact() }
        add("app shortcut…", R.drawable.ic_shortcut) { pickAppShortcut(g) }
        if (Prefs.pinnedShortcuts(this).isNotEmpty()) add("saved shortcuts…", R.drawable.ic_shortcut) { pickPinned(g) }
        add("app screen…", R.drawable.ic_activity) { pickApp { pickActivity(g, it) } }
        listOf(GestureActions.FLASHLIGHT, GestureActions.CAMERA, GestureActions.PLAY_PAUSE, GestureActions.NEXT_TRACK,
            GestureActions.NOTIFICATIONS, GestureActions.QUICK_SETTINGS, GestureActions.ALL_APPS).forEach { simple(it) }

        MinimalDialog.options(this, title = g.title, subtitle = "now: " + GestureActions.label(this, Prefs.gesture(this, g)).lowercase(),
            items = choices.map { it.first }.toTypedArray(), icons = icons.toIntArray()) { choices[it].second() }
    }

    private fun pickApp(onPick: (AppInfo) -> Unit) =
        MinimalDialog.appList(this, "choose app", AppRepository.apps, onTap = onPick)

    private fun pickContact() {
        try { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }
        catch (_: Exception) { Toast.makeText(this, "no contacts app found", Toast.LENGTH_SHORT).show() }
    }

    private fun pickAppShortcut(g: Prefs.Gesture) {
        if (!AppShortcuts.available(this)) {
            MinimalDialog.confirm(this, title = "app shortcuts",
                message = "android only lets the default launcher use app shortcuts.\n\nset MinimalSF as your default launcher first.",
                positiveText = "open settings", negativeText = "cancel",
                onPositive = { try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {} })
            return
        }
        pickApp { app ->
            val shortcuts = AppShortcuts.forPackage(this, app.packageName)
            if (shortcuts.isEmpty()) { Toast.makeText(this, "${app.label} has no shortcuts", Toast.LENGTH_SHORT).show(); return@pickApp }
            MinimalDialog.options(this, title = app.label, subtitle = "app shortcuts",
                items = shortcuts.map { AppShortcuts.label(it) }.toTypedArray(),
                icons = IntArray(shortcuts.size) { R.drawable.ic_shortcut }) { i ->
                val s = shortcuts[i]
                save(g, GestureActions.shortcut(s.`package`, s.id, AppShortcuts.label(s)))
            }
        }
    }

    private fun pickPinned(g: Prefs.Gesture) {
        val pinned = Prefs.pinnedShortcuts(this)
        MinimalDialog.options(this, title = "saved shortcuts", subtitle = "added from other apps",
            items = pinned.map { it.substringAfterLast('|') }.toTypedArray(),
            icons = IntArray(pinned.size) { R.drawable.ic_shortcut },
            trailing = pinned.map { AppRepository.labelFor(this, it.substringBefore('|')).lowercase() as String? }.toTypedArray()
        ) { i ->
            val p = pinned[i].split('|')
            save(g, GestureActions.shortcut(p[0], p.getOrElse(1) { "" }, p.getOrElse(2) { "shortcut" }))
        }
    }

    /** Activity Launcher style: every screen an app lets other apps open directly. */
    private fun pickActivity(g: Prefs.Gesture, app: AppInfo) {
        @Suppress("DEPRECATION")
        val activities = try {
            packageManager.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES).activities
                ?.filter { it.exported && it.enabled } ?: emptyList()
        } catch (_: Exception) { emptyList() }
        if (activities.isEmpty()) { Toast.makeText(this, "${app.label} has no screens to open", Toast.LENGTH_SHORT).show(); return }

        val entries = activities.map { ai ->
            val short = ai.name.substringAfterLast('.')
            val label = ai.loadLabel(packageManager).toString().takeIf { it.isNotBlank() && it != app.label } ?: short
            Triple(label.lowercase(), short, ComponentName(ai.packageName, ai.name))
        }.distinctBy { it.third }.sortedBy { it.first }

        MinimalDialog.options(this, title = app.label, subtitle = "${entries.size} screens · some may need extra data and fail to open",
            items = entries.map { it.first }.toTypedArray(),
            icons = IntArray(entries.size) { R.drawable.ic_activity },
            trailing = entries.map { if (it.first == it.second.lowercase()) null else it.second }.toTypedArray()
        ) { i -> save(g, GestureActions.activity(entries[i].third, entries[i].first)) }
    }

    private fun promptAccessibility() {
        MinimalDialog.confirm(this, title = "enable screen lock",
            message = "to lock the screen with a gesture, MinimalSF needs accessibility permission.\n\nonly the lock action is used - no data is read or collected.",
            positiveText = "open settings", negativeText = "later",
            onPositive = { try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {} })
    }
}
