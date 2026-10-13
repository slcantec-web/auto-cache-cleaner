package com.autocachecleaner.app

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppCacheAdapter(
    private val onOpen: (AppCacheInfo) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<AppCacheAdapter.VH>() {

    private var items: List<AppCacheInfo> = emptyList()
    private val selected = HashSet<String>()

    /** Replaces the list. Ticked apps stay ticked if they are still in the list. */
    fun submit(list: List<AppCacheInfo>) {
        items = list
        selected.retainAll(list.map { it.packageName }.toSet())
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    fun selectAll(select: Boolean) {
        selected.clear()
        if (select) items.forEach { selected.add(it.packageName) }
        notifyDataSetChanged()
        onSelectionChanged(selected.size)
    }

    fun selectedCount(): Int = selected.size

    /** Ticked apps in list order (largest cache first). */
    fun selectedPackages(): List<String> =
        items.filter { it.packageName in selected }.map { it.packageName }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val check: CheckBox = view.findViewById(R.id.appCheck)
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

        holder.check.setOnCheckedChangeListener(null)
        holder.check.isChecked = item.packageName in selected
        holder.check.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selected.add(item.packageName) else selected.remove(item.packageName)
            onSelectionChanged(selected.size)
        }

        holder.itemView.setOnClickListener { onOpen(item) }
    }

    override fun getItemCount(): Int = items.size
}
