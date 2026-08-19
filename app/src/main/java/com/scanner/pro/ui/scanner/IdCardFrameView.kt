package com.scanner.pro.ui.scanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Static "align the card here" guide frame for ID Cards mode: four white
 * L-shaped corner brackets with a soft glow, like a typical ID/passport
 * scanning viewfinder in a premium scanner app.
 *
 * Unlike [DetectionOverlayView], this does NOT track live edge detection --
 * its brackets are fixed to the view's own bounds. ScannerFragment reads
 * this view's on-screen position (relative to the camera preview) and maps
 * it onto the captured photo to decide what to crop.
 */
class IdCardFrameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    // Faint full-outline hint so the frame reads clearly even between glow pulses.
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(70, 255, 255, 255)
    }

    private val armLength = 44f

    // Slow ambient glow pulse so the guide feels alive rather than a static overlay.
    private var glowAlpha = 90
    private val pulseAnimator = ValueAnimator.ofInt(60, 150).apply {
        duration = 1100
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            glowAlpha = it.animatedValue as Int
            invalidate()
        }
    }

    init {
        // Required for Paint.setShadowLayer() (the bracket glow) to render.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        pulseAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val a = armLength.coerceAtMost(minOf(w, h) / 3f)

        bracketPaint.setShadowLayer(14f, 0f, 0f, Color.argb(glowAlpha, 41, 121, 255))

        // Faint rounded outline of the whole card area.
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, 18f, 18f, outlinePaint)

        // Top-left corner
        canvas.drawPath(Path().apply {
            moveTo(0f, a); lineTo(0f, 0f); lineTo(a, 0f)
        }, bracketPaint)
        // Top-right corner
        canvas.drawPath(Path().apply {
            moveTo(w - a, 0f); lineTo(w, 0f); lineTo(w, a)
        }, bracketPaint)
        // Bottom-right corner
        canvas.drawPath(Path().apply {
            moveTo(w, h - a); lineTo(w, h); lineTo(w - a, h)
        }, bracketPaint)
        // Bottom-left corner
        canvas.drawPath(Path().apply {
            moveTo(a, h); lineTo(0f, h); lineTo(0f, h - a)
        }, bracketPaint)
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }
}
