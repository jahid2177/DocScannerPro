package com.scanner.pro.ui.signature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Minimal finger-drawn signature pad: black ink on a transparent surface so the
 * captured bitmap can be composited straight onto a scanned page.
 */
class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paths = mutableListOf<Path>()
    private var currentPath: Path? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    fun isEmpty(): Boolean = paths.isEmpty()

    fun clear() {
        paths.clear()
        currentPath = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val path = Path().apply { moveTo(event.x, event.y) }
                currentPath = path
                paths.add(path)
            }
            MotionEvent.ACTION_MOVE -> currentPath?.lineTo(event.x, event.y)
            MotionEvent.ACTION_UP -> currentPath = null
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (path in paths) canvas.drawPath(path, paint)
    }

    /** Renders the strokes into a tightly-cropped transparent bitmap, or null if empty. */
    fun exportBitmap(): Bitmap? {
        if (isEmpty() || width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (path in paths) canvas.drawPath(path, paint)
        return bitmap
    }
}
