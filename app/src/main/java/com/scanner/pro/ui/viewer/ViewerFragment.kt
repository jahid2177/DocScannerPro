package com.scanner.pro.ui.viewer

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.scanner.pro.R
import com.scanner.pro.di.ViewModelFactory
import com.scanner.pro.model.Resource
import com.scanner.pro.model.ScanDocument
import com.scanner.pro.pdf.PdfOptions
import com.scanner.pro.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

class ViewerFragment : Fragment(R.layout.fragment_viewer) {

    private val viewModel: ScannerViewModel by activityViewModels { ViewModelFactory.getInstance(requireContext()) }
    private lateinit var adapter: PageAdapter
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar

    /** Set right before triggering a PDF export from the bottom sheet; run once exportState reports success. */
    private var pendingExportAction: ((File) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val documentId = arguments?.getString("documentId")
        toolbar = view.findViewById(R.id.toolbar)
        val recycler = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_pages)
        val addPage = view.findViewById<android.widget.TextView>(R.id.action_add_page)
        val ocrAction = view.findViewById<android.widget.TextView>(R.id.action_ocr)
        val pdfAction = view.findViewById<android.widget.TextView>(R.id.action_pdf)

        toolbar.setNavigationOnClickListener {
            if (adapter.selectionMode) adapter.clearSelection() else findNavController().popBackStack()
        }
        showNormalMenu()

        adapter = PageAdapter(
            onOpen = { page -> showFullScreenPreview(page.processedImagePath) },
            onRotate = { viewModel.rotatePage(it, clockwise = true) },
            onCrop = { page ->
                findNavController().navigate(R.id.action_viewer_to_crop, Bundle().apply {
                    putString("imagePath", page.originalImagePath)
                    putParcelable("corners", page.corners)
                    putString("pageId", page.id)
                    putString("documentId", viewModel.activeDocument.value?.id)
                })
            },
            onDuplicate = { viewModel.duplicatePage(it) },
            onDelete = { page ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete this page?")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deletePage(page.id) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onLongPress = { updateSelectionToolbar() },
            onSelectionToggled = { updateSelectionToolbar() }
        )
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler.adapter = adapter

        val touchHelper = ItemTouchHelper(PageDragCallback(adapter) { from, to ->
            viewModel.reorderPages(from, to)
        })
        touchHelper.attachToRecyclerView(recycler)

        addPage.setOnClickListener { findNavController().navigate(R.id.action_viewer_to_scanner) }

        ocrAction.setOnClickListener {
            val page = viewModel.activeDocument.value?.pages?.firstOrNull()
            if (page == null) {
                Snackbar.make(view, "Add a page first", Snackbar.LENGTH_SHORT).show()
            } else {
                viewModel.runOcr(page)
                findNavController().navigate(R.id.action_viewer_to_ocr, Bundle().apply {
                    putString("pageId", page.id)
                })
            }
        }

        pdfAction.setOnClickListener {
            val doc = viewModel.activeDocument.value ?: return@setOnClickListener
            val outputFile = File(requireContext().cacheDir, "${doc.name}.pdf")
            viewModel.exportPdf(outputFile, PdfOptions(title = doc.name))
            findNavController().navigate(R.id.action_viewer_to_pdf_preview)
        }

        if (documentId != null) viewModel.resumeDocument(documentId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeDocument.collect { doc ->
                    if (!adapter.selectionMode) toolbar.title = doc?.name ?: "Document"
                    adapter.submitList(doc?.pages?.toList().orEmpty())
                }
            }
        }

