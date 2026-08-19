package com.scanner.pro.ui.scanner

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaActionSound
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ScaleGestureDetector
import android.view.animation.OvershootInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.scanner.pro.R
import com.scanner.pro.camera.CameraManager
import com.scanner.pro.di.ViewModelFactory
import com.scanner.pro.model.DocumentCorners
import com.scanner.pro.utils.PermissionManager
import com.scanner.pro.viewmodel.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScannerFragment : Fragment(R.layout.fragment_scanner) {

    private enum class ScanMode { SCAN, ID_CARD }

    private val viewModel: ScannerViewModel by activityViewModels { ViewModelFactory.getInstance(requireContext()) }
    private lateinit var cameraManager: CameraManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var previewView: PreviewView
    private lateinit var overlay: DetectionOverlayView

    private var autoCaptureEnabled = true
    private var isCapturing = false

    private var currentMode = ScanMode.SCAN
    // Holds the front-of-card capture while we wait for the back-of-card capture.
    private var pendingIdCardFront: Pair<File, DocumentCorners>? = null
    private lateinit var cardSideLabel: android.widget.TextView
    private lateinit var idCardFrame: View

    // Premium capture-feel bits: shutter button reference (so its "ready" ring can be
    // swapped when detection stabilizes), the full-screen flash view, and a shutter
    // sound played on every capture.
    private lateinit var captureButton: android.widget.ImageButton
    private lateinit var flashView: View
    private var shutterSound: MediaActionSound? = null
    private var wasStable = false
    private var lastPageCount = -1

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importImage(it) }
    }
    private val pickPdf = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importPdf(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = view.findViewById(R.id.preview_view)
        overlay = view.findViewById(R.id.detection_overlay)
        val closeButton = view.findViewById<android.widget.ImageButton>(R.id.button_close)
        val flashButton = view.findViewById<android.widget.ImageButton>(R.id.button_flash)
        captureButton = view.findViewById(R.id.button_capture)
        flashView = view.findViewById(R.id.view_flash)
        shutterSound = MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
        val galleryButton = view.findViewById<android.widget.ImageButton>(R.id.button_gallery)
        val importPdfButton = view.findViewById<android.widget.ImageButton>(R.id.button_import_pdf)
        val autoSwitch = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_auto_capture)
        val pageCountText = view.findViewById<android.widget.TextView>(R.id.text_page_count)
        val fabDone = view.findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fab_done)
        val rationaleText = view.findViewById<android.widget.TextView>(R.id.text_permission_rationale)
        val tabScan = view.findViewById<android.widget.TextView>(R.id.tab_scan)
        val tabSmartErase = view.findViewById<android.widget.TextView>(R.id.tab_smart_erase)
        val tabIdCards = view.findViewById<android.widget.TextView>(R.id.tab_id_cards)
        cardSideLabel = view.findViewById(R.id.text_card_side)
        idCardFrame = view.findViewById(R.id.id_card_frame)

        tabScan.setOnClickListener { selectMode(ScanMode.SCAN, tabScan, tabSmartErase, tabIdCards) }
        tabIdCards.setOnClickListener { selectMode(ScanMode.ID_CARD, tabScan, tabSmartErase, tabIdCards) }
        tabSmartErase.setOnClickListener {
            // Not implemented yet: show a toast and leave the current mode untouched.
            android.widget.Toast.makeText(requireContext(), R.string.smart_erase_coming_soon, android.widget.Toast.LENGTH_SHORT).show()
        }

        autoCaptureEnabled = viewModel.settings.autoCapture
        autoSwitch.isChecked = autoCaptureEnabled
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            autoCaptureEnabled = checked
            viewModel.settings.autoCapture = checked
        }

        permissionManager = PermissionManager.fromFragment(this)
        cameraManager = CameraManager(requireContext(), viewLifecycleOwner)

        if (permissionManager.hasCameraPermission(requireContext())) {
            startCamera()
        } else {
            rationaleText.visibility = View.VISIBLE
            permissionManager.request { granted ->
                if (granted) {
                    rationaleText.visibility = View.GONE
                    startCamera()
                }
            }
        }

        closeButton.setOnClickListener { findNavController().popBackStack() }

        var torchOn = false
        flashButton.setOnClickListener {
            torchOn = !torchOn
            cameraManager.toggleTorch(torchOn)
        }

        captureButton.setOnClickListener { capture() }
        // Tactile press feedback: shrink slightly on touch-down, spring back on release.
        // Returning false keeps the click listener above working normally.
        captureButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(OvershootInterpolator()).start()
            }
            false
        }
        galleryButton.setOnClickListener { pickImage.launch("image/*") }
        importPdfButton.setOnClickListener { pickPdf.launch("application/pdf") }

        val scaleDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cameraManager.zoomBy(detector.scaleFactor)
                return true
            }
        })
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN) {
                cameraManager.focusOnTap(previewView, event)
            }
            true
        }

        fabDone.setOnClickListener {
            findNavController().navigate(
                R.id.action_scanner_to_viewer,
                Bundle().apply { putString("documentId", viewModel.activeDocument.value?.id) }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeDocument.collect { doc ->
                    val count = doc?.pages?.size ?: 0
                    pageCountText.text = "$count page${if (count == 1) "" else "s"}"
                    fabDone.visibility = if (count > 0) View.VISIBLE else View.GONE

                    // Small "pop" whenever a new page lands, so the badge doesn't just
                    // silently update its text -- it visibly acknowledges the capture.
                    if (lastPageCount in 0 until count) {
                        pageCountText.animate().cancel()
                        pageCountText.scaleX = 1f
                        pageCountText.scaleY = 1f
                        pageCountText.animate()
                            .scaleX(1.18f).scaleY(1.18f).setDuration(110)
                            .withEndAction {
                                pageCountText.animate().scaleX(1f).scaleY(1f)
                                    .setDuration(140).setInterpolator(OvershootInterpolator()).start()
                            }.start()
                    }
                    lastPageCount = count
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                cameraManager.documentDetector.detectionState.collect { detection ->
                    if (detection == null) return@collect
                    overlay.update(detection.corners, detection.frameWidth, detection.frameHeight, detection.isStable)
                    updateCaptureReadyState(detection.isStable)

                    if (autoCaptureEnabled && detection.isStable && !isCapturing && currentMode == ScanMode.SCAN) {
                        capture()
                    }
                }
            }
        }
    }

    private fun startCamera() {
        cameraManager.startCamera(previewView, onError = {
            android.widget.Toast.makeText(requireContext(), "Camera unavailable on this device", android.widget.Toast.LENGTH_LONG).show()
        })
    }

    /**
     * Swaps the shutter button's ring color to teal and gives a light haptic tick the
     * moment detection becomes stable, so the user feels/sees "locked on, capturing now"
     * an instant before auto-capture actually fires -- rather than the photo just
     * appearing with no warning.
     */
    private fun updateCaptureReadyState(isStable: Boolean) {
        if (isStable == wasStable) return
        wasStable = isStable
        if (currentMode != ScanMode.SCAN) return
        captureButton.setBackgroundResource(
            if (isStable) R.drawable.bg_capture_button_ready else R.drawable.bg_capture_button
        )
        if (isStable) {
            captureButton.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            )
        }
    }

    /** Real-camera shutter feel: a quick white flash, a haptic tick, and the shutter sound. */
    private fun triggerShutterEffect() {
        flashView.animate().cancel()
        flashView.alpha = 0.85f
        flashView.animate().alpha(0f).setDuration(180).setListener(null).start()
        captureButton.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
        shutterSound?.play(MediaActionSound.SHUTTER_CLICK)
    }

    private fun capture() {
        if (isCapturing) return
        isCapturing = true
        cameraManager.documentDetector.resetStability()
        triggerShutterEffect()

        val outputFile = File(requireContext().cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        cameraManager.capturePhoto(
            outputFile = outputFile,
            onSaved = { file ->
                when (currentMode) {
                    ScanMode.SCAN -> {
                        // Detect corners on the actual saved (EXIF-corrected, full-resolution)
                        // photo rather than reusing the live overlay's corners -- see
                        // resolveCorners() for why those two coordinate spaces don't match.
                        viewLifecycleOwner.lifecycleScope.launch {
                            val corners = resolveCorners(file)
                            isCapturing = false
                            if (corners != null) navigateToCrop(file, corners)
                        }
                    }
                    ScanMode.ID_CARD -> {
                        isCapturing = false
                        val corners = resolveIdCardFrameCorners(file)
                        if (corners != null) handleIdCardCapture(file, corners)
                    }
                }
            },
            onError = {
                isCapturing = false
                android.widget.Toast.makeText(requireContext(), "Capture failed, please try again", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    /**
     * Runs document-edge detection on the just-captured photo itself, decoded at the same
     * [com.scanner.pro.utils.BitmapUtils.SCAN_MAX_DIMENSION] the crop editor and repository use.
     *
     * This used to just return [lastCorners] (the last result from the live preview overlay).
     * That was wrong: [lastCorners] is computed by [com.scanner.pro.camera.DocumentDetector] on
     * CameraX's ImageAnalysis stream, which (a) is a much lower, independently-chosen resolution
     * than the actual capture, (b) is not rotated to match the saved photo's upright EXIF
     * orientation, and (c) can differ in aspect ratio from the capture stream entirely. Passing
     * those coordinates straight into the crop editor as if they applied to the saved photo's
     * pixels produced a badly misaligned initial crop box on most devices. Detecting fresh on
     * the saved file guarantees the corners are always in the right coordinate space.
     */
    private suspend fun resolveCorners(file: File): DocumentCorners? = withContext(Dispatchers.Default) {
        val bmp = com.scanner.pro.utils.BitmapUtils.decodeSampledBitmap(
            file.absolutePath, com.scanner.pro.utils.BitmapUtils.SCAN_MAX_DIMENSION
        ) ?: return@withContext null
        val mat = com.scanner.pro.opencv.OpenCVHelper.bitmapToMat(bmp)
        val detected = com.scanner.pro.opencv.OpenCVHelper.findDocumentCorners(mat)
        mat.release()
        val corners = detected ?: DocumentCorners.defaultForSize(bmp.width, bmp.height)
        bmp.recycle()
        corners
    }

    /**
     * ID Cards mode doesn't use live edge detection: it crops to wherever the
     * static [idCardFrame] guide currently sits on screen, mapped from
     * preview-view coordinates onto the captured photo's pixel dimensions.
     * (Assumes the photo's aspect ratio roughly matches the preview's, which
     * holds for CameraX's default preview/capture binding on most devices.)
     */
    private fun resolveIdCardFrameCorners(file: File): DocumentCorners? {
        val bmp = com.scanner.pro.utils.BitmapUtils.decodeSampledBitmap(file.absolutePath, 3000) ?: return null
        val bitmapWidth = bmp.width
        val bitmapHeight = bmp.height
        bmp.recycle()

        val frameLoc = IntArray(2)
        val previewLoc = IntArray(2)
        idCardFrame.getLocationOnScreen(frameLoc)
        previewView.getLocationOnScreen(previewLoc)
        if (previewView.width == 0 || previewView.height == 0) {
            return DocumentCorners.defaultForSize(bitmapWidth, bitmapHeight)
        }

        val relLeft = (frameLoc[0] - previewLoc[0]).toFloat() / previewView.width
        val relTop = (frameLoc[1] - previewLoc[1]).toFloat() / previewView.height
        val relRight = relLeft + idCardFrame.width.toFloat() / previewView.width
        val relBottom = relTop + idCardFrame.height.toFloat() / previewView.height

        val left = (relLeft * bitmapWidth).coerceIn(0f, bitmapWidth.toFloat())
        val top = (relTop * bitmapHeight).coerceIn(0f, bitmapHeight.toFloat())
        val right = (relRight * bitmapWidth).coerceIn(0f, bitmapWidth.toFloat())
        val bottom = (relBottom * bitmapHeight).coerceIn(0f, bitmapHeight.toFloat())

        return DocumentCorners(
            topLeft = android.graphics.PointF(left, top),
            topRight = android.graphics.PointF(right, top),
            bottomRight = android.graphics.PointF(right, bottom),
            bottomLeft = android.graphics.PointF(left, bottom)
        )
    }

    /**
     * ID Cards mode captures twice in a row: first the front, then (after the
     * user flips the physical card) the back. The pair is merged into a
     * single document page instead of going through the manual crop screen.
     */
    private fun handleIdCardCapture(file: File, corners: DocumentCorners) {
        val front = pendingIdCardFront
        if (front == null) {
            pendingIdCardFront = file to corners
            cardSideLabel.text = getString(R.string.back_label)
            android.widget.Toast.makeText(requireContext(), R.string.id_card_capture_back, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            pendingIdCardFront = null
            viewModel.addIdCardPage(front.first, front.second, file, corners)
            cardSideLabel.text = getString(R.string.front_label)
            android.widget.Toast.makeText(requireContext(), R.string.id_card_saved, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectMode(
        mode: ScanMode,
        tabScan: android.widget.TextView,
        tabSmartErase: android.widget.TextView,
        tabIdCards: android.widget.TextView
    ) {
        if (currentMode == mode) return
        // Switching modes mid-card cancels any pending front-of-card capture.
        pendingIdCardFront?.first?.delete()
        pendingIdCardFront = null
        currentMode = mode

        val selected = android.graphics.Color.WHITE
        val unselected = android.graphics.Color.parseColor("#B3FFFFFF")
        tabScan.setTextColor(if (mode == ScanMode.SCAN) selected else unselected)
        tabScan.setTypeface(null, if (mode == ScanMode.SCAN) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tabIdCards.setTextColor(if (mode == ScanMode.ID_CARD) selected else unselected)
        tabIdCards.setTypeface(null, if (mode == ScanMode.ID_CARD) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tabSmartErase.setTextColor(unselected)
        tabSmartErase.setTypeface(null, android.graphics.Typeface.NORMAL)

        if (mode == ScanMode.ID_CARD) {
            cardSideLabel.visibility = View.VISIBLE
            cardSideLabel.text = getString(R.string.front_label)
            idCardFrame.visibility = View.VISIBLE
            overlay.visibility = View.GONE
            wasStable = false
            captureButton.setBackgroundResource(R.drawable.bg_capture_button)
            android.widget.Toast.makeText(requireContext(), R.string.id_card_capture_front, android.widget.Toast.LENGTH_SHORT).show()
        } else {
            cardSideLabel.visibility = View.GONE
            idCardFrame.visibility = View.GONE
            overlay.visibility = View.VISIBLE
        }
    }

    /** Imports a picked photo through the same mode-aware flow as a live capture. */
    private fun importImage(uri: Uri) {
        val file = File(requireContext().cacheDir, "import_${System.currentTimeMillis()}.jpg")
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw java.io.IOException("Could not open picked image")
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "Could not import image", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val bmp = com.scanner.pro.utils.BitmapUtils.decodeSampledBitmap(
            file.absolutePath, com.scanner.pro.utils.BitmapUtils.SCAN_MAX_DIMENSION
        )
        if (bmp == null) {
            file.delete()
            android.widget.Toast.makeText(requireContext(), "Could not import image", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val corners = DocumentCorners.defaultForSize(bmp.width, bmp.height)
        bmp.recycle()

        when (currentMode) {
            ScanMode.SCAN -> navigateToCrop(file, corners)
            ScanMode.ID_CARD -> handleIdCardCapture(file, corners)
        }
    }

    /** Imports every page of a picked PDF as a page in the active document. */
    private fun importPdf(uri: Uri) {
        android.widget.Toast.makeText(requireContext(), "Importing PDF\u2026", android.widget.Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val pageFiles = withContext(Dispatchers.IO) { renderPdfToImages(uri) }
            if (pageFiles.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "Could not import PDF", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                viewModel.importPdfPages(pageFiles)
            }
        }
    }

    /** Renders each page of the PDF at [uri] to a JPEG in the cache dir, in page order. */
    private fun renderPdfToImages(uri: Uri): List<File> {
        val files = mutableListOf<File>()
        val pfd = try {
            requireContext().contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            null
        } ?: return files

        try {
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        // Render at 2x the PDF's native point size for reasonably sharp output.
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val file = File(requireContext().cacheDir, "pdf_import_${System.currentTimeMillis()}_$i.jpg")
                        com.scanner.pro.utils.BitmapUtils.saveJpeg(bitmap, file, 92)
                        bitmap.recycle()
                        files.add(file)
                    }
                }
            }
        } catch (e: Exception) {
            // Return whatever pages were successfully rendered before the failure.
        } finally {
            pfd.close()
        }
        return files
    }

    private fun navigateToCrop(file: File, corners: DocumentCorners) {
        val bundle = Bundle().apply {
            putString("imagePath", file.absolutePath)
            putParcelable("corners", corners)
        }
        findNavController().navigate(R.id.action_scanner_to_crop, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingIdCardFront?.first?.delete()
        cameraManager.shutdown()
        shutterSound?.release()
        shutterSound = null
    }
}
