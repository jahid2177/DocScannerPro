package com.scanner.pro.ui.viewer

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class PageDragCallback(
    private val adapter: PageAdapter,
    private val onDropped: (from: Int, to: Int) -> Unit
) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        adapter.moveItem(from, to)
        onDropped(from, to)
        return true
    }

    // Long-press is used to enter multi-select mode (see PageAdapter), so drag
    // reordering is no longer started by a long-press -- it would otherwise
    // grab the same gesture and block selection from ever triggering.
    override fun isLongPressDragEnabled(): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Reordering only; swipe-to-delete is intentionally not wired here to
        // avoid accidental data loss — deletion goes through the confirm dialog.
    }
}
