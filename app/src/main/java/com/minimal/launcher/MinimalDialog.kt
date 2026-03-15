package com.minimal.launcher

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

object MinimalDialog {

    private const val BORDER = "#FF444444"
    private const val BG = "#FF000000"
    private const val TEXT = "#FFCCCCCC"
    private const val TITLE = "#FFFFFFFF"
    private const val OPTION = "#FFDDDDDD"
    private const val PRESS = "#FF1A1A1A"
    private const val DIVIDER = "#FF222222"

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun confirm(ctx: Context, title: String? = null, message: String, positiveText: String,
                negativeText: String? = null, onPositive: () -> Unit, onNegative: (() -> Unit)? = null) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        val root = frame(ctx)
        if (title != null) { root.addView(title(ctx, title)); root.addView(divider(ctx)) }
        root.addView(TextView(ctx).apply {
            text = message; setTextColor(Color.parseColor(TEXT)); textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16)); lineHeight = dp(ctx, 20)
        })
        root.addView(divider(ctx))
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        if (negativeText != null) {
            row.addView(btn(ctx, negativeText, 1f) { dialog.dismiss(); onNegative?.invoke() })
            row.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 1), ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.parseColor(DIVIDER))
            })
        }
        row.addView(btn(ctx, positiveText, 1f) { dialog.dismiss(); onPositive() })
        root.addView(row)
        dialog.setContentView(root)
        dialog.window?.setLayout(dp(ctx, 300), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    fun options(ctx: Context, title: String? = null, items: Array<String>, onSelect: (Int) -> Unit) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        val root = frame(ctx)
        if (title != null) { root.addView(title(ctx, title)); root.addView(divider(ctx)) }
        items.forEachIndexed { i, label ->
            if (i > 0) root.addView(divider(ctx))
            root.addView(option(ctx, label) { dialog.dismiss(); onSelect(i) })
        }
        dialog.setContentView(root)
        dialog.window?.setLayout(dp(ctx, 280), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    fun singleChoice(ctx: Context, title: String, items: Array<String>, checkedIndex: Int, onSelect: (Int) -> Unit) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        val root = frame(ctx)
        root.addView(title(ctx, title)); root.addView(divider(ctx))
        items.forEachIndexed { i, label ->
            if (i > 0) root.addView(divider(ctx))
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12))
                isClickable = true; isFocusable = true; setBackgroundColor(Color.parseColor(BG))
                setOnClickListener { dialog.dismiss(); onSelect(i) }
                touchHL(this)
            }
            row.addView(TextView(ctx).apply {
                text = if (i == checkedIndex) "◉" else "○"
                setTextColor(if (i == checkedIndex) Color.WHITE else Color.parseColor("#FF555555"))
                textSize = 14f; setPadding(0, 0, dp(ctx, 14), 0)
            })
            row.addView(TextView(ctx).apply {
                text = label; setTextColor(Color.parseColor(OPTION)); textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
            })
            root.addView(row)
        }
        dialog.setContentView(root)
        dialog.window?.setLayout(dp(ctx, 260), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    /**
     * Step slider dialog for auto-launch delay.
     */
    fun stepSlider(ctx: Context, title: String, steps: LongArray, currentValue: Long, onSelect: (Long) -> Unit) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        val root = frame(ctx)
        root.addView(title(ctx, title)); root.addView(divider(ctx))

        val currentIdx = steps.indexOf(currentValue).coerceAtLeast(0)

        val valueText = TextView(ctx).apply {
            text = "${steps[currentIdx]}ms"
            setTextColor(Color.WHITE); textSize = 24f; typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 8))
        }
        root.addView(valueText)

        // Step labels row
        val labelsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(ctx, 20), dp(ctx, 0), dp(ctx, 20), dp(ctx, 4))
        }
        steps.forEach { v ->
            labelsRow.addView(TextView(ctx).apply {
                text = if (v == 404L) "404" else "${v}"
                setTextColor(Color.parseColor("#FF555555")); textSize = 8f
                typeface = android.graphics.Typeface.MONOSPACE; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        root.addView(labelsRow)

        val seekBar = SeekBar(ctx).apply {
            max = steps.size - 1; progress = currentIdx
            setPadding(dp(ctx, 24), dp(ctx, 8), dp(ctx, 24), dp(ctx, 16))
            progressDrawable?.setTint(Color.WHITE)
            thumb?.setTint(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = steps[progress]
                    valueText.text = "${v}ms"
                    if (v == 404L) valueText.setTextColor(Color.parseColor("#FF4444")) else valueText.setTextColor(Color.WHITE)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        root.addView(seekBar)
        root.addView(divider(ctx))

        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        btnRow.addView(btn(ctx, "cancel", 1f) { dialog.dismiss() })
        btnRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 1), ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor(DIVIDER))
        })
        btnRow.addView(btn(ctx, "set", 1f) { dialog.dismiss(); onSelect(steps[seekBar.progress]) })
        root.addView(btnRow)

        dialog.setContentView(root)
        dialog.window?.setLayout(dp(ctx, 300), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    // --- Scrollable app list dialog ---
    fun appList(ctx: Context, title: String, apps: List<AppInfo>, onTap: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        val root = frame(ctx)
        root.addView(title(ctx, "$title (${apps.size})"))
        root.addView(divider(ctx))

        val scroll = android.widget.ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(ctx, 350)
            )
        }
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        apps.forEachIndexed { i, app ->
            if (i > 0) list.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1))
                setBackgroundColor(Color.parseColor("#FF111111"))
            })
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 10))
                isClickable = true; isFocusable = true; isLongClickable = true
                setBackgroundColor(Color.parseColor(BG))
                setOnClickListener { dialog.dismiss(); onTap(app) }
                setOnLongClickListener { dialog.dismiss(); onLongPress(app); true }
                touchHL(this)
            }
            val icon = android.widget.ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 30))
                setImageDrawable(app.icon); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                val gs = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                colorFilter = android.graphics.ColorMatrixColorFilter(gs)
            }
            row.addView(icon)
            row.addView(TextView(ctx).apply {
                text = app.label; setTextColor(Color.parseColor(OPTION)); textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(dp(ctx, 14), 0, 0, 0)
            })
            list.addView(row)
        }
        scroll.addView(list)
        root.addView(scroll)

        root.addView(divider(ctx))
        root.addView(btn(ctx, "close", 0f) { dialog.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        dialog.setContentView(root)
        dialog.window?.setLayout(dp(ctx, 300), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    // --- Internals ---

    private fun frame(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(Color.parseColor(BG)); setStroke(dp(ctx, 1), Color.parseColor(BORDER))
        }
    }

    private fun title(ctx: Context, t: String) = TextView(ctx).apply {
        text = t; setTextColor(Color.parseColor(TITLE)); textSize = 14f
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 12))
    }

    private fun divider(ctx: Context) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1))
        setBackgroundColor(Color.parseColor(DIVIDER))
    }

    private fun option(ctx: Context, label: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; setTextColor(Color.parseColor(OPTION)); textSize = 13f
        typeface = android.graphics.Typeface.MONOSPACE
        setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14))
        isClickable = true; isFocusable = true; setBackgroundColor(Color.parseColor(BG))
        setOnClickListener { onClick() }
        touchHL(this)
    }

    private fun btn(ctx: Context, text: String, weight: Float, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text; setTextColor(Color.parseColor(OPTION)); textSize = 12f
        typeface = android.graphics.Typeface.MONOSPACE; gravity = Gravity.CENTER
        setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
        layoutParams = if (weight > 0f) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        isClickable = true; isFocusable = true; setBackgroundColor(Color.parseColor(BG))
        setOnClickListener { onClick() }
        touchHL(this)
    }

    private fun touchHL(v: View) {
        v.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.setBackgroundColor(Color.parseColor(PRESS))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.setBackgroundColor(Color.parseColor(BG))
            }
            false
        }
    }
}
