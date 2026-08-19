package com.scanner.pro.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.scanner.pro.R
import com.scanner.pro.di.ViewModelFactory
import com.scanner.pro.model.ScanDocument
import com.scanner.pro.repository.SortOrder
import com.scanner.pro.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: ScannerViewModel by activityViewModels { ViewModelFactory.getInstance(requireContext()) }
    private lateinit var adapter: DocumentAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        (requireActivity() as androidx.appcompat.app.AppCompatActivity).setSupportActionBar(toolbar)

        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_documents)
        val emptyState = view.findViewById<android.widget.TextView>(R.id.empty_state)
        val swipeRefresh = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe_refresh)
        val fab = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_scan)

        adapter = DocumentAdapter(
            onOpen = { doc ->
                findNavController().navigate(
                    R.id.action_home_to_viewer,
                    Bundle().apply { putString("documentId", doc.id) }
                )
            },
            onFavorite = { viewModel.toggleFavorite(it.id) },
            onRename = { showRenameDialog(it) },
            onDelete = { confirmDelete(it) },
            onDuplicate = { viewModel.duplicateDocument(it.id) },
            onShare = { shareDocument(it) },
            onSelectionToggled = { updateSelectionUi() }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        fab.setOnClickListener {
            viewModel.startNewDocument()
            findNavController().navigate(R.id.action_home_to_scanner)
        }

        swipeRefresh.setOnRefreshListener {
            viewModel.refreshDocuments()
            swipeRefresh.isRefreshing = false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.documents.collect { docs ->
                    adapter.submitList(docs)
                    emptyState.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDocuments()
    }

    private fun updateSelectionUi() {
        val activity = requireActivity() as androidx.appcompat.app.AppCompatActivity
        if (adapter.selectionMode) {
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            activity.supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
            activity.supportActionBar?.title = "${adapter.selectedIds.size} selected"
        } else {
            activity.supportActionBar?.setDisplayHomeAsUpEnabled(false)
            activity.supportActionBar?.title = getString(R.string.app_name)
        }
        activity.invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (adapter.selectionMode) {
            inflater.inflate(R.menu.menu_home_selection, menu)
            return
        }
        inflater.inflate(R.menu.menu_home, menu)
        val searchItem = menu.findItem(R.id.action_search)
        (searchItem.actionView as? SearchView)?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.search(newText.orEmpty())
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            if (adapter.selectionMode) adapter.clearSelection()
            true
        }
        R.id.action_delete_selected -> { confirmDeleteSelected(); true }
        R.id.action_sort -> { showSortMenu(); true }
        R.id.action_settings -> { findNavController().navigate(R.id.action_home_to_settings); true }
        R.id.action_about -> { findNavController().navigate(R.id.action_home_to_about); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun confirmDeleteSelected() {
        val ids = adapter.selectedIds.toSet()
        if (ids.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${ids.size} document${if (ids.size == 1) "" else "s"}?")
            .setMessage("This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteDocuments(ids)
                adapter.clearSelection()
                Snackbar.make(requireView(), "Deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSortMenu() {
        val popup = android.widget.PopupMenu(requireContext(), requireView().findViewById(R.id.toolbar))
        popup.menuInflater.inflate(R.menu.menu_sort, popup.menu)
        popup.setOnMenuItemClickListener {
            val order = when (it.itemId) {
                R.id.sort_date_newest -> SortOrder.DATE_NEWEST
                R.id.sort_date_oldest -> SortOrder.DATE_OLDEST
                R.id.sort_name_az -> SortOrder.NAME_AZ
                R.id.sort_name_za -> SortOrder.NAME_ZA
                else -> SortOrder.DATE_NEWEST
            }
            viewModel.applySort(order)
            true
        }
        popup.show()
    }

    private fun showRenameDialog(doc: ScanDocument) {
        val input = EditText(requireContext()).apply { setText(doc.name) }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename document")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) viewModel.renameDocument(doc.id, newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(doc: ScanDocument) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete \"${doc.name}\"?")
            .setMessage("This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteDocument(doc.id)
                Snackbar.make(requireView(), "Document deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareDocument(doc: ScanDocument) {
        val page = doc.pages.firstOrNull() ?: return
        val file = File(page.processedImagePath)
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share document"))
    }
}
