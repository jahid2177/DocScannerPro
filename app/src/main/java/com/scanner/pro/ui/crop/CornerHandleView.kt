package com.scanner.pro.ui.crop

/** Which corner a handle represents, used to know which DocumentCorners field to update. */
enum class CornerPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

/**
 * Plain-data representation of a draggable handle: pixel position (in view
 * coordinates) plus a fixed radius used both for drawing and hit-testing.
 * Kept separate from CropOverlayView so it can be unit-tested without Android.
 */
data class CornerHandle(
    val position: CornerPosition,
    var x: Float,
    var y: Float,
    val touchRadius: Float = 60f
) {
    fun isTouched(touchX: Float, touchY: Float): Boolean {
        val dx = touchX - x
        val dy = touchY - y
        return (dx * dx + dy * dy) <= touchRadius * touchRadius
    }
}
