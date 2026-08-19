package com.scanner.pro.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.scanner.pro.R
import com.scanner.pro.model.ScanDocument
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentAdapter(
    private val onOpen: (ScanDocument) -> Unit,
    private val onFavorite: (ScanDocument) -> Unit,
    private val onRename: (ScanDocument) -> Unit,
    private val onDelete: (ScanDocument) -> Unit,
    private val onDuplicate: (ScanDocument) -> Unit,
    private val onShare: (ScanDocument) -> Unit,
    private val onLongPress: (ScanDocument) -> Unit = {},
    private val onSelectionToggled: () -> Unit = {}
) : ListAdapter<ScanDocument, DocumentAdapter.VH>(DIFF) {

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

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view
        val thumbnail: android.widget.ImageView = view.findViewById(R.id.thumbnail)
        val name: android.widget.TextView = view.findViewById(R.id.doc_name)
        val meta: android.widget.TextView = view.findViewById(R.id.doc_meta)
        val favoriteButton: android.widget.ImageButton = view.findViewById(R.id.favorite_button)
        val moreButton: android.widget.ImageButton = view.findViewById(R.id.more_button)
        val selectedCheck: android.widget.ImageView = view.findViewById(R.id.selected_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val doc = getItem(position)
        holder.name.text = doc.name
        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(doc.updatedAt))
        holder.meta.text = "${doc.pages.size} page${if (doc.pages.size == 1) "" else "s"} - $dateStr"

        doc.pages.firstOrNull()?.thumbnailPath?.let {
            Glide.with(holder.thumbnail).load(it).centerCrop().into(holder.thumbnail)
        }

        val isSelected = selectedIds.contains(doc.id)
        holder.selectedCheck.visibility = if (selectionMode && isSelected) View.VISIBLE else View.GONE
        holder.card.alpha = if (selectionMode && !isSelected) 0.6f else 1f
        holder.favoriteButton.visibility = if (selectionMode) View.GONE else View.VISIBLE
        holder.moreButton.visibility = if (selectionMode) View.GONE else View.VISIBLE

        holder.favoriteButton.setImageResource(
            if (doc.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        holder.favoriteButton.setOnClickListener { onFavorite(doc) }

        holder.itemView.setOnClickListener {
            if (selectionMode) toggleSelection(doc.id) else onOpen(doc)
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) { startSelection(doc.id) } else { toggleSelection(doc.id) }
            onLongPress(doc)
            true
        }

        holder.moreButton.setOnClickListener { anchor ->
            PopupMenu(anchor.context, anchor).apply {
                menu.add("Rename")
                menu.add("Duplicate")
                menu.add("Share")
                menu.add("Delete")
                setOnMenuItemClickListener { item ->
                    when (item.title) {
                        "Rename" -> onRename(doc)
                        "Duplicate" -> onDuplicate(doc)
                        "Share" -> onShare(doc)
                        "Delete" -> onDelete(doc)
                    }
                    true
                }
            }.show()
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScanDocument>() {
            override fun areItemsTheSame(oldItem: ScanDocument, newItem: ScanDocument) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: ScanDocument, newItem: ScanDocument) =
                oldItem == newItem
        }
    }
}
