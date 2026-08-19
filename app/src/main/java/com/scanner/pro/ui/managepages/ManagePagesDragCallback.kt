package com.scanner.pro.ui.managepages

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class ManagePagesDragCallback(
    private val adapter: ManagePagesAdapter,
    private val onDropped: (from: Int, to: Int) -> Unit
) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
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

    // Long-press enters multi-select instead (see ManagePagesAdapter), so drag
    // is only started via ItemTouchHelper.startDrag() from a dedicated handle
    // touch, matching the "hold and drag" hint shown on screen.
    override fun isLongPressDragEnabled(): Boolean = true

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
}
