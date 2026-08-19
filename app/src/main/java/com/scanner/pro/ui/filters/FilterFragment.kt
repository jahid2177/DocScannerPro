package com.scanner.pro.ui.filters

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.scanner.pro.R
import com.scanner.pro.di.ViewModelFactory
import com.scanner.pro.model.ScanFilterType
import com.scanner.pro.model.ScanPage
import com.scanner.pro.opencv.ImageFilterProcessor
import com.scanner.pro.ui.signature.SignatureDialogFragment
import com.scanner.pro.utils.BitmapUtils
import com.scanner.pro.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch

/**
 * The page editor shown right after a page is captured & cropped (and also
 * reachable again from the Viewer): filter strip + Retake / Left(rotate) /
 * Crop / Extract Text / Sign / Manage Pages, matching the familiar
 * CamScanner-style post-scan screen. Expects `pageId` in arguments; falls
 * back to the most recently added page of the active document.
 */
class FilterFragment : Fragment(R.layout.fragment_filter) {

    private val viewModel: ScannerViewModel by activityViewModels { ViewModelFactory.getInstance(requireContext()) }
    private var pageId: String? = null
    private var originalBitmap: Bitmap? = null
    private var baseBitmap: Bitmap? = null
    private var selectedFilter: ScanFilterType = ScanFilterType.DOCUMENT

    private lateinit var imageView: android.widget.ImageView
    private lateinit var progress: android.widget.ProgressBar
    private lateinit var titleText: android.widget.TextView
    private lateinit var adapter: FilterAdapter

    private fun currentPage(): ScanPage? =
        viewModel.activeDocument.value?.pages?.firstOrNull { it.id == pageId }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val documentId = arguments?.getString("documentId")
        if (documentId != null) viewModel.resumeDocument(documentId)

        pageId = arguments?.getString("pageId") ?: viewModel.activeDocument.value?.pages?.lastOrNull()?.id
        val page = currentPage() ?: return

        imageView = view.findViewById(R.id.image_preview)
        progress = view.findViewById(R.id.progress)
        titleText = view.findViewById(R.id.text_title)
        val backButton = view.findViewById<android.widget.ImageButton>(R.id.button_back)
        val editTitleButton = view.findViewById<android.widget.ImageButton>(R.id.button_edit_title)
        val addPageButton = view.findViewById<android.widget.TextView>(R.id.button_add_page)
        val strip = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.filter_strip)
        val confirmButton = view.findViewById<android.widget.ImageButton>(R.id.button_confirm)

        val actionRetake = view.findViewById<android.widget.LinearLayout>(R.id.action_retake)
        val actionRotateLeft = view.findViewById<android.widget.LinearLayout>(R.id.action_rotate_left)
        val actionCrop = view.findViewById<android.widget.LinearLayout>(R.id.action_crop)
        val actionExtractText = view.findViewById<android.widget.LinearLayout>(R.id.action_extract_text)
        val actionSign = view.findViewById<android.widget.LinearLayout>(R.id.action_sign)
        val actionManagePages = view.findViewById<android.widget.LinearLayout>(R.id.action_manage_pages)

        titleText.text = viewModel.activeDocument.value?.name ?: "Document"

        selectedFilter = page.filterType
        originalBitmap = BitmapUtils.decodeSampledBitmap(page.originalImagePath, 2048)
        baseBitmap = page.corners?.let { corners ->
            originalBitmap?.let { kotlinx.coroutines.runBlocking { ImageFilterProcessor.perspectiveCorrect(it, corners) } }
        } ?: originalBitmap

        val adapterFilters = FilterAdapter.DISPLAY_ORDER
        adapter = FilterAdapter(adapterFilters) { filter ->
            selectedFilter = filter
            baseBitmap?.let { base ->
                viewLifecycleOwner.lifecycleScope.launch {
                    progress.visibility = View.VISIBLE
                    val filtered = ImageFilterProcessor.applyFilter(base, filter)
                    imageView.setImageBitmap(filtered)
                    progress.visibility = View.GONE
                    currentPage()?.let { viewModel.reapplyFilter(it, filter) }
                }
            }
        }
        strip.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        strip.adapter = adapter

        baseBitmap?.let { base ->
            imageView.setImageBitmap(base)
            viewLifecycleOwner.lifecycleScope.launch {
                val previews = ImageFilterProcessor.generateFilterPreviews(base)
                adapter.setThumbnails(previews)
                adapter.setSelected(selectedFilter)
            }
        }

        backButton.setOnClickListener { findNavController().popBackStack() }

        editTitleButton.setOnClickListener { showRenameDialog() }

        addPageButton.setOnClickListener {
            findNavController().navigate(R.id.action_filter_to_scanner)
        }

        actionRetake.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Retake this page?")
                .setMessage("The current capture will be discarded.")
                .setPositiveButton("Retake") { _, _ ->
                    val id = pageId
                    if (id != null) viewModel.deletePage(id)
                    findNavController().navigate(R.id.action_filter_to_scanner)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        actionRotateLeft.setOnClickListener {
            currentPage()?.let { viewModel.rotatePage(it, clockwise = false) }
        }

        actionCrop.setOnClickListener {
            val current = currentPage() ?: return@setOnClickListener
            findNavController().navigate(R.id.action_filter_to_crop, Bundle().apply {
                putString("imagePath", current.originalImagePath)
                putParcelable("corners", current.corners)
                putString("pageId", current.id)
                putString("documentId", viewModel.activeDocument.value?.id)
            })
        }

        actionExtractText.setOnClickListener {
            val current = currentPage() ?: return@setOnClickListener
            viewModel.runOcr(current)
            findNavController().navigate(R.id.action_filter_to_ocr, Bundle().apply {
                putString("pageId", current.id)
            })
        }

        actionSign.setOnClickListener {
            SignatureDialogFragment { signatureBitmap ->
                currentPage()?.let { viewModel.addSignatureToPage(it, signatureBitmap) }
            }.show(childFragmentManager, "signature")
        }

        actionManagePages.setOnClickListener {
            findNavController().navigate(R.id.action_filter_to_manage_pages, Bundle().apply {
                putString("documentId", viewModel.activeDocument.value?.id)
            })
        }

        confirmButton.setOnClickListener {
            findNavController().navigate(R.id.action_filter_to_viewer, Bundle().apply {
                putString("documentId", viewModel.activeDocument.value?.id)
            })
        }

        // Keep the preview in sync with rotate / recrop / signature edits made
        // on this page (each rewrites processedImagePath on disk and pushes a
        // fresh activeDocument value).
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.activeDocument.collect { doc ->
                    titleText.text = doc?.name ?: "Document"
                    val fresh = doc?.pages?.firstOrNull { it.id == pageId } ?: return@collect
                    val bitmap = BitmapUtils.decodeSampledBitmap(fresh.processedImagePath, 2048) ?: return@collect
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun showRenameDialog() {
        val doc = viewModel.activeDocument.value ?: return
        val input = android.widget.EditText(requireContext()).apply { setText(doc.name) }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename document")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    doc.name = newName
                    viewModel.renameDocument(doc.id, newName)
                    titleText.text = newName
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        originalBitmap?.recycle()
    }
}
