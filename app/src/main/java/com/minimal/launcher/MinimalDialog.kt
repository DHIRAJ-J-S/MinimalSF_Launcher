package com.minimal.launcher

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
    private val MUTED = Color.parseColor("#FF555555")
    private val PRESS = Color.parseColor("#33FFFFFF")
    private val DIVIDER = Color.parseColor("#FF222222")
    private val INPUT_BG = Color.parseColor("#FF0D0D0D")
    private val ACCENT = Color.parseColor("#FFFF4444")

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    private fun tf(ctx: Context): Typeface = FontManager.getTypeface(ctx)

    fun confirm(ctx: Context, title: String? = null, message: String, positiveText: String,
                negativeText: String? = null, onPositive: () -> Unit, onNegative: (() -> Unit)? = null,
                onDismiss: (() -> Unit)? = null) {
        val dialog = newDialog(ctx)
        // Fires for any close: button, back, or tapping outside
        if (onDismiss != null) dialog.setOnDismissListener { onDismiss() }
        val root = frame(ctx)
        if (title != null) { root.addView(title(ctx, title)); root.addView(divider(ctx)) }
        root.addView(TextView(ctx).apply {
            text = message; setTextColor(TEXT); textSize = 13f; typeface = tf(ctx)
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16)); setLineSpacing(dp(ctx, 4).toFloat(), 1f)
        })
        root.addView(divider(ctx))
        val buttons = mutableListOf<Pair<String, () -> Unit>>()
        if (negativeText != null) buttons += negativeText to { dialog.dismiss(); onNegative?.invoke(); Unit }
        buttons += positiveText to { dialog.dismiss(); onPositive() }
        root.addView(buttonRow(ctx, buttons))
        show(dialog, root, 300)
    }

    /**
     * Menu. [icons] (drawable ids, same length as [items]) are drawn in a fixed-width column so
     * labels line up; [trailing] shows a dim value on the right of a row (e.g. usage time).
     */
    fun options(ctx: Context, title: String? = null, items: Array<String>, icons: IntArray? = null,
                subtitle: String? = null, trailing: Array<String?>? = null, onSelect: (Int) -> Unit) {
        val dialog = newDialog(ctx)
        val root = frame(ctx)
        if (title != null) { root.addView(title(ctx, title, subtitle)); root.addView(divider(ctx)) }
        root.addView(list(ctx, items.mapIndexed { i, label ->
            row(ctx, icons?.getOrNull(i), OPTION, label, OPTION, trailing?.getOrNull(i)) { dialog.dismiss(); onSelect(i) }
        }))
        show(dialog, root, 290)
    }

    /** An on/off switch shown on the right of a dialog's title (e.g. "bold" in the font picker). */
    class TitleToggle(val label: String, val isOn: () -> Boolean, val onToggle: () -> Unit)

    /** Options list that marks the current value — used for every multi-value setting. */
    fun singleChoice(ctx: Context, title: String, items: Array<String>, checkedIndex: Int,
                     toggle: TitleToggle? = null, onSelect: (Int) -> Unit) {
        val dialog = newDialog(ctx)
        val root = frame(ctx)
        root.addView(if (toggle == null) title(ctx, title) else titleWithToggle(ctx, title, toggle)); root.addView(divider(ctx))
        root.addView(list(ctx, items.mapIndexed { i, label ->
            val checked = i == checkedIndex
            row(ctx, if (checked) R.drawable.ic_radio_on else R.drawable.ic_radio_off,
                if (checked) Color.WHITE else MUTED, label, if (checked) Color.WHITE else OPTION) { dialog.dismiss(); onSelect(i) }
        }))
        show(dialog, root, 290)
    }

    /** Rows separated by dividers; scrolls when there are more than fit on screen. */
    private fun list(ctx: Context, rows: List<View>): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        rows.forEachIndexed { i, r -> if (i > 0) col.addView(divider(ctx)); col.addView(r) }
        if (rows.size <= 7) return col
        return android.widget.ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                minOf(rows.size * dp(ctx, 49), (ctx.resources.displayMetrics.heightPixels * 0.55f).toInt()))
            addView(col)
        }
    }

    private fun row(ctx: Context, icon: Int?, iconTint: Int, label: String, labelColor: Int,
                    trailing: String? = null, onClick: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(ctx, 48)
        setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12))
        if (icon != null) addView(android.widget.ImageView(ctx).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(iconTint)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18)).apply { marginEnd = dp(ctx, 16) }
        })
        addView(TextView(ctx).apply {
            text = label; setTextColor(labelColor); textSize = 13f; typeface = tf(ctx)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (trailing != null) addView(TextView(ctx).apply {
            text = trailing; setTextColor(MUTED); textSize = 12f; typeface = tf(ctx)
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
            textSize = 24f; typeface = tf(ctx); gravity = Gravity.CENTER
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
                text = "$v"; setTextColor(MUTED); textSize = 9f
                typeface = tf(ctx); gravity = Gravity.CENTER
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
        adapter.setTypeface(tf(ctx), 0.93f)
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
            setTextColor(Color.WHITE); textSize = 14f; typeface = tf(ctx)
            background = GradientDrawable().apply { setColor(INPUT_BG); setStroke(dp(ctx, 1), DIVIDER) }
            setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
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
            text = t; setTextColor(TITLE); textSize = 14f; typeface = tf(ctx)
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, if (subtitle == null) 12 else 2))
        }
        if (subtitle == null) return titleView
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            addView(TextView(ctx).apply {
                text = subtitle; setTextColor(MUTED); textSize = 11f; typeface = tf(ctx)
                setPadding(dp(ctx, 20), 0, dp(ctx, 20), dp(ctx, 12))
            })
        }
    }

    private fun titleWithToggle(ctx: Context, t: String, toggle: TitleToggle) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(title(ctx, t).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        val chip = TextView(ctx).apply {
            text = toggle.label; textSize = 12f; gravity = Gravity.CENTER
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
            // Re-font the whole dialog so its own text reflects the change immediately
            FontManager.applyTo(chip.rootView, tf(ctx), 1f)
            paint()
        }
        addView(chip)
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
            addView(TextView(ctx).apply {
                text = label; setTextColor(OPTION); textSize = 12f; typeface = tf(ctx); gravity = Gravity.CENTER
                setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
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
