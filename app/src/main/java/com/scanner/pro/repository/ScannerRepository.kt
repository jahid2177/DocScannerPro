package com.scanner.pro.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.scanner.pro.model.*
import com.scanner.pro.ocr.OCRHelper
import com.scanner.pro.opencv.ImageFilterProcessor
import com.scanner.pro.pdf.PdfGenerator
import com.scanner.pro.pdf.PdfOptions
import com.scanner.pro.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single entry point the ViewModel talks to. Wraps FileManager (persistence),
 * OCRHelper, and PdfGenerator, and owns the "what happens after a page is
 * captured" pipeline: perspective-correct -> apply default filter -> save
 * originals/processed/thumbnail -> persist metadata.
 */
class ScannerRepository(private val context: Context) {

    val fileManager = FileManager(context)
    private val ocrHelper = OCRHelper(context)
    private val pdfGenerator = PdfGenerator()

    suspend fun createDocument(name: String): ScanDocument {
        val doc = ScanDocument(name = name)
        fileManager.saveDocument(doc)
        return doc
    }

    /**
     * Takes a freshly captured full-resolution photo, applies perspective
     * correction using the detected/edited [corners], applies [defaultFilter],
     * and writes original + processed + thumbnail files into the document's
     * folder. Returns the new ScanPage, already appended to [document].
     */
    suspend fun addPageFromCapture(
        document: ScanDocument,
        capturedFile: File,
        corners: DocumentCorners,
        defaultFilter: ScanFilterType = ScanFilterType.DOCUMENT
    ): Result<ScanPage> = withContext(Dispatchers.Default) {
        try {
            val docDir = fileManager.documentDirectory(document.id)
            if (!BitmapUtils.hasEnoughStorage(docDir)) {
                return@withContext Result.failure(StorageFullException())
            }

            val original = BitmapUtils.decodeSampledBitmap(capturedFile.absolutePath, BitmapUtils.SCAN_MAX_DIMENSION)
                ?: return@withContext Result.failure(IllegalStateException("Could not decode captured image"))

            val corrected = ImageFilterProcessor.perspectiveCorrect(original, corners)
            val filtered = ImageFilterProcessor.applyFilter(corrected, defaultFilter)
            val thumbnail = BitmapUtils.createThumbnail(filtered, 300)

            val pageId = java.util.UUID.randomUUID().toString()
            val originalPath = File(docDir, "${pageId}_original.jpg")
            val processedPath = File(docDir, "${pageId}_processed.jpg")
            val thumbnailPath = File(docDir, "${pageId}_thumb.jpg")

            BitmapUtils.saveJpeg(original, originalPath, 95)
            BitmapUtils.saveJpeg(filtered, processedPath, 92)
            BitmapUtils.saveJpeg(thumbnail, thumbnailPath, 80)

            val page = ScanPage(
                id = pageId,
                originalImagePath = originalPath.absolutePath,
                processedImagePath = processedPath.absolutePath,
                thumbnailPath = thumbnailPath.absolutePath,
                corners = corners,
                filterType = defaultFilter
            )

            document.pages.add(page)
            fileManager.saveDocument(document)

            original.recycle(); corrected.recycle(); filtered.recycle(); thumbnail.recycle()
            capturedFile.delete() // raw camera temp file no longer needed

            Result.success(page)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * ID Cards mode: takes two separate captures (front + back of a card),
     * perspective-corrects and filters each independently, then stacks them
     * vertically onto a single page instead of creating two document pages.
     */
    suspend fun addIdCardPage(
        document: ScanDocument,
        frontFile: File,
        frontCorners: DocumentCorners,
        backFile: File,
        backCorners: DocumentCorners,
        defaultFilter: ScanFilterType = ScanFilterType.DOCUMENT
    ): Result<ScanPage> = withContext(Dispatchers.Default) {
        try {
            val docDir = fileManager.documentDirectory(document.id)
            if (!BitmapUtils.hasEnoughStorage(docDir)) {
                return@withContext Result.failure(StorageFullException())
            }

            val frontOriginal = BitmapUtils.decodeSampledBitmap(frontFile.absolutePath, 3000)
                ?: return@withContext Result.failure(IllegalStateException("Could not decode front image"))
            val backOriginal = BitmapUtils.decodeSampledBitmap(backFile.absolutePath, 3000)
                ?: return@withContext Result.failure(IllegalStateException("Could not decode back image"))

            val frontCorrected = ImageFilterProcessor.perspectiveCorrect(frontOriginal, frontCorners)
            val backCorrected = ImageFilterProcessor.perspectiveCorrect(backOriginal, backCorners)

            val frontFiltered = ImageFilterProcessor.applyFilter(frontCorrected, defaultFilter)
            val backFiltered = ImageFilterProcessor.applyFilter(backCorrected, defaultFilter)

            val combined = composeIdCardPage(frontFiltered, backFiltered)
            val thumbnail = BitmapUtils.createThumbnail(combined, 300)

            val pageId = java.util.UUID.randomUUID().toString()
            val originalPath = File(docDir, "${pageId}_original.jpg")
            val processedPath = File(docDir, "${pageId}_processed.jpg")
            val thumbnailPath = File(docDir, "${pageId}_thumb.jpg")

            BitmapUtils.saveJpeg(combined, originalPath, 95)
            BitmapUtils.saveJpeg(combined, processedPath, 92)
            BitmapUtils.saveJpeg(thumbnail, thumbnailPath, 80)

            val page = ScanPage(
                id = pageId,
                originalImagePath = originalPath.absolutePath,
                processedImagePath = processedPath.absolutePath,
                thumbnailPath = thumbnailPath.absolutePath,
                corners = null,
                filterType = defaultFilter
            )

            document.pages.add(page)
            fileManager.saveDocument(document)

            frontOriginal.recycle(); backOriginal.recycle()
            frontCorrected.recycle(); backCorrected.recycle()
            frontFiltered.recycle(); backFiltered.recycle()
            combined.recycle(); thumbnail.recycle()
            frontFile.delete(); backFile.delete()

            Result.success(page)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lays the front and back card images out on a spacious white page --
     * generous side/top/bottom margins and a clear gap between the two cards
     * -- rather than tightly stacking them edge to edge.
     */
    private fun composeIdCardPage(front: Bitmap, back: Bitmap): Bitmap {
        val cardWidth = maxOf(front.width, back.width)
        val cardHeight = maxOf(
            front.height * cardWidth / front.width,
            back.height * cardWidth / back.width
        )

        val sideMargin = (cardWidth * 0.28f).toInt()
        val topMargin = (cardHeight * 0.42f).toInt()
        val middleGap = (cardHeight * 0.30f).toInt()
        val bottomMargin = (cardHeight * 0.42f).toInt()

        val pageWidth = cardWidth + sideMargin * 2
        val pageHeight = topMargin + cardHeight + middleGap + cardHeight + bottomMargin

        val page = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val frontScaled = Bitmap.createScaledBitmap(front, cardWidth, front.height * cardWidth / front.width, true)
        val backScaled = Bitmap.createScaledBitmap(back, cardWidth, back.height * cardWidth / back.width, true)

        val frontLeft = (pageWidth - frontScaled.width) / 2f
        val frontTop = topMargin.toFloat()
        canvas.drawBitmap(frontScaled, frontLeft, frontTop, null)

        val backLeft = (pageWidth - backScaled.width) / 2f
        val backTop = (topMargin + cardHeight + middleGap).toFloat()
        canvas.drawBitmap(backScaled, backLeft, backTop, null)

        if (frontScaled !== front) frontScaled.recycle()
        if (backScaled !== back) backScaled.recycle()

        return page
    }

    suspend fun reapplyFilter(document: ScanDocument, page: ScanPage, filter: ScanFilterType): Result<ScanPage> =
        withContext(Dispatchers.Default) {
            try {
                val original = BitmapUtils.decodeSampledBitmap(page.originalImagePath, BitmapUtils.SCAN_MAX_DIMENSION)
                    ?: return@withContext Result.failure(IllegalStateException("Missing original image"))
                val corrected = page.corners?.let { ImageFilterProcessor.perspectiveCorrect(original, it) } ?: original
                val filtered = ImageFilterProcessor.applyFilter(corrected, filter)

                BitmapUtils.saveJpeg(filtered, File(page.processedImagePath), 92)
                val thumbnail = BitmapUtils.createThumbnail(filtered, 300)
                BitmapUtils.saveJpeg(thumbnail, File(page.thumbnailPath), 80)

                page.filterType = filter
                fileManager.saveDocument(document)

                original.recycle()
                if (corrected !== original) corrected.recycle()
                filtered.recycle(); thumbnail.recycle()

                Result.success(page)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun recropPage(document: ScanDocument, page: ScanPage, newCorners: DocumentCorners): Result<ScanPage> =
        withContext(Dispatchers.Default) {
            try {
                val original = BitmapUtils.decodeSampledBitmap(page.originalImagePath, BitmapUtils.SCAN_MAX_DIMENSION)
                    ?: return@withContext Result.failure(IllegalStateException("Missing original image"))
                val corrected = ImageFilterProcessor.perspectiveCorrect(original, newCorners)
                val filtered = ImageFilterProcessor.applyFilter(corrected, page.filterType)

                BitmapUtils.saveJpeg(filtered, File(page.processedImagePath), 92)
                val thumbnail = BitmapUtils.createThumbnail(filtered, 300)
                BitmapUtils.saveJpeg(thumbnail, File(page.thumbnailPath), 80)

                page.corners = newCorners
                fileManager.saveDocument(document)

                original.recycle(); corrected.recycle(); filtered.recycle(); thumbnail.recycle()
                Result.success(page)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun rotatePage(document: ScanDocument, page: ScanPage, clockwise: Boolean): Result<ScanPage> =
        withContext(Dispatchers.Default) {
            try {
                val processed = BitmapUtils.decodeSampledBitmap(page.processedImagePath, 3000)
                    ?: return@withContext Result.failure(IllegalStateException("Missing processed image"))
                val rotated = ImageFilterProcessor.rotate90(processed, clockwise)
                BitmapUtils.saveJpeg(rotated, File(page.processedImagePath), 92)
                val thumbnail = BitmapUtils.createThumbnail(rotated, 300)
                BitmapUtils.saveJpeg(thumbnail, File(page.thumbnailPath), 80)

                page.rotationDegrees = (page.rotationDegrees + if (clockwise) 90 else -90) % 360
                fileManager.saveDocument(document)

                processed.recycle(); rotated.recycle(); thumbnail.recycle()
                Result.success(page)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun applySignature(document: ScanDocument, page: ScanPage, signature: Bitmap): Result<ScanPage> =
        withContext(Dispatchers.Default) {
            try {
                val processed = BitmapUtils.decodeSampledBitmap(page.processedImagePath, 3000)
                    ?: return@withContext Result.failure(IllegalStateException("Missing processed image"))

                val targetWidth = (processed.width * 0.32f).toInt().coerceAtLeast(1)
                val targetHeight = (signature.height.toFloat() * targetWidth / signature.width).toInt().coerceAtLeast(1)
                val scaledSignature = Bitmap.createScaledBitmap(signature, targetWidth, targetHeight, true)

                val margin = (processed.width * 0.04f).toInt()
                val result = processed.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(result)
                val left = (result.width - scaledSignature.width - margin).toFloat()
                val top = (result.height - scaledSignature.height - margin).toFloat()
                canvas.drawBitmap(scaledSignature, left, top, null)

                BitmapUtils.saveJpeg(result, File(page.processedImagePath), 92)
                val thumbnail = BitmapUtils.createThumbnail(result, 300)
                BitmapUtils.saveJpeg(thumbnail, File(page.thumbnailPath), 80)
                fileManager.saveDocument(document)

                processed.recycle(); scaledSignature.recycle(); result.recycle(); thumbnail.recycle()
                Result.success(page)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun deletePage(document: ScanDocument, pageId: String) {
        document.pages.removeAll { it.id == pageId }
    }

    fun reorderPages(document: ScanDocument, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val page = document.pages.removeAt(fromIndex)
        document.pages.add(toIndex, page)
    }

    suspend fun duplicatePage(document: ScanDocument, page: ScanPage): ScanPage = withContext(Dispatchers.IO) {
        val docDir = fileManager.documentDirectory(document.id)
        val newId = java.util.UUID.randomUUID().toString()
        val newOriginal = File(docDir, "${newId}_original.jpg").also { File(page.originalImagePath).copyTo(it) }
        val newProcessed = File(docDir, "${newId}_processed.jpg").also { File(page.processedImagePath).copyTo(it) }
        val newThumb = File(docDir, "${newId}_thumb.jpg").also { File(page.thumbnailPath).copyTo(it) }

        val copy = page.copy(
            id = newId,
            originalImagePath = newOriginal.absolutePath,
            processedImagePath = newProcessed.absolutePath,
            thumbnailPath = newThumb.absolutePath
        )
        val insertAt = document.pages.indexOf(page) + 1
        document.pages.add(insertAt.coerceIn(0, document.pages.size), copy)
        copy
    }

    suspend fun runOcr(page: ScanPage, language: OcrLanguage): Result<OcrResult> = withContext(Dispatchers.Default) {
        try {
            val bitmap = BitmapUtils.decodeSampledBitmap(page.processedImagePath, 2500)
                ?: return@withContext Result.failure(IllegalStateException("Missing processed image"))
            val result = ocrHelper.recognize(bitmap, language)
            page.ocrResult = result
            page.isOcrProcessed = true
            bitmap.recycle()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportPdf(document: ScanDocument, options: PdfOptions, outputFile: File): Result<File> =
        pdfGenerator.generate(document.pages, outputFile, options)

    suspend fun exportImage(page: ScanPage, outputFile: File, asPng: Boolean): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val bitmap = BitmapUtils.decodeSampledBitmap(page.processedImagePath, 3000)
                    ?: return@withContext Result.failure(IllegalStateException("Missing processed image"))
                val ok = if (asPng) BitmapUtils.savePng(bitmap, outputFile) else BitmapUtils.saveJpeg(bitmap, outputFile, 95)
                bitmap.recycle()
                if (ok) Result.success(outputFile) else Result.failure(IllegalStateException("Failed to write file"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

class StorageFullException : Exception("Not enough storage space to save the scan")
