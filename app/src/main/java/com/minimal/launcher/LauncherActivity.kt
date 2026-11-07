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
        super.onCreate(savedInstanceState)
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
        musicBar.setOnTouchListener { v, event ->
            // The detector eats every touch, so the ripple is driven by hand
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.background?.setHotspot(event.x, event.y); v.isPressed = true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.isPressed = false
                MotionEvent.ACTION_MOVE -> if (abs(event.x - (v.getTag(R.id.tag_touch_x) as? Float ?: event.x)) > dp(16f)) v.isPressed = false
            }
            if (event.actionMasked == MotionEvent.ACTION_DOWN) v.setTag(R.id.tag_touch_x, event.x)
            musicGesture.onTouchEvent(event); true
        }
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
        AppRepository.removeListener(appsListener)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun onAppsChanged() {
        // Refresh whatever is on screen. Only auto-launch if the user is actively on the home screen
        // (e.g. typed before the first load finished), not because a package changed in the background.
        if (showingAllApps) { adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this)); updateScrubber() }
        else filterApps(searchInput.text.toString(), allowAutoLaunch = hasWindowFocus())

        if (Prefs.isFirstLaunch(this)) { Prefs.setFirstLaunchDone(this); showFirstLaunchSetup() }
    }

    // --- Insets: pad for bars + keyboard, animated in sync with the keyboard ---

    private fun setupInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
            }.joinToString("\n\n")
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
            } ?: Toast.makeText(this, "app not found - check gesture settings", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    override fun lockScreen() {
        if (!LockAccessibilityService.isSupported) {
            MinimalDialog.confirm(this, title = "screen lock unavailable",
                message = "locking the screen needs android 9 or newer.\n\nyou can set ${lastGesture.title} to do something else in settings → gestures.",
                positiveText = "ok", onPositive = {})
            return
        }
        if (LockAccessibilityService.instance?.lock() == true) return
        MinimalDialog.confirm(this,
            title = "enable screen lock",
            message = "to lock the screen with ${lastGesture.title}, MinimalSF needs accessibility permission.\n\nonly the lock action is used - no data is read or collected.",
            positiveText = "open settings",
            negativeText = "cancel",
            onPositive = { SpecialAccess.open(this, Settings.ACTION_ACCESSIBILITY_SETTINGS) }
        )
    }

    // --- Settings-driven UI ---

    private fun applyUi() {
        uiDirty = false
        applySearchPosition()
        applyHomeVisibility()
        applyFont()
        updateMusicBarVisibility()
        applyAppListLayout()
        updateHomeTip()
        clockFormat = null
        updateClock()
        if (showingAllApps) { adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this)); updateScrubber() }
        // The floating keyboard side may have changed
        ViewCompat.requestApplyInsets(rootLayout)
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
        if (uiDirty) applyUi()

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
     * One welcome prompt instead of three in a row. Setting it as home is the only step that matters on
     * day one; the now playing bar and the rest are mentioned and live in settings.
     */
    private fun showFirstLaunchSetup() {
        val isDefault = isDefaultLauncher()
        MinimalDialog.confirm(this, title = "welcome to MinimalSF",
            message = "type to launch any app - one match opens on its own.\n\n" +
                "fonts, gestures, the now playing bar and more are in settings." +
                (if (isDefault) "" else "\n\nset MinimalSF as your home screen so it opens when you press home."),
            positiveText = if (isDefault) "go to settings" else "set as home",
            negativeText = "later",
            onPositive = {
                if (isDefault) startActivity(Intent(this, SettingsActivity::class.java))
                else try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {}
            },
            onNegative = {}
        )
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
            val what = if (method == "expandSettingsPanel") "quick settings" else "notifications"
            Toast.makeText(this, "can't open $what on this phone - try another gesture action", Toast.LENGTH_SHORT).show()
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
        musicBar.visibility = if (Prefs.showMusic(this) && music) View.VISIBLE else View.GONE
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
        val isHidden = app.key in Prefs.getHiddenApps(this)
        val currentKeyword = Prefs.getKeywords(this)[app.key] ?: ""

        // The app's own shortcuts first ("new chat", "navigate home"…), like other launchers show them
        val shortcuts = AppShortcuts.forPackage(this, app.packageName, app.user).take(4)
        val menu = listOfNotNull(
            Triple("app info", R.drawable.ic_info) { keepAllAppsOpen = showingAllApps; try { getSystemService(LauncherApps::class.java)?.startAppDetailsActivity(app.component, app.user, null, null) } catch (_: Exception) {}; Unit },
            if (!app.canUninstall) null else Triple("uninstall", R.drawable.ic_uninstall) { keepAllAppsOpen = showingAllApps; try { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")).putExtra(Intent.EXTRA_USER, app.user)) } catch (_: Exception) {}; Unit },
            Triple("set keyword", R.drawable.ic_keyword) { promptKeyword(app, currentKeyword) },
            Triple(if (isHidden) "show in search" else "hide from search", if (isHidden) R.drawable.ic_eye else R.drawable.ic_eye_off) {
                Prefs.setAppHidden(this, app.key, !isHidden)
                if (showingAllApps) { adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this)); updateScrubber() }
                else filterApps(searchInput.text.toString(), allowAutoLaunch = false)
            }
        )
        val items = shortcuts.map { AppShortcuts.label(it) } + menu.map { it.first }
        val icons = IntArray(shortcuts.size) { R.drawable.ic_shortcut } + menu.map { it.second }.toIntArray()
        val trailing = arrayOfNulls<String>(shortcuts.size) +
            menu.map { if (it.first == "set keyword") currentKeyword.ifEmpty { null } else null }.toTypedArray()
        // Usage stats only cover this profile
        val usage = if (screenTimeActive() && !app.otherProfile) ScreenTime.today[app.packageName]?.let { "${ScreenTime.format(it)} today" } else null
        val version = try { packageManager.getPackageInfo(app.packageName, 0).versionName } catch (_: Exception) { null }
        val details = listOfNotNull(app.packageName, version?.let { "version $it" }, usage).joinToString("\n")

        MinimalDialog.options(this, title = app.label, items = items.toTypedArray(), icons = icons,
            subtitle = details, trailing = trailing) { picked ->
            if (picked < shortcuts.size) {
                val s = shortcuts[picked]
                if (!AppShortcuts.start(this, s.`package`, s.id, user = app.user)) Toast.makeText(this, "shortcut unavailable", Toast.LENGTH_SHORT).show()
                return@options
            }
            menu[picked - shortcuts.size].third()
        }
    }

    private fun promptKeyword(app: AppInfo, current: String) {
        MinimalDialog.textInput(this,
            title = "keyword for ${app.label}",
            hint = "e.g. wa for whatsapp (empty = remove)",
            prefill = current,
            onSubmit = { keyword ->
                val owner = if (keyword.isEmpty()) null else Prefs.keywordOwner(this, keyword, app.key)
                if (owner != null) {
                    MinimalDialog.confirm(this, title = "keyword in use",
                        message = "\"${keyword.lowercase()}\" is already used by ${AppRepository.labelFor(this, owner)}.\n\nmove it to ${app.label}?",
                        positiveText = "move", negativeText = "cancel",
                        onPositive = {
                            Prefs.setKeyword(this, owner, "")
                            Prefs.setKeyword(this, app.key, keyword)
                        })
                } else {
                    Prefs.setKeyword(this, app.key, keyword)
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
        val show = Prefs.showScreenTime(this)
        screenTimeText.visibility = if (show) View.VISIBLE else View.GONE
        updatePanes()
        if (!show) return
        if (!ScreenTime.hasAccess(this)) { screenTimeText.text = "tap to allow screen time"; return }
        if (ScreenTime.today.isEmpty()) screenTimeText.text = "screen time …"
        ScreenTime.refresh(this) {
            if (!isDestroyed) screenTimeText.text = "screen time ${ScreenTime.format(ScreenTime.totalToday)}"
        }
    }

    private fun onScreenTimeClick() {
        if (!ScreenTime.hasAccess(this)) { ScreenTime.openAccessSettings(this); return }
        val byApp = AppRepository.mainApps().associateBy { it.packageName }
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
        apply(allAppsBtn, if (showingAllApps) "close" else "all apps", if (showingAllApps) R.drawable.ic_close else R.drawable.ic_grid)
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
        clockText.text = fmt.format(now)
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

    private fun searchApps(q: String, hidden: Set<String>, keywords: Map<String, String>): List<AppInfo> {
        val fromStart = Prefs.searchFromStart(this)
        return AppRepository.apps.mapNotNull { app ->
            if (app.key in hidden) null
            else rank(app, q, keywords[app.key], fromStart)?.let { app to it }
        }.sortedBy { it.second }.map { it.first }
    }

    /** The icon of a visible result row, for the launch animation. */
    private fun rowIcon(position: Int) = (appList.findViewHolderForAdapterPosition(position) as? AppAdapter.VH)?.icon

    private fun filterApps(raw: String, allowAutoLaunch: Boolean = true) {
        cancelAutoLaunch()
        val q = raw.trim().lowercase()
        updateCompact()
        clearBtn.visibility = if (raw.isNotEmpty()) View.VISIBLE else View.GONE

        if (q.isNotEmpty() && showingAllApps) setAllAppsMode(false)
        if (q.isEmpty()) {
            bestMatch = null
            if (showingAllApps) { adapter.update(AppRepository.apps, "", Prefs.getHiddenApps(this)); updateScrubber() }
            else showResults(Results.IDLE)
            return
        }

        val apps = AppRepository.apps
        val hidden = Prefs.getHiddenApps(this)
        val keywords = Prefs.getKeywords(this)

        // Exact keyword wins outright
        val keywordMatch = apps.firstOrNull { it.key !in hidden && keywords[it.key] == q }
        if (keywordMatch != null) { showAutoLaunch(keywordMatch, allowAutoLaunch); return }

        val matches = searchApps(q, hidden, keywords)

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
            Toast.makeText(this, "can't open ${app.label}", Toast.LENGTH_SHORT).show()
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
        private val GREY = Color.parseColor("#888888")
        private val FAINT = Color.parseColor("#808080")
        private const val KEYBOARD_MIN_DOCKED_DP = 100f
        private const val FLOATING_KEYBOARD_DP = 345f
    }
}
