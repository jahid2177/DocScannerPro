package com.scanner.pro.model

import android.graphics.PointF
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * The four corners of a detected document, in image (pixel) coordinate space,
 * ordered top-left -> top-right -> bottom-right -> bottom-left.
 */
@Parcelize
data class DocumentCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) : Parcelable {
    fun toList(): List<PointF> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        /** Sensible default covering ~85% of the frame, used when no contour is found. */
        fun defaultForSize(width: Int, height: Int): DocumentCorners {
            val marginX = width * 0.075f
            val marginY = height * 0.075f
            return DocumentCorners(
                topLeft = PointF(marginX, marginY),
                topRight = PointF(width - marginX, marginY),
                bottomRight = PointF(width - marginX, height - marginY),
                bottomLeft = PointF(marginX, height - marginY)
            )
        }
    }
}

enum class ScanFilterType {
    ORIGINAL,
    MAGIC_COLOR,
    AUTO_ENHANCE,
    GRAYSCALE,
    BLACK_AND_WHITE,
    DOCUMENT,
    LIGHTEN,
    DARKEN,
    SHARPEN,
    WARM,
    COOL,
    HIGH_CONTRAST,
    SOFT,
    BACKGROUND_REMOVAL,
    NO_SHADOW,
    NO_WATERMARK
}

enum class PageSize { A4, LETTER, LEGAL }

enum class OcrLanguage(val mlKitScript: String, val tesseractCode: String, val displayName: String) {
    ENGLISH("latin", "eng", "English"),
    BENGALI("devanagari", "ben", "বাংলা"),
    HINDI("devanagari", "hin", "हिन्दी"),
    ARABIC("latin", "ara", "العربية"),
    FRENCH("latin", "fra", "Français"),
    SPANISH("latin", "spa", "Español"),
    GERMAN("latin", "deu", "Deutsch"),
    CHINESE("chinese", "chi_sim", "中文"),
    JAPANESE("japanese", "jpn", "日本語"),
    KOREAN("korean", "kor", "한국어")
}

/**
 * A single scanned page: original capture, corners used for perspective correction,
 * the currently applied filter, and the resulting processed image path.
 */
@Parcelize
data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val originalImagePath: String,
    var processedImagePath: String,
    var thumbnailPath: String,
    var corners: DocumentCorners?,
    var rotationDegrees: Int = 0,
    var filterType: ScanFilterType = ScanFilterType.DOCUMENT,
    var brightness: Float = 0f,
    var contrast: Float = 1f,
    var isOcrProcessed: Boolean = false,
    var ocrResult: OcrResult? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable

/**
 * A full multi-page document/scan session.
 */
@Parcelize
data class ScanDocument(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val pages: MutableList<ScanPage> = mutableListOf(),
    var folderId: String? = null,
    var isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) : Parcelable

@Parcelize
data class BoundingBox(val left: Float, val top: Float, val right: Float, val bottom: Float) : Parcelable

@Parcelize
data class OcrLine(val text: String, val confidence: Float, val boundingBox: BoundingBox) : Parcelable

@Parcelize
data class OcrBlock(
    val text: String,
    val lines: List<OcrLine>,
    val confidence: Float,
    val boundingBox: BoundingBox
) : Parcelable

/**
 * Structured OCR output: full text plus a block/line hierarchy with confidence + boxes,
 * so callers can render highlighted overlays or export searchable PDFs.
 */
@Parcelize
data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock>,
    val detectedLanguage: OcrLanguage,
    val averageConfidence: Float,
    val engineUsed: String // "MLKit" or "Tesseract"
) : Parcelable

sealed class ScanError(val message: String) {
    data object PermissionDenied : ScanError("Camera permission was denied")
    data object CameraUnavailable : ScanError("Camera is unavailable on this device")
    data object StorageFull : ScanError("Not enough storage space to save the scan")
    data class OcrFailed(val reason: String) : ScanError("OCR processing failed: $reason")
    data class PdfFailed(val reason: String) : ScanError("PDF generation failed: $reason")
    data class OpenCvFailed(val reason: String) : ScanError("Image processing failed: $reason")
    data class Unknown(val reason: String) : ScanError(reason)
}

sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val error: ScanError) : Resource<Nothing>()
}
