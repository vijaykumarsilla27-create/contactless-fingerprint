package com.biometrics.contactless.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Step 1: Project Setup & CameraX Integration
 *
 * Binds Preview + ImageAnalysis to the activity lifecycle. ImageAnalysis
 * uses STRATEGY_KEEP_ONLY_LATEST so the analyzer never backs up behind
 * slow pipeline stages -- if segmentation/rectification takes longer
 * than the frame interval, CameraX drops intermediate frames rather than
 * queuing them, keeping the live preview responsive.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val analyzer: FrameAnalyzer
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null

    fun start() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture?.addListener({
            val cameraProvider = cameraProviderFuture!!.get()
            bindUseCases(cameraProvider)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(android.util.Size(1920, 1080))
            .build()
            .also { it.setAnalyzer(cameraExecutor, analyzer) }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
        )
    }

    /** Toggles torch -- referenced by the spec's "manual torch toggle" hardware requirement. */
    fun setTorchEnabled(enabled: Boolean, camera: androidx.camera.core.Camera?) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
