package com.minimal.launcher

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Every dialog in the app. All share one frame, font, divider and press-feedback style.
 */
object MinimalDialog {

    private val BORDER = Color.parseColor("#FF444444")
    private val BG = Color.parseColor("#FF000000")
    private val TEXT = Color.parseColor("#FFCCCCCC")
    private val TITLE = Color.WHITE
    private val OPTION = Color.parseColor("#FFDDDDDD")
    private val MUTED = Color.parseColor("#FF808080")
    private val PRESS = Color.parseColor("#33FFFFFF")
    private val DIVIDER = Color.parseColor("#FF222222")
    private val INPUT_BG = Color.parseColor("#FF0D0D0D")
    private val ACCENT = Color.parseColor("#FF4488FF")

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private fun tf(ctx: Context): Typeface = FontManager.getTypeface(ctx)

    /** Sets the dialog font at [sp] scaled by the font size setting, and remembers [sp] for [refont]. */
    private fun TextView.font(ctx: Context, sp: Float) {
        setTag(R.id.tag_dialog_sp, sp)
        textSize = sp * FontManager.sizeMultiplier(ctx)
        typeface = tf(ctx)
    }

    /** Re-applies the current font and size to an open dialog after the font settings change. */
    private fun refont(ctx: Context, v: View) {
        if (v is TextView) (v.getTag(R.id.tag_dialog_sp) as? Float)?.let { v.font(ctx, it) }
        if (v is ViewGroup) for (i in 0 until v.childCount) refont(ctx, v.getChildAt(i))
    }

