package com.minimal.launcher

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (AppInfo, View) -> Unit,
    private val onLongClick: (AppInfo) -> Unit = {}
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var apps: List<AppInfo> = emptyList()
    private var query: String = ""
    private var hidden: Set<String> = emptySet()
    private var typeface: Typeface = Typeface.MONOSPACE
    private var sizeMultiplier: Float = 1f

    val items: List<AppInfo> get() = apps

    fun update(newApps: List<AppInfo>, newQuery: String, newHidden: Set<String> = hidden) {
        val old = apps
        val oldHidden = hidden
        val q = newQuery.lowercase()
        // Highlight depends on the query, so a changed query must rebind rows that stay put
        val queryChanged = q != query
        apps = newApps; query = q; hidden = newHidden
        if (old.isEmpty() && newApps.isEmpty()) return
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newApps.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].packageName == newApps[n].packageName
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val a = old[o]; val b = newApps[n]
                return !queryChanged && a.label == b.label && a.icon === b.icon &&
                    (a.packageName in oldHidden) == (b.packageName in newHidden)
            }
        }, false).dispatchUpdatesTo(this)
    }

    fun setTypeface(tf: Typeface, mult: Float) {
        if (tf == typeface && mult == sizeMultiplier) return
        typeface = tf; sizeMultiplier = mult
        notifyItemRangeChanged(0, apps.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)

        holder.name.typeface = typeface
        holder.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * sizeMultiplier)

        val name = app.label
        val idx = if (query.isEmpty()) -1 else app.labelLower.indexOf(query)
        holder.name.text = if (idx >= 0) {
            SpannableString(name).apply {
                setSpan(ForegroundColorSpan(Color.WHITE), idx, idx + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), idx, idx + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else name

        // Hidden apps still show in "all apps" (so they can be launched/unhidden) but dimmed
        holder.itemView.alpha = if (app.packageName in hidden) 0.35f else 1f
    }

    override fun getItemCount() = apps.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val name: TextView = v.findViewById(R.id.appName)

        init {
            icon.colorFilter = GRAYSCALE
            v.setOnClickListener { appAt()?.let { onClick(it, icon) } }
            v.setOnLongClickListener { appAt()?.let { onLongClick(it) }; true }
        }

        private fun appAt(): AppInfo? =
            bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION && it < apps.size }?.let { apps[it] }
    }

    companion object {
        val GRAYSCALE = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }
}
