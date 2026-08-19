package com.scanner.pro.ui.viewer

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.scanner.pro.R
import com.scanner.pro.model.ScanPage

class PageAdapter(
    private val onOpen: (ScanPage) -> Unit,
    private val onRotate: (ScanPage) -> Unit,
    private val onCrop: (ScanPage) -> Unit,
    private val onDuplicate: (ScanPage) -> Unit,
    private val onDelete: (ScanPage) -> Unit,
    private val onLongPress: (ScanPage) -> Unit = {},
    private val onSelectionToggled: () -> Unit = {}
) : ListAdapter<ScanPage, PageAdapter.VH>(DIFF) {

    var selectionMode: Boolean = false
        private set
    val selectedIds: MutableSet<String> = mutableSetOf()

    fun startSelection(id: String) {
        selectionMode = true
        selectedIds.clear()
        selectedIds.add(id)
        notifyDataSetChanged()
        onSelectionToggled()
    }

    fun clearSelection() {
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionToggled()
    }

    private fun toggleSelection(id: String) {
        if (!selectedIds.remove(id)) selectedIds.add(id)
        if (selectedIds.isEmpty()) selectionMode = false
        notifyDataSetChanged()
        onSelectionToggled()
    }

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val thumb: android.widget.ImageView = view.findViewById(R.id.page_thumb)
        val label: android.widget.TextView = view.findViewById(R.id.page_label)
        val rotate: android.widget.ImageButton = view.findViewById(R.id.button_rotate)
        val crop: android.widget.ImageButton = view.findViewById(R.id.button_crop)
        val more: android.widget.ImageButton = view.findViewById(R.id.button_page_more)
        val selectedCheck: android.widget.ImageView = view.findViewById(R.id.selected_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_page_thumb, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val page = getItem(position)
        holder.label.text = "Page ${position + 1}"
        Glide.with(holder.thumb).load(page.thumbnailPath).centerCrop().into(holder.thumb)

        val isSelected = selectedIds.contains(page.id)
        holder.selectedCheck.visibility = if (selectionMode && isSelected) android.view.View.VISIBLE else android.view.View.GONE
        holder.itemView.alpha = if (selectionMode && !isSelected) 0.6f else 1f
        holder.rotate.visibility = if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE
        holder.crop.visibility = if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE
        holder.more.visibility = if (selectionMode) android.view.View.GONE else android.view.View.VISIBLE

        holder.itemView.setOnClickListener {
            if (selectionMode) toggleSelection(page.id) else onOpen(page)
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) { startSelection(page.id) } else { toggleSelection(page.id) }
            onLongPress(page)
            true
        }
        holder.rotate.setOnClickListener { onRotate(page) }
        holder.crop.setOnClickListener { onCrop(page) }
        holder.more.setOnClickListener { anchor ->
            PopupMenu(anchor.context, anchor).apply {
                menu.add("Recrop")
                menu.add("Duplicate")
                menu.add("Delete")
                setOnMenuItemClickListener {
                    when (it.title) {
                        "Recrop" -> onCrop(page)
                        "Duplicate" -> onDuplicate(page)
                        "Delete" -> onDelete(page)
                    }
                    true
                }
            }.show()
        }
    }

    /** Swaps two items for drag-and-drop reordering (called by ItemTouchHelper). */
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
