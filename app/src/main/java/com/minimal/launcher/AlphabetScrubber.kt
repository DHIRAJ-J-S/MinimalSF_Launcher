package com.minimal.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * A-Z strip on the right of all apps: touch or drag over a letter to jump to the first app starting
 * with it. Only letters that have apps are drawn, so every one of them goes somewhere.
 */
class AlphabetScrubber @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    /** Called with the adapter position of the first app under the touched letter. */
    var onJump: ((Int) -> Unit)? = null

    private var letters: List<Char> = emptyList()
    private var positions: List<Int> = emptyList()
    private var active = -1

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
    }
    private val idle = Color.parseColor("#FF808080")

    init { contentDescription = "jump to letter" }

    fun setTypeface(tf: Typeface) { paint.typeface = tf; invalidate() }

    /** [labels] in list order; a section starts wherever the first letter changes. Digits and symbols share "#". */
    fun setLabels(labels: List<String>) {
        val l = mutableListOf<Char>(); val p = mutableListOf<Int>()
        labels.forEachIndexed { i, label ->
            val c = label.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
            if (c !in l) { l += c; p += i }
        }
        letters = l; positions = p; active = -1
        invalidate()
    }

    val hasLetters get() = letters.size > 1

    override fun onDraw(canvas: Canvas) {
        if (letters.isEmpty()) return
        val step = rowHeight()
        val top = (height - step * letters.size) / 2f
        letters.forEachIndexed { i, c ->
            paint.color = if (i == active) Color.WHITE else idle
            paint.isFakeBoldText = i == active
            canvas.drawText(c.toString(), width / 2f, top + step * i + step / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        }
    }

    /** Letters are at most 22dp apart and squeeze together on short screens. */
    private fun rowHeight(): Float {
        val max = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, resources.displayMetrics)
        return minOf(max, height.toFloat() / letters.size.coerceAtLeast(1))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (letters.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val step = rowHeight()
                val top = (height - step * letters.size) / 2f
                val i = ((e.y - top) / step).toInt().coerceIn(0, letters.size - 1)
                if (i != active) {
                    active = i; invalidate()
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onJump?.invoke(positions[i])
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { active = -1; invalidate() }
        }
        return true
    }
}
