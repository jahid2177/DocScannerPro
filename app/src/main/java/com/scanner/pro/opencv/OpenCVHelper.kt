package com.scanner.pro.opencv

import android.graphics.Bitmap
import android.graphics.PointF
import com.scanner.pro.model.DocumentCorners
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.core.Core
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Central OpenCV pipeline used by both the live camera preview (document detection)
 * and the post-capture editing pipeline (crop, filters, enhancement).
 *
 * All public functions are synchronous and expected to be called from a background
 * dispatcher (Dispatchers.Default) since OpenCV ops are CPU-bound.
 */
object OpenCVHelper {

    // ---------------------------------------------------------------------
    // Bitmap <-> Mat conversion
    // ---------------------------------------------------------------------

    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        Utils.bitmapToMat(argb, mat)
        // OpenCV loads as RGBA; convert to BGR for standard imgproc pipelines.
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2BGR)
        return mat
    }

    fun matToBitmap(mat: Mat): Bitmap {
        val rgbaMat = Mat()
        when (mat.channels()) {
            1 -> Imgproc.cvtColor(mat, rgbaMat, Imgproc.COLOR_GRAY2RGBA)
            3 -> Imgproc.cvtColor(mat, rgbaMat, Imgproc.COLOR_BGR2RGBA)
            else -> mat.copyTo(rgbaMat)
        }
        val bitmap = Bitmap.createBitmap(rgbaMat.cols(), rgbaMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgbaMat, bitmap)
        rgbaMat.release()
        return bitmap
    }

    // ---------------------------------------------------------------------
    // Basic building blocks
    // ---------------------------------------------------------------------

    fun toGray(src: Mat): Mat {
        val dst = Mat()
        Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGR2GRAY)
        return dst
    }

    fun gaussianBlur(src: Mat, kernelSize: Int = 5): Mat {
        val dst = Mat()
        val k = if (kernelSize % 2 == 0) kernelSize + 1 else kernelSize
        Imgproc.GaussianBlur(src, dst, Size(k.toDouble(), k.toDouble()), 0.0)
        return dst
    }

    fun medianBlur(src: Mat, kernelSize: Int = 5): Mat {
        val dst = Mat()
        val k = if (kernelSize % 2 == 0) kernelSize + 1 else kernelSize
        Imgproc.medianBlur(src, dst, k)
        return dst
    }

    fun bilateralFilter(src: Mat, d: Int = 9, sigmaColor: Double = 75.0, sigmaSpace: Double = 75.0): Mat {
        val dst = Mat()
        Imgproc.bilateralFilter(src, dst, d, sigmaColor, sigmaSpace)
        return dst
    }

    fun adaptiveThreshold(src: Mat, blockSize: Int = 15, c: Double = 8.0): Mat {
        val gray = if (src.channels() > 1) toGray(src) else src
        val dst = Mat()
        val block = if (blockSize % 2 == 0) blockSize + 1 else blockSize
        Imgproc.adaptiveThreshold(
            gray, dst, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
            block, c
        )
        return dst
    }

    fun morphologyClose(src: Mat, kernelSize: Int = 5): Mat {
        val dst = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(kernelSize.toDouble(), kernelSize.toDouble()))
        Imgproc.morphologyEx(src, dst, Imgproc.MORPH_CLOSE, kernel)
        return dst
    }

    fun cannyEdges(src: Mat, threshold1: Double = 75.0, threshold2: Double = 200.0): Mat {
        val gray = if (src.channels() > 1) toGray(src) else src
        val blurred = gaussianBlur(gray, 5)
        val edges = Mat()
        Imgproc.Canny(blurred, edges, threshold1, threshold2)
        val dilated = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edges, dilated, kernel)
        blurred.release()
        edges.release()
        if (gray !== src) gray.release()
        return dilated
    }

    // ---------------------------------------------------------------------
    // Document detection
    // ---------------------------------------------------------------------

    /**
     * Finds the largest 4-point quadrilateral contour in [src], which we treat
     * as the document boundary. Returns null if no confident quad is found
     * (caller should fall back to DocumentCorners.defaultForSize).
     */
    fun findDocumentCorners(src: Mat): DocumentCorners? {
        val edges = cannyEdges(src)
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        edges.release()

        val imageArea = src.rows() * src.cols()

        // Pass 1: strict quad match (fast path for clean, high-contrast edges).
        var bestQuad = bestQuadContour(contours, imageArea, epsilonFactor = 0.02)
        // Pass 2: same contours, looser polygon tolerance -- catches documents with
        // slightly rounded/uneven edges (bent paper, low-contrast background) that
        // approxPolyDP couldn't collapse to exactly 4 points at the strict tolerance.
        if (bestQuad == null) {
            bestQuad = bestQuadContour(contours, imageArea, epsilonFactor = 0.04)
        }

        if (bestQuad != null) {
            val points = bestQuad.toArray().map { PointF(it.x.toFloat(), it.y.toFloat()) }
            bestQuad.release()
            contours.forEach { it.release() }
            return orderPoints(points)
        }

        // Pass 3: no clean quad at all -- fall back to the minimum-area rotated
        // rectangle around the single largest plausible contour, so a genuine
        // (if noisy) document edge still beats a full-frame default guess.
        var largest: MatOfPoint? = null
        var largestArea = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < imageArea * 0.15) continue
            if (area > largestArea) {
                largestArea = area
                largest = contour
            }
        }
        val fallbackQuad = largest?.let {
            val rect = Imgproc.minAreaRect(MatOfPoint2f(*it.toArray()))
            val boxPoints = MatOfPoint2f()
            Imgproc.boxPoints(rect, boxPoints)
            boxPoints.toArray().map { p -> PointF(p.x.toFloat(), p.y.toFloat()) }.also { boxPoints.release() }
        }
        contours.forEach { it.release() }

        return fallbackQuad?.let { orderPoints(it) }
    }

    /** Finds the largest 4-point convex contour among [contours], or null if none qualify. */
    private fun bestQuadContour(contours: List<MatOfPoint>, imageArea: Int, epsilonFactor: Double): MatOfPoint2f? {
        var bestQuad: MatOfPoint2f? = null
        var bestArea = 0.0
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            // Ignore contours that are too small to plausibly be the document.
            if (area < imageArea * 0.15) continue

            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, epsilonFactor * peri, true)

            if (approx.total() == 4L && Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) {
                if (area > bestArea) {
                    bestArea = area
                    bestQuad?.release()
                    bestQuad = approx
                } else {
                    approx.release()
                }
            } else {
                approx.release()
            }
            contour2f.release()
        }
        return bestQuad
    }

    /**
     * Orders four arbitrary points into TL, TR, BR, BL using sum/difference heuristics:
     * TL has smallest (x+y), BR has largest (x+y), TR has smallest (y-x), BL has largest (y-x).
     */
    fun orderPoints(points: List<PointF>): DocumentCorners {
        require(points.size == 4) { "orderPoints requires exactly 4 points" }
        val sorted = points.sortedBy { it.x + it.y }
        val topLeft = sorted.first()
        val bottomRight = sorted.last()
        val remaining = points - topLeft - bottomRight
        val topRight = remaining.maxByOrNull { it.x - it.y } ?: remaining[0]
        val bottomLeft = (remaining - topRight).firstOrNull() ?: remaining[1]
        return DocumentCorners(topLeft, topRight, bottomRight, bottomLeft)
    }

    // ---------------------------------------------------------------------
    // Perspective correction
    // ---------------------------------------------------------------------

    /**
     * Warps [src] so that the quadrilateral described by [corners] becomes a flat,
     * axis-aligned rectangle. Output dimensions are computed from the corner
     * distances so aspect ratio is preserved.
     */
    fun warpPerspective(src: Mat, corners: DocumentCorners): Mat {
        val (tl, tr, br, bl) = corners
        val widthTop = distance(tl, tr)
        val widthBottom = distance(bl, br)
        val maxWidth = max(widthTop, widthBottom).toInt().coerceAtLeast(1)

        val heightLeft = distance(tl, bl)
        val heightRight = distance(tr, br)
        val maxHeight = max(heightLeft, heightRight).toInt().coerceAtLeast(1)

        val srcPoints = MatOfPoint2f(
            Point(tl.x.toDouble(), tl.y.toDouble()),
            Point(tr.x.toDouble(), tr.y.toDouble()),
            Point(br.x.toDouble(), br.y.toDouble()),
            Point(bl.x.toDouble(), bl.y.toDouble())
        )
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((maxWidth - 1).toDouble(), 0.0),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble()),
            Point(0.0, (maxHeight - 1).toDouble())
        )

        val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val dst = Mat()
        Imgproc.warpPerspective(src, dst, transform, Size(maxWidth.toDouble(), maxHeight.toDouble()))

        srcPoints.release()
        dstPoints.release()
        transform.release()
        return dst
    }

    private operator fun DocumentCorners.component1() = topLeft
    private operator fun DocumentCorners.component2() = topRight
    private operator fun DocumentCorners.component3() = bottomRight
    private operator fun DocumentCorners.component4() = bottomLeft

    private fun distance(a: PointF, b: PointF): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    // ---------------------------------------------------------------------
    // Skew correction (for pages that don't need full 4-corner perspective warp)
    // ---------------------------------------------------------------------

    fun deskew(src: Mat): Mat {
        val gray = toGray(src)
        val thresh = Mat()
        Imgproc.threshold(gray, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        val points = MatOfPoint()
        Core.findNonZero(thresh, points)
        if (points.empty()) {
            gray.release(); thresh.release(); points.release()
            return src.clone()
        }
        val rect = Imgproc.minAreaRect(MatOfPoint2f(*points.toArray()))
        var angle = rect.angle
        if (angle < -45) angle += 90.0

        val center = Point(src.cols() / 2.0, src.rows() / 2.0)
        val rotMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0)
        val dst = Mat()
        Imgproc.warpAffine(src, dst, rotMatrix, src.size(), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)

        gray.release(); thresh.release(); points.release(); rotMatrix.release()
        return dst
    }

    // ---------------------------------------------------------------------
    // Enhancement / filter pipeline
    // ---------------------------------------------------------------------

    fun sharpen(src: Mat, amount: Double = 1.0): Mat {
        val blurred = gaussianBlur(src, 3)
        val dst = Mat()
        Core.addWeighted(src, 1.0 + amount, blurred, -amount, 0.0, dst)
        blurred.release()
        return dst
    }

    /** CLAHE-based auto contrast: works per-channel on the L channel in LAB space. */
    fun autoContrast(src: Mat): Mat {
        val lab = Mat()
        Imgproc.cvtColor(src, lab, Imgproc.COLOR_BGR2Lab)
        val channels = mutableListOf<Mat>()
        Core.split(lab, channels)

        val clahe = Imgproc.createCLAHE(2.5, Size(8.0, 8.0))
        val equalized = Mat()
        clahe.apply(channels[0], equalized)
        equalized.copyTo(channels[0])

        val merged = Mat()
        Core.merge(channels, merged)
        val dst = Mat()
        Imgproc.cvtColor(merged, dst, Imgproc.COLOR_Lab2BGR)

        lab.release(); merged.release(); equalized.release()
        channels.forEach { it.release() }
        return dst
    }

    /** Removes soft shadows via large-kernel background estimation + division. */
    fun removeShadow(src: Mat): Mat {
        val channels = mutableListOf<Mat>()
        Core.split(src, channels)
        val resultChannels = mutableListOf<Mat>()

        for (channel in channels) {
            val dilated = Mat()
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
            Imgproc.dilate(channel, dilated, kernel)

            val bg = Mat()
            Imgproc.medianBlur(dilated, bg, 21)

            val diff = Mat()
            Core.absdiff(channel, bg, diff)
            val inverted = Mat()
            Core.bitwise_not(diff, inverted)

            val normalized = Mat()
            Core.normalize(inverted, normalized, 0.0, 255.0, Core.NORM_MINMAX)
            resultChannels.add(normalized)

            dilated.release(); bg.release(); diff.release(); inverted.release()
        }

        val dst = Mat()
        Core.merge(resultChannels, dst)
        channels.forEach { it.release() }
        resultChannels.forEach { it.release() }
        return dst
    }

    /**
     * Best-effort "no watermark" cleanup for faint, light-gray background marks
     * (stamps, printed watermarks, light stains): stretches the L channel so
     * anything above a lightness threshold is pushed toward pure white while
     * darker ink/text is left alone. This is a levels-style approximation, not
     * true content-aware watermark removal -- it works best on faint marks that
     * are visibly lighter than the actual text.
     */
    fun removeWatermark(src: Mat): Mat {
        val shadowRemoved = removeShadow(src)
        val lab = Mat()
        Imgproc.cvtColor(shadowRemoved, lab, Imgproc.COLOR_BGR2Lab)
        val channels = mutableListOf<Mat>()
        Core.split(lab, channels)

        // Push light pixels (background + faint watermark) toward 255, leave
        // darker pixels (real text/ink) mostly untouched.
        val stretched = Mat()
        Core.normalize(channels[0], stretched, 0.0, 255.0, Core.NORM_MINMAX)
        val cleaned = Mat()
        Imgproc.threshold(stretched, cleaned, 200.0, 255.0, Imgproc.THRESH_TOZERO_INV)
        Core.add(cleaned, Scalar(55.0), cleaned)
        cleaned.copyTo(channels[0])

        val merged = Mat()
        Core.merge(channels, merged)
        val dst = Mat()
        Imgproc.cvtColor(merged, dst, Imgproc.COLOR_Lab2BGR)

        shadowRemoved.release(); lab.release(); stretched.release(); cleaned.release(); merged.release()
        channels.forEach { it.release() }
        return dst
    }

    fun adjustBrightnessContrast(src: Mat, brightness: Double, contrast: Double): Mat {
        val dst = Mat()
        // dst = src * contrast + brightness
        src.convertTo(dst, -1, contrast, brightness)
        return dst
    }

    fun adjustTemperature(src: Mat, warmth: Double): Mat {
        // warmth in [-100, 100]: positive boosts red/yellow, negative boosts blue.
        val channels = mutableListOf<Mat>()
        Core.split(src, channels) // B, G, R
        val b = Mat(); val r = Mat()
        channels[0].convertTo(b, -1, 1.0, -warmth * 0.5)
        channels[2].convertTo(r, -1, 1.0, warmth * 0.5)
        val merged = Mat()
        Core.merge(listOf(b, channels[1], r), merged)
        channels.forEach { it.release() }
        b.release(); r.release()
        return merged
    }

    fun toGrayscaleFilter(src: Mat): Mat {
        val gray = toGray(src)
        val dst = Mat()
        Imgproc.cvtColor(gray, dst, Imgproc.COLOR_GRAY2BGR)
        gray.release()
        return dst
    }

    fun toBlackAndWhite(src: Mat): Mat {
        val bw = adaptiveThreshold(src, blockSize = 21, c = 10.0)
        val dst = Mat()
        Imgproc.cvtColor(bw, dst, Imgproc.COLOR_GRAY2BGR)
        bw.release()
        return dst
    }

    /** "Magic color" — CamScanner-style: whitens background, boosts saturation of ink/content. */
    fun magicColor(src: Mat): Mat {
        val shadowRemoved = removeShadow(src)
        val contrasted = autoContrast(shadowRemoved)
        val hsv = Mat()
        Imgproc.cvtColor(contrasted, hsv, Imgproc.COLOR_BGR2HSV)
        val channels = mutableListOf<Mat>()
        Core.split(hsv, channels)
        channels[1].convertTo(channels[1], -1, 1.3, 0.0) // boost saturation
        val merged = Mat()
        Core.merge(channels, merged)
        val dst = Mat()
        Imgproc.cvtColor(merged, dst, Imgproc.COLOR_HSV2BGR)

        shadowRemoved.release(); contrasted.release(); hsv.release(); merged.release()
        channels.forEach { it.release() }
        return dst
    }

    fun documentFilter(src: Mat): Mat {
        // Balanced "scanned document" look: denoise, whiten background, sharpen text.
        val denoised = bilateralFilter(src, 9, 60.0, 60.0)
        val contrasted = autoContrast(denoised)
        val sharpened = sharpen(contrasted, 0.6)
        denoised.release(); contrasted.release()
        return sharpened
    }

    /** Simple background removal via Otsu mask on the document (assumes light background). */
    fun removeBackground(src: Mat): Mat {
        val gray = toGray(src)
        val mask = Mat()
        Imgproc.threshold(gray, mask, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        val dst = Mat(src.size(), src.type(), Scalar(255.0, 255.0, 255.0))
        src.copyTo(dst, mask)
        gray.release(); mask.release()
        return dst
    }

    fun autoEnhance(src: Mat): Mat {
        val shadowRemoved = removeShadow(src)
        val contrasted = autoContrast(shadowRemoved)
        val denoised = bilateralFilter(contrasted, 7, 50.0, 50.0)
        val sharpened = sharpen(denoised, 0.5)
        shadowRemoved.release(); contrasted.release(); denoised.release()
        return sharpened
    }

    /**
     * Applies a named [type] filter to [src] and returns a new Mat. Caller owns
     * and must release the result (and, ideally, [src] if no longer needed).
     */
    fun applyFilter(src: Mat, type: com.scanner.pro.model.ScanFilterType): Mat = when (type) {
        com.scanner.pro.model.ScanFilterType.ORIGINAL -> src.clone()
        com.scanner.pro.model.ScanFilterType.MAGIC_COLOR -> magicColor(src)
        com.scanner.pro.model.ScanFilterType.AUTO_ENHANCE -> autoEnhance(src)
        com.scanner.pro.model.ScanFilterType.GRAYSCALE -> toGrayscaleFilter(src)
        com.scanner.pro.model.ScanFilterType.BLACK_AND_WHITE -> toBlackAndWhite(src)
        com.scanner.pro.model.ScanFilterType.DOCUMENT -> documentFilter(src)
        com.scanner.pro.model.ScanFilterType.LIGHTEN -> adjustBrightnessContrast(src, 30.0, 1.0)
        com.scanner.pro.model.ScanFilterType.DARKEN -> adjustBrightnessContrast(src, -30.0, 1.0)
        com.scanner.pro.model.ScanFilterType.SHARPEN -> sharpen(src, 1.2)
        com.scanner.pro.model.ScanFilterType.WARM -> adjustTemperature(src, 40.0)
        com.scanner.pro.model.ScanFilterType.COOL -> adjustTemperature(src, -40.0)
        com.scanner.pro.model.ScanFilterType.HIGH_CONTRAST -> adjustBrightnessContrast(src, 0.0, 1.5)
        com.scanner.pro.model.ScanFilterType.SOFT -> gaussianBlur(src, 3)
        com.scanner.pro.model.ScanFilterType.BACKGROUND_REMOVAL -> removeBackground(src)
        com.scanner.pro.model.ScanFilterType.NO_SHADOW -> removeShadow(src)
        com.scanner.pro.model.ScanFilterType.NO_WATERMARK -> removeWatermark(src)
    }

    fun rotate90(src: Mat, clockwise: Boolean = true): Mat {
        val dst = Mat()
        Core.rotate(src, dst, if (clockwise) Core.ROTATE_90_CLOCKWISE else Core.ROTATE_90_COUNTERCLOCKWISE)
        return dst
    }

    fun flipHorizontal(src: Mat): Mat {
        val dst = Mat()
        Core.flip(src, dst, 1)
        return dst
    }
}
