package com.scanner.pro.opencv

import android.graphics.Bitmap
import com.scanner.pro.model.ScanFilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin coroutine-friendly wrapper so the ViewModel/UI layer never touches
 * OpenCV's Mat type directly — everything in and out is a Bitmap.
 */
object ImageFilterProcessor {

    suspend fun applyFilter(source: Bitmap, filter: ScanFilterType): Bitmap = withContext(Dispatchers.Default) {
        val mat = OpenCVHelper.bitmapToMat(source)
        val filtered = OpenCVHelper.applyFilter(mat, filter)
        val result = OpenCVHelper.matToBitmap(filtered)
        mat.release()
        filtered.release()
        result
    }

    suspend fun applyBrightnessContrast(source: Bitmap, brightness: Float, contrast: Float): Bitmap =
        withContext(Dispatchers.Default) {
            val mat = OpenCVHelper.bitmapToMat(source)
            val adjusted = OpenCVHelper.adjustBrightnessContrast(mat, brightness.toDouble(), contrast.toDouble())
            val result = OpenCVHelper.matToBitmap(adjusted)
            mat.release()
            adjusted.release()
            result
        }

    /** Full pipeline for a fresh capture: perspective-warp to the given corners, then deskew. */
    suspend fun perspectiveCorrect(source: Bitmap, corners: com.scanner.pro.model.DocumentCorners): Bitmap =
        withContext(Dispatchers.Default) {
            val mat = OpenCVHelper.bitmapToMat(source)
            val warped = OpenCVHelper.warpPerspective(mat, corners)
            val deskewed = OpenCVHelper.deskew(warped)
            val result = OpenCVHelper.matToBitmap(deskewed)
            mat.release(); warped.release(); deskewed.release()
            result
        }

    suspend fun rotate90(source: Bitmap, clockwise: Boolean): Bitmap = withContext(Dispatchers.Default) {
        val mat = OpenCVHelper.bitmapToMat(source)
        val rotated = OpenCVHelper.rotate90(mat, clockwise)
        val result = OpenCVHelper.matToBitmap(rotated)
        mat.release(); rotated.release()
        result
    }

    /** Renders low-res thumbnails of every filter for the filter-picker strip. */
    suspend fun generateFilterPreviews(source: Bitmap): Map<ScanFilterType, Bitmap> = withContext(Dispatchers.Default) {
        val thumbnail = com.scanner.pro.utils.BitmapUtils.createThumbnail(source, 160)
        val mat = OpenCVHelper.bitmapToMat(thumbnail)
        val results = mutableMapOf<ScanFilterType, Bitmap>()
        for (type in ScanFilterType.values()) {
            val filtered = OpenCVHelper.applyFilter(mat, type)
            results[type] = OpenCVHelper.matToBitmap(filtered)
            filtered.release()
        }
        mat.release()
        results
    }
}
