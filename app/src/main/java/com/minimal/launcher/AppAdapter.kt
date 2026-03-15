package com.minimal.launcher

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit = {}
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var apps: List<AppInfo> = emptyList()
    private var query: String = ""

    private val grayscaleFilter = ColorMatrixColorFilter(
        ColorMatrix().apply { setSaturation(0f) }
    )

    fun update(newApps: List<AppInfo>, newQuery: String) {
        val old = apps
        apps = newApps
        query = newQuery.lowercase()
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newApps.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].packageName == newApps[n].packageName
            override fun areContentsTheSame(o: Int, n: Int) = old[o].label == newApps[n].label
        }).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.icon.colorFilter = grayscaleFilter

        val name = app.label
        val spannable = SpannableString(name)
        if (query.isNotEmpty()) {
            val idx = name.lowercase().indexOf(query)
            if (idx >= 0) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    idx, idx + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(android.graphics.Typeface.BOLD),
                    idx, idx + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        holder.name.text = spannable
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener {
            onLongClick(app)
            true
        }
    }

    override fun getItemCount() = apps.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.appIcon)
        val name: TextView = v.findViewById(R.id.appName)
    }
}
