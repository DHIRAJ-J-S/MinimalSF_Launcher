package com.minimal.launcher

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
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
import android.text.TextPaint
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max

class LauncherActivity : AppCompatActivity(), GestureActions.Host {

    private enum class Results { IDLE, LIST, NO_MATCH, AUTO }

    private lateinit var rootLayout: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var ramText: TextView
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
    private lateinit var homeTipView: TextView
    private lateinit var musicTipView: LinearLayout
    private lateinit var allAppsBtn: TextView
    private lateinit var settingsBtn: TextView
    private lateinit var headerGroup: LinearLayout
    private lateinit var resultsGroup: LinearLayout
    private lateinit var footerGroup: LinearLayout
    private lateinit var navGroup: LinearLayout

    // Landscape: two panes, and the keyboard is either docked (takes height) or floating (covers a side)
    private var landscape = false
    // Landscape's left pane (clock, music, todo) and the line beside it
    private var infoPane: View? = null
    private var paneDivider: View? = null
    private var dockedIme = false
    private var floatingIme = false

    private lateinit var todoList: MaxHeightRecyclerView
    private lateinit var todoDivider: View
    private lateinit var todoSection: View
    private lateinit var todoInput: EditText
    private lateinit var todoCount: TextView
    private lateinit var todoUndo: TextView
    private lateinit var appListFrame: View
    private lateinit var scrubber: AlphabetScrubber
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
    // What the optional MinimalSF Extended plugin's toggles change here; the defaults without it
    private var ext = Extended.DEFAULT
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> uiDirty = true }
    private val appsListener: () -> Unit = { onAppsChanged() }

    // Media - event driven via session listener + controller callback (no polling)
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var activeController: MediaController? = null
    private var musicNeedsAccess = false
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = displayMeta(metadata)
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlayIcon(state)
            onMusicStateChanged()
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

    // A 2s refresh is plenty for a glance; only runs while the RAM text is shown
    private val ramRunnable = object : Runnable {
        override fun run() { updateRam(); handler.postDelayed(this, 2_000) }
    }

    private lateinit var homeGestureDetector: GestureDetector
    // The gesture that ran last, so prompts can name it ("to lock the screen with swipe down...")
    private var lastGesture = Prefs.Gesture.DOUBLE_TAP

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.init(this)
        super.onCreate(savedInstanceState)
        Theme.update(this)
        setContentView(R.layout.activity_launcher)

        rootLayout = findViewById(R.id.rootLayout)
        clockText = findViewById(R.id.clockText)
        ramText = findViewById(R.id.ramText)
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
        homeTipView = findViewById(R.id.homeTipView)
        musicTipView = findViewById(R.id.musicTipView)
        allAppsBtn = findViewById(R.id.allAppsBtn)
        settingsBtn = findViewById(R.id.settingsBtn)
        todoList = findViewById(R.id.todoList)
        todoDivider = findViewById(R.id.todoDivider)
        todoSection = findViewById(R.id.todoSection)
        todoInput = findViewById(R.id.todoInput)
        todoCount = findViewById(R.id.todoCount)
        todoUndo = findViewById(R.id.todoUndo)
        appListFrame = findViewById(R.id.appListFrame)
        scrubber = findViewById(R.id.alphabetScrubber)
        scrubber.onJump = { pos -> (appList.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, 0) }
        headerGroup = findViewById(R.id.headerGroup)
        resultsGroup = findViewById(R.id.resultsGroup)
        footerGroup = findViewById(R.id.footerGroup)
        navGroup = findViewById(R.id.navGroup)

        applyOrientation()
        setupInsets()
        todoInput.setOnFocusChangeListener { _, _ -> updateCompact() }

        autoLaunchIcon.colorFilter = AppAdapter.GRAYSCALE
        findViewById<View>(R.id.musicTipClose).setOnClickListener { dismissMusicTip() }

        clockText.setOnClickListener { openClock() }
        // Re-fit when the clock's space changes (rotation, landscape panes)
        (clockText.parent as View).addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ ->
            if (r - l != or - ol) fitClock()
        }

        // Home gesture: double-tap & long-press on empty space anywhere on the home screen. Fed from
        // dispatchTouchEvent, so it also works where the app list is short or squeezed by the keyboard.
        homeGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!gesturesAllowed() || !isEmptySpace(e)) return false
                dismissHomeTip()
                runGesture(Prefs.Gesture.DOUBLE_TAP)
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                if (gesturesAllowed() && isEmptySpace(e)) runGesture(Prefs.Gesture.LONG_PRESS)
            }
        })

        adapter = AppAdapter(
            onClick = { app, view -> launchApp(app, view) },
            onLongClick = { app -> showAppOptions(app) }
        )
        appList.layoutManager = LinearLayoutManager(this)
        appList.adapter = adapter
        appList.defaultFocusHighlightEnabled = false
        appList.itemAnimator = null
        appList.setHasFixedSize(true)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterApps(s.toString()) }
        })
        // Enter / "go" launches the top result
        searchInput.setOnEditorActionListener { _, actionId, event ->
            val hardwareEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER
            val enter = actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                (hardwareEnter && event?.action == KeyEvent.ACTION_DOWN)
            if (enter) bestMatch?.let { launchApp(it, if (results == Results.AUTO) autoLaunchIcon else null) }
            // Also swallow Enter's key-up: unhandled, Android moves focus down to the results list.
            // It lands here after a hardware Enter in the todo box, which moves focus to search.
            enter || hardwareEnter
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
        todoInput.setOnEditorActionListener { _, actionId, event ->
            when {
                actionId == EditorInfo.IME_ACTION_DONE -> { addTodo(); true }
                // A hardware Enter arrives as key events; handling both stops Android moving focus
                // down to the results list (which shows as a grey block)
                event?.keyCode == KeyEvent.KEYCODE_ENTER -> { if (event.action == KeyEvent.ACTION_DOWN) addTodo(); true }
                else -> false
            }
        }
        findViewById<View>(R.id.addTodoBtn).setOnClickListener { addTodo() }
        screenTimeText.setOnClickListener { onScreenTimeClick() }
        settingsBtn.setOnClickListener {
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
        // No press highlight on purpose: the play / pause icon changing is the feedback
        musicBar.setOnTouchListener { _, event -> musicGesture.onTouchEvent(event); true }
        // TalkBack doesn't send touches: double tap is a click, and skip / open are in its actions menu
        musicBar.setOnClickListener { if (musicNeedsAccess) openNotificationAccess() else togglePlayPause() }
        musicBar.setOnLongClickListener { openMusicPlayer(); true }
        ViewCompat.addAccessibilityAction(musicBar, "next track") { _, _ -> skipNext(); true }
        ViewCompat.addAccessibilityAction(musicBar, "previous track") { _, _ -> skipPrev(); true }

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
        watchWallpaperColours(false)
        AppRepository.removeListener(appsListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun onAppsChanged() {
        refreshBuiltInShortcuts()
        // Refresh whatever is on screen. Only auto-launch if the user is actively on the home screen
        // (e.g. typed before the first load finished), not because a package changed in the background.
        if (showingAllApps) { adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this)); updateScrubber() }
        else filterApps(searchInput.text.toString(), allowAutoLaunch = hasWindowFocus())

        if (Prefs.isFirstLaunch(this)) { Prefs.setFirstLaunchDone(this); showFirstLaunchSetup() }
    }

    // --- Insets: pad for bars + keyboard, animated in sync with the keyboard ---

    private fun setupInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener { syncDimToKeyboard() }
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            navBottom = bars.bottom
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            // A floating keyboard reports (almost) no height, so it would sit on top of the content.
            // Apps can't ask where it is, so keep clear the side chosen in settings.
            val docked = landscape && imeVisible && imeBottom >= dp(KEYBOARD_MIN_DOCKED_DP)
            floatingIme = landscape && imeVisible && !docked
            val reserve = if (floatingIme) minOf(dp(FLOATING_KEYBOARD_DP), v.width * 0.4f).toInt() else 0
            val onLeft = Prefs.floatingKeyboardLeft(this)
            v.setPadding(bars.left + (if (onLeft) reserve else 0), bars.top, bars.right + (if (onLeft) 0 else reserve), v.paddingBottom)
            todoList.maxHeightPx = if (imeVisible || landscape) dp(72f).toInt() else todoList.xmlMaxHeightPx
            // While the keyboard animates, onProgress drives the bottom padding frame-by-frame
            if (!imeAnimating) {
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, max(bars.bottom, imeBottom))
            }
            syncDimToKeyboard()
            if (docked != dockedIme) { dockedIme = docked; updateCompact() }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setWindowInsetsAnimationCallback(rootLayout,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) imeAnimating = true
                }
                override fun onProgress(insets: WindowInsetsCompat, running: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
                    val bottom = max(navBottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                    if (rootLayout.paddingBottom != bottom) {
                        rootLayout.setPadding(rootLayout.paddingLeft, rootLayout.paddingTop, rootLayout.paddingRight, bottom)
                        syncDimToKeyboard()
                    }
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
        // Already home with the keyboard closed (e.g. with back): home brings it back. Coming from
        // another app, the window isn't focused yet and onWindowFocusChanged shows it instead.
        if (!Prefs.autoKeyboard(this)) hideKeyboard()
        else if (hasWindowFocus() && !Prefs.isFirstLaunch(this)) showKeyboard()
    }

    /**
     * Moves the groups between one column (portrait) and two panes (landscape). Views are moved rather
     * than recreating the activity, so rotating keeps the search text and the keyboard.
     */
    private fun applyOrientation() {
        val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        // The XML is already the portrait arrangement
        if (land == landscape) return
        landscape = land
        val focused = currentFocus
        val groups = listOf(headerGroup, resultsGroup, footerGroup, navGroup)
        groups.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        rootLayout.removeAllViews()
        rootLayout.orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        fun rows(height: Int, weight: Float = 0f) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, weight)
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        if (land) {
            fun pane() = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            val info = pane()
            info.addView(headerGroup, rows(wrap))
            // Pushes music + todo to the bottom of the pane
            info.addView(View(this), rows(0, 1f))
            info.addView(footerGroup, rows(wrap))
            val apps = pane()
            apps.addView(resultsGroup, rows(0, 1f))
            apps.addView(navGroup, rows(wrap))
            rootLayout.addView(info)
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(1f).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(getColor(R.color.grey_border))
            }
            rootLayout.addView(divider)
            rootLayout.addView(apps)
            infoPane = info
            paneDivider = divider
        } else {
            infoPane = null
            paneDivider = null
            rootLayout.addView(headerGroup, rows(wrap))
            rootLayout.addView(resultsGroup, rows(0, 1f))
            rootLayout.addView(footerGroup, rows(wrap))
            rootLayout.addView(navGroup, rows(wrap))
        }
        // There's less height in landscape
        (clockText.layoutParams as ViewGroup.MarginLayoutParams).topMargin = dp(if (land) 16f else 32f).toInt()
        focused?.requestFocus()
    }

    private fun resetHomeState() {
        cancelAutoLaunch()
        launchCooldown = false
        // Otherwise the keyboard stays closed on return and typing goes into the todo box
        if (todoInput.hasFocus()) searchInput.requestFocus()
        if (keepAllAppsOpen) {
            // Returning from app info - keep all apps open
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
            }.joinToString("\n\n").let { if (it.isEmpty()) it else it + "\n\n" + str(R.string.close_tip) }
        // Tapping the tip closes it for good, like using one of the gestures does
        homeTipView.setOnClickListener { dismissHomeTip() }
        homeTipView.visibility = if (homeTipWanted(results)) View.VISIBLE else View.GONE
    }

    /** The tip only shows on the idle home screen, and not at all when both its gestures are set to none. */
    private fun homeTipWanted(state: Results) = state == Results.IDLE && !showingAllApps && homeTipView.text.isNotEmpty()

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

    /** Not on anything you can tap: the clock, search, todo, music bar, buttons or an app row. */
    private fun isEmptySpace(e: MotionEvent): Boolean {
        if (startsIn(e, clockText, screenTimeText, searchTopSlot, searchBottomSlot, autoLaunchBar,
                musicBar, musicTipView, todoSection, navGroup)) return false
        if (!appList.isShown || !appList.getGlobalVisibleRect(hitRect)) return true
        if (!hitRect.contains(e.rawX.toInt(), e.rawY.toInt())) return true
        return appList.findChildViewUnder(e.rawX - hitRect.left, e.rawY - hitRect.top) == null
    }

    /**
     * Home gestures only run on the plain home screen. While searching or in all apps the screen is a
     * list, where a stray double tap (lock) or a swipe would interrupt picking an app.
     */
    private fun gesturesAllowed() = !showingAllApps && searchInput.text.isNullOrEmpty()

    /** Runs whatever the user put on [g] in settings → gestures. Returns false if it's set to "none". */
    private fun runGesture(g: Prefs.Gesture): Boolean {
        val code = Prefs.gesture(this, g)
        if (code.isEmpty()) return false
        lastGesture = g
        if (g == Prefs.Gesture.LONG_PRESS) appList.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        return GestureActions.run(this, code, this)
    }

    // GestureActions.Host
    override fun expandNotifications() = expandStatusBar("expandNotificationsPanel")
    override fun expandQuickSettings() = expandStatusBar("expandSettingsPanel")

    override fun launchApp(key: String) {
        val app = AppRepository.apps.firstOrNull { it.key == key }
        if (app != null) { launchApp(app); return }
        try {
            packageManager.getLaunchIntentForPackage(key)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
            } ?: Toast.makeText(this, str(R.string.app_not_found_check_gesture), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    override fun lockScreen() {
        if (!LockAccessibilityService.isSupported) {
            MinimalDialog.confirm(this, title = str(R.string.screen_lock_unavailable),
                message = str(R.string.locking_the_screen_needs_android, lastGesture.title),
                positiveText = str(R.string.ok), onPositive = {})
            return
        }
        if (LockAccessibilityService.instance?.lock() == true) return
        MinimalDialog.confirm(this,
            title = str(R.string.enable_screen_lock),
            message = str(R.string.to_lock_the_screen_with_2, lastGesture.title),
            positiveText = str(R.string.open_settings),
            negativeText = str(R.string.cancel),
            onPositive = { SpecialAccess.open(this, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        )
    }

    // --- Settings-driven UI ---

    /**
     * Re-reads MinimalSF Extended's toggles (settings > extended menu). Cheap: they're a local copy, and
     * the plugin itself isn't woken. Also catches the plugin being uninstalled, which puts back the defaults.
     */
    private fun applyExtended() {
        ext = Extended.flags(this)
        val colour = Prefs.colourIcons(this)
        if (colour != AppAdapter.colourIcons) {
            AppAdapter.colourIcons = colour
            @Suppress("NotifyDataSetChanged") adapter.notifyDataSetChanged()
        }
        autoLaunchIcon.colorFilter = if (colour) null else AppAdapter.GRAYSCALE
        updateSearchVisibility()
        refreshBuiltInShortcuts()
    }

    /** Apps' built-in shortcuts in search (MinimalSF Extended toggle), re-read in the background. */
    private fun refreshBuiltInShortcuts() = SearchShortcuts.refreshBuiltIn(this, ext.allShortcuts) {
        if (!isDestroyed && !showingAllApps && !searchInput.text.isNullOrEmpty())
            filterApps(searchInput.text.toString(), allowAutoLaunch = false)
    }

    /**
     * "hide search bar until you type": the bar is see-through while it's empty, but stays in place, so
     * typing still goes into it and tapping where it is still opens the keyboard.
     */
    private fun updateSearchVisibility() {
        searchInput.alpha = if (Prefs.hideEmptySearch(this) && searchInput.text.isNullOrEmpty()) 0f else 1f
    }

    private fun applyUi() {
        uiDirty = false
        applySearchPosition()
        applyHomeVisibility()
        applyFont()
        applyWallpaper()
        applyStatusBar()
        updateMusicBarVisibility()
        applyAppListLayout()
        updateHomeTip()
        clockFormat = null
        updateClock()
        if (showingAllApps) { adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this)); updateScrubber() }
        // The floating keyboard side may have changed
        ViewCompat.requestApplyInsets(rootLayout)
    }

    /** settings > home screen > hide status bar. A swipe from the top edge still shows it for a moment. */
    private fun applyStatusBar() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (Prefs.hideStatusBar(this)) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.statusBars())
        } else c.show(WindowInsetsCompat.Type.statusBars())
    }

    // --- Wallpaper (settings > home screen > show wallpaper) ---

    private var wallpaperOn = false
    private var wallpaperListener: Any? = null

    /**
     * Shows the phone's wallpaper behind the home screen, under a black layer so white text stays readable.
     * Nothing here reads the image (that would need a storage permission): Android's summary of its main
     * colours is enough to tell a light wallpaper, which always gets at least [Prefs.LIGHT_MIN_DIM].
     */
    private fun applyWallpaper() {
        val on = Prefs.showWallpaper(this)
        wallpaperOn = on
        if (on) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.navigationBarColor = Color.TRANSPARENT
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Theme.bg))
            window.navigationBarColor = Theme.bg
        }
        val solid = if (on) Color.TRANSPARENT else Theme.bg
        todoSection.setBackgroundColor(solid)
        navGroup.setBackgroundColor(solid)
        val bar = if (on) Color.argb(0x99, Color.red(Theme.bg), Color.red(Theme.bg), Color.red(Theme.bg)) else getColor(R.color.search_bg)
        musicBar.setBackgroundColor(bar)
        musicTipView.setBackgroundColor(bar)
        (searchInput.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor(bar)

        AppAdapter.textShadow = on
        shadowAll(rootLayout)
        @Suppress("NotifyDataSetChanged") adapter.notifyDataSetChanged()
        @Suppress("NotifyDataSetChanged") todoAdapter.notifyDataSetChanged()

        watchWallpaperColours(on)
        updateWallpaperDim()
    }

    private fun shadowAll(v: View) {
        if (v is RecyclerView) return
        if (v is TextView) AppAdapter.wallpaperStyle(v)
        if (v is ImageView) {
            val base = (v.getTag(R.id.tag_base_tint) as? ColorStateList) ?: v.imageTintList?.also { v.setTag(R.id.tag_base_tint, it) }
            if (base != null) v.imageTintList = if (AppAdapter.textShadow) ColorStateList.valueOf(AppAdapter.lift(base.defaultColor)) else base
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) shadowAll(v.getChildAt(i))
    }

    /** The wallpaper's main colours, re-checked when the user changes their wallpaper. */
    private var wallpaperLight = false

    private fun watchWallpaperColours(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 27) return
        val wm = android.app.WallpaperManager.getInstance(this)
        (wallpaperListener as? android.app.WallpaperManager.OnColorsChangedListener)?.let { wm.removeOnColorsChangedListener(it) }
        wallpaperListener = null
        if (!on) return
        wallpaperLight = isLight(wm.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM))
        val l = android.app.WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and android.app.WallpaperManager.FLAG_SYSTEM != 0) {
                wallpaperLight = isLight(colors); updateWallpaperDim()
            }
        }
        wm.addOnColorsChangedListener(l, handler)
        wallpaperListener = l
    }

    @android.annotation.TargetApi(27)
    private fun isLight(c: android.app.WallpaperColors?): Boolean {
        c ?: return false
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            c.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) return true
        // Any bright main colour counts: a half-white picture can still average out as "dark"
        return listOfNotNull(c.primaryColor, c.secondaryColor, c.tertiaryColor).any { it.luminance() > 0.4f }
    }

    /** Full dim while searching or in all apps; with the gradient on, lighter in the empty middle of home. */
    private fun updateWallpaperDim() {
        if (!wallpaperOn) { rootLayout.setBackgroundColor(Theme.bg); return }
        var dim = Prefs.wallpaperDim(this)
        // Dark theme: white text, so a light wallpaper needs a stronger black layer. Light theme: a white layer,
        // kept lighter so the picture isn't washed out; the text gets a white glow instead
        if (Theme.light) dim = dim * 55 / 100
        else if (wallpaperLight) dim = max(dim, Prefs.LIGHT_MIN_DIM)
        val full = Color.argb((dim * 255 / 100).toInt(), Color.red(Theme.bg), Color.red(Theme.bg), Color.red(Theme.bg))
        val idle = !showingAllApps && searchInput.text.isNullOrEmpty()
        rootLayout.background = if (Prefs.wallpaperGradient(this) && idle) {
            // Header (clock, date) and footer (search, music, todo, buttons) keep the full dim
            val fullA = dim * 255 / 100f
            KeyboardAwareDim(fullA, fullA * if (Theme.light) 0.2f else 0.35f).also { syncDim(it) }
        } else android.graphics.drawable.ColorDrawable(full)
    }

    /** Keeps the dim's bands in place: the top one on the clock, the bottom one just above the keyboard. */
    private fun syncDimToKeyboard() { (rootLayout.background as? KeyboardAwareDim)?.let { syncDim(it) } }

    private fun syncDim(d: KeyboardAwareDim) {
        // Full dim down to the middle of the lowest thing at the top (clock, date, screen time, or the search
        // bar when it's at the top), then the fade. Follows clock size and whatever is turned off; with
        // nothing at the top it's the top edge
        val rootTop = IntArray(2).also { rootLayout.getLocationInWindow(it) }[1]
        val top = listOf(clockText, dateText, screenTimeText, searchTopSlot)
            .filter { it.isShown && it.height > 0 }
            .maxOfOrNull { IntArray(2).also { p -> it.getLocationInWindow(p) }[1] - rootTop + it.height / 2 }
            ?: rootLayout.paddingTop
        // The bottom band starts at the top of the first control at the bottom (search bar, music, todo,
        // buttons). Those sit on the bottom padding, so while the keyboard slides they move exactly with it:
        // the band is placed from the padding every frame, using their height measured when it isn't moving.
        val visible = rootLayout.height - rootLayout.paddingBottom
        if (!imeAnimating) {
            val rootY = IntArray(2).also { rootLayout.getLocationInWindow(it) }[1]
            val bottomStart = listOf(searchBottomSlot, musicBar, todoSection, navGroup)
                .filter { it.isShown && it.height > 0 }
                .minOfOrNull { IntArray(2).also { p -> it.getLocationInWindow(p) }[1] - rootY }
            bottomControlsHeight = if (bottomStart == null) 0 else visible - bottomStart
        }
        d.set(top, visible - bottomControlsHeight, visible)
    }
    private var bottomControlsHeight = 0

    /**
     * The "lighter in the middle" dim: full dim above [topPx] (the middle of the clock) and below [bottomPx]
     * (the top of the bottom controls), fading in between. Both follow the layout, so they move with the
     * clock size and the keyboard. Behind the keyboard it's full dim.
     */
    private class KeyboardAwareDim(private val fullA: Float, private val midA: Float) : android.graphics.drawable.Drawable() {
        // Black over the wallpaper in the dark theme, white in the light one
        private val shade = Color.red(Theme.bg)
        private val paint = android.graphics.Paint()
        private var topPx = 0
        private var bottomPx = 0
        private var visibleBottom = 0

        fun set(top: Int, bottomStart: Int, visible: Int) {
            if (top == topPx && bottomStart == bottomPx && visible == visibleBottom) return
            topPx = top; bottomPx = bottomStart; visibleBottom = visible; paint.shader = null; invalidateSelf()
        }

        private fun shader(): android.graphics.Shader {
            val b = bounds
            val h = (if (visibleBottom > 0) visibleBottom else b.height()).toFloat().coerceAtLeast(1f)
            val top = (topPx / h).coerceIn(0f, 1f)
            val bottom = (bottomPx / h).coerceIn(top, 1f); val ramp = 0.12f
            val stops = IntArray(81) { i ->
                val y = i / 80f
                val t = when {
                    y <= top || y >= bottom -> 1f
                    y < top + ramp -> 1 - (y - top) / ramp
                    y > bottom - ramp -> 1 - (bottom - y) / ramp
                    else -> 0f
                }.coerceIn(0f, 1f)
                android.graphics.Color.argb((midA + (fullA - midA) * t).toInt(), shade, shade, shade)
            }
            return android.graphics.LinearGradient(0f, b.top.toFloat(), 0f, b.top + h, stops, null, android.graphics.Shader.TileMode.CLAMP)
        }

        override fun draw(canvas: android.graphics.Canvas) {
            if (paint.shader == null) paint.shader = shader()
            canvas.drawRect(bounds, paint)
        }
        override fun onBoundsChange(bounds: android.graphics.Rect) { paint.shader = null }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
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
        fitClock()

        adapter.setTypeface(tf, m)
        todoAdapter.setTypeface(tf, m)
        scrubber.setTypeface(tf)

        // Icon size follows the text size, so re-apply after a font size change
        applyNavButtons()
    }

    override fun onResume() {
        super.onResume()
        // Light / dark is per screen (the home screen can stay dark over a dark wallpaper while settings
        // are light): take this screen's back, and redraw the parts drawn in code if it differs
        val wasLight = Theme.light
        Theme.update(this)
        if (uiDirty || wasLight != Theme.light) applyUi()
        applyExtended()

        handler.removeCallbacks(clockRunnable); handler.post(clockRunnable)
        handler.removeCallbacks(ramRunnable)
        if (Prefs.showRam(this)) handler.post(ramRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(ramRunnable)
        cancelAutoLaunch()
    }

    // Start/stop, not resume/pause: the home screen is still visible while paused (an app opening over
    // it, a dialog-style activity), and the bar would read "no music playing" during that
    override fun onStart() {
        super.onStart()
        startMusic()
    }

    override fun onStop() {
        super.onStop()
        stopMusic()
        // Reset while hidden, so returning home shows a clean screen without a visible flicker
        resetHomeState()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        clockFormat = null
        updateClock()
        applyOrientation()
        updateCompact()
        ViewCompat.requestApplyInsets(rootLayout)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // On first launch the welcome prompt is about to open (once apps load); showing the keyboard
        // now would make it flash open and shut. It opens normally once the prompts are dismissed.
        if (Prefs.isFirstLaunch(this)) return
        if (hasFocus && !showingAllApps && !todoInput.hasFocus() && Prefs.autoKeyboard(this)) showKeyboard()
    }

    // --- First launch ---

    /**
     * One welcome prompt instead of three in a row: a tip, a way into settings, and - only when it isn't
     * already - setting MinimalSF as the default launcher. The now playing bar and the rest live in settings.
     */
    private fun showFirstLaunchSetup() {
        val isDefault = isDefaultLauncher()
        val openSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
        val message = str(R.string.type_to_launch_any_app) +
            (if (isDefault) "" else str(R.string.n_nset_minimalsf_as_your))
        if (isDefault) {
            MinimalDialog.confirm(this, title = str(R.string.welcome_to_minimalsf), message = message,
                negativeText = str(R.string.later), onNegative = {},
                positiveText = str(R.string.settings_2), onPositive = { openSettings() })
        } else {
            MinimalDialog.confirm(this, title = str(R.string.welcome_to_minimalsf), message = message,
                negativeText = str(R.string.later), onNegative = {},
                neutralText = str(R.string.settings_2), onNeutral = { openSettings() },
                positiveText = str(R.string.set_default),
                onPositive = { try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {} })
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    }

    // --- Swipes (down / up / left / right) on the home screen ---

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Watches every touch for double tap / long press; it never consumes them
        homeGestureDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pullStartX = ev.rawX; pullStartY = ev.rawY; pullTriggered = false
                // Only watch directions that have an action, and never steal a scroll from a list
                // that can still move that way, or a horizontal drag inside the music bar / text boxes
                val home = gesturesAllowed()
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
        try { Class.forName("android.app.StatusBarManager").getMethod(method).invoke(getSystemService("statusbar")) }
        catch (_: Exception) {
            val what = if (method == "expandSettingsPanel") str(R.string.quick_settings) else str(R.string.notifications)
            Toast.makeText(this, str(R.string.can_t_open_on_this, what), Toast.LENGTH_SHORT).show()
        }
    }

    // --- Media ---

    private fun startMusic() {
        if (!Prefs.showMusic(this) || sessionsListener != null) return
        val msm = getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(this, MusicNotificationListener::class.java)
        try {
            val l = MediaSessionManager.OnActiveSessionsChangedListener { bindController(pickController(it)) }
            msm.addOnActiveSessionsChangedListener(l, component, handler)
            sessionsListener = l
            musicNeedsAccess = false
            bindController(pickController(msm.getActiveSessions(component)))
        } catch (_: SecurityException) {
            musicNeedsAccess = true
            bindController(null)
            musicTitle.text = str(R.string.tap_to_allow_music_access)
        }
    }

    private fun stopMusic() {
        sessionsListener?.let { l ->
            try { getSystemService(MediaSessionManager::class.java)?.removeOnActiveSessionsChangedListener(l) } catch (_: Exception) {}
        }
        sessionsListener = null
        musicStopping = true
        bindController(null)
        musicStopping = false
    }

    private fun pickController(ctrls: List<MediaController>?): MediaController? =
        ctrls?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: ctrls?.firstOrNull()

    private fun bindController(c: MediaController?) {
        if (activeController?.sessionToken != c?.sessionToken) {
            try { activeController?.unregisterCallback(mediaCallback) } catch (_: Exception) {}
            activeController = c
            c?.registerCallback(mediaCallback, handler)
        }
        onMusicStateChanged()
        if (c == null) { musicTitle.text = str(R.string.no_music_playing); musicIconView.setImageResource(R.drawable.ic_music); return }
        displayMeta(c.metadata)
        updatePlayIcon(c.playbackState)
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) showMusicTip()
    }

    private fun displayMeta(meta: MediaMetadata?) {
        if (meta == null) { musicTitle.text = str(R.string.no_music_playing); return }
        val t = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "unknown"
        val a = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        musicTitle.text = if (a.isNotEmpty()) "$t · $a" else t
    }

    private fun updatePlayIcon(s: PlaybackState?) {
        // Drawn icons, not ▶/⏸ characters: Samsung's font forces ⏸ to a colour emoji
        musicIconView.setImageResource(if (s?.state == PlaybackState.STATE_PLAYING) R.drawable.ic_pause else R.drawable.ic_play)
    }

    /** Playing, or about to (buffering): "only when music is playing" keeps the bar for both. */
    private fun isPlaying() = activeController?.playbackState?.state.let {
        it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING
    }

    /**
     * "only while music plays": after a pause the bar stays for [MUSIC_PAUSE_GRACE_MS], so the music can be
     * resumed from it, then hides. Called on every playback change.
     */
    private var wasPlaying = false
    private var pausedAt = 0L
    private var musicStopping = false
    private val musicHideRunnable = Runnable { updateCompact() }
    private fun onMusicStateChanged() {
        // Leaving home unbinds the player; that isn't a pause
        if (musicStopping) return
        val playing = isPlaying()
        if (wasPlaying && !playing) pausedAt = android.os.SystemClock.elapsedRealtime()
        wasPlaying = playing
        if (!Prefs.musicOnlyWhenPlaying(this)) return
        handler.removeCallbacks(musicHideRunnable)
        val left = MUSIC_PAUSE_GRACE_MS - (android.os.SystemClock.elapsedRealtime() - pausedAt)
        if (!playing && pausedAt > 0 && left > 0) handler.postDelayed(musicHideRunnable, left)
        updateCompact()
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
    private fun openNotificationAccess() = SpecialAccess.open(this, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    private fun updateMusicBarVisibility() {
        if (!Prefs.showMusic(this)) stopMusic()
        updateCompact()
    }

    /**
     * While searching or browsing all apps, the todo section and music bar step aside so the results
     * get the space - with the keyboard up there was none left for them.
     */
    private fun updateCompact() {
        // Landscape results have their own pane, so only a docked keyboard's lost height matters there.
        // The clock and music fit beside it; typing a task swaps them for the todo section.
        val compact = if (landscape) dockedIme else showingAllApps || searchInput.text.isNotBlank()
        val editingTodo = landscape && dockedIme && todoInput.hasFocus()
        headerGroup.visibility = if (editingTodo) View.GONE else View.VISIBLE
        val todo = if ((compact && !editingTodo) || !Prefs.showTodo(this)) View.GONE else View.VISIBLE
        if (todoSection.visibility != todo) { todoSection.visibility = todo; todoDivider.visibility = todo }
        val music = if (landscape) !editingTodo else !compact
        val playingOk = !Prefs.musicOnlyWhenPlaying(this) || isPlaying() ||
            (pausedAt > 0 && android.os.SystemClock.elapsedRealtime() - pausedAt < MUSIC_PAUSE_GRACE_MS)
        musicBar.visibility = if (Prefs.showMusic(this) && music && playingOk) View.VISIBLE else View.GONE
        if (compact || !Prefs.showMusic(this)) musicTipView.visibility = View.GONE
        // Beside a docked keyboard the results only have room for a row or two without these buttons
        navGroup.visibility = if (landscape && dockedIme && searchInput.text.isNotBlank()) View.GONE else View.VISIBLE
        // Stays visible in all apps even if hidden, as it's the way to close it
        allAppsBtn.visibility = if (Prefs.showAllAppsButton(this) || showingAllApps) View.VISIBLE else View.GONE
        settingsBtn.visibility = if (Prefs.showSettingsButton(this)) View.VISIBLE else View.GONE
        updatePanes()
    }

    /** With everything on the left hidden, search and apps take the whole width instead of half. */
    private fun updatePanes() {
        val info = infoPane ?: return
        val hasContent = listOf(clockText, dateText, ramText, screenTimeText, musicBar, musicTipView, todoSection)
            .any { it.visibility == View.VISIBLE }
        val v = if (hasContent) View.VISIBLE else View.GONE
        info.visibility = v
        paneDivider?.visibility = v
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

    private val hideUndo = Runnable { todoUndo.visibility = View.GONE; updateTodoCount() }

    /** Delete sits next to "important", so a removal can be undone for a few seconds. */
    private fun offerUndo(item: TodoItem, position: Int) {
        handler.removeCallbacks(hideUndo)
        todoCount.visibility = View.GONE
        todoUndo.visibility = View.VISIBLE
        todoUndo.setOnClickListener {
            handler.removeCallbacks(hideUndo)
            val at = position.coerceIn(0, todos.size)
            todos.add(at, item); saveTodos(); todoAdapter.notifyItemInserted(at)
            hideUndo.run()
        }
        handler.postDelayed(hideUndo, 5000)
    }

    private fun updateTodoCount() {
        todoCount.visibility = if (todoUndo.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
                // Position is read at click time - the bind-time position goes stale after removals
                val toggleDone = View.OnClickListener { edit { it.copy(done = !it.done) } }
                checkBox.setOnClickListener(toggleDone)
                todoText.setOnClickListener(toggleDone)
                importantBtn.setOnClickListener { edit { it.copy(important = !it.important) } }
                removeBtn.setOnClickListener {
                    val p = bindingAdapterPosition
                    if (p != RecyclerView.NO_POSITION) {
                        val removed = todos.removeAt(p); saveTodos(); notifyItemRemoved(p)
                        offerUndo(removed, p)
                    }
                }
                // Rows are one line; long press shows the whole task and lets it be changed
                todoText.setOnLongClickListener {
                    val p = bindingAdapterPosition
                    if (p == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                    val id = todos[p].id
                    MinimalDialog.textInput(this@LauncherActivity, "edit task", "task", todos[p].text) { text ->
                        val i = todos.indexOfFirst { it.id == id }
                        if (i >= 0 && text.isNotEmpty()) { todos[i] = todos[i].copy(text = text); saveTodos(); notifyItemChanged(i) }
                    }
                    true
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
            AppAdapter.shadow(holder.todoText)
            holder.todoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * mult)
            holder.todoText.text = item.text
            holder.checkBox.setImageResource(if (item.done) R.drawable.ic_check_on else R.drawable.ic_check_off)
            holder.checkBox.alpha = if (item.done) 0.4f else 1f

            val strike = if (item.done) holder.todoText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                         else holder.todoText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.todoText.paintFlags = strike
            holder.todoText.alpha = if (item.done) 0.3f else 1f
            holder.todoText.setTextColor(if (item.important && !item.done) RED else AppAdapter.lift(GREY))
            holder.checkBox.imageTintList = ColorStateList.valueOf(if (item.important && !item.done) RED else GREY)
            holder.importantBtn.imageTintList = ColorStateList.valueOf(if (item.important) RED else FAINT)
            holder.removeBtn.imageTintList = ColorStateList.valueOf(FAINT)
        }
    }

    // --- App options ---

    private fun showAppOptions(app: AppInfo) {
        cancelAutoLaunch()
        if (app.isShortcut) { showShortcutOptions(app); return }
        val isHidden = app.key in Prefs.getHiddenApps(this)
        val currentKeyword = Prefs.getKeywords(this)[app.key] ?: ""

        // The app's own shortcuts first ("new chat", "navigate home"…), like other launchers show them
        // Each entry can be turned off in settings > extended menu > long press menu (MinimalSF Extended)
        val shortcuts = if (!Prefs.menuShows(this, "menu_shortcuts")) emptyList()
            else AppShortcuts.forPackage(this, app.packageName, app.user).take(4)
        // Actions picked for the main menu (settings > extended menu > long press menu) sit next to app
        // info; the rest stay behind "extended…". Only then is the plugin asked for its actions here.
        val split = ext.mainActions.isNotEmpty() && ext.anyAction && Extended.available(this)
        val all = if (split) Extended.actions(this) else emptyList()
        val extActions = all.filter { it.id in ext.mainActions }
        val rest = all.filter { it.id !in ext.mainActions }
        val menu = listOfNotNull(
            if (!Prefs.menuShows(this, "menu_app_info")) null else Entry(str(R.string.app_info), R.drawable.ic_info) { keepAllAppsOpen = showingAllApps; try { getSystemService(LauncherApps::class.java)?.startAppDetailsActivity(app.component, app.user, null, null) } catch (_: Exception) {} },
            if (!app.canUninstall || !Prefs.menuShows(this, "menu_uninstall")) null else Entry(str(R.string.uninstall), R.drawable.ic_uninstall) { keepAllAppsOpen = showingAllApps; try { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")).putExtra(Intent.EXTRA_USER, app.user)) } catch (_: Exception) {} },
            if (!Prefs.menuShows(this, "menu_keyword")) null else Entry(str(R.string.set_keyword), R.drawable.ic_keyword, trailing = currentKeyword.ifEmpty { null }) { KeywordPrompt.show(this, app.key, app.label) },
        ) + extActions.map { a -> Entry(a.label, R.drawable.ic_extended, drawable = a.icon) { runExtended(app, a) } } + listOfNotNull(
            // Only with the optional MinimalSF Extended plugin installed, and some of its actions turned on
            if (!Extended.available(this) || !ext.anyAction || (split && rest.isEmpty())) null
            else Entry(str(R.string.extended), R.drawable.ic_extended) { showExtended(app, if (split) rest else null) },
            if (!Prefs.menuShows(this, "menu_hide")) null else Entry(if (isHidden) str(R.string.show_in_search) else str(R.string.hide_from_search), if (isHidden) R.drawable.ic_eye else R.drawable.ic_eye_off) {
                Prefs.setAppHidden(this, app.key, !isHidden)
                if (showingAllApps) { adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this)); updateScrubber() }
                else filterApps(searchInput.text.toString(), allowAutoLaunch = false)
            }
        )
        val items = shortcuts.map { AppShortcuts.label(it) } + menu.map { it.label }
        val icons = IntArray(shortcuts.size) { R.drawable.ic_shortcut } + menu.map { it.icon }.toIntArray()
        val drawables = List(shortcuts.size) { null } + menu.map { it.drawable }
        val trailing = arrayOfNulls<String>(shortcuts.size) + menu.map { it.trailing }.toTypedArray()
        // Usage stats only cover this profile
        val usage = if (screenTimeActive() && !app.otherProfile) ScreenTime.today[app.packageName]?.let { "${ScreenTime.format(it)} today" } else null
        val version = try { packageManager.getPackageInfo(app.packageName, 0).versionName } catch (_: Exception) { null }
        val details = listOfNotNull(app.packageName, version?.let { "version $it" }, usage).joinToString("\n")

        MinimalDialog.options(this, title = app.label, items = items.toTypedArray(), icons = icons,
            subtitle = details, trailing = trailing, iconDrawables = drawables) { picked ->
            if (picked < shortcuts.size) {
                val s = shortcuts[picked]
                if (!AppShortcuts.start(this, s.`package`, s.id, user = app.user)) Toast.makeText(this, str(R.string.shortcut_unavailable), Toast.LENGTH_SHORT).show()
                return@options
            }
            menu[picked - shortcuts.size].run()
        }
    }

    /** Long press on a shortcut in the results: a keyword and removal for saved ones, app info for all. */
    private fun showShortcutOptions(sc: AppInfo) {
        val saved = SearchShortcuts.savedEntry(this, sc.key)
        val keyword = Prefs.getKeywords(this)[sc.key] ?: ""
        val refilter = { filterApps(searchInput.text.toString(), allowAutoLaunch = false) }
        val menu = listOfNotNull(
            if (saved == null) null else Entry(str(R.string.set_keyword), R.drawable.ic_keyword, trailing = keyword.ifEmpty { null }) {
                KeywordPrompt.show(this, sc.key, sc.matchLabel, refilter)
            },
            AppRepository.apps.firstOrNull { it.key == sc.packageName }?.let { app ->
                Entry(str(R.string.app_info), R.drawable.ic_info) { try { getSystemService(LauncherApps::class.java)?.startAppDetailsActivity(app.component, app.user, null, null) } catch (_: Exception) {} }
            },
            if (saved == null) null else Entry(str(R.string.remove_saved_shortcut), R.drawable.ic_close) {
                MinimalDialog.confirm(this, title = str(R.string.remove_shortcut), message = str(R.string.remove_from_saved_shortcuts, sc.matchLabel),
                    positiveText = str(R.string.remove), negativeText = str(R.string.cancel),
                    onPositive = { Prefs.removePinnedShortcut(this, saved); refilter() })
            },
        )
        MinimalDialog.options(this, title = sc.matchLabel,
            subtitle = if (saved != null) str(R.string.saved_shortcut, AppRepository.labelFor(this, sc.packageName).lowercase())
                       else str(R.string.app_shortcut_2, AppRepository.labelFor(this, sc.packageName).lowercase()),
            items = menu.map { it.label }.toTypedArray(), icons = menu.map { it.icon }.toIntArray(),
            trailing = menu.map { it.trailing }.toTypedArray()) { menu[it].run() }
    }

    /** MinimalSF Extended's actions (force stop, clear, freeze, freeform), run by the plugin through Shizuku. */
    /**
     * The plugin decides what's in this menu (names, icons, confirm prompts), so new actions arrive with
     * a plugin update; MinimalSF only shows the list and passes the chosen action back.
     */
    private fun showExtended(app: AppInfo, only: List<Extended.Action>? = null) {
        val actions = only ?: Extended.actions(this)
        if (actions.isEmpty()) { Toast.makeText(this, str(R.string.minimalsf_extended_has_no_actions), Toast.LENGTH_SHORT).show(); return }
        MinimalDialog.options(this, title = app.label, subtitle = str(R.string.minimalsf_extended),
            items = actions.map { it.label }.toTypedArray(), iconDrawables = actions.map { it.icon }) { picked ->
            runExtended(app, actions[picked])
        }
    }

    /** Runs one of the plugin's actions on [app], asking first when the plugin says to. */
    private fun runExtended(app: AppInfo, action: Extended.Action) {
        val run = { if (!Extended.run(this, action.id, app)) Toast.makeText(this, str(R.string.minimalsf_extended_isn_t_available), Toast.LENGTH_SHORT).show() }
        val confirm = action.confirm
        if (confirm == null) run()
        else MinimalDialog.confirm(this, title = confirm.title.replace("{app}", app.label),
            message = confirm.message.replace("{app}", app.label),
            positiveText = confirm.button, negativeText = str(R.string.cancel), onPositive = run)
    }

    /** One long press menu entry. [drawable] (an icon from the plugin) wins over [icon]. */
    private class Entry(val label: String, val icon: Int, val drawable: android.graphics.drawable.Drawable? = null,
                        val trailing: String? = null, val run: () -> Unit)

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
        if (intent == null) { Toast.makeText(this, str(R.string.no_clock_app_found), Toast.LENGTH_SHORT).show(); return }
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    // --- Screen time ---

    private fun screenTimeActive() = Prefs.showScreenTime(this) && ScreenTime.hasAccess(this)

    private fun refreshScreenTime() {
        val show = Prefs.showScreenTime(this)
        screenTimeText.visibility = if (show) View.VISIBLE else View.GONE
        updatePanes()
        if (!show) return
        if (!ScreenTime.hasAccess(this)) { screenTimeText.text = str(R.string.tap_to_allow_screen_time); return }
        if (ScreenTime.today.isEmpty()) screenTimeText.text = str(R.string.screen_time)
        ScreenTime.refresh(this) {
            if (!isDestroyed) screenTimeText.text = str(R.string.screen_time_2, ScreenTime.format(ScreenTime.totalToday))
        }
    }

    private fun onScreenTimeClick() {
        if (!ScreenTime.hasAccess(this)) { ScreenTime.openAccessSettings(this); return }
        val byApp = AppRepository.mainApps().associateBy { it.packageName }
        val top = ScreenTime.today.entries.filter { it.key in byApp }.sortedByDescending { it.value }.take(8)
        val wellbeing = ScreenTime.wellbeingIntent(this)
        val items = top.map { byApp.getValue(it.key).label }
        val trailing = top.map { ScreenTime.format(it.value) }
        if (items.isEmpty() && wellbeing == null) return
        MinimalDialog.options(this, title = str(R.string.screen_time_today),
            subtitle = ScreenTime.format(ScreenTime.totalToday) + " total",
            items = items.toTypedArray(), trailing = trailing.toTypedArray<String?>(),
            // Digital Wellbeing (or the phone's own screen time page) sits at the top right
            button = wellbeing?.let { w -> MinimalDialog.TitleButton(str(R.string.digital_wellbeing)) { try { startActivity(w) } catch (_: Exception) {} } }
        ) { which -> byApp[top[which].key]?.let { launchApp(it) } }
    }

    /** Line icon left of a text button, sized to the text so both nav buttons match. */
    private fun setNavIcon(tv: TextView, res: Int?) {
        val d = res?.let { androidx.core.content.ContextCompat.getDrawable(this, it)?.mutate() }
        val size = (tv.textSize * 1.25f).toInt()
        d?.setBounds(0, 0, size, size)
        d?.setTint(tv.currentTextColor)
        tv.setCompoundDrawablesRelative(d, null, null, null)
    }

    /** The two bottom buttons, shown as icon + text, icon only or text only. */
    private fun applyNavButtons() {
        val style = Prefs.navStyle(this)
        fun apply(tv: TextView, label: String, icon: Int) {
            tv.text = if (style == "icon") "" else label
            tv.contentDescription = label
            setNavIcon(tv, if (style == "text") null else icon)
        }
        apply(allAppsBtn, if (showingAllApps) str(R.string.close) else str(R.string.all_apps), if (showingAllApps) R.drawable.ic_close else R.drawable.ic_grid)
        apply(settingsBtn, "settings", R.drawable.ic_settings)
    }

    /** All apps can be a grid; search results are always a list, so their numbers stay readable. */
    private fun applyAppListLayout() {
        val columns = if (showingAllApps && Prefs.allAppsGrid(this)) Prefs.allAppsColumns(this) else 0
        val current = appList.layoutManager
        if (columns > 0) {
            if (current is GridLayoutManager) current.spanCount = columns
            else appList.layoutManager = GridLayoutManager(this, columns)
        } else {
            val lm = (current as? LinearLayoutManager)?.takeIf { it !is GridLayoutManager }
                ?: LinearLayoutManager(this).also { appList.layoutManager = it }
            // With the search bar at the bottom, results start right above it (closest to the thumb).
            // Positions don't change, so the number badges still read 1, 2, 3 from the best match.
            val reverse = Prefs.searchAtBottom(this) && !showingAllApps
            if (lm.reverseLayout != reverse) lm.reverseLayout = reverse
        }
        adapter.setGrid(columns > 0)
        adapter.setShowIcons(Prefs.showAppIcons(this))
    }

    /** Hides whatever the user turned off in settings, keeping the spacing right for what's left. */
    private fun applyHomeVisibility() {
        val clock = Prefs.showClock(this)
        val date = Prefs.showDate(this)
        fun shown(on: Boolean) = if (on) View.VISIBLE else View.GONE
        clockText.visibility = shown(clock)
        dateText.visibility = shown(date)
        ramText.visibility = shown(Prefs.showRam(this))
        // The date's bottom padding normally separates the header from the search bar
        val gap = dp(12f).toInt()
        headerGroup.setPadding(0, if (!clock && !date) gap else 0, 0, if (!date) gap else 0)
        (screenTimeText.layoutParams as ViewGroup.MarginLayoutParams).topMargin = if (date) dp(-14f).toInt() else 0
        updatePanes()
    }

    // --- Clock ---

    private fun updateRam() {
        ramText.text = try {
            val mi = ActivityManager.MemoryInfo()
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            // Used/total, the way storage and system monitors read; availMem includes what Android can reclaim
            "ram ${(mi.totalMem - mi.availMem) / (1024 * 1024)}/${mi.totalMem / (1024 * 1024)}mb"
        } catch (_: Exception) { "ram ---mb" }
    }

    private fun updateClock() {
        val use24 = Prefs.use24hClock(this)
        val fmt = clockFormat?.takeIf { clockIs24h == use24 }
            ?: SimpleDateFormat(if (use24) "HH:mm" else "h:mm a", Locale.getDefault()).also { clockFormat = it; clockIs24h = use24 }
        // A format keeps the timezone it was created with, and these live as long as the home screen
        val zone = TimeZone.getDefault()
        fmt.timeZone = zone
        dateFormat.timeZone = zone
        val now = Date()
        // "am"/"pm" are the only letters, so the case setting can apply to the whole text
        clockText.text = fmt.format(now).let { t ->
            if (use24) t else when (Prefs.amPmCase(this)) { "lower" -> t.lowercase(); "upper" -> t.uppercase(); else -> t }
        }
        dateText.text = dateFormat.format(now)
        fitClock()
    }

    /** The chosen clock size, made smaller only if the time wouldn't fit on one line. */
    private fun fitClock() {
        val dm = resources.displayMetrics
        val want = FontManager.clockSizeSp(this)
        val avail = (clockText.parent as View).width - clockText.paddingLeft - clockText.paddingRight
        var size = want
        if (avail > 0) {
            val paint = TextPaint(clockText.paint)
            val text = clockText.text.toString()
            while (size > 20f) {
                paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, dm)
                if (paint.measureText(text) <= avail) break
                size -= 2f
            }
        }
        if (clockText.textSize != TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, dm)) {
            clockText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }
    }

    // --- Search ---

    /**
     * Ranking: label prefix → keyword prefix → word start → anywhere in label → anywhere in keyword.
     * "starts with" mode only allows the first two. With "keyword replaces app name" on, an app that has
     * a keyword matches on the keyword only.
     */
    private fun rank(app: AppInfo, q: String, keyword: String?, fromStart: Boolean, keywordOnly: Boolean): Int? {
        // "keyword replaces app name": an app with a keyword is found by its keyword alone
        if (keywordOnly && keyword != null) return when {
            keyword.startsWith(q) -> 0
            fromStart -> null
            keyword.contains(q) -> 3
            else -> null
        }
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

    /** Apps first, then shortcuts (saved, and built-in ones with MinimalSF Extended), each ranked on its own. */
    private fun searchApps(q: String, hidden: Set<String>, keywords: Map<String, String>): List<AppInfo> {
        val (apps, shortcuts) = searchSplit(q, hidden, keywords)
        return apps + shortcuts
    }

    private fun searchSplit(q: String, hidden: Set<String>, keywords: Map<String, String>): Pair<List<AppInfo>, List<AppInfo>> {
        val fromStart = Prefs.searchFromStart(this)
        val keywordOnly = Prefs.keywordReplacesName(this)
        fun ranked(list: List<AppInfo>) = list.mapNotNull { app ->
            if (app.key in hidden) null
            else rank(app, q, keywords[app.key], fromStart, keywordOnly)?.let { app to it }
        }.sortedBy { it.second }.map { it.first }
        return ranked(AppRepository.apps) to ranked(SearchShortcuts.all(this, ext.allShortcuts, hidden))
    }

    /**
     * What opens on its own for these matches (settings > search > when an app and a shortcut match).
     * Null shows the list.
     */
    private fun autoLaunchTarget(apps: List<AppInfo>, shortcuts: List<AppInfo>): AppInfo? = when (Prefs.shortcutAutoLaunch(this)) {
        "app" -> apps.singleOrNull() ?: if (apps.isEmpty()) shortcuts.singleOrNull() else null
        "shortcut" -> shortcuts.singleOrNull() ?: if (shortcuts.isEmpty()) apps.singleOrNull() else null
        "never" -> if (shortcuts.isEmpty()) apps.singleOrNull() else null
        else -> (apps + shortcuts).singleOrNull()
    }

    /** The icon of a visible result row, for the launch animation. */
    private fun rowIcon(position: Int) = (appList.findViewHolderForAdapterPosition(position) as? AppAdapter.VH)?.icon

    private fun filterApps(raw: String, allowAutoLaunch: Boolean = true) {
        cancelAutoLaunch()
        val q = raw.trim().lowercase()
        updateCompact()
        clearBtn.visibility = if (raw.isNotEmpty()) View.VISIBLE else View.GONE
        updateSearchVisibility()

        if (q.isNotEmpty() && showingAllApps) setAllAppsMode(false)
        updateWallpaperDim()
        if (q.isEmpty()) {
            bestMatch = null
            if (showingAllApps) { adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this)); updateScrubber() }
            else showResults(Results.IDLE)
            return
        }

        val apps = AppRepository.apps
        val hidden = Prefs.getHiddenApps(this)
        val keywords = Prefs.getKeywords(this)

        // Exact keyword wins outright (an app's, or a saved shortcut's)
        val keywordMatch = apps.firstOrNull { it.key !in hidden && keywords[it.key] == q }
            ?: SearchShortcuts.saved(this).firstOrNull { it.key !in hidden && it.packageName !in hidden && keywords[it.key] == q }
        if (keywordMatch != null) { showAutoLaunch(keywordMatch, allowAutoLaunch); return }

        val (appMatches, shortcutMatches) = searchSplit(q, hidden, keywords)
        val matches = appMatches + shortcutMatches

        // "w2" opens the 2nd result for "w". Only when "w2" itself matches nothing, so an app with a
        // number in its name ("fifa 23") can still be typed out.
        if (matches.isEmpty() && q.length > 1 && q.last() in '1'..'9') {
            val base = q.dropLast(1).trimEnd()
            val results = searchApps(base, hidden, keywords)
            val target = results.getOrNull(q.last() - '1')
            if (target != null) {
                bestMatch = target
                adapter.update(results, base, hidden)
                showResults(Results.LIST)
                if (allowAutoLaunch) launchApp(target, rowIcon(q.last() - '1'))
                return
            }
        }

        // With auto-launch turned off (MinimalSF Extended) a single match is a normal row: tap or Enter opens it
        val auto = if (Prefs.autoLaunchSingle(this)) autoLaunchTarget(appMatches, shortcutMatches) else null
        when {
            matches.isEmpty() -> { bestMatch = null; showResults(Results.NO_MATCH) }
            auto != null -> showAutoLaunch(auto, allowAutoLaunch)
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
        setAppListShown(state == Results.IDLE || state == Results.LIST)
        noMatch.visibility = if (state == Results.NO_MATCH) View.VISIBLE else View.GONE
        autoLaunchBar.visibility = if (state == Results.AUTO) View.VISIBLE else View.GONE
        if (state == Results.AUTO && prev != Results.AUTO) {
            autoLaunchBar.alpha = 0f
            autoLaunchBar.animate().alpha(1f).setDuration(120).start()
        }
        if (!Prefs.homeTipShown(this)) homeTipView.visibility = if (homeTipWanted(state)) View.VISIBLE else View.GONE
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
        // Our own entry in the list, shown when the settings button is hidden
        if (app.packageName == packageName) { startActivity(Intent(this, SettingsActivity::class.java)); return }
        keepAllAppsOpen = false
        app.shortcutId?.let { id ->
            launchCooldown = true
            if (!AppShortcuts.start(this, app.packageName, id, source?.takeIf { it.isShown && it.width > 0 })) {
                launchCooldown = false
                val msg = if (AppShortcuts.available(this)) str(R.string.shortcut_no_longer_available) else str(R.string.set_minimalsf_as_your_home)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                return
            }
            handler.postDelayed({ launchCooldown = false }, 600)
            return
        }
        // Reveal the app from the icon that was tapped
        val opts = source?.takeIf { it.isShown && it.width > 0 }?.let {
            ActivityOptions.makeClipRevealAnimation(it, 0, 0, it.width, it.height).toBundle()
        }
        // Block a second launch from keystrokes that arrive while this one opens.
        // The search text is cleared in onStop, once the app covers the screen.
        launchCooldown = true
        try {
            // Opens this exact icon, in its own profile (a package's launch intent can only reach one icon)
            getSystemService(LauncherApps::class.java).startMainActivity(app.component, app.user, null, opts)
        } catch (_: Exception) {
            launchCooldown = false
            Toast.makeText(this, str(R.string.can_t_open, app.label), Toast.LENGTH_SHORT).show()
            AppRepository.reload(this)
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
        updateScrubber()
        hideKeyboard()
    }

    /** The list and its frame go together, so a hidden list doesn't keep its share of the space. */
    private fun setAppListShown(shown: Boolean) {
        val v = if (shown) View.VISIBLE else View.GONE
        appList.visibility = v; appListFrame.visibility = v
    }

    /** A-Z strip: all apps only, when turned on in settings and the list is long enough to need it. */
    private fun updateScrubber() {
        val items = adapter.items
        val wanted = showingAllApps && Prefs.showAlphabetScrubber(this) && items.size > 20
        if (wanted) scrubber.setLabels(items.map { it.label })
        scrubber.visibility = if (wanted && scrubber.hasLetters) View.VISIBLE else View.GONE
    }

    private fun closeAllApps() {
        setAllAppsMode(false)
        if (searchInput.text.isNotEmpty()) searchInput.text.clear() else showResults(Results.IDLE)
        if (Prefs.autoKeyboard(this)) showKeyboard()
    }

    private fun setAllAppsMode(on: Boolean) {
        showingAllApps = on
        updateWallpaperDim()
        if (!on) scrubber.visibility = View.GONE
        updateCompact()
        applyNavButtons()
        applyAppListLayout()
        if (on) {
            results = Results.LIST
            setAppListShown(true)
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
        private val GREY get() = if (Theme.light) Color.parseColor("#6B6B6B") else Color.parseColor("#888888")
        private val FAINT get() = if (Theme.light) Color.parseColor("#707070") else Color.parseColor("#808080")
        private const val KEYBOARD_MIN_DOCKED_DP = 100f
        private const val MUSIC_PAUSE_GRACE_MS = 10 * 60_000L
        private const val FLOATING_KEYBOARD_DP = 345f
    }
}
