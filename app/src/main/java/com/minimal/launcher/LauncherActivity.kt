package com.minimal.launcher

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class LauncherActivity : AppCompatActivity(), GestureActions.Host {

    private enum class Results { IDLE, LIST, NO_MATCH, AUTO }

    private lateinit var rootLayout: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var searchInput: EditText
    private lateinit var clearBtn: View
    private lateinit var screenTimeText: TextView
    private lateinit var searchTopSlot: FrameLayout
    private lateinit var searchBottomSlot: FrameLayout
    private lateinit var appList: RecyclerView
    private lateinit var noMatch: TextView
    private lateinit var autoLaunchBar: LinearLayout
    private lateinit var autoLaunchIcon: ImageView
    private lateinit var autoLaunchName: TextView
    private lateinit var musicBar: LinearLayout
    private lateinit var musicTitle: TextView
    private lateinit var musicIconView: ImageView
    private lateinit var ramText: TextView
    private lateinit var homeTipView: TextView
    private lateinit var musicTipView: LinearLayout
    private lateinit var allAppsBtn: TextView

    private lateinit var todoList: MaxHeightRecyclerView
    private lateinit var todoDivider: View
    private lateinit var todoSection: View
    private lateinit var todoInput: EditText
    private lateinit var todoCount: TextView
    private var todos = mutableListOf<TodoItem>()
    private lateinit var todoAdapter: InlineTodoAdapter

    private lateinit var adapter: AppAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var autoLaunchRunnable: Runnable? = null
    private var showingAllApps = false
    private var keepAllAppsOpen = false
    private var launchCooldown = false
    private var results = Results.IDLE
    private var bestMatch: AppInfo? = null

    // Set when any setting changes; UI is re-applied on next resume instead of every resume
    private var uiDirty = true
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> uiDirty = true }
    private val appsListener: () -> Unit = { onAppsChanged() }

    // Media — event driven via session listener + controller callback (no polling)
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var activeController: MediaController? = null
    private var musicNeedsAccess = false
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = displayMeta(metadata)
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlayIcon(state)
            if (state?.state == PlaybackState.STATE_PLAYING) showMusicTip()
        }
        override fun onSessionDestroyed() = bindController(null)
    }

    // Pull-down
    private var pullStartX = 0f
    private var pullStartY = 0f
    private var swipeDown = false
    private var swipeUp = false
    private var swipeLeft = false
    private var swipeRight = false
    private var pullTriggered = false
    private val pullThreshold by lazy { dp(48f) }
    private val hitRect = Rect()

    // IME-synced insets
    private var imeAnimating = false
    private var navBottom = 0

    private var clockFormat: SimpleDateFormat? = null
    private var clockIs24h = false
    private var dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())

    // Aligned to the minute boundary so the clock never lags up to 30s behind
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            // Also runs once on every resume, since onResume posts this immediately
            refreshScreenTime()
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 20)
        }
    }
    private val statsPollRunnable = object : Runnable { override fun run() { updateSystemStats(); handler.postDelayed(this, 2_000) } }

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
        screenTimeText = findViewById(R.id.screenTimeText)
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
        allAppsBtn = findViewById(R.id.allAppsBtn)
        todoList = findViewById(R.id.todoList)
        todoDivider = findViewById(R.id.todoDivider)
        todoSection = findViewById(R.id.todoSection)
        todoInput = findViewById(R.id.todoInput)
        todoCount = findViewById(R.id.todoCount)

        setupInsets()

        autoLaunchIcon.colorFilter = AppAdapter.GRAYSCALE
        findViewById<View>(R.id.musicTipClose).setOnClickListener { dismissMusicTip() }

        clockText.setOnClickListener { openClock() }

        // Home gesture: double-tap & long-press on empty space (not on an app row)
        homeGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!isEmptySpace(e)) return false
                dismissHomeTip()
                runGesture(Prefs.Gesture.DOUBLE_TAP)
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (isEmptySpace(e)) runGesture(Prefs.Gesture.LONG_PRESS)
            }
        })
        appList.setOnTouchListener { _, event -> homeGestureDetector.onTouchEvent(event); false }

        adapter = AppAdapter(
            onClick = { app, view -> launchApp(app, view) },
            onLongClick = { app -> showAppOptions(app) }
        )
        appList.layoutManager = LinearLayoutManager(this)
        appList.adapter = adapter
        appList.itemAnimator = null
        appList.setHasFixedSize(true)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterApps(s.toString()) }
        })
        // Enter / "go" launches the top result
        searchInput.setOnEditorActionListener { _, actionId, event ->
            val enter = actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (enter) bestMatch?.let { launchApp(it, if (results == Results.AUTO) autoLaunchIcon else null) }
            enter
        }
        clearBtn.setOnClickListener { searchInput.text.clear(); searchInput.requestFocus() }

        todos = TodoStore.load(this)
        todoAdapter = InlineTodoAdapter()
        todoList.layoutManager = LinearLayoutManager(this)
        todoList.adapter = todoAdapter
        todoList.itemAnimator = DefaultItemAnimator().apply {
            supportsChangeAnimations = false
            addDuration = 140; removeDuration = 140; moveDuration = 160
        }
        todoInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addTodo(); true } else false
        }
        findViewById<View>(R.id.addTodoBtn).setOnClickListener { addTodo() }
        screenTimeText.setOnClickListener { onScreenTimeClick() }
        findViewById<TextView>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        updateTodoCount()

        allAppsBtn.setOnClickListener { if (showingAllApps) closeAllApps() else openAllApps() }

        // Music bar gestures
        val musicGesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (musicNeedsAccess) openNotificationAccess() else togglePlayPause()
                return true
            }
            override fun onLongPress(e: MotionEvent) { openMusicPlayer() }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dx = e2.x - (e1?.x ?: e2.x)
                if (abs(dx) > dp(32f) && abs(velocityX) > abs(velocityY)) {
                    dismissMusicTip()
                    if (dx < 0) skipNext() else skipPrev(); return true
                }
                return false
            }
        })
        musicBar.setOnTouchListener { _, event -> musicGesture.onTouchEvent(event); true }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Home screen: back only closes things, never leaves
                when {
                    showingAllApps -> closeAllApps()
                    searchInput.text.isNotEmpty() -> searchInput.text.clear()
                }
            }
        })

        Prefs.registerListener(this, prefsListener)
        AppRepository.addListener(appsListener)
        AppRepository.init(this)

        applyUi()
        updateClock()
    }

    override fun onDestroy() {
        Prefs.unregisterListener(this, prefsListener)
        AppRepository.removeListener(appsListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun onAppsChanged() {
        // Refresh whatever is on screen. Only auto-launch if the user is actively on the home screen
        // (e.g. typed before the first load finished), not because a package changed in the background.
        if (showingAllApps) adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this))
        else filterApps(searchInput.text.toString(), allowAutoLaunch = hasWindowFocus())

        if (Prefs.isFirstLaunch(this)) { Prefs.setFirstLaunchDone(this); showFirstLaunchSetup() }
    }

    // --- Insets: pad for bars + keyboard, animated in sync with the keyboard ---

    private fun setupInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            navBottom = bars.bottom
            v.setPadding(bars.left, bars.top, bars.right, v.paddingBottom)
            todoList.maxHeightPx = if (insets.isVisible(WindowInsetsCompat.Type.ime())) dp(72f).toInt() else todoList.xmlMaxHeightPx
            // While the keyboard animates, onProgress drives the bottom padding frame-by-frame
            if (!imeAnimating) {
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight,
                    max(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom))
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setWindowInsetsAnimationCallback(rootLayout,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) imeAnimating = true
                }
                override fun onProgress(insets: WindowInsetsCompat, running: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
                    val bottom = max(navBottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                    if (rootLayout.paddingBottom != bottom)
                        rootLayout.setPadding(rootLayout.paddingLeft, rootLayout.paddingTop, rootLayout.paddingRight, bottom)
                    return insets
                }
                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        imeAnimating = false
                        ViewCompat.requestApplyInsets(rootLayout)
                    }
                }
            })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Home pressed while already home
        resetHomeState()
    }

    private fun resetHomeState() {
        cancelAutoLaunch()
        launchCooldown = false
        if (keepAllAppsOpen) {
            // Returning from app info — keep all apps open
            keepAllAppsOpen = false
            if (showingAllApps) return
        }
        if (showingAllApps) closeAllApps() else if (searchInput.text.isNotEmpty()) searchInput.text.clear()
        else showResults(Results.IDLE)
    }

    // --- Tips ---

    private fun updateHomeTip() {
        if (Prefs.homeTipShown(this)) { homeTipView.visibility = View.GONE; return }
        // Mention the two gestures people are most likely to try, as they're currently set
        homeTipView.text = listOf(Prefs.Gesture.SWIPE_DOWN to "pull down", Prefs.Gesture.DOUBLE_TAP to "double tap")
            .mapNotNull { (g, verb) ->
                Prefs.gesture(this, g).takeIf { it.isNotEmpty() }?.let { "$verb: ${GestureActions.label(this, it).lowercase()}" }
            }.joinToString("\n\n")
        homeTipView.visibility = if (results == Results.IDLE && !showingAllApps) View.VISIBLE else View.GONE
    }

    private fun dismissHomeTip() {
        if (!Prefs.homeTipShown(this)) {
            Prefs.setHomeTipShown(this)
            homeTipView.animate().alpha(0f).setDuration(150).withEndAction {
                homeTipView.visibility = View.GONE; homeTipView.alpha = 1f
            }.start()
        }
    }

    private fun showMusicTip() {
        if (!Prefs.musicTipShown(this) && musicBar.visibility == View.VISIBLE && musicTipView.visibility != View.VISIBLE) {
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

    private fun isEmptySpace(e: MotionEvent) = appList.findChildViewUnder(e.x, e.y) == null

    /** Runs whatever the user put on [g] in settings → gestures. Returns false if it's set to "none". */
    private fun runGesture(g: Prefs.Gesture): Boolean {
        val code = Prefs.gesture(this, g)
        if (code.isEmpty()) return false
        if (g == Prefs.Gesture.LONG_PRESS) appList.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        return GestureActions.run(this, code, this)
    }

    // GestureActions.Host
    override fun expandNotifications() = expandStatusBar("expandNotificationsPanel")
    override fun expandQuickSettings() = expandStatusBar("expandSettingsPanel")

    override fun launchApp(pkg: String) {
        val app = AppRepository.apps.firstOrNull { it.packageName == pkg }
        if (app != null) { launchApp(app); return }
        try {
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
            } ?: Toast.makeText(this, "app not found — check gesture settings", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    override fun lockScreen() {
        if (!SystemFeatures.lockSupported) {
            MinimalDialog.confirm(this, title = "screen lock unavailable",
                message = "double-tap to lock needs android 9 or newer.\n\nyou can set double tap to open an app instead in settings.",
                positiveText = "ok", onPositive = {})
            return
        }
        if (SystemFeatures.lock()) return
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

    // --- Settings-driven UI ---

    private fun applyUi() {
        uiDirty = false
        applySearchPosition()
        applyFont()
        updateMusicBarVisibility()
        updateHomeTip()
        clockFormat = null
        updateClock()
        if (showingAllApps) adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this))
    }

    private fun applySearchPosition() {
        val target = if (Prefs.searchAtBottom(this)) searchBottomSlot else searchTopSlot
        val other = if (target === searchTopSlot) searchBottomSlot else searchTopSlot
        // Move the search views into the right slot (works in both directions)
        if (searchInput.parent !== target) {
            val hadFocus = searchInput.hasFocus()
            (searchInput.parent as? ViewGroup)?.removeView(searchInput)
            (clearBtn.parent as? ViewGroup)?.removeView(clearBtn)
            target.addView(searchInput)
            target.addView(clearBtn)
            if (hadFocus) searchInput.requestFocus()
        }
        target.visibility = View.VISIBLE
        other.visibility = View.GONE
    }

    private fun applyFont() {
        val tf = FontManager.getTypeface(this)
        val m = FontManager.sizeMultiplier(this)

        FontManager.applyTo(rootLayout, tf, m, skip = setOf(clockText))
        clockText.typeface = Typeface.create(tf, Typeface.BOLD)
        clockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, FontManager.clockSizeSp(this))

        adapter.setTypeface(tf, m)
        todoAdapter.setTypeface(tf, m)

        // Icon size follows the text size, so re-apply after a font size change
        setNavIcon(allAppsBtn, if (showingAllApps) R.drawable.ic_close else R.drawable.ic_grid)
        setNavIcon(findViewById(R.id.settingsBtn), R.drawable.ic_settings)
    }

    override fun onResume() {
        super.onResume()
        if (uiDirty) applyUi()

        handler.removeCallbacks(clockRunnable); handler.post(clockRunnable)
        handler.removeCallbacks(statsPollRunnable); handler.post(statsPollRunnable)
        startMusic()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(statsPollRunnable)
        cancelAutoLaunch()
        stopMusic()
    }

    override fun onStop() {
        super.onStop()
        // Reset while hidden, so returning home shows a clean screen without a visible flicker
        resetHomeState()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        clockFormat = null
        updateClock()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !showingAllApps && !todoInput.hasFocus()) showKeyboard()
    }

    // --- First launch ---

    private fun showFirstLaunchSetup() {
        MinimalDialog.confirm(this, title = "welcome to MinimalSF",
            message = "type to launch any app — one match opens on its own.\n\n" +
                "fonts, gestures, screen time, search and more can be changed in settings.",
            positiveText = "go to settings", negativeText = "later",
            // Settings has the default-launcher row too, so the next prompt isn't needed after it
            onPositive = { startActivity(Intent(this, SettingsActivity::class.java)) },
            onNegative = { showDefaultLauncherPrompt() }
        )
    }

    private fun showDefaultLauncherPrompt() {
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
        if (isFinishing || isDestroyed || !SystemFeatures.AVAILABLE) return
        MinimalDialog.confirm(this, title = "enable now playing bar?",
            message = "show currently playing music on your home screen with playback controls.\n\nrequires notification access to read music info.\n\nno data is tracked.",
            positiveText = "enable", negativeText = "no thanks",
            onPositive = {
                Prefs.setShowMusic(this, true)
                if (!SystemFeatures.musicAccessEnabled(this)) openNotificationAccess()
            },
            onNegative = { Prefs.setShowMusic(this, false) }
        )
    }

    // --- Swipes (down / up / left / right) on the home screen ---

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pullStartX = ev.rawX; pullStartY = ev.rawY; pullTriggered = false
                // Only watch directions that have an action, and never steal a scroll from a list
                // that can still move that way, or a horizontal drag inside the music bar / text boxes
                val home = !showingAllApps
                swipeDown = home && Prefs.gesture(this, Prefs.Gesture.SWIPE_DOWN).isNotEmpty() && !startsInList(ev, -1)
                swipeUp = home && Prefs.gesture(this, Prefs.Gesture.SWIPE_UP).isNotEmpty() && !startsInList(ev, 1)
                val sideways = home && !startsIn(ev, musicBar, searchInput, todoInput)
                swipeLeft = sideways && Prefs.gesture(this, Prefs.Gesture.SWIPE_LEFT).isNotEmpty()
                swipeRight = sideways && Prefs.gesture(this, Prefs.Gesture.SWIPE_RIGHT).isNotEmpty()
            }
            MotionEvent.ACTION_MOVE -> if (!pullTriggered) {
                val dx = ev.rawX - pullStartX
                val dy = ev.rawY - pullStartY
                val g = when {
                    swipeDown && dy > pullThreshold && dy > abs(dx) * 2 -> Prefs.Gesture.SWIPE_DOWN
                    swipeUp && -dy > pullThreshold && -dy > abs(dx) * 2 -> Prefs.Gesture.SWIPE_UP
                    swipeRight && dx > pullThreshold && dx > abs(dy) * 2 -> Prefs.Gesture.SWIPE_RIGHT
                    swipeLeft && -dx > pullThreshold && -dx > abs(dy) * 2 -> Prefs.Gesture.SWIPE_LEFT
                    else -> null
                }
                if (g != null) {
                    pullTriggered = true
                    // Cancel the gesture for children so nothing is left in a pressed state
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    dismissHomeTip()
                    runGesture(g)
                    return true
                }
            }
        }
        if (pullTriggered) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) pullTriggered = false
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    /** Touch started in a list that can still scroll in [direction] (-1 = up, 1 = down). */
    private fun startsInList(ev: MotionEvent, direction: Int) =
        listOf(appList, todoList).any { it.canScrollVertically(direction) && startsIn(ev, it) }

    private fun startsIn(ev: MotionEvent, vararg views: View) = views.any { v ->
        v.isShown && v.getGlobalVisibleRect(hitRect) && hitRect.contains(ev.rawX.toInt(), ev.rawY.toInt())
    }

    @SuppressLint("WrongConstant")
    private fun expandStatusBar(method: String) {
        try { Class.forName("android.app.StatusBarManager").getMethod(method).invoke(getSystemService("statusbar")) } catch (_: Exception) {}
    }

    // --- Media ---

    private fun startMusic() {
        if (!Prefs.showMusic(this) || sessionsListener != null) return
        val msm = getSystemService(MediaSessionManager::class.java) ?: return
        val component = SystemFeatures.musicListener(this) ?: return
        try {
            val l = MediaSessionManager.OnActiveSessionsChangedListener { bindController(pickController(it)) }
            msm.addOnActiveSessionsChangedListener(l, component, handler)
            sessionsListener = l
            musicNeedsAccess = false
            bindController(pickController(msm.getActiveSessions(component)))
        } catch (_: SecurityException) {
            musicNeedsAccess = true
            bindController(null)
            musicTitle.text = "tap to allow music access"
        }
    }

    private fun stopMusic() {
        sessionsListener?.let { l ->
            try { getSystemService(MediaSessionManager::class.java)?.removeOnActiveSessionsChangedListener(l) } catch (_: Exception) {}
        }
        sessionsListener = null
        bindController(null)
    }

    private fun pickController(ctrls: List<MediaController>?): MediaController? =
        ctrls?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: ctrls?.firstOrNull()

    private fun bindController(c: MediaController?) {
        if (activeController?.sessionToken != c?.sessionToken) {
            try { activeController?.unregisterCallback(mediaCallback) } catch (_: Exception) {}
            activeController = c
            c?.registerCallback(mediaCallback, handler)
        }
        if (c == null) { musicTitle.text = "no music playing"; musicIconView.setImageResource(R.drawable.ic_music); return }
        displayMeta(c.metadata)
        updatePlayIcon(c.playbackState)
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) showMusicTip()
    }

    private fun displayMeta(meta: MediaMetadata?) {
        if (meta == null) { musicTitle.text = "no music playing"; return }
        val t = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "unknown"
        val a = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        musicTitle.text = if (a.isNotEmpty()) "$t · $a" else t
    }

    private fun updatePlayIcon(s: PlaybackState?) {
        // Drawn icons, not ▶/⏸ characters: Samsung's font forces ⏸ to a colour emoji
        musicIconView.setImageResource(if (s?.state == PlaybackState.STATE_PLAYING) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun togglePlayPause() {
        val c = activeController ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) { c.transportControls.pause(); musicIconView.setImageResource(R.drawable.ic_play) }
        else { c.transportControls.play(); musicIconView.setImageResource(R.drawable.ic_pause) }
    }
    private fun skipNext() { activeController?.transportControls?.skipToNext() }
    private fun skipPrev() { activeController?.transportControls?.skipToPrevious() }
    private fun openMusicPlayer() {
        activeController?.let { c -> packageManager.getLaunchIntentForPackage(c.packageName)?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it); return } }
        try { startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
    private fun openNotificationAccess() {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
    }

    private fun updateMusicBarVisibility() {
        if (!Prefs.showMusic(this)) stopMusic()
        updateCompact()
    }

    /**
     * While searching or browsing all apps, the todo section and music bar step aside so the results
     * get the space — with the keyboard up there was none left for them.
     */
    private fun updateCompact() {
        val compact = showingAllApps || searchInput.text.isNotBlank()
        val todo = if (compact) View.GONE else View.VISIBLE
        if (todoSection.visibility != todo) { todoSection.visibility = todo; todoDivider.visibility = todo }
        musicBar.visibility = if (Prefs.showMusic(this) && !compact) View.VISIBLE else View.GONE
        if (compact || !Prefs.showMusic(this)) musicTipView.visibility = View.GONE
    }

    // --- Todo ---

    private fun addTodo() {
        val text = todoInput.text.toString().trim(); if (text.isEmpty()) return
        todos.add(TodoItem(System.currentTimeMillis(), text, false, false))
        saveTodos()
        todoAdapter.notifyItemInserted(todos.size - 1)
        todoList.scrollToPosition(todos.size - 1)
        todoInput.text.clear()
        searchInput.requestFocus()
    }

    private fun saveTodos() { TodoStore.save(this, todos); updateTodoCount() }

    private fun updateTodoCount() {
        todoCount.text = todos.count { !it.done }.let { if (it > 0) "$it" else "" }
    }

    inner class InlineTodoAdapter : RecyclerView.Adapter<InlineTodoAdapter.VH>() {
        private var typeface: Typeface = Typeface.MONOSPACE
        private var mult = 1f

        init { setHasStableIds(true) }

        fun setTypeface(tf: Typeface, m: Float) {
            if (tf == typeface && m == mult) return
            typeface = tf; mult = m
            notifyItemRangeChanged(0, todos.size)
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val checkBox: ImageView = v.findViewById(R.id.checkBox)
            val todoText: TextView = v.findViewById(R.id.todoText)
            val importantBtn: ImageView = v.findViewById(R.id.importantBtn)
            val removeBtn: ImageView = v.findViewById(R.id.removeBtn)

            init {
                // Position is read at click time — the bind-time position goes stale after removals
                val toggleDone = View.OnClickListener { edit { it.copy(done = !it.done) } }
                checkBox.setOnClickListener(toggleDone)
                todoText.setOnClickListener(toggleDone)
                importantBtn.setOnClickListener { edit { it.copy(important = !it.important) } }
                removeBtn.setOnClickListener {
                    val p = bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION) { todos.removeAt(p); saveTodos(); notifyItemRemoved(p) }
                }
            }

            private fun edit(f: (TodoItem) -> TodoItem) {
                val p = bindingAdapterPosition
                if (p == RecyclerView.NO_POSITION) return
                todos[p] = f(todos[p]); saveTodos(); notifyItemChanged(p)
            }
        }

        override fun getItemId(position: Int) = todos[position].id
        override fun getItemCount() = todos.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_todo_inline, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = todos[position]
            holder.todoText.typeface = typeface
            holder.todoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * mult)
            holder.todoText.text = item.text
            holder.checkBox.setImageResource(if (item.done) R.drawable.ic_check_on else R.drawable.ic_check_off)
            holder.checkBox.alpha = if (item.done) 0.4f else 1f

            val strike = if (item.done) holder.todoText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                         else holder.todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.todoText.paintFlags = strike
            holder.todoText.alpha = if (item.done) 0.3f else 1f
            holder.todoText.setTextColor(if (item.important && !item.done) RED else GREY)
            holder.checkBox.imageTintList = ColorStateList.valueOf(if (item.important && !item.done) RED else GREY)
            holder.importantBtn.imageTintList = ColorStateList.valueOf(if (item.important) RED else FAINT)
            holder.removeBtn.imageTintList = ColorStateList.valueOf(FAINT)
        }
    }

    // --- App options ---

    private fun showAppOptions(app: AppInfo) {
        cancelAutoLaunch()
        val isHidden = app.packageName in Prefs.getHiddenApps(this)
        val currentKeyword = Prefs.getKeywords(this)[app.packageName] ?: ""

        // The app's own shortcuts first ("new chat", "navigate home"…), like other launchers show them
        val shortcuts = AppShortcuts.forPackage(this, app.packageName).take(4)
        val items = shortcuts.map { AppShortcuts.label(it) } +
            listOf("app info", "uninstall", "set keyword", if (isHidden) "show in search" else "hide from search")
        val icons = IntArray(shortcuts.size) { R.drawable.ic_shortcut } + intArrayOf(R.drawable.ic_info, R.drawable.ic_uninstall,
            R.drawable.ic_keyword, if (isHidden) R.drawable.ic_eye else R.drawable.ic_eye_off)
        val trailing = arrayOfNulls<String>(shortcuts.size) + arrayOf(null, null, currentKeyword.ifEmpty { null }, null)
        val usage = if (screenTimeActive()) ScreenTime.today[app.packageName]?.let { "${ScreenTime.format(it)} today" } else null

        MinimalDialog.options(this, title = app.label, items = items.toTypedArray(), icons = icons,
            subtitle = usage, trailing = trailing) { picked ->
            if (picked < shortcuts.size) {
                val s = shortcuts[picked]
                if (!AppShortcuts.start(this, s.`package`, s.id)) Toast.makeText(this, "shortcut unavailable", Toast.LENGTH_SHORT).show()
                return@options
            }
            when (picked - shortcuts.size) {
                0 -> { keepAllAppsOpen = showingAllApps; try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))) } catch (_: Exception) {} }
                1 -> { keepAllAppsOpen = showingAllApps; try { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))) } catch (_: Exception) {} }
                2 -> promptKeyword(app, currentKeyword)
                3 -> {
                    Prefs.setAppHidden(this, app.packageName, !isHidden)
                    if (showingAllApps) adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this))
                    else filterApps(searchInput.text.toString(), allowAutoLaunch = false)
                }
            }
        }
    }

    private fun promptKeyword(app: AppInfo, current: String) {
        MinimalDialog.textInput(this,
            title = "keyword for ${app.label}",
            hint = "e.g. wa for whatsapp (empty = remove)",
            prefill = current,
            onSubmit = { keyword ->
                val owner = if (keyword.isEmpty()) null else Prefs.keywordOwner(this, keyword, app.packageName)
                if (owner != null) {
                    MinimalDialog.confirm(this, title = "keyword in use",
                        message = "\"${keyword.lowercase()}\" is already used by ${AppRepository.labelFor(this, owner)}.\n\nmove it to ${app.label}?",
                        positiveText = "move", negativeText = "cancel",
                        onPositive = {
                            Prefs.setKeyword(this, owner, "")
                            Prefs.setKeyword(this, app.packageName, keyword)
                        })
                } else {
                    Prefs.setKeyword(this, app.packageName, keyword)
                }
            }
        )
    }

    // --- Clock ---

    /**
     * Opens the phone's alarms. Some clock apps (Samsung) only accept SHOW_ALARMS from apps holding
     * SET_ALARM, and others don't handle it at all, so fall back to launching the clock app itself.
     */
    private fun openClock() {
        val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        try { startActivity(showAlarms); return } catch (_: Exception) {}

        val handler = packageManager.resolveActivity(showAlarms, 0)?.activityInfo?.packageName
        val known = listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock",
            "com.oneplus.deskclock", "com.coloros.alarmclock", "com.motorola.timeweatherwidget")
        val intent = (listOfNotNull(handler) + known).firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
            ?: AppRepository.apps.firstOrNull { it.labelLower == "clock" }?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
        if (intent == null) { Toast.makeText(this, "no clock app found", Toast.LENGTH_SHORT).show(); return }
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    // --- Screen time ---

    private fun screenTimeActive() = Prefs.showScreenTime(this) && ScreenTime.hasAccess(this)

    private fun refreshScreenTime() {
        if (!Prefs.showScreenTime(this)) { screenTimeText.visibility = View.GONE; return }
        screenTimeText.visibility = View.VISIBLE
        if (!ScreenTime.hasAccess(this)) { screenTimeText.text = "tap to allow screen time"; return }
        if (ScreenTime.today.isEmpty()) screenTimeText.text = "screen time …"
        ScreenTime.refresh(this) {
            if (!isDestroyed) screenTimeText.text = "screen time ${ScreenTime.format(ScreenTime.totalToday)}"
        }
    }

    private fun onScreenTimeClick() {
        if (!ScreenTime.hasAccess(this)) { ScreenTime.openAccessSettings(this); return }
        val byApp = AppRepository.apps.associateBy { it.packageName }
        val top = ScreenTime.today.entries.filter { it.key in byApp }.sortedByDescending { it.value }.take(8)
        val wellbeing = ScreenTime.wellbeingIntent(this)
        val items = top.map { byApp.getValue(it.key).label } + listOfNotNull(wellbeing?.let { "open digital wellbeing" })
        val trailing = top.map { ScreenTime.format(it.value) }
        if (items.isEmpty()) return
        MinimalDialog.options(this, title = "screen time today",
            subtitle = ScreenTime.format(ScreenTime.totalToday) + " total",
            items = items.toTypedArray(), trailing = trailing.toTypedArray<String?>()) { which ->
            // Tapping an app opens it; the last row opens Digital Wellbeing
            if (which < top.size) byApp[top[which].key]?.let { launchApp(it) }
            else wellbeing?.let { try { startActivity(it) } catch (_: Exception) {} }
        }
    }

    /** Line icon left of a text button, sized to the text so both nav buttons match. */
    private fun setNavIcon(tv: TextView, res: Int) {
        val d = androidx.core.content.ContextCompat.getDrawable(this, res)?.mutate() ?: return
        val size = (tv.textSize * 1.25f).toInt()
        d.setBounds(0, 0, size, size)
        d.setTint(tv.currentTextColor)
        tv.setCompoundDrawablesRelative(d, null, null, null)
    }

    // --- Clock & stats ---

    private fun updateClock() {
        val use24 = Prefs.use24hClock(this)
        val fmt = clockFormat?.takeIf { clockIs24h == use24 }
            ?: SimpleDateFormat(if (use24) "HH:mm" else "h:mm a", Locale.getDefault()).also { clockFormat = it; clockIs24h = use24 }
        val now = Date()
        clockText.text = fmt.format(now)
        dateText.text = dateFormat.format(now)
    }

    private fun updateSystemStats() {
        try {
            val mi = ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            ramText.text = "ram ${mi.availMem / (1024 * 1024)}/${mi.totalMem / (1024 * 1024)}mb"
        } catch (_: Exception) { ramText.text = "ram ---mb" }
    }

    // --- Search ---

    /**
     * Ranking: label prefix → keyword prefix → word start → anywhere in label → anywhere in keyword.
     * "starts with" mode only allows the first two.
     */
    private fun rank(app: AppInfo, q: String, keyword: String?, fromStart: Boolean): Int? {
        val label = app.labelLower
        return when {
            label.startsWith(q) -> 0
            keyword?.startsWith(q) == true -> 1
            fromStart -> null
            label.contains(" $q") -> 2
            label.contains(q) -> 3
            keyword?.contains(q) == true -> 4
            else -> null
        }
    }

    private fun filterApps(raw: String, allowAutoLaunch: Boolean = true) {
        cancelAutoLaunch()
        val q = raw.trim().lowercase()
        updateCompact()
        clearBtn.visibility = if (raw.isNotEmpty()) View.VISIBLE else View.GONE

        if (q.isNotEmpty() && showingAllApps) setAllAppsMode(false)
        if (q.isEmpty()) {
            bestMatch = null
            if (showingAllApps) adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this))
            else showResults(Results.IDLE)
            return
        }

        val apps = AppRepository.apps
        val hidden = Prefs.getHiddenApps(this)
        val keywords = Prefs.getKeywords(this)

        // Exact keyword wins outright
        val keywordMatch = apps.firstOrNull { it.packageName !in hidden && keywords[it.packageName] == q }
        if (keywordMatch != null) { showAutoLaunch(keywordMatch, allowAutoLaunch); return }

        val fromStart = Prefs.searchFromStart(this)
        val matches = apps.mapNotNull { app ->
            if (app.packageName in hidden) null
            else rank(app, q, keywords[app.packageName], fromStart)?.let { app to it }
        }.sortedBy { it.second }.map { it.first }

        when (matches.size) {
            0 -> { bestMatch = null; showResults(Results.NO_MATCH) }
            1 -> showAutoLaunch(matches[0], allowAutoLaunch)
            else -> {
                bestMatch = matches[0]
                adapter.update(matches, q, hidden)
                appList.scrollToPosition(0)
                showResults(Results.LIST)
            }
        }
    }

    private fun showAutoLaunch(app: AppInfo, allowAutoLaunch: Boolean) {
        bestMatch = app
        autoLaunchIcon.setImageDrawable(app.icon)
        autoLaunchName.text = app.label
        showResults(Results.AUTO)
        if (allowAutoLaunch && !launchCooldown) scheduleAutoLaunch(app)
    }

    private fun showResults(state: Results) {
        val prev = results
        results = state
        if (state != Results.LIST) adapter.update(emptyList(), "")
        appList.visibility = if (state == Results.IDLE || state == Results.LIST) View.VISIBLE else View.GONE
        noMatch.visibility = if (state == Results.NO_MATCH) View.VISIBLE else View.GONE
        autoLaunchBar.visibility = if (state == Results.AUTO) View.VISIBLE else View.GONE
        if (state == Results.AUTO && prev != Results.AUTO) {
            autoLaunchBar.alpha = 0f
            autoLaunchBar.animate().alpha(1f).setDuration(120).start()
        }
        if (!Prefs.homeTipShown(this)) homeTipView.visibility = if (state == Results.IDLE && !showingAllApps) View.VISIBLE else View.GONE
    }

    private fun scheduleAutoLaunch(app: AppInfo) {
        val r = Runnable { launchApp(app, autoLaunchIcon) }
        autoLaunchRunnable = r
        handler.postDelayed(r, Prefs.autoDelay(this))
    }

    private fun cancelAutoLaunch() { autoLaunchRunnable?.let { handler.removeCallbacks(it) }; autoLaunchRunnable = null }

    private fun launchApp(app: AppInfo, source: View? = null) {
        cancelAutoLaunch()
        if (launchCooldown) return
        keepAllAppsOpen = false
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, "can't open ${app.label}", Toast.LENGTH_SHORT).show()
            AppRepository.reload(this)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        // Reveal the app from the icon that was tapped
        val opts = source?.takeIf { it.isShown && it.width > 0 }?.let {
            ActivityOptions.makeClipRevealAnimation(it, 0, 0, it.width, it.height).toBundle()
        }
        // Block a second launch from keystrokes that arrive while this one opens.
        // The search text is cleared in onStop, once the app covers the screen.
        launchCooldown = true
        try {
            startActivity(intent, opts)
        } catch (_: Exception) {
            launchCooldown = false
            Toast.makeText(this, "can't open ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        handler.postDelayed({ launchCooldown = false }, 600)
    }

    // --- All apps ---

    override fun openAllApps() {
        setAllAppsMode(true)
        if (searchInput.text.isNotEmpty()) searchInput.text.clear()
        adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this))
        appList.scrollToPosition(0)
        hideKeyboard()
    }

    private fun closeAllApps() {
        setAllAppsMode(false)
        if (searchInput.text.isNotEmpty()) searchInput.text.clear() else showResults(Results.IDLE)
        showKeyboard()
    }

    private fun setAllAppsMode(on: Boolean) {
        showingAllApps = on
        updateCompact()
        allAppsBtn.text = if (on) "close" else "all apps"
        setNavIcon(allAppsBtn, if (on) R.drawable.ic_close else R.drawable.ic_grid)
        if (on) {
            results = Results.LIST
            appList.visibility = View.VISIBLE
            noMatch.visibility = View.GONE
            autoLaunchBar.visibility = View.GONE
            homeTipView.visibility = View.GONE
            cancelAutoLaunch()
        }
    }

    // --- Keyboard ---

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, searchInput).hide(WindowInsetsCompat.Type.ime())
        searchInput.clearFocus()
    }

    private fun showKeyboard() {
        searchInput.requestFocus()
        searchInput.post {
            if (hasWindowFocus() && !showingAllApps) WindowCompat.getInsetsController(window, searchInput).show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    companion object {
        private val RED = Color.parseColor("#FF4444")
        private val GREY = Color.parseColor("#888888")
        private val FAINT = Color.parseColor("#4A4A4A")
    }
}
