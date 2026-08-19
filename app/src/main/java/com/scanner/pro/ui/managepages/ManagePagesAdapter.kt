package com.scanner.pro.ui.managepages

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.scanner.pro.R
import com.scanner.pro.model.ScanPage

/**
 * A single tap toggles a page's selection (selection mode is simply "one or
 * more pages selected" -- there's no separate long-press gesture for it, so
 * it never fights with ItemTouchHelper's own long-press-to-drag detection).
 */
class ManagePagesAdapter(
    private val onSelectionChanged: () -> Unit
) : ListAdapter<ScanPage, ManagePagesAdapter.VH>(DIFF) {

    val selectedIds: MutableSet<String> = mutableSetOf()
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()

    fun toggleSelection(id: String) {
        if (!selectedIds.remove(id)) selectedIds.add(id)
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val frame: android.widget.FrameLayout = view.findViewById(R.id.thumb_frame)
        val thumb: android.widget.ImageView = view.findViewById(R.id.page_thumb)
        val check: android.widget.ImageView = view.findViewById(R.id.selected_check)
        val badge: android.widget.TextView = view.findViewById(R.id.page_number_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manage_page, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val page = getItem(position)
        Glide.with(holder.thumb).load(page.thumbnailPath).fitCenter().into(holder.thumb)
        holder.badge.text = "${position + 1}"

        val isSelected = selectedIds.contains(page.id)
        holder.frame.isSelected = isSelected
        holder.check.visibility = if (isSelected) android.view.View.VISIBLE else android.view.View.GONE
        holder.itemView.alpha = if (selectionMode && !isSelected) 0.55f else 1f

        holder.itemView.setOnClickListener { toggleSelection(page.id) }
    }

    /** Swaps two items for drag-and-drop reordering (called by the ItemTouchHelper). */
    fun moveItem(from: Int, to: Int) {
        val list = currentList.toMutableList()
        val page = list.removeAt(from)
        list.add(to, page)
        submitList(list)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScanPage>() {
            override fun areItemsTheSame(oldItem: ScanPage, newItem: ScanPage) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ScanPage, newItem: ScanPage) = oldItem == newItem
        }
    }
}
