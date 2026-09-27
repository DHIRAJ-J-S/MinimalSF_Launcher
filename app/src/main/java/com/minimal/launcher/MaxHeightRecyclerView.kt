package com.minimal.launcher

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView ignores android:maxHeight, so the todo list used to grow without limit
 * and push the app results off screen. This honours it.
 */
class MaxHeightRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    /** Max height in px; can be changed at runtime (e.g. smaller while the keyboard is open). */
    var maxHeightPx: Int = 0
        set(v) { if (field != v) { field = v; requestLayout() } }

    /** The maxHeight from XML. */
    val xmlMaxHeightPx: Int

    init {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.maxHeight))
        xmlMaxHeightPx = a.getDimensionPixelSize(0, 0)
        maxHeightPx = xmlMaxHeightPx
        a.recycle()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        if (maxHeightPx > 0 && View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.EXACTLY) {
            val limit = View.MeasureSpec.getSize(heightSpec).let { if (it > 0) minOf(it, maxHeightPx) else maxHeightPx }
            super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST))
        } else {
            super.onMeasure(widthSpec, heightSpec)
        }
    }
}
