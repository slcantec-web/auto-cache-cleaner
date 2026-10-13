package com.autocachecleaner.app

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppCacheAdapter(
    private val onClick: (AppCacheInfo) -> Unit
) : RecyclerView.Adapter<AppCacheAdapter.VH>() {

    private var items: List<AppCacheInfo> = emptyList()

    fun submit(list: List<AppCacheInfo>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val label: TextView = view.findViewById(R.id.appLabel)
        val pkg: TextView = view.findViewById(R.id.appPackage)
        val size: TextView = view.findViewById(R.id.appCacheSize)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app_cache, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.label.text = item.label
        holder.pkg.text = item.packageName
        holder.size.text = Formatter.formatFileSize(context, item.cacheBytes)
        holder.icon.setImageDrawable(
            try {
                context.packageManager.getApplicationIcon(item.packageName)
            } catch (e: Exception) {
                null
            }
        )
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
