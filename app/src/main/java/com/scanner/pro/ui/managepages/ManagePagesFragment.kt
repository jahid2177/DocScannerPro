package com.scanner.pro.ui.managepages

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.scanner.pro.R
import com.scanner.pro.di.ViewModelFactory
import com.scanner.pro.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch

/**
 * Dedicated "Manage Pages" screen for the active document: reorder pages by
 * dragging, nudge a selected page left/right by one position, or delete a
 * multi-selection. Reachable from the page editor's "Manage Pages" action.
 */
class ManagePagesFragment : Fragment(R.layout.fragment_manage_pages) {

    private val viewModel: ScannerViewModel by activityViewModels { ViewModelFactory.getInstance(requireContext()) }
    private lateinit var adapter: ManagePagesAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val documentId = arguments?.getString("documentId")
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_pages)
        val selectionCount = view.findViewById<android.widget.TextView>(R.id.text_selection_count)
        val deselectAll = view.findViewById<android.widget.TextView>(R.id.button_deselect_all)
        val selectionActions = view.findViewById<android.widget.LinearLayout>(R.id.selection_actions)
        val moveLeft = view.findViewById<android.widget.LinearLayout>(R.id.action_move_left)
        val moveRight = view.findViewById<android.widget.LinearLayout>(R.id.action_move_right)
        val deletePages = view.findViewById<android.widget.LinearLayout>(R.id.action_delete_pages)
        val closeButton = view.findViewById<android.widget.ImageButton>(R.id.button_close)
        val doneButton = view.findViewById<android.widget.ImageButton>(R.id.button_done)

        adapter = ManagePagesAdapter(onSelectionChanged = { updateSelectionUi(selectionCount, deselectAll, selectionActions, moveLeft, moveRight) })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        val touchHelper = ItemTouchHelper(ManagePagesDragCallback(adapter) { from, to ->
            viewModel.reorderPages(from, to)
        })
        touchHelper.attachToRecyclerView(recycler)

        deselectAll.setOnClickListener { adapter.clearSelection() }
        closeButton.setOnClickListener { findNavController().popBackStack() }
        doneButton.setOnClickListener { findNavController().popBackStack() }

        moveLeft.setOnClickListener {
            val doc = viewModel.activeDocument.value ?: return@setOnClickListener
            val id = adapter.selectedIds.singleOrNull() ?: return@setOnClickListener
            val index = doc.pages.indexOfFirst { it.id == id }
            if (index > 0) viewModel.reorderPages(index, index - 1)
        }
        moveRight.setOnClickListener {
            val doc = viewModel.activeDocument.value ?: return@setOnClickListener
            val id = adapter.selectedIds.singleOrNull() ?: return@setOnClickListener
            val index = doc.pages.indexOfFirst { it.id == id }
            if (index in 0 until doc.pages.size - 1) viewModel.reorderPages(index, index + 1)
        }
        deletePages.setOnClickListener {
            val ids = adapter.selectedIds.toSet()
            if (ids.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Delete ${ids.size} page${if (ids.size == 1) "" else "s"}?")
                .setMessage("This can't be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.deletePages(ids)
                    adapter.clearSelection()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        if (documentId != null) viewModel.resumeDocument(documentId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeDocument.collect { doc ->
                    adapter.submitList(doc?.pages?.toList().orEmpty())
                }
            }
        }
    }

    private fun updateSelectionUi(
        selectionCount: android.widget.TextView,
        deselectAll: android.widget.TextView,
        selectionActions: android.widget.LinearLayout,
        moveLeft: android.widget.LinearLayout,
        moveRight: android.widget.LinearLayout
    ) {
        val count = adapter.selectedIds.size
        if (count > 0) {
            selectionCount.text = "$count selected"
            deselectAll.visibility = View.VISIBLE
            selectionActions.visibility = View.VISIBLE
            val singleSelected = count == 1
            moveLeft.alpha = if (singleSelected) 1f else 0.4f
            moveRight.alpha = if (singleSelected) 1f else 0.4f
            moveLeft.isEnabled = singleSelected
            moveRight.isEnabled = singleSelected
        } else {
            selectionCount.text = "Manage Pages"
            deselectAll.visibility = View.GONE
            selectionActions.visibility = View.GONE
        }
    }
}