        // Drives the two export-sheet actions that need a freshly generated PDF file.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportState.collect { state ->
                    if (state is Resource.Success) {
                        pendingExportAction?.invoke(state.data)
                        pendingExportAction = null
                    } else if (state is Resource.Error) {
                        pendingExportAction = null
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // Toolbar: normal menu vs. multi-select menu
    // -----------------------------------------------------------------

    private fun showNormalMenu() {
        toolbar.navigationIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.menu_viewer)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_more) {
                showExportSheet()
                true
            } else false
        }
    }

    private fun showSelectionMenu() {
        toolbar.navigationIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.menu_viewer_selection)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete_selected_pages) {
                confirmDeleteSelectedPages()
                true
            } else false
        }
    }

    private fun updateSelectionToolbar() {
        if (adapter.selectionMode) {
            toolbar.title = "${adapter.selectedIds.size} selected"
            showSelectionMenu()
        } else {
            toolbar.title = viewModel.activeDocument.value?.name ?: "Document"
            showNormalMenu()
        }
    }

    private fun confirmDeleteSelectedPages() {
        val ids = adapter.selectedIds.toSet()
        if (ids.isEmpty()) return
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

    // -----------------------------------------------------------------
    // Tap-to-preview
    // -----------------------------------------------------------------

    private fun showFullScreenPreview(imagePath: String) {
        val dialog = android.app.Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_page_preview, null)
        val imageView = view.findViewById<android.widget.ImageView>(R.id.preview_image)
        val closeButton = view.findViewById<android.widget.ImageButton>(R.id.preview_close)
        Glide.with(imageView).load(imagePath).fitCenter().into(imageView)
        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(view)
        dialog.show()
    }

    // -----------------------------------------------------------------
    // Export bottom sheet: Quality / Share PDF / Save to device / Share images / Delete All
    // -----------------------------------------------------------------

    private fun showExportSheet() {
        val doc = viewModel.activeDocument.value ?: return
        val sheet = BottomSheetDialog(requireContext())
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_export, null)
        sheet.setContentView(view)

        val nameText = view.findViewById<android.widget.TextView>(R.id.sheet_doc_name)
        val qualityButton = view.findViewById<android.widget.Button>(R.id.button_quality)
        nameText.text = doc.name
        qualityButton.text = "Quality: ${qualityLabel(viewModel.settings.imageQuality)}"

        qualityButton.setOnClickListener {
            val levels = arrayOf("Low", "Medium", "High")
            val values = intArrayOf(45, 75, 95)
            AlertDialog.Builder(requireContext())
                .setTitle("Export quality")
                .setItems(levels) { _, which ->
                    viewModel.settings.imageQuality = values[which]
                    qualityButton.text = "Quality: ${levels[which]}"
                }
                .show()
        }

        view.findViewById<View>(R.id.action_share_pdf).setOnClickListener {
            sheet.dismiss()
            sharePdf(doc)
        }
        view.findViewById<View>(R.id.action_save_device).setOnClickListener {
            sheet.dismiss()
            saveToDevice(doc)
        }
        view.findViewById<View>(R.id.action_share_images).setOnClickListener {
            sheet.dismiss()
            shareImages(doc)
        }
        view.findViewById<View>(R.id.action_delete_all).setOnClickListener {
            sheet.dismiss()
            confirmDeleteAll(doc)
        }

        sheet.show()
    }

    private fun qualityLabel(value: Int): String = when {
        value <= 55 -> "Low"
        value <= 85 -> "Medium"
        else -> "High"
    }

    private fun sharePdf(doc: ScanDocument) {
        val outputFile = File(requireContext().cacheDir, "${doc.name}.pdf")
        pendingExportAction = { file ->
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
        }
        viewModel.exportPdf(outputFile, PdfOptions(title = doc.name, quality = viewModel.settings.imageQuality))
    }

    private fun saveToDevice(doc: ScanDocument) {
        val outputFile = File(requireContext().cacheDir, "${doc.name}.pdf")
        pendingExportAction = { file -> saveFileToDownloads(file, "application/pdf") }
        viewModel.exportPdf(outputFile, PdfOptions(title = doc.name, quality = viewModel.settings.imageQuality))
    }

    private fun saveFileToDownloads(file: File, mimeType: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    Snackbar.make(requireView(), "Could not save file", Snackbar.LENGTH_SHORT).show()
                    return
                }
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dest = File(downloadsDir, file.name)
                file.copyTo(dest, overwrite = true)
            }
            Snackbar.make(requireView(), "Saved to Downloads", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(requireView(), "Could not save file", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun shareImages(doc: ScanDocument) {
        if (doc.pages.isEmpty()) {
            Snackbar.make(requireView(), "No pages to share", Snackbar.LENGTH_SHORT).show()
            return
        }
        val uris = ArrayList(doc.pages.map { page ->
            FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", File(page.processedImagePath))
        })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share images"))
    }

    private fun confirmDeleteAll(doc: ScanDocument) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete all pages?")
            .setMessage("This removes every page in \"${doc.name}\". This can't be undone.")
            .setPositiveButton("Delete All") { _, _ -> viewModel.deleteAllPages() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
