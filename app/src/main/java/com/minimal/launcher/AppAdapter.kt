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
    private var grid = false
    private var showIcons = true

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
            override fun areItemsTheSame(o: Int, n: Int) = old[o].key == newApps[n].key
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val a = old[o]; val b = newApps[n]
                // A search result's number is its position, so a row that moved must rebind too
                return !queryChanged && (q.isEmpty() || o == n) && a.label == b.label && a.icon === b.icon &&
                    (a.key in oldHidden) == (b.key in newHidden)
            }
        }, false).dispatchUpdatesTo(this)
    }

    fun setTypeface(tf: Typeface, mult: Float) {
        if (tf == typeface && mult == sizeMultiplier) return
        typeface = tf; sizeMultiplier = mult
        notifyItemRangeChanged(0, apps.size)
    }

    /** Cells instead of rows. The caller swaps the list's layout manager to match. */
    fun setGrid(on: Boolean) {
        if (on == grid) return
        grid = on
        notifyDataSetChanged()
    }

    /** Names only, for the list. Grid cells always keep their icon. */
    fun setShowIcons(on: Boolean) {
        if (on == showIcons) return
        showIcons = on
        notifyItemRangeChanged(0, apps.size)
    }

    override fun getItemViewType(position: Int) = if (grid) VIEW_GRID else VIEW_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val layout = if (viewType == VIEW_GRID) R.layout.item_app_grid else R.layout.item_app
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.icon.colorFilter = if (colourIcons) null else GRAYSCALE
        holder.icon.visibility = if (grid || showIcons) View.VISIBLE else View.GONE

        holder.name.typeface = typeface
        holder.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, (if (grid) 11f else 14f) * sizeMultiplier)

        val name = app.label
        // Searched in the label itself: lowercasing can change its length ("İ"), which puts labelLower's
        // positions past the end of the label
        val idx = if (query.isEmpty()) -1 else name.indexOf(query, ignoreCase = true)
        holder.name.text = if (idx >= 0) {
            SpannableString(name).apply {
                setSpan(ForegroundColorSpan(Color.WHITE), idx, idx + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), idx, idx + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else name

        // Only single digits: typing "1" of a "12" would already have opened the first app
        holder.badge?.let { badge ->
            if (query.isNotEmpty() && position < MAX_BADGES) {
                badge.visibility = View.VISIBLE
                badge.typeface = Typeface.create(typeface, Typeface.BOLD)
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * sizeMultiplier)
                badge.text = (position + 1).toString()
            } else {
                badge.visibility = View.GONE
            }
        }

        // Hidden apps still show in "all apps" (so they can be launched/unhidden) but dimmed
        holder.itemView.alpha = if (app.key in hidden) 0.35f else 1f
    }

    override fun getItemCount() = apps.size

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val name: TextView = v.findViewById(R.id.appName)
        val badge: TextView? = v.findViewById(R.id.appBadge)

        init {
            v.setOnClickListener { appAt()?.let { onClick(it, icon) } }
            v.setOnLongClickListener { appAt()?.let { onLongClick(it) }; true }
        }

        private fun appAt(): AppInfo? =
            bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION && it < apps.size }?.let { apps[it] }
    }

    companion object {
        const val MAX_BADGES = 9
        private const val VIEW_ROW = 0
        private const val VIEW_GRID = 1
        val GRAYSCALE = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        /** Icons in colour instead of black and white (MinimalSF Extended's "coloured app icons"). */
        var colourIcons = false
    }
}
