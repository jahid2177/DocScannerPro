package com.scanner.pro.pdf

import android.graphics.Bitmap
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize as ItextPageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.EncryptionConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.WriterProperties
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.TextAlignment
import com.scanner.pro.model.OcrResult
import com.scanner.pro.model.PageSize
import com.scanner.pro.model.ScanPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class PdfOptions(
    val pageSize: PageSize = PageSize.A4,
    val quality: Int = 85,          // JPEG re-compression quality, 0-100
    val losslessMode: Boolean = false,
    val password: String? = null,
    val title: String = "Scanned Document",
    val author: String = "DocScanner Pro",
    val makeSearchable: Boolean = true
)

/**
 * Builds a single multi-page PDF from a list of already-filtered/cropped page
 * images. When [PdfOptions.makeSearchable] is true and a page has an OCR
 * result attached, its recognized text is layered on top of the image as
 * invisible (render-mode-3) text positioned at each line's bounding box —
 * the standard "searchable scanned PDF" technique.
 */
class PdfGenerator {

    suspend fun generate(pages: List<ScanPage>, outputFile: File, options: PdfOptions): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val writerProperties = WriterProperties().apply {
                    if (options.password != null) {
                        setStandardEncryption(
                            null,
                            options.password.toByteArray(),
                            EncryptionConstants.ALLOW_PRINTING,
                            EncryptionConstants.ENCRYPTION_AES_128
                        )
                    }
                }

                val writer = PdfWriter(outputFile.absolutePath, writerProperties)
                val pdfDoc = PdfDocument(writer)
                pdfDoc.documentInfo.title = options.title
                pdfDoc.documentInfo.author = options.author

                val itextPageSize = when (options.pageSize) {
                    PageSize.A4 -> ItextPageSize.A4
                    PageSize.LETTER -> ItextPageSize.LETTER
                    PageSize.LEGAL -> ItextPageSize.LEGAL
                }

                val document = Document(pdfDoc, itextPageSize)
                document.setMargins(0f, 0f, 0f, 0f)

                val font = PdfFontFactory.createFont()

                for (page in pages) {
                    val bitmap = com.scanner.pro.utils.BitmapUtils.decodeSampledBitmap(page.processedImagePath, 3000)
                        ?: continue

                    val imageBytes = compressBitmap(bitmap, options)
                    val itextImage = Image(ImageDataFactory.create(imageBytes))

                    // Fit the image to the page while preserving aspect ratio.
                    val pageWidth = itextPageSize.width
                    val pageHeight = itextPageSize.height
                    val scale = minOf(pageWidth / bitmap.width, pageHeight / bitmap.height)
                    itextImage.scaleToFit(bitmap.width * scale, bitmap.height * scale)
                    itextImage.setFixedPosition(
                        pdfDoc.numberOfPages + 1,
                        (pageWidth - bitmap.width * scale) / 2,
                        (pageHeight - bitmap.height * scale) / 2
                    )

                    document.add(itextImage)

                    if (options.makeSearchable && page.ocrResult != null) {
                        addInvisibleTextLayer(
                            document, pdfDoc, page.ocrResult!!, font,
                            imageOriginX = (pageWidth - bitmap.width * scale) / 2,
                            imageOriginY = (pageHeight - bitmap.height * scale) / 2,
                            scale = scale,
                            bitmapHeight = bitmap.height,
                            pageNumber = pdfDoc.numberOfPages
                        )
                    }

                    bitmap.recycle()
                }

                document.close()
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Merges several already-generated PDFs into one output file (used by "combine documents"). */
    suspend fun merge(inputFiles: List<File>, outputFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val merger = com.itextpdf.kernel.utils.PdfMerger(
                PdfDocument(PdfWriter(outputFile.absolutePath))
            )
            for (file in inputFiles) {
                val src = PdfDocument(com.itextpdf.kernel.pdf.PdfReader(file.absolutePath))
                merger.merge(src, 1, src.numberOfPages)
                src.close()
            }
            merger.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compressBitmap(bitmap: Bitmap, options: PdfOptions): ByteArray {
        val stream = ByteArrayOutputStream()
        val format = if (options.losslessMode) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val quality = if (options.losslessMode) 100 else options.quality
        bitmap.compress(format, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Places each OCR line as a transparent (render mode 3 = neither fill nor
     * stroke) text run at its recognized bounding box, scaled/translated into
     * the same coordinate space the image was placed in. The text is invisible
     * but selectable/searchable/copy-pasteable, exactly like Adobe's "OCR text
     * layer" scans.
     */
    private fun addInvisibleTextLayer(
        document: Document,
        pdfDoc: PdfDocument,
        ocrResult: OcrResult,
        font: com.itextpdf.kernel.font.PdfFont,
        imageOriginX: Float,
        imageOriginY: Float,
        scale: Float,
        bitmapHeight: Int,
        pageNumber: Int
    ) {
        val canvas = com.itextpdf.kernel.pdf.canvas.PdfCanvas(pdfDoc.getPage(pageNumber))
        canvas.beginText()
        canvas.setFontAndSize(font, 10f)
        canvas.setTextRenderingMode(com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants.TextRenderingMode.INVISIBLE)

        for (block in ocrResult.blocks) {
            for (line in block.lines) {
                val box = line.boundingBox
                // Flip Y: bitmap coords have origin top-left, PDF coords origin bottom-left.
                val pdfX = imageOriginX + box.left * scale
                val pdfY = imageOriginY + (bitmapHeight - box.bottom) * scale
                val fontSize = ((box.bottom - box.top) * scale).coerceAtLeast(4f)

                canvas.setFontAndSize(font, fontSize)
                canvas.moveText(pdfX.toDouble(), pdfY.toDouble())
                canvas.showText(line.text)
                canvas.moveText(-pdfX.toDouble(), -pdfY.toDouble())
            }
        }

        canvas.endText()
    }
}
