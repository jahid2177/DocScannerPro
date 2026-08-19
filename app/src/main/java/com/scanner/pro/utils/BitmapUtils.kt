package com.scanner.pro.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Bitmap helpers written with 2GB-RAM devices in mind: everything here decodes
 * at a bounded sample size and recycles intermediates aggressively.
 */
object BitmapUtils {

    /**
     * Single source of truth for the pixel space that [com.scanner.pro.model.DocumentCorners]
     * are defined in throughout the scan pipeline (crop editor, capture processing, recrop,
     * filter re-apply). Every decode of an original/captured file that either (a) produces
     * corners for the crop UI or (b) applies previously-produced corners via
     * [com.scanner.pro.opencv.ImageFilterProcessor.perspectiveCorrect] MUST use this same
     * value. Mixing a different maxDimension between the "corners were computed against
     * this decode" step and the "corners are applied to this decode" step silently shifts
     * or clips the crop, since [decodeSampledBitmap] only downsamples in power-of-two steps
     * and different caps can land on different steps for the same source image.
     */
    const val SCAN_MAX_DIMENSION = 2048

    /** Decodes a file to a bitmap downsampled to fit within [maxDimension] on its longest side. */
    fun decodeSampledBitmap(path: String, maxDimension: Int = 2048): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        var sampleSize = 1
        var (w, h) = boundsOptions.outWidth to boundsOptions.outHeight
        while (w / 2 >= maxDimension || h / 2 >= maxDimension) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(path, decodeOptions) ?: return null
        return correctOrientation(bitmap, path)
    }

    /** Reads EXIF orientation and rotates the bitmap so it displays upright, then recycles the original. */
    fun correctOrientation(bitmap: Bitmap, path: String): Bitmap {
        val orientation = try {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    fun createThumbnail(bitmap: Bitmap, maxDimension: Int = 300): Bitmap {
        val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
        if (ratio >= 1f) return bitmap
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /** Saves [bitmap] to [file] as JPEG at [quality] (0-100). Returns success. */
    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92): Boolean = try {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        true
    } catch (e: Exception) {
        false
    }

    fun savePng(bitmap: Bitmap, file: File): Boolean = try {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Approximate free-space check before writing a scan (avoids partial/corrupt files). */
    fun hasEnoughStorage(dir: File, requiredBytes: Long = 20L * 1024 * 1024): Boolean =
        dir.usableSpace > requiredBytes
}
