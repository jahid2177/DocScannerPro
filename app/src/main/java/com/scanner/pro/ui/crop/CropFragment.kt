package com.scanner.pro.ui.crop

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.scanner.pro.R
import com.scanner.pro.di.ViewModelFactory
import com.scanner.pro.model.DocumentCorners
import com.scanner.pro.utils.BitmapUtils
import com.scanner.pro.viewmodel.ScannerViewModel
import java.io.File

/**
 * Two modes, distinguished by whether "pageId" is present in the arguments:
 *  - Fresh capture (no pageId): confirming adds a brand-new page and moves on
 *    to the page editor (FilterFragment) to pick a filter.
 *  - Recrop (pageId present, e.g. from the Viewer or the editor's "Crop"
 *    action): confirming re-warps that existing page in place and returns to
 *    wherever the user came from.
 */
class CropFragment : Fragment(R.layout.fragment_crop) {

    private val viewModel: ScannerViewModel by activityViewModels { ViewModelFactory.getInstance(requireContext()) }
    private var imagePath: String? = null
    private var initialCorners: DocumentCorners? = null
    private var pageId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imagePath = arguments?.getString("imagePath")
        initialCorners = arguments?.getParcelable("corners")
        pageId = arguments?.getString("pageId")

        val imageView = view.findViewById<android.widget.ImageView>(R.id.image_preview)
        val overlay = view.findViewById<CropOverlayView>(R.id.crop_overlay)
        val resetButton = view.findViewById<android.widget.TextView>(R.id.button_reset)
        val confirmButton = view.findViewById<android.widget.Button>(R.id.button_confirm)

        val path = imagePath ?: return
        val bitmap = BitmapUtils.decodeSampledBitmap(path, BitmapUtils.SCAN_MAX_DIMENSION) ?: return
        imageView.setImageBitmap(bitmap)

        val corners = initialCorners ?: DocumentCorners.defaultForSize(bitmap.width, bitmap.height)
        overlay.setup(bitmap.width, bitmap.height, corners)

        resetButton.setOnClickListener { overlay.resetToFullImage() }

        confirmButton.setOnClickListener {
            val finalCorners = overlay.getCorrectedCorners()
            val recropPageId = pageId
            if (recropPageId != null) {
                val page = viewModel.activeDocument.value?.pages?.firstOrNull { it.id == recropPageId }
                if (page != null) viewModel.recropPage(page, finalCorners)
                findNavController().popBackStack()
            } else {
                viewModel.addPage(File(path), finalCorners)
                findNavController().navigate(R.id.action_crop_to_filter, Bundle().apply {
                    putString("documentId", viewModel.activeDocument.value?.id)
                })
            }
        }
    }
}
