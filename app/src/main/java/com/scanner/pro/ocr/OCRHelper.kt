package com.scanner.pro.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.scanner.pro.model.BoundingBox
import com.scanner.pro.model.OcrBlock
import com.scanner.pro.model.OcrLanguage
import com.scanner.pro.model.OcrLine
import com.scanner.pro.model.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Runs OCR on a page bitmap. ML Kit (fully offline once its model is bundled)
 * is tried first for every language it supports; languages ML Kit can't read
 * well (Bengali, Arabic — no dedicated ML Kit script model) fall back to
 * Tesseract with the matching trained-data file.
 *
 * ML Kit languages that DO get a real, dedicated script recognizer: Latin
 * (English/French/Spanish/German), Chinese, Japanese, Korean, Devanagari (Hindi).
 * Everything else (Bengali, Arabic) always routes to Tesseract.
 */
class OCRHelper(private val context: Context) {

    private val tessDataDir: File by lazy {
        File(context.filesDir, "tessdata").apply { mkdirs() }
    }

    /** Languages ML Kit cannot recognize at all and must always go through Tesseract. */
    private val mlKitUnsupported = setOf(OcrLanguage.BENGALI, OcrLanguage.ARABIC)

    suspend fun recognize(bitmap: Bitmap, language: OcrLanguage): OcrResult = withContext(Dispatchers.Default) {
        if (language in mlKitUnsupported) {
            return@withContext runTesseract(bitmap, language)
        }
        try {
            runMlKit(bitmap, language)
        } catch (e: Exception) {
            // ML Kit model missing/download failed on-device -> fall back gracefully.
            runTesseract(bitmap, language)
        }
    }

    // ---------------------------------------------------------------------
    // ML Kit
    // ---------------------------------------------------------------------

    private suspend fun runMlKit(bitmap: Bitmap, language: OcrLanguage): OcrResult {
        val options: TextRecognizerOptionsInterface = when (language) {
            OcrLanguage.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
            OcrLanguage.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
            OcrLanguage.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
            OcrLanguage.HINDI -> DevanagariTextRecognizerOptions.Builder().build()
            else -> TextRecognizerOptions.DEFAULT_OPTIONS
        }
        val recognizer = TextRecognition.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)

        val visionText: Text = suspendCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        val blocks = visionText.textBlocks.map { block ->
            val lines = block.lines.map { line ->
                OcrLine(
                    text = line.text,
                    // ML Kit doesn't expose per-line confidence on all versions; elements do.
                    confidence = line.elements.mapNotNull { it.confidence }.average().let {
                        if (it.isNaN()) 0.85f else it.toFloat()
                    },
                    boundingBox = line.boundingBox?.toBoundingBox() ?: BoundingBox(0f, 0f, 0f, 0f)
                )
            }
            OcrBlock(
                text = block.text,
                lines = lines,
                confidence = lines.map { it.confidence }.averageOrDefault(0.85f),
                boundingBox = block.boundingBox?.toBoundingBox() ?: BoundingBox(0f, 0f, 0f, 0f)
            )
        }

        recognizer.close()

        return OcrResult(
            fullText = visionText.text,
            blocks = blocks,
            detectedLanguage = language,
            averageConfidence = blocks.map { it.confidence }.averageOrDefault(0.85f),
            engineUsed = "MLKit"
        )
    }

    // ---------------------------------------------------------------------
    // Tesseract fallback
    // ---------------------------------------------------------------------

    /**
     * Ensures the .traineddata file for [language] exists under filesDir/tessdata.
     * Trained-data files ship as raw assets (see INTEGRATION_GUIDE.md for where to
     * source them) and are copied once on first use.
     */
    private fun ensureTrainedDataAvailable(language: OcrLanguage) {
        val target = File(tessDataDir, "${language.tesseractCode}.traineddata")
        if (target.exists()) return
        val assetPath = "tessdata/${language.tesseractCode}.traineddata"
        context.assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun runTesseract(bitmap: Bitmap, language: OcrLanguage): OcrResult {
        ensureTrainedDataAvailable(language)
        val api = TessBaseAPI()
        val initialized = api.init(context.filesDir.absolutePath, language.tesseractCode)
        if (!initialized) {
            api.end()
            throw IllegalStateException("Tesseract failed to initialize for ${language.tesseractCode}")
        }

        api.setImage(bitmap)
        val fullText = api.utF8Text ?: ""
        val meanConfidence = (api.meanConfidence().toFloat() / 100f).coerceIn(0f, 1f)

        // Tesseract4Android's per-word box API varies by release; parsing hOCR
        // output is the stable, documented way to recover line-level boxes.
        val lines = parseHocrLines(api.getHOCRText(0), meanConfidence)

        val block = OcrBlock(
            text = fullText,
            lines = lines.ifEmpty {
                listOf(OcrLine(fullText, meanConfidence, BoundingBox(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())))
            },
            confidence = meanConfidence,
            boundingBox = BoundingBox(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        )

        api.end()

        return OcrResult(
            fullText = fullText,
            blocks = listOf(block),
            detectedLanguage = language,
            averageConfidence = meanConfidence,
            engineUsed = "Tesseract"
        )
    }

    /** Extracts `ocr_line` spans and their `bbox x0 y0 x1 y1` title attribute from Tesseract's hOCR output. */
    private fun parseHocrLines(hocr: String?, confidence: Float): List<OcrLine> {
        if (hocr.isNullOrBlank()) return emptyList()
        val lineRegex = Regex(
            """class=['"]ocr_line['"][^>]*title=['"][^'"]*bbox (\d+) (\d+) (\d+) (\d+)[^>]*>(.*?)</span>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val tagStripRegex = Regex("<[^>]+>")

        return lineRegex.findAll(hocr).mapNotNull { match ->
            val (x0, y0, x1, y1, rawContent) = match.destructured
            val text = tagStripRegex.replace(rawContent, " ").replace(Regex("\\s+"), " ").trim()
            if (text.isEmpty()) return@mapNotNull null
            OcrLine(
                text = text,
                confidence = confidence,
                boundingBox = BoundingBox(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat())
            )
        }.toList()
    }

    private fun android.graphics.Rect.toBoundingBox() =
        BoundingBox(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

    private fun List<Float>.averageOrDefault(default: Float) =
        if (isEmpty()) default else average().toFloat()
}
