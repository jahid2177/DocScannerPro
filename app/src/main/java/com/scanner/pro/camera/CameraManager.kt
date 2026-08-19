package com.scanner.pro.camera

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Thin, testable wrapper around CameraX's Preview / ImageCapture / ImageAnalysis
 * use cases. Owns the analyzer executor and exposes simple suspend/callback APIs
 * so the UI layer (ScannerFragment) doesn't touch CameraX types directly.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val documentDetector = DocumentDetector()

    private var currentLensFacing = CameraSelector.LENS_FACING_BACK

    fun startCamera(
        previewView: PreviewView,
        onReady: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val providerFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindUseCases(previewView)
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Camera init failed", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases(previewView: PreviewView) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, documentDetector) }

        val selector = CameraSelector.Builder().requireLensFacing(currentLensFacing).build()

        camera = provider.bindToLifecycle(
            lifecycleOwner, selector, preview, imageCapture, imageAnalysis
        )

        // Sensible default: continuous auto-focus so the live preview stays sharp
        // even before the user taps to focus.
        camera?.cameraControl?.enableTorch(false)
    }

    fun capturePhoto(outputFile: File, onSaved: (File) -> Unit, onError: (ImageCaptureException) -> Unit) {
        val capture = imageCapture ?: return
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSaved(outputFile)
                override fun onError(exception: ImageCaptureException) = onError(exception)
            }
        )
    }

    fun setFlashMode(mode: Int) {
        imageCapture?.flashMode = mode
    }

    fun toggleTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun hasFlashUnit(): Boolean = camera?.cameraInfo?.hasFlashUnit() ?: false

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun getMaxZoomRatio(): Float = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
    fun getMinZoomRatio(): Float = camera?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f
    fun getCurrentZoomRatio(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    fun zoomBy(scaleFactor: Float) {
        val current = getCurrentZoomRatio()
        val newRatio = (current * scaleFactor).coerceIn(getMinZoomRatio(), getMaxZoomRatio())
        setZoomRatio(newRatio)
    }

    /** Tap-to-focus: converts a view tap into a CameraX metering point and focuses there. */
    fun focusOnTap(previewView: PreviewView, event: MotionEvent) {
        val factory = previewView.meteringPointFactory
        val point = factory.createPoint(event.x, event.y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun switchCamera(previewView: PreviewView) {
        currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindUseCases(previewView)
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }

    companion object {
        private const val TAG = "CameraManager"
    }
}