    /** Scrolls [child] once it's taller than [maxFraction] of the screen, so long text can't push the buttons off. */
    private fun scrollable(ctx: Context, child: View, maxFraction: Float = 0.5f) = object : android.widget.ScrollView(ctx) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val max = (resources.displayMetrics.heightPixels * maxFraction).toInt()
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST))
        }
    }.apply {
        overScrollMode = View.OVER_SCROLL_NEVER
        isScrollbarFadingEnabled = false
        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        addView(child)
    }

    /** Message with up to three buttons, left to right: [negativeText], [neutralText], [positiveText]. */
    fun confirm(ctx: Context, title: String? = null, message: String, positiveText: String,
                negativeText: String? = null, onPositive: () -> Unit, onNegative: (() -> Unit)? = null,
                onDismiss: (() -> Unit)? = null, neutralText: String? = null, onNeutral: (() -> Unit)? = null) {
        val dialog = newDialog(ctx)
        // Fires for any close: button, back, or tapping outside
        if (onDismiss != null) dialog.setOnDismissListener { onDismiss() }
        val root = frame(ctx)
        if (title != null) { root.addView(title(ctx, title)); root.addView(divider(ctx)) }
        root.addView(scrollable(ctx, TextView(ctx).apply {
            text = message; setTextColor(TEXT); font(ctx, 13f)
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16)); setLineSpacing(dp(ctx, 4).toFloat(), 1f)
        }))
        root.addView(divider(ctx))
        val buttons = mutableListOf<Pair<String, () -> Unit>>()
        if (negativeText != null) buttons += negativeText to { dialog.dismiss(); onNegative?.invoke(); Unit }
        if (neutralText != null) buttons += neutralText to { dialog.dismiss(); onNeutral?.invoke(); Unit }
        buttons += positiveText to { dialog.dismiss(); onPositive() }
        root.addView(buttonRow(ctx, buttons))
        show(dialog, root, 300)
    }

    /**
     * Menu. [icons] (drawable ids, same length as [items]) are drawn in a fixed-width column so
     * labels line up; [trailing] shows a dim value on the right of a row (e.g. usage time).
     */
    fun options(ctx: Context, title: String? = null, items: Array<String>, icons: IntArray? = null,
                subtitle: String? = null, trailing: Array<String?>? = null,
                iconDrawables: List<android.graphics.drawable.Drawable?>? = null, onSelect: (Int) -> Unit) {
        val dialog = newDialog(ctx)
        val root = frame(ctx)
        if (title != null) { root.addView(title(ctx, title, subtitle)); root.addView(divider(ctx)) }
        root.addView(list(ctx, items.mapIndexed { i, label ->
            row(ctx, icons?.getOrNull(i), OPTION, label, OPTION, trailing?.getOrNull(i),
                iconDrawables?.getOrNull(i)) { dialog.dismiss(); onSelect(i) }
        }))
        show(dialog, root, 290)
    }

    /**
     * A row in [toggleMenu]. With [isOn] it's a toggle: tapping it calls [onClick] and redraws its
     * [on] / [off] in place, the dialog stays open. Without, it's a link (→) that closes the dialog first.
     */
    class MenuRow(val label: String, val icon: Int? = null, val drawable: android.graphics.drawable.Drawable? = null,
                  val isOn: (() -> Boolean)? = null, val onClick: () -> Unit)

    /** A menu of toggles and links, like a settings page in a box (settings > extended menu). */
    fun toggleMenu(ctx: Context, title: String, subtitle: String? = null, rows: List<MenuRow>) {
        val dialog = newDialog(ctx)
        val root = frame(ctx)
        root.addView(title(ctx, title, subtitle)); root.addView(divider(ctx))
        root.addView(list(ctx, rows.map { r ->
            val isOn = r.isOn
            lateinit var view: LinearLayout
            fun paint() {
                if (isOn == null) return
                val on = isOn()
                (view.getChildAt(view.childCount - 1) as TextView).apply {
                    text = if (on) "[on]" else "[off]"
                    setTextColor(if (on) Color.WHITE else MUTED)
                }
                view.contentDescription = "${r.label}, ${if (on) "on" else "off"}"
            }
            view = row(ctx, r.icon, OPTION, r.label, OPTION, if (isOn == null) "→" else "", r.drawable) {
                if (isOn == null) { dialog.dismiss(); r.onClick() } else { r.onClick(); paint() }
            }
            paint()
            view
        }))
        show(dialog, root, 300)
    }

    /** An on/off switch shown on the right of a dialog's title (e.g. "bold" in the font picker). */
    class TitleToggle(val label: String, val isOn: () -> Boolean, val onToggle: () -> Unit)

    /** A row of small buttons under a dialog's title for a related setting, e.g. font size in the font picker. */
    class Segmented(val label: String, val options: Array<String>, val selected: () -> Int, val onSelect: (Int) -> Unit)

    /** Options list that marks the current value - used for every multi-value setting. */
    fun singleChoice(ctx: Context, title: String, items: Array<String>, checkedIndex: Int,
                     toggle: TitleToggle? = null, segmented: Segmented? = null, onSelect: (Int) -> Unit) {
        val dialog = newDialog(ctx)
        val root = frame(ctx)
        root.addView(if (toggle == null) title(ctx, title) else titleWithToggle(ctx, title, toggle)); root.addView(divider(ctx))
        if (segmented != null) { root.addView(segmentedRow(ctx, segmented)); root.addView(divider(ctx)) }
        root.addView(list(ctx, items.mapIndexed { i, label ->
            val checked = i == checkedIndex
            row(ctx, if (checked) R.drawable.ic_radio_on else R.drawable.ic_radio_off,
                if (checked) Color.WHITE else MUTED, label, if (checked) Color.WHITE else OPTION) { dialog.dismiss(); onSelect(i) }
        }))
        show(dialog, root, 290)
    }

    /**
     * Rows separated by dividers; scrolls when there are more than fit on screen. A long list has to
     * look scrollable: it ends halfway through a row, fades out at the bottom and keeps a scroll bar
     * showing. Without these, people took the rows on screen for the whole list.
     */
    private fun list(ctx: Context, rows: List<View>): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        rows.forEachIndexed { i, r -> if (i > 0) col.addView(divider(ctx)); col.addView(r) }
        if (rows.size <= 7) return col
        val rowHeight = dp(ctx, 49)
        val maxHeight = (ctx.resources.displayMetrics.heightPixels * 0.55f).toInt()
        val wholeRows = ((maxHeight - rowHeight / 2) / rowHeight).coerceAtLeast(1)
        return android.widget.ScrollView(ctx).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                minOf(rows.size * rowHeight, wholeRows * rowHeight + rowHeight / 2))
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                verticalScrollbarThumbDrawable = GradientDrawable().apply { setColor(MUTED); setSize(dp(ctx, 3), dp(ctx, 24)) }
                verticalScrollbarTrackDrawable = GradientDrawable().apply { setColor(DIVIDER); setSize(dp(ctx, 3), dp(ctx, 24)) }
            }
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(ctx, 40))
            addView(col)
        }
    }

    private fun row(ctx: Context, icon: Int?, iconTint: Int, label: String, labelColor: Int,
                    trailing: String? = null, drawable: android.graphics.drawable.Drawable? = null,
                    onClick: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(ctx, 48)
        setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12))
        // An icon from another app (e.g. the MinimalSF Extended plugin) comes as a Drawable
        if (icon != null || drawable != null) addView(android.widget.ImageView(ctx).apply {
            if (drawable != null) setImageDrawable(drawable) else setImageResource(icon!!)
            imageTintList = ColorStateList.valueOf(iconTint)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)).apply { marginEnd = dp(ctx, 16) }
        })
        addView(TextView(ctx).apply {
            text = label; setTextColor(labelColor); font(ctx, 13f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (trailing != null) addView(TextView(ctx).apply {
            text = trailing; setTextColor(MUTED); font(ctx, 12f)
            setPadding(dp(ctx, 12), 0, 0, 0)
        })
        pressable(this, onClick)
    }

    /**
     * Step slider dialog for auto-launch delay.
     */
    fun stepSlider(ctx: Context, title: String, steps: LongArray, currentValue: Long, onSelect: (Long) -> Unit) {
        val dialog = newDialog(ctx)
        val root = frame(ctx)
        root.addView(title(ctx, title)); root.addView(divider(ctx))

        // Nearest step, so a stored value that isn't a step doesn't snap to 0
        val currentIdx = steps.indices.minByOrNull { kotlin.math.abs(steps[it] - currentValue) } ?: 0

        val valueText = TextView(ctx).apply {
            font(ctx, 24f); gravity = Gravity.CENTER
            setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 8))
        }
        fun showValue(v: Long) {
            valueText.text = "${v}ms"
            valueText.setTextColor(if (v == 404L) ACCENT else Color.WHITE)
        }
        showValue(steps[currentIdx])
        root.addView(valueText)

        val labelsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(ctx, 20), 0, dp(ctx, 20), dp(ctx, 4))
        }
        steps.forEach { v ->
            labelsRow.addView(TextView(ctx).apply {
                text = "$v"; setTextColor(MUTED); font(ctx, 9f)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        root.addView(labelsRow)

        val seekBar = SeekBar(ctx).apply {
            max = steps.size - 1; progress = currentIdx
            setPadding(dp(ctx, 24), dp(ctx, 8), dp(ctx, 24), dp(ctx, 16))
            progressTintList = ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = ColorStateList.valueOf(MUTED)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) = showValue(steps[progress])
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(seekBar)
        root.addView(divider(ctx))
        root.addView(buttonRow(ctx, listOf(
            "cancel" to { dialog.dismiss() },
            "set" to { dialog.dismiss(); onSelect(steps[seekBar.progress]) }
        )))
        show(dialog, root, 300)
    }

    // --- Scrollable app list dialog (recycled rows, same look as the home list) ---
    fun appList(ctx: Context, title: String, apps: List<AppInfo>, onTap: (AppInfo) -> Unit) {
        val dialog = newDialog(ctx)
        val root = frame(ctx)
        root.addView(title(ctx, "$title (${apps.size})"))
        root.addView(divider(ctx))

        val adapter = AppAdapter(onClick = { app, _ -> dialog.dismiss(); onTap(app) })
        adapter.setTypeface(tf(ctx), 0.93f * FontManager.sizeMultiplier(ctx))
        val list = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            this.adapter = adapter
            itemAnimator = null
            setHasFixedSize(true)
            setPadding(dp(ctx, 16), dp(ctx, 4), dp(ctx, 16), dp(ctx, 4))
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                (ctx.resources.displayMetrics.heightPixels * 0.55f).toInt().coerceAtMost(dp(ctx, 420)))
        }
        adapter.update(apps, "")
        // Long app lists get a filter box: type part of a name
        if (apps.size > 10) {
            val search = EditText(ctx).apply {
                hint = "search"; setHintTextColor(MUTED)
                setTextColor(Color.WHITE); font(ctx, 13f)
                background = GradientDrawable().apply { setColor(INPUT_BG); setStroke(dp(ctx, 1), DIVIDER) }
                setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 4))
                }
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        val q = s?.toString()?.trim().orEmpty()
                        adapter.update(if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }, "")
                        list.scrollToPosition(0)
                    }
                })
            }
            root.addView(search)
            // Opening a picker shouldn't throw up the keyboard; it comes up when the box is tapped
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        root.addView(list)

        root.addView(divider(ctx))
        root.addView(buttonRow(ctx, listOf("close" to { dialog.dismiss() })))
        show(dialog, root, 300)
    }

    /**
     * Text input dialog.
     */
    fun textInput(ctx: Context, title: String, hint: String, prefill: String = "", onSubmit: (String) -> Unit) {
        val dialog = newDialog(ctx)
        val root = frame(ctx)
        root.addView(title(ctx, title)); root.addView(divider(ctx))

        val input = EditText(ctx).apply {
            setHint(hint); setHintTextColor(MUTED)
            setText(prefill); setSelection(prefill.length)
            setTextColor(Color.WHITE); font(ctx, 14f)
            background = GradientDrawable().apply { setColor(INPUT_BG); setStroke(dp(ctx, 1), DIVIDER) }
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            // Single-line input (Enter means done), but long text wraps instead of scrolling sideways.
            // Turning single line off would add the multi-line flag and make Enter insert a new line.
            isSingleLine = true; setHorizontallyScrolling(false); maxLines = 6
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
            }
        }
        val submit = { dialog.dismiss(); onSubmit(input.text.toString().trim()) }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { submit(); true } else false }
        root.addView(input)
        root.addView(divider(ctx))
        root.addView(buttonRow(ctx, listOf("cancel" to { dialog.dismiss() }, "save" to submit)))

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        show(dialog, root, 300)
        input.requestFocus()
    }

    // --- Internals ---

    private fun newDialog(ctx: Context) = Dialog(ctx).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        // Close with the activity: home destroys settings (the launcher clears its task), and a dialog
        // still showing then leaks its window.
        val owner = ctx as? androidx.lifecycle.LifecycleOwner ?: return@apply
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY && isShowing) dismiss()
        }
        owner.lifecycle.addObserver(observer)
        window?.decorView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { owner.lifecycle.removeObserver(observer) }
        })
    }

    private fun show(dialog: Dialog, root: View, widthDp: Int) {
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val maxW = (context.resources.displayMetrics.widthPixels * 0.9f).toInt()
            setLayout(dp(context, widthDp).coerceAtMost(maxW), ViewGroup.LayoutParams.WRAP_CONTENT)
            setWindowAnimations(android.R.style.Animation_Dialog)
            setDimAmount(0.75f)
        }
        dialog.show()
    }

    private fun frame(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { setColor(BG); setStroke(dp(ctx, 1), BORDER) }
        // Keeps ripples inside the 1dp border
        setPadding(dp(ctx, 1), dp(ctx, 1), dp(ctx, 1), dp(ctx, 1))
    }

    private fun title(ctx: Context, t: String, subtitle: String? = null): View {
        val titleView = TextView(ctx).apply {
            text = t; setTextColor(TITLE); font(ctx, 14f)
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, if (subtitle == null) 12 else 2))
        }
        if (subtitle == null) return titleView
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            addView(TextView(ctx).apply {
                text = subtitle; setTextColor(MUTED); font(ctx, 11f)
                setPadding(dp(ctx, 20), 0, dp(ctx, 20), dp(ctx, 12))
            })
        }
    }

    private fun titleWithToggle(ctx: Context, t: String, toggle: TitleToggle) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(title(ctx, t).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        val chip = TextView(ctx).apply {
            text = toggle.label; font(ctx, 12f); gravity = Gravity.CENTER
            setPadding(dp(ctx, 12), dp(ctx, 5), dp(ctx, 12), dp(ctx, 5))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = dp(ctx, 16) }
            isClickable = true; isFocusable = true
        }
        // Label is always bold (it previews what it does); on = white with a white outline, off = dim
        fun paint() {
            val on = toggle.isOn()
            chip.typeface = Typeface.create(tf(ctx), Typeface.BOLD)
            chip.setTextColor(if (on) Color.WHITE else MUTED)
            chip.background = RippleDrawable(ColorStateList.valueOf(PRESS),
                GradientDrawable().apply { setColor(BG); setStroke(dp(ctx, 1), if (on) Color.WHITE else DIVIDER) }, null)
            chip.contentDescription = "${toggle.label} ${if (on) "on" else "off"}"
        }
        paint()
        chip.setOnClickListener {
            toggle.onToggle()
            // Re-font the whole dialog so its own text reflects the change immediately (bold off too)
            refont(ctx, chip.rootView)
            paint()
        }
        addView(chip)
    }

    /** Chips in the same style as the title toggle: the chosen one white with a white outline. */
    private fun segmentedRow(ctx: Context, seg: Segmented) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 12), dp(ctx, 12))
        addView(TextView(ctx).apply {
            text = seg.label; setTextColor(MUTED); font(ctx, 12f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val chips = seg.options.map { option ->
            TextView(ctx).apply {
                text = option; font(ctx, 12f); gravity = Gravity.CENTER
                setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginEnd = dp(ctx, 8) }
                isClickable = true; isFocusable = true
            }
        }
        fun paint() {
            val selected = seg.selected()
            chips.forEachIndexed { i, chip ->
                val on = i == selected
                chip.typeface = tf(ctx)
                chip.setTextColor(if (on) Color.WHITE else MUTED)
                chip.background = RippleDrawable(ColorStateList.valueOf(PRESS),
                    GradientDrawable().apply { setColor(BG); setStroke(dp(ctx, 1), if (on) Color.WHITE else DIVIDER) }, null)
                chip.contentDescription = "${seg.label} ${seg.options[i]}${if (on) ", selected" else ""}"
            }
        }
        chips.forEachIndexed { i, chip ->
            chip.setOnClickListener { seg.onSelect(i); refont(ctx, chip.rootView); paint() }
            addView(chip)
        }
        paint()
    }

    /**
     * List or grid, with a columns slider that appears once grid is picked. Picking list closes the
     * dialog; with grid it stays open so the size can be set. Every change is passed on straight away.
     */
    /** List, names-only list or grid; picking grid reveals a columns slider. */
    fun gridPicker(ctx: Context, title: String, isGrid: Boolean, icons: Boolean, columns: Int, min: Int, max: Int,
                   onChange: (grid: Boolean, icons: Boolean, columns: Int) -> Unit) {
        val dialog = newDialog(ctx)
        val root = frame(ctx)
        root.addView(title(ctx, title)); root.addView(divider(ctx))
        var grid = isGrid
        var withIcons = icons || isGrid
        var cols = columns.coerceIn(min, max)

        val sizeLabel = TextView(ctx).apply {
            setTextColor(MUTED); font(ctx, 12f)
            setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 4))
        }
        fun showSize() { sizeLabel.text = "columns · $cols" }
        val sizeSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(sizeLabel)
            addView(SeekBar(ctx).apply {
                this.max = max - min; progress = cols - min
                setPadding(dp(ctx, 24), dp(ctx, 8), dp(ctx, 24), dp(ctx, 16))
                progressTintList = ColorStateList.valueOf(Color.WHITE)
                progressBackgroundTintList = ColorStateList.valueOf(MUTED)
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                contentDescription = "columns"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, value: Int, fromUser: Boolean) {
                        cols = value + min; showSize(); onChange(true, true, cols)
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
        }

        lateinit var listRow: LinearLayout
        lateinit var namesRow: LinearLayout
        lateinit var gridRow: LinearLayout
        fun paint() {
            for ((r, on) in listOf(listRow to (!grid && withIcons), namesRow to (!grid && !withIcons), gridRow to grid)) {
                (r.getChildAt(0) as android.widget.ImageView).apply {
                    setImageResource(if (on) R.drawable.ic_radio_on else R.drawable.ic_radio_off)
                    imageTintList = ColorStateList.valueOf(if (on) Color.WHITE else MUTED)
                }
                (r.getChildAt(1) as TextView).setTextColor(if (on) Color.WHITE else OPTION)
            }
            sizeSection.visibility = if (grid) View.VISIBLE else View.GONE
            showSize()
        }
        listRow = row(ctx, R.drawable.ic_radio_off, MUTED, "list", OPTION) {
            grid = false; withIcons = true; onChange(false, true, cols); dialog.dismiss()
        }
        namesRow = row(ctx, R.drawable.ic_radio_off, MUTED, "list - without icons", OPTION) {
            grid = false; withIcons = false; onChange(false, false, cols); dialog.dismiss()
        }
        gridRow = row(ctx, R.drawable.ic_radio_off, MUTED, "grid", OPTION) {
            grid = true; withIcons = true; onChange(true, true, cols); paint()
        }
        root.addView(list(ctx, listOf(listRow, namesRow, gridRow)))
        root.addView(sizeSection)
        root.addView(divider(ctx))
        root.addView(buttonRow(ctx, listOf("done" to { dialog.dismiss() })))
        paint()
        show(dialog, root, 290)
    }

    private fun divider(ctx: Context) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1))
        setBackgroundColor(DIVIDER)
    }

    private fun buttonRow(ctx: Context, buttons: List<Pair<String, () -> Unit>>) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        buttons.forEachIndexed { i, (label, action) ->
            if (i > 0) addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 1), ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(DIVIDER)
            })
            // Three buttons share the width, so they get less side padding to keep labels on one line
            val side = dp(ctx, if (buttons.size > 2) 6 else 16)
            // A label ending in ↗ opens a website: the arrow is drawn as an icon, since fonts draw it tiny
            val external = label.endsWith("↗")
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                setPadding(side, dp(ctx, 14), side, dp(ctx, 14))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(ctx).apply {
                    text = label.removeSuffix("↗").trimEnd(); setTextColor(OPTION); font(ctx, 12f); gravity = Gravity.CENTER
                })
                if (external) addView(android.widget.ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_external)
                    imageTintList = ColorStateList.valueOf(OPTION)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    layoutParams = LinearLayout.LayoutParams(dp(ctx, 14), dp(ctx, 14)).apply { marginStart = dp(ctx, 6) }
                })
                if (external) contentDescription = label.removeSuffix("↗").trimEnd() + ", opens a website"
                pressable(this, action)
            })
        }
    }

    private fun pressable(v: View, onClick: () -> Unit) {
        v.isClickable = true; v.isFocusable = true
        v.background = RippleDrawable(ColorStateList.valueOf(PRESS), null, ColorDrawable(Color.WHITE))
        v.setOnClickListener { onClick() }
    }
}
