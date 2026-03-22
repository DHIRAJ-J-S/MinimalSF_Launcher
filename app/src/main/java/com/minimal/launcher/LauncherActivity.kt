package com.minimal.launcher

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class LauncherActivity : AppCompatActivity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var searchInput: EditText
    private lateinit var clearBtn: TextView
    private lateinit var searchTopSlot: FrameLayout
    private lateinit var searchBottomSlot: FrameLayout
    private lateinit var appList: RecyclerView
    private lateinit var noMatch: TextView
    private lateinit var autoLaunchBar: LinearLayout
    private lateinit var autoLaunchIcon: ImageView
    private lateinit var autoLaunchName: TextView
    private lateinit var musicBar: LinearLayout
    private lateinit var musicTitle: TextView
    private lateinit var musicIconView: TextView
    private lateinit var ramText: TextView
    private lateinit var homeTipView: TextView
    private lateinit var musicTipView: LinearLayout

    private lateinit var todoList: RecyclerView
    private lateinit var todoInput: EditText
    private lateinit var todoCount: TextView
    private var todos = mutableListOf<TodoItem>()
    private lateinit var todoAdapter: InlineTodoAdapter

    private lateinit var adapter: AppAdapter
    private var allApps: List<AppInfo> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private var autoLaunchRunnable: Runnable? = null
    private var showingAllApps = false

    private var activeController: MediaController? = null
    private var mediaCallback: MediaController.Callback? = null
    private var musicEverPlayed = false
    private var appsLoaded = false

    private var pullStartY = 0f
    private var pullTriggered = false
    private var launchCooldown = false
    private var keepAllAppsOpen = false

    private val grayscaleFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    private val clockRunnable = object : Runnable { override fun run() { updateClock(); handler.postDelayed(this, 30_000) } }
    private val musicPollRunnable = object : Runnable { override fun run() { updateNowPlaying(); handler.postDelayed(this, 3_000) } }
    private val statsPollRunnable = object : Runnable { override fun run() { updateSystemStats(); handler.postDelayed(this, 2_000) } }

    // Home screen gesture detector for double-tap and long-press on empty space
    private lateinit var homeGestureDetector: GestureDetector

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        rootLayout = findViewById(R.id.rootLayout)
        clockText = findViewById(R.id.clockText)
        dateText = findViewById(R.id.dateText)
        searchTopSlot = findViewById(R.id.searchTopSlot)
        searchBottomSlot = findViewById(R.id.searchBottomSlot)
        searchInput = findViewById(R.id.searchInput)
        clearBtn = findViewById(R.id.clearBtn)
        appList = findViewById(R.id.appList)
        noMatch = findViewById(R.id.noMatch)
        autoLaunchBar = findViewById(R.id.autoLaunchBar)
        autoLaunchIcon = findViewById(R.id.autoLaunchIcon)
        autoLaunchName = findViewById(R.id.autoLaunchName)
        musicBar = findViewById(R.id.musicBar)
        musicTitle = findViewById(R.id.musicTitle)
        musicIconView = findViewById(R.id.musicIcon)
        ramText = findViewById(R.id.ramText)
        homeTipView = findViewById(R.id.homeTipView)
        musicTipView = findViewById(R.id.musicTipView)
        todoList = findViewById(R.id.todoList)
        todoInput = findViewById(R.id.todoInput)
        todoCount = findViewById(R.id.todoCount)

        applySearchPosition()

        // Home tips
        if (!Prefs.homeTipShown(this)) { homeTipView.visibility = View.VISIBLE }
        findViewById<TextView>(R.id.musicTipClose).setOnClickListener { dismissMusicTip() }

        clockText.setOnClickListener {
            try { startActivity(Intent("android.intent.action.SHOW_ALARMS")) }
            catch (_: Exception) {
                try {
                    val i = packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                        ?: packageManager.getLaunchIntentForPackage("com.android.deskclock")
                    if (i != null) startActivity(i)
                } catch (_: Exception) {}
            }
        }

        // Home gesture: double-tap & long-press on empty space
        homeGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                dismissHomeTip()
                executeDoubleTapAction()
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                executeLongPressAction()
            }
        })
        homeGestureDetector.setIsLongpressEnabled(true)

        appList.setOnTouchListener { _, event -> homeGestureDetector.onTouchEvent(event); false }

        rootLayout.setOnTouchListener { _, event -> handlePullDown(event) }
        // Clock and date: no separate touch listeners needed — dispatchTouchEvent handles pull-down globally
        // Click listeners on clock/date work normally when pull isn't triggered

        adapter = AppAdapter(
            onClick = { app -> launchApp(app) },
            onLongClick = { app -> showAppOptions(app) }
        )
        appList.layoutManager = LinearLayoutManager(this)
        appList.adapter = adapter
        appList.itemAnimator = null

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterApps(s.toString()) }
        })
        clearBtn.setOnClickListener { searchInput.text.clear(); searchInput.requestFocus() }

        todos = TodoStore.load(this)
        todoAdapter = InlineTodoAdapter()
        todoList.layoutManager = LinearLayoutManager(this)
        todoList.adapter = todoAdapter
        todoList.itemAnimator = null
        todoInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addTodo(); true } else false
        }
        findViewById<TextView>(R.id.addTodoBtn).setOnClickListener { addTodo() }
        findViewById<TextView>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val allAppsBtn = findViewById<TextView>(R.id.allAppsBtn)
        allAppsBtn.setOnClickListener {
            if (showingAllApps) {
                closeAllApps()
            } else {
                showingAllApps = true; allAppsBtn.text = "⊟ close"
                adapter.update(allApps, ""); appList.visibility = View.VISIBLE
                noMatch.visibility = View.GONE; autoLaunchBar.visibility = View.GONE
                hideKeyboard()
            }
        }

        // Music bar gestures
        val musicGesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { togglePlayPause(); return true }
            override fun onLongPress(e: MotionEvent) { openMusicPlayer() }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (Math.abs(dx) > 80 && Math.abs(velocityX) > 150) {
                    dismissMusicTip()
                    if (dx < 0) skipNext() else skipPrev(); return true
                }
                return false
            }
        })
        musicGesture.setIsLongpressEnabled(true)
        musicBar.setOnTouchListener { _, event -> musicGesture.onTouchEvent(event); true }

        // Immediate: show clock (fast, no I/O)
        updateClock()
        applySearchPosition()
        applyFont()

        // Deferred: load apps and todos after first frame draws
        window.decorView.post {
            loadApps()
            appsLoaded = true
            todos = TodoStore.load(this)
            refreshTodo()
            updateMusicBar()
            updateNowPlaying()

            if (Prefs.isFirstLaunch(this)) { Prefs.setFirstLaunchDone(this); showFirstLaunchSetup() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetHomeState()
    }

    private fun resetHomeState() {
        cancelAutoLaunch()
        launchCooldown = false
        if (keepAllAppsOpen) {
            // Returning from uninstall/app info — keep all apps open, refresh list
            keepAllAppsOpen = false
            if (showingAllApps) {
                loadApps()
                adapter.update(allApps, "")
            }
            searchInput.text.clear()
            autoLaunchBar.visibility = View.GONE
            return
        }
        if (showingAllApps) closeAllApps()
        searchInput.text.clear()
        autoLaunchBar.visibility = View.GONE
        appList.visibility = View.VISIBLE
        noMatch.visibility = View.GONE
        adapter.update(emptyList(), "")
    }

    // --- Tips ---

    private fun dismissHomeTip() {
        if (!Prefs.homeTipShown(this)) {
            Prefs.setHomeTipShown(this)
            homeTipView.visibility = View.GONE
        }
    }

    private fun showMusicTip() {
        if (!Prefs.musicTipShown(this) && !musicEverPlayed) {
            musicEverPlayed = true
            musicTipView.visibility = View.VISIBLE
        }
    }

    private fun dismissMusicTip() {
        if (musicTipView.visibility == View.VISIBLE) {
            Prefs.setMusicTipShown(this)
            musicTipView.visibility = View.GONE
        }
    }

    // --- Gestures ---

    private fun executeDoubleTapAction() {
        val action = Prefs.doubleTapAction(this)
        if (action == "lock") {
            lockScreen()
        } else if (action.isNotEmpty()) {
            launchPackage(action)
        }
    }

    private fun executeLongPressAction() {
        val action = Prefs.longPressAction(this)
        if (action.isNotEmpty()) {
            launchPackage(action)
        }
    }

    private fun launchPackage(pkg: String) {
        try {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
            }
        } catch (_: Exception) {}
    }

    private fun lockScreen() {
        val svc = LockAccessibilityService.instance
        if (svc != null) {
            svc.lock()
        } else {
            MinimalDialog.confirm(this,
                title = "enable screen lock",
                message = "to lock screen by double-tap, MinimalSF needs accessibility permission.\n\nonly the lock action is used — no data is read or collected.",
                positiveText = "open settings",
                negativeText = "cancel",
                onPositive = {
                    try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
                }
            )
        }
    }

    // --- Search bar position ---

    private fun applySearchPosition() {
        val bottom = Prefs.searchAtBottom(this)
        if (bottom) {
            searchTopSlot.visibility = View.GONE
            searchTopSlot.removeAllViews()
            (searchInput.parent as? ViewGroup)?.removeView(searchInput)
            (clearBtn.parent as? ViewGroup)?.removeView(clearBtn)
            searchBottomSlot.visibility = View.VISIBLE
            searchBottomSlot.addView(searchInput)
            searchBottomSlot.addView(clearBtn)
        } else {
            searchTopSlot.visibility = View.VISIBLE
            searchBottomSlot.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        resetHomeState()

        handler.post(clockRunnable)
        handler.post(statsPollRunnable)
        updateClock()
        updateMusicBar()

        if (Prefs.showMusic(this)) handler.post(musicPollRunnable)

        // Defer heavier work past first frame
        window.decorView.post {
            // Reload apps only if needed (returning from settings/uninstall)
            if (appsLoaded) loadApps()
            todos = TodoStore.load(this)
            refreshTodo()
            updateNowPlaying()
            applySearchPosition()
            applyFont()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable); handler.removeCallbacks(musicPollRunnable)
        handler.removeCallbacks(statsPollRunnable); cancelAutoLaunch(); unregisterMediaCallback()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle config changes ourselves instead of activity recreation
        updateClock()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !showingAllApps) showKeyboard()
    }

    @Suppress("deprecation")
    override fun onBackPressed() {
        if (showingAllApps) { closeAllApps(); return }
        if (searchInput.text.isNotEmpty()) { searchInput.text.clear(); return }
        // On home screen already — do nothing, don't call super
    }

    // --- First launch ---

    private fun showFirstLaunchSetup() {
        MinimalDialog.confirm(this, title = "set as default launcher?",
            message = "set MinimalSF as your default home screen for the fastest app launching experience.",
            positiveText = "set default", negativeText = "later",
            onPositive = {
                try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {}
                handler.postDelayed({ showMusicSetupPrompt() }, 500)
            },
            onNegative = { Prefs.setDefaultPromptDismissed(this); showMusicSetupPrompt() }
        )
    }

    private fun showMusicSetupPrompt() {
        MinimalDialog.confirm(this, title = "enable now playing bar?",
            message = "show currently playing music on your home screen with playback controls.\n\nrequires notification access to read music info.\n\nno data is tracked.",
            positiveText = "enable", negativeText = "no thanks",
            onPositive = {
                Prefs.setShowMusic(this, true); updateMusicBar()
                try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
            },
            onNegative = { Prefs.setShowMusic(this, false) }
        )
    }

    // --- Pull down ---

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (handlePullDown(ev)) return true  // Consume — don't let click listeners fire
        return super.dispatchTouchEvent(ev)
    }

    private fun handlePullDown(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pullStartY = event.rawY; pullTriggered = false }
            MotionEvent.ACTION_MOVE -> {
                if (!pullTriggered && !showingAllApps) {
                    if (event.rawY - pullStartY > 60) { pullTriggered = true; dismissHomeTip(); expandNotificationBar(); return true }
                }
                if (pullTriggered) return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pullTriggered) { pullTriggered = false; return true }
                pullTriggered = false
            }
        }
        return false
    }

    @Suppress("deprecation")
    private fun expandNotificationBar() {
        try { Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(getSystemService("statusbar")) } catch (_: Exception) {}
    }

    // --- Media ---

    private fun findActiveMediaController(): MediaController? {
        try {
            val msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return null
            val c = ComponentName(this, MusicNotificationListener::class.java)
            val ctrls = try { msm.getActiveSessions(c) } catch (_: SecurityException) { try { msm.getActiveSessions(null) } catch (_: SecurityException) { null } }
            return ctrls?.firstOrNull()
        } catch (_: Exception) { return null }
    }

    private fun updateNowPlaying() {
        if (!Prefs.showMusic(this)) return
        val ctrl = findActiveMediaController()
        if (ctrl != null) {
            if (activeController?.sessionToken != ctrl.sessionToken) {
                unregisterMediaCallback(); activeController = ctrl
                mediaCallback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) { handler.post { displayMeta(metadata) } }
                    override fun onPlaybackStateChanged(state: PlaybackState?) { handler.post { updatePlayIcon(state) } }
                }
                ctrl.registerCallback(mediaCallback!!, handler)
            }
            displayMeta(ctrl.metadata); updatePlayIcon(ctrl.playbackState)
            // Show music tip on first play
            if (ctrl.playbackState?.state == PlaybackState.STATE_PLAYING) showMusicTip()
        } else { musicTitle.text = "no music playing"; musicIconView.text = "♪" }
    }

    private fun displayMeta(meta: MediaMetadata?) {
        if (meta == null) { musicTitle.text = "no music playing"; return }
        val t = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "unknown"
        val a = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        musicTitle.text = if (a.isNotEmpty()) "$t · $a" else t
    }

    private fun updatePlayIcon(s: PlaybackState?) {
        musicIconView.text = if (s?.state == PlaybackState.STATE_PLAYING) "\u23F8\uFE0E"  // ⏸︎
        else "\u25B6\uFE0E"  // ▶︎
    }
    private fun unregisterMediaCallback() { try { mediaCallback?.let { activeController?.unregisterCallback(it) } } catch (_: Exception) {}; activeController = null; mediaCallback = null }
    private fun togglePlayPause() { val c = findActiveMediaController() ?: return; if (c.playbackState?.state == PlaybackState.STATE_PLAYING) { c.transportControls.pause(); musicIconView.text = "▶" } else { c.transportControls.play(); musicIconView.text = "⏸" } }
    private fun skipNext() { findActiveMediaController()?.transportControls?.skipToNext() }
    private fun skipPrev() { findActiveMediaController()?.transportControls?.skipToPrevious() }
    private fun openMusicPlayer() {
        findActiveMediaController()?.let { c -> packageManager.getLaunchIntentForPackage(c.packageName)?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it); return } }
        try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)) } catch (_: Exception) {}
    }

    // --- Todo ---

    private fun addTodo() {
        val text = todoInput.text.toString().trim(); if (text.isEmpty()) return
        todos.add(TodoItem(System.currentTimeMillis(), text, false, false))
        TodoStore.save(this, todos); todoInput.text.clear(); refreshTodo(); searchInput.requestFocus()
    }

    private fun refreshTodo() {
        todoAdapter.notifyDataSetChanged()
        todoCount.text = todos.count { !it.done }.let { if (it > 0) "$it" else "" }
    }

    inner class InlineTodoAdapter : RecyclerView.Adapter<InlineTodoAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val checkBox: TextView = v.findViewById(R.id.checkBox); val todoText: TextView = v.findViewById(R.id.todoText)
            val importantBtn: TextView = v.findViewById(R.id.importantBtn); val removeBtn: TextView = v.findViewById(R.id.removeBtn)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_todo_inline, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = todos[position]
            holder.checkBox.text = if (item.done) "☑" else "☐"; holder.checkBox.alpha = if (item.done) 0.3f else 1f
            holder.todoText.text = item.text
            when {
                item.done -> { holder.todoText.paintFlags = holder.todoText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG; holder.todoText.alpha = 0.3f; holder.todoText.setTextColor(Color.parseColor("#888888")) }
                item.important -> { holder.todoText.paintFlags = holder.todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv(); holder.todoText.alpha = 1f; holder.todoText.setTextColor(Color.parseColor("#FF4444")) }
                else -> { holder.todoText.paintFlags = holder.todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv(); holder.todoText.alpha = 1f; holder.todoText.setTextColor(Color.parseColor("#888888")) }
            }
            holder.checkBox.setTextColor(if (item.important && !item.done) Color.parseColor("#FF4444") else Color.parseColor("#555555"))
            holder.importantBtn.setTextColor(if (item.important) Color.parseColor("#FF4444") else Color.parseColor("#333333"))
            holder.checkBox.setOnClickListener { todos[position] = item.copy(done = !item.done); TodoStore.save(this@LauncherActivity, todos); refreshTodo() }
            holder.todoText.setOnClickListener { todos[position] = item.copy(done = !item.done); TodoStore.save(this@LauncherActivity, todos); refreshTodo() }
            holder.importantBtn.setOnClickListener { todos[position] = item.copy(important = !item.important); TodoStore.save(this@LauncherActivity, todos); refreshTodo() }
            holder.removeBtn.setOnClickListener { todos.removeAt(position); TodoStore.save(this@LauncherActivity, todos); refreshTodo() }
        }
        override fun getItemCount() = todos.size
    }

    // --- App search ---

    private fun showAppOptions(app: AppInfo) {
        cancelAutoLaunch()
        val hidden = Prefs.getHiddenApps(this)
        val isHidden = hidden.contains(app.packageName)
        val keywords = Prefs.getKeywords(this)
        val currentKeyword = keywords[app.packageName] ?: ""

        val items = mutableListOf(
            "ℹ  app info",
            "✕  uninstall",
            "⌨  set keyword" + if (currentKeyword.isNotEmpty()) " [$currentKeyword]" else "",
            if (isHidden) "◉  show in search" else "○  hide from search"
        )

        MinimalDialog.options(this, title = app.label, items = items.toTypedArray()) { which ->
            when (which) {
                0 -> { keepAllAppsOpen = showingAllApps; try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${app.packageName}") }) } catch (_: Exception) {} }
                1 -> { keepAllAppsOpen = showingAllApps; try { startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.parse("package:${app.packageName}") }) } catch (_: Exception) {} }
                2 -> {
                    MinimalDialog.textInput(this,
                        title = "set keyword for ${app.label}",
                        hint = "e.g. wa for whatsapp",
                        prefill = currentKeyword,
                        onSubmit = { keyword -> Prefs.setKeyword(this, app.packageName, keyword) }
                    )
                }
                3 -> {
                    Prefs.setAppHidden(this, app.packageName, !isHidden)
                    if (showingAllApps) adapter.update(allApps, "")
                }
            }
        }
    }

    private fun updateClock() {
        val now = Date()
        clockText.text = SimpleDateFormat(if (Prefs.use24hClock(this)) "HH:mm" else "h:mm a", Locale.getDefault()).format(now)
        dateText.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now).lowercase()
    }

    private fun updateSystemStats() {
        try {
            val mi = android.app.ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
            ramText.text = "ram ${mi.availMem / (1024 * 1024)}/${mi.totalMem / (1024 * 1024)}mb"
        } catch (_: Exception) { ramText.text = "ram ---mb" }
    }

    private fun loadApps() {
        val pm = packageManager
        allApps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    private fun filterApps(query: String) {
        if (launchCooldown) return  // Suppress search while app is launching
        cancelAutoLaunch(); val q = query.trim().lowercase()
        clearBtn.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
        if (q.isNotEmpty() && showingAllApps) { showingAllApps = false; findViewById<TextView>(R.id.allAppsBtn).text = "⊞ all apps"; showKeyboard() }
        if (q.isEmpty()) {
            if (showingAllApps) { adapter.update(allApps, ""); appList.visibility = View.VISIBLE }
            else { adapter.update(emptyList(), ""); appList.visibility = View.VISIBLE }
            noMatch.visibility = View.GONE; autoLaunchBar.visibility = View.GONE; return
        }

        val keywords = Prefs.getKeywords(this)
        val hiddenApps = Prefs.getHiddenApps(this)

        // Check for exact keyword match first
        val keywordMatch = allApps.firstOrNull { app ->
            !hiddenApps.contains(app.packageName) && keywords[app.packageName] == q
        }
        if (keywordMatch != null) {
            adapter.update(emptyList(), q); appList.visibility = View.GONE; noMatch.visibility = View.GONE
            autoLaunchBar.visibility = View.VISIBLE
            autoLaunchIcon.setImageDrawable(keywordMatch.icon); autoLaunchIcon.colorFilter = grayscaleFilter
            autoLaunchName.text = keywordMatch.label; scheduleAutoLaunch(keywordMatch)
            return
        }

        // Normal search — exclude hidden apps
        val fromStart = Prefs.searchFromStart(this)
        val f = allApps.filter { app ->
            !hiddenApps.contains(app.packageName) && (
                (if (fromStart) app.label.lowercase().startsWith(q) else app.label.lowercase().contains(q)) ||
                (keywords[app.packageName]?.contains(q) == true)
            )
        }
        when {
            f.isEmpty() -> { adapter.update(emptyList(), q); appList.visibility = View.GONE; noMatch.visibility = View.VISIBLE; autoLaunchBar.visibility = View.GONE }
            f.size == 1 -> {
                adapter.update(emptyList(), q); appList.visibility = View.GONE; noMatch.visibility = View.GONE; autoLaunchBar.visibility = View.VISIBLE
                autoLaunchIcon.setImageDrawable(f[0].icon); autoLaunchIcon.colorFilter = grayscaleFilter
                autoLaunchName.text = f[0].label; scheduleAutoLaunch(f[0])
            }
            else -> { adapter.update(f, q); appList.visibility = View.VISIBLE; noMatch.visibility = View.GONE; autoLaunchBar.visibility = View.GONE }
        }
    }

    private fun scheduleAutoLaunch(app: AppInfo) { autoLaunchRunnable = Runnable { launchApp(app) }; handler.postDelayed(autoLaunchRunnable!!, Prefs.autoDelay(this)) }
    private fun cancelAutoLaunch() { autoLaunchRunnable?.let { handler.removeCallbacks(it) }; autoLaunchRunnable = null }

    private fun launchApp(app: AppInfo) {
        cancelAutoLaunch()
        launchCooldown = true
        if (showingAllApps) closeAllApps()
        packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
        }
        searchInput.text.clear()
        // Reset cooldown after a delay — covers fast typing overlap
        handler.postDelayed({ launchCooldown = false }, 600)
    }

    private fun closeAllApps() {
        showingAllApps = false
        try { findViewById<TextView>(R.id.allAppsBtn).text = "⊞ all apps" } catch (_: Exception) {}
        adapter.update(emptyList(), "")
        searchInput.text.clear()
        showKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        searchInput.clearFocus()
    }

    private fun showKeyboard() {
        searchInput.requestFocus()
        handler.postDelayed({
            searchInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_FORCED)
        }, 150)
    }

    private fun updateMusicBar() { musicBar.visibility = if (Prefs.showMusic(this)) View.VISIBLE else View.GONE; if (!Prefs.showMusic(this)) handler.removeCallbacks(musicPollRunnable) }

    private fun applyFont() {
        val tf = FontManager.getTypeface(this)
        val m = FontManager.sizeMultiplier(this)

        clockText.typeface = Typeface.create(tf, Typeface.BOLD)
        clockText.textSize = FontManager.clockSizeSp(this)

        dateText.typeface = tf; dateText.textSize = 12f * m
        searchInput.typeface = tf; searchInput.textSize = 16f * m
        musicTitle.typeface = tf; musicTitle.textSize = 14f * m
        ramText.typeface = tf; ramText.textSize = 11f * m

        adapter.setTypeface(tf, m)
    }
}
