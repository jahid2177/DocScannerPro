package com.scanner.pro.ui.scanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.animation.doOnEnd
import com.scanner.pro.model.DocumentCorners

/**
 * Draws the live "found a document" quadrilateral over the CameraX preview.
 *
 * Premium touches (to read like a paid scanner app rather than a debug overlay):
 *  - Corner positions glide between detections instead of snapping frame to frame
 *    (a short morph animation runs every time new corners arrive).
 *  - Color/glow shifts from amber (still searching) to blue (stable, ready).
 *  - A soft glow on the outline plus ring-style corner handles that gently "breathe"
 *    once the frame is stable, signalling that auto-capture is about to fire.
 */
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** The corners actually being drawn right now (interpolated toward [targetCorners]). */
    private var displayedCorners: DocumentCorners? = null
    private var targetCorners: DocumentCorners? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var isStable = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val cornerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val cornerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Ambient pulse used for the fill alpha at all times.
    private var pulseAlpha = 120
    private val pulseAnimator = ValueAnimator.ofInt(90, 190).apply {
        duration = 900
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulseAlpha = it.animatedValue as Int
            invalidate()
        }
    }

    // Corner-to-corner glide animation, restarted every time a fresh detection arrives.
    private var morphAnimator: ValueAnimator? = null

    // Extra "breathing" scale applied to the corner handles once stable & locked.
    private var stableScale = 1f
    private val stableScaleAnimator = ValueAnimator.ofFloat(1f, 1.35f).apply {
        duration = 550
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            stableScale = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        // A software layer is required for Paint.setShadowLayer() (the outline glow)
        // to render correctly on every device.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        pulseAnimator.start()
    }

    fun update(corners: DocumentCorners?, sourceWidth: Int, sourceHeight: Int, isStable: Boolean) {
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight

        if (isStable != this.isStable) {
            this.isStable = isStable
            if (isStable) {
                if (!stableScaleAnimator.isRunning) stableScaleAnimator.start()
            } else {
                stableScaleAnimator.cancel()
                stableScale = 1f
            }
        }

        if (corners == null) {
            morphAnimator?.cancel()
            displayedCorners = null
            targetCorners = null
            invalidate()
            return
        }

        val from = displayedCorners
        targetCorners = corners
        if (from == null) {
            // First detection: snap straight in, nothing to glide from yet.
            displayedCorners = corners
            invalidate()
            return
        }

        morphAnimator?.cancel()
        morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 110
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                displayedCorners = lerpCorners(from, corners, t)
                invalidate()
            }
            doOnEnd { displayedCorners = corners }
            start()
        }
    }

    private fun lerpCorners(a: DocumentCorners, b: DocumentCorners, t: Float): DocumentCorners = DocumentCorners(
        topLeft = lerpPoint(a.topLeft, b.topLeft, t),
        topRight = lerpPoint(a.topRight, b.topRight, t),
        bottomRight = lerpPoint(a.bottomRight, b.bottomRight, t),
        bottomLeft = lerpPoint(a.bottomLeft, b.bottomLeft, t)
    )

    private fun lerpPoint(a: PointF, b: PointF, t: Float) =
        PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = displayedCorners ?: return
        if (sourceWidth == 0 || sourceHeight == 0) return

        val scaleX = width.toFloat() / sourceWidth
        val scaleY = height.toFloat() / sourceHeight

        val color = if (isStable) STABLE_COLOR else SEARCHING_COLOR
        val glowColor = if (isStable) STABLE_GLOW else SEARCHING_GLOW

        strokePaint.color = color
        strokePaint.alpha = 255
        strokePaint.setShadowLayer(18f, 0f, 0f, glowColor)

        fillPaint.color = color
        fillPaint.alpha = pulseAlpha / 4

        cornerRingPaint.color = color
        cornerRingPaint.alpha = 220
        cornerDotPaint.color = Color.WHITE

        val pts = listOf(
            PointF(c.topLeft.x * scaleX, c.topLeft.y * scaleY),
            PointF(c.topRight.x * scaleX, c.topRight.y * scaleY),
            PointF(c.bottomRight.x * scaleX, c.bottomRight.y * scaleY),
            PointF(c.bottomLeft.x * scaleX, c.bottomLeft.y * scaleY)
        )

        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(pts[2].x, pts[2].y)
            lineTo(pts[3].x, pts[3].y)
            close()
        }

        // Subtle top-to-bottom sheen on the fill so it doesn't read as a flat tint.
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.argb(fillPaint.alpha, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(fillPaint.alpha / 3, Color.red(color), Color.green(color), Color.blue(color)),
            Shader.TileMode.CLAMP
        )

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        val baseRadius = 9f
        val ringRadius = (baseRadius + 6f) * (if (isStable) stableScale else 1f)
        for (point in pts) {
            canvas.drawCircle(point.x, point.y, ringRadius, cornerRingPaint)
            canvas.drawCircle(point.x, point.y, baseRadius, cornerDotPaint)
        }
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        stableScaleAnimator.cancel()
        morphAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private val STABLE_COLOR = Color.parseColor("#2979FF")    // blue: locked & ready
        private val SEARCHING_COLOR = Color.parseColor("#FFC107") // amber: still searching
        private val STABLE_GLOW = Color.parseColor("#802979FF")
        private val SEARCHING_GLOW = Color.parseColor("#80FFC107")
    }
}
