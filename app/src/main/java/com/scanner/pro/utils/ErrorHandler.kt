package com.scanner.pro.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.scanner.pro.model.ScanError

/**
 * Single place that turns a ScanError into user-facing feedback, so every
 * screen shows errors the same way and recovery actions (Retry) stay consistent.
 */
object ErrorHandler {

    fun show(anchor: View, error: ScanError, onRetry: (() -> Unit)? = null) {
        val message = when (error) {
            is ScanError.PermissionDenied -> "Camera permission is required to scan."
            is ScanError.CameraUnavailable -> "Camera is unavailable on this device."
            is ScanError.StorageFull -> "Not enough storage space. Free up some space and try again."
            is ScanError.OcrFailed -> "Couldn't extract text: ${error.reason}"
            is ScanError.PdfFailed -> "Couldn't create the PDF: ${error.reason}"
            is ScanError.OpenCvFailed -> "Couldn't process the image: ${error.reason}"
            is ScanError.Unknown -> error.reason
        }

        val snackbar = Snackbar.make(anchor, message, Snackbar.LENGTH_LONG)
        if (onRetry != null) {
            snackbar.setAction("Retry") { onRetry() }
        }
        snackbar.show()
    }
}
