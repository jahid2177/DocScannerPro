package com.scanner.pro.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.scanner.pro.model.DocumentCorners
import com.scanner.pro.opencv.OpenCVHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream

/**
 * Analyzer plugged into CameraX's ImageAnalysis use case. Runs OpenCVHelper's
 * document-detection pipeline on a throttled subset of frames and publishes
 * results via [detectionState] for the UI to render an animated overlay border.
 *
 * Frame throttling + downscaling keep this comfortable on 2GB-RAM devices.
 */
class DocumentDetector(
    private val analysisWidth: Int = 480 // downscale target for detection-only frames
) : ImageAnalysis.Analyzer {

    data class DetectionResult(
        val corners: DocumentCorners?,
        val frameWidth: Int,
        val frameHeight: Int,
        val isStable: Boolean
    )

    private val _detectionState = MutableStateFlow<DetectionResult?>(null)
    val detectionState: StateFlow<DetectionResult?> = _detectionState.asStateFlow()

    // Stability tracking: only flip to "stable" (ready for auto-capture) once
    // several consecutive frames agree closely on corner positions.
    private var lastCorners: DocumentCorners? = null
    private var stableFrameCount = 0
    private val stabilityThresholdPx = 12f
    private val framesRequiredForStability = 6

    private var frameCounter = 0
    private val analyzeEveryNthFrame = 2 // skip every other frame to save CPU

    override fun analyze(image: ImageProxy) {
        frameCounter++
        if (frameCounter % analyzeEveryNthFrame != 0) {
            image.close()
            return
        }

        try {
            val bitmap = imageProxyToBitmap(image)
            val scale = analysisWidth.toFloat() / bitmap.width
            val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, analysisWidth, scaledHeight, true)

            val mat: Mat = OpenCVHelper.bitmapToMat(scaledBitmap)
            val corners = OpenCVHelper.findDocumentCorners(mat)
            mat.release()

            // Scale corners back up to full-frame coordinates for overlay drawing.
            val fullCorners = corners?.let { scaleCorners(it, 1f / scale) }

            updateStability(fullCorners)

            _detectionState.value = DetectionResult(
                corners = fullCorners,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height,
                isStable = stableFrameCount >= framesRequiredForStability
            )

            if (scaledBitmap != bitmap) scaledBitmap.recycle()
            bitmap.recycle()
        } catch (e: Exception) {
            // Detection failures should never crash the preview; just skip this frame.
            _detectionState.value = _detectionState.value?.copy(isStable = false)
        } finally {
            image.close()
        }
    }

    private fun updateStability(corners: DocumentCorners?) {
        val previous = lastCorners
        if (corners == null || previous == null) {
            stableFrameCount = 0
        } else {
            val movement = listOf(
                distance(previous.topLeft, corners.topLeft),
                distance(previous.topRight, corners.topRight),
                distance(previous.bottomRight, corners.bottomRight),
                distance(previous.bottomLeft, corners.bottomLeft)
            ).max()

            stableFrameCount = if (movement < stabilityThresholdPx) stableFrameCount + 1 else 0
        }
        lastCorners = corners
    }

    private fun distance(a: android.graphics.PointF, b: android.graphics.PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun scaleCorners(corners: DocumentCorners, factor: Float): DocumentCorners = DocumentCorners(
        topLeft = android.graphics.PointF(corners.topLeft.x * factor, corners.topLeft.y * factor),
        topRight = android.graphics.PointF(corners.topRight.x * factor, corners.topRight.y * factor),
        bottomRight = android.graphics.PointF(corners.bottomRight.x * factor, corners.bottomRight.y * factor),
        bottomLeft = android.graphics.PointF(corners.bottomLeft.x * factor, corners.bottomLeft.y * factor)
    )

    /** Converts a YUV_420_888 ImageProxy (CameraX's default analysis format) to an RGB Bitmap. */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
        val jpegBytes = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    fun resetStability() {
        stableFrameCount = 0
        lastCorners = null
    }
}
