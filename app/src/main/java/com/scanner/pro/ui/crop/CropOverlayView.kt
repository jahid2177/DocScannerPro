package com.scanner.pro.ui.crop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.scanner.pro.model.DocumentCorners
import kotlin.math.max
import kotlin.math.min

/**
 * Renders the four crop handles over a captured page image and lets the user
 * drag each corner independently. Corners snap to the image bounds when
 * dragged near an edge so it's easy to select "the whole page" precisely.
 *
 * Coordinates are tracked in *view* space; [getCorrectedCorners] converts back
 * to image-pixel space for the OpenCV perspective warp.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var imageWidth = 0
    private var imageHeight = 0
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private val handles = mutableListOf<CornerHandle>()
    private var draggingHandle: CornerHandle? = null
    private val snapThresholdPx = 40f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2979FF")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#332979FF")
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2979FF")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    /** Call once the bitmap dimensions and the corners (image-pixel space) are known. */
    fun setup(imageWidth: Int, imageHeight: Int, corners: DocumentCorners) {
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        post {
            computeTransform()
            handles.clear()
            val tl = toViewCoords(corners.topLeft)
            val tr = toViewCoords(corners.topRight)
            val br = toViewCoords(corners.bottomRight)
            val bl = toViewCoords(corners.bottomLeft)
            handles += CornerHandle(CornerPosition.TOP_LEFT, tl.x, tl.y)
            handles += CornerHandle(CornerPosition.TOP_RIGHT, tr.x, tr.y)
            handles += CornerHandle(CornerPosition.BOTTOM_RIGHT, br.x, br.y)
            handles += CornerHandle(CornerPosition.BOTTOM_LEFT, bl.x, bl.y)
            invalidate()
        }
    }

    private fun computeTransform() {
        if (imageWidth == 0 || imageHeight == 0 || width == 0 || height == 0) return
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        scale = min(scaleX, scaleY)
        offsetX = (width - imageWidth * scale) / 2f
        offsetY = (height - imageHeight * scale) / 2f
    }

    private fun toViewCoords(p: PointF): PointF =
        PointF(p.x * scale + offsetX, p.y * scale + offsetY)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeTransform()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (handles.size != 4) return

        val tl = handles.first { it.position == CornerPosition.TOP_LEFT }
        val tr = handles.first { it.position == CornerPosition.TOP_RIGHT }
        val br = handles.first { it.position == CornerPosition.BOTTOM_RIGHT }
        val bl = handles.first { it.position == CornerPosition.BOTTOM_LEFT }

        val path = Path().apply {
            moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, linePaint)

        for (h in handles) {
            canvas.drawCircle(h.x, h.y, 22f, handlePaint)
            canvas.drawCircle(h.x, h.y, 22f, handleBorderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingHandle = handles.firstOrNull { it.isTouched(event.x, event.y) }
                return draggingHandle != null
            }
            MotionEvent.ACTION_MOVE -> {
                val handle = draggingHandle ?: return false
                handle.x = snapX(event.x.coerceIn(offsetX, offsetX + imageWidth * scale))
                handle.y = snapY(event.y.coerceIn(offsetY, offsetY + imageHeight * scale))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingHandle = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapX(x: Float): Float {
        val left = offsetX
        val right = offsetX + imageWidth * scale
        return when {
            x - left < snapThresholdPx -> left
            right - x < snapThresholdPx -> right
            else -> x
        }
    }

    private fun snapY(y: Float): Float {
        val top = offsetY
        val bottom = offsetY + imageHeight * scale
        return when {
            y - top < snapThresholdPx -> top
            bottom - y < snapThresholdPx -> bottom
            else -> y
        }
    }

    /** Resets all four handles to the image's full extent (used by "reset crop"). */
    fun resetToFullImage() {
        if (imageWidth == 0 || imageHeight == 0) return
        setup(imageWidth, imageHeight, DocumentCorners.defaultForSize(imageWidth, imageHeight).let {
            DocumentCorners(
                topLeft = PointF(0f, 0f),
                topRight = PointF(imageWidth.toFloat(), 0f),
                bottomRight = PointF(imageWidth.toFloat(), imageHeight.toFloat()),
                bottomLeft = PointF(0f, imageHeight.toFloat())
            )
        })
    }

    /** Converts the current handle positions (view space) back to image-pixel space. */
    fun getCorrectedCorners(): DocumentCorners {
        fun toImage(h: CornerHandle) = PointF((h.x - offsetX) / scale, (h.y - offsetY) / scale)
        val tl = handles.first { it.position == CornerPosition.TOP_LEFT }
        val tr = handles.first { it.position == CornerPosition.TOP_RIGHT }
        val br = handles.first { it.position == CornerPosition.BOTTOM_RIGHT }
        val bl = handles.first { it.position == CornerPosition.BOTTOM_LEFT }
        return DocumentCorners(toImage(tl), toImage(tr), toImage(br), toImage(bl))
    }
}
