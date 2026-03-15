package com.minimal.launcher

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Custom dialog builder that matches the launcher's monochrome thin-line aesthetic.
 * All dialogs use black background, white thin border, monospace font.
 */
object MinimalDialog {

    private const val BORDER_COLOR = "#FF333333"
    private const val BG_COLOR = "#FF000000"
    private const val TEXT_COLOR = "#FF999999"
    private const val TITLE_COLOR = "#FFFFFFFF"
    private const val OPTION_COLOR = "#FFAAAAAA"
    private const val OPTION_PRESS_COLOR = "#FF222222"
    private const val DIVIDER_COLOR = "#FF1A1A1A"

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    /**
     * Confirmation dialog with title, message, and up to 2 buttons.
     */
    fun confirm(
        ctx: Context,
        title: String? = null,
        message: String,
        positiveText: String,
        negativeText: String? = null,
        onPositive: () -> Unit,
        onNegative: (() -> Unit)? = null
    ) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val root = buildFrame(ctx)

        // Title
        if (title != null) {
            root.addView(buildTitle(ctx, title))
            root.addView(buildDivider(ctx))
        }

        // Message
        val msg = TextView(ctx).apply {
            text = message
            setTextColor(Color.parseColor(TEXT_COLOR))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16))
            lineHeight = dp(ctx, 20)
        }
        root.addView(msg)

        // Buttons
        root.addView(buildDivider(ctx))
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        if (negativeText != null) {
            btnRow.addView(buildButton(ctx, negativeText, 1f) {
                dialog.dismiss()
                onNegative?.invoke()
            })
            // Vertical divider
            btnRow.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 1), ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(Color.parseColor(DIVIDER_COLOR))
            })
        }

        btnRow.addView(buildButton(ctx, positiveText, 1f) {
            dialog.dismiss()
            onPositive()
        })

        root.addView(btnRow)
        dialog.setContentView(root)
        dialog.window?.setLayout(dp(ctx, 300), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    /**
     * Options list dialog (replaces AlertDialog.setItems).
     */
    fun options(
        ctx: Context,
        title: String? = null,
        items: Array<String>,
        onSelect: (Int) -> Unit
    ) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val root = buildFrame(ctx)

        if (title != null) {
            root.addView(buildTitle(ctx, title))
            root.addView(buildDivider(ctx))
        }

        items.forEachIndexed { index, label ->
            if (index > 0) root.addView(buildDivider(ctx))

            val item = TextView(ctx).apply {
                text = label
                setTextColor(Color.parseColor(OPTION_COLOR))
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    dialog.dismiss()
                    onSelect(index)
                }
                setBackgroundColor(Color.parseColor(BG_COLOR))
                setOnTouchListener { v, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> v.setBackgroundColor(Color.parseColor(OPTION_PRESS_COLOR))
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                            v.setBackgroundColor(Color.parseColor(BG_COLOR))
                    }
                    false
                }
            }
            root.addView(item)
        }

        dialog.setContentView(root)
        dialog.window?.setLayout(dp(ctx, 280), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    /**
     * Single choice dialog (replaces AlertDialog.setSingleChoiceItems).
     */
    fun singleChoice(
        ctx: Context,
        title: String,
        items: Array<String>,
        checkedIndex: Int,
        onSelect: (Int) -> Unit
    ) {
        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)

        val root = buildFrame(ctx)
        root.addView(buildTitle(ctx, title))
        root.addView(buildDivider(ctx))

        items.forEachIndexed { index, label ->
            if (index > 0) root.addView(buildDivider(ctx))

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12))
                isClickable = true
                isFocusable = true
                setBackgroundColor(Color.parseColor(BG_COLOR))
                setOnClickListener {
                    dialog.dismiss()
                    onSelect(index)
                }
                setOnTouchListener { v, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> v.setBackgroundColor(Color.parseColor(OPTION_PRESS_COLOR))
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                            v.setBackgroundColor(Color.parseColor(BG_COLOR))
                    }
                    false
                }
            }

            val indicator = TextView(ctx).apply {
                text = if (index == checkedIndex) "◉" else "○"
                setTextColor(if (index == checkedIndex) Color.WHITE else Color.parseColor("#FF444444"))
                textSize = 14f
                setPadding(0, 0, dp(ctx, 14), 0)
            }
            row.addView(indicator)

            val labelView = TextView(ctx).apply {
                text = label
                setTextColor(Color.parseColor(OPTION_COLOR))
                textSize = 13f
                typeface = android.graphics.Typeface.MONOSPACE
            }
            row.addView(labelView)

            root.addView(row)
        }

        dialog.setContentView(root)
        dialog.window?.setLayout(dp(ctx, 260), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.show()
    }

    // --- Internal builders ---

    private fun buildFrame(ctx: Context): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(BG_COLOR))

            // Create border effect with a wrapper
            val border = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor(BG_COLOR))
                setStroke(dp(ctx, 1), Color.parseColor(BORDER_COLOR))
            }
            background = border
        }
    }

    private fun buildTitle(ctx: Context, title: String): TextView {
        return TextView(ctx).apply {
            text = title
            setTextColor(Color.parseColor(TITLE_COLOR))
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 12))
        }
    }

    private fun buildDivider(ctx: Context): View {
        return View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1)
            )
            setBackgroundColor(Color.parseColor(DIVIDER_COLOR))
        }
    }

    private fun buildButton(ctx: Context, text: String, weight: Float, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor(OPTION_COLOR))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.parseColor(BG_COLOR))
            setOnClickListener { onClick() }
            setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> v.setBackgroundColor(Color.parseColor(OPTION_PRESS_COLOR))
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                        v.setBackgroundColor(Color.parseColor(BG_COLOR))
                }
                false
            }
        }
    }
}
