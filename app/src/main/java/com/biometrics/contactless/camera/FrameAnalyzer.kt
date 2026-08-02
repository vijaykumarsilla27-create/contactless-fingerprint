package com.biometrics.contactless.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.biometrics.contactless.pipeline.FingerDetector
import com.biometrics.contactless.utils.BenchmarkLogger

/**
 * Matches reference Snippet B exactly in shape and behavior:
 *   - constructor takes an injected FingerDetector + onTriggerCaptured callback
 *   - detector.detectAndCheckQuality(imageProxy) does the gating
 *   - on trigger: hands the raw ImageProxy to the callback and returns
 *     WITHOUT closing it -- closing becomes the receiver's responsibility
 *     once it's done with the frame (this matters: closing here while the
 *     receiver is still reading from it would crash the downstream pipeline)
 *   - on no-trigger: closes imageProxy itself, as shown in the reference
 *
 * One deviation from the literal snippet, done deliberately: the logger is
 * now an INJECTED constructor parameter rather than a private instance
 * owned by this class. A private-per-analyzer logger can only ever report
 * on the Detection stage, but Section 6's expected verification log shows
 * all four stages (Detection, Segmentation, Rectification, FIR Encoding)
 * in one combined summary -- which requires the same BenchmarkLogger
 * instance to be shared with whatever runs the other three stages
 * (CaptureProcessor). MainActivity owns the single shared instance and
 * injects it into both.
 */
class FrameAnalyzer(
    private val detector: FingerDetector,
    private val logger: BenchmarkLogger,
    private val onTriggerCaptured: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        logger.startStage("Detection")
        val isFingerValid = detector.detectAndCheckQuality(imageProxy)
        logger.stopStage("Detection")

        if (isFingerValid) {
            onTriggerCaptured(imageProxy)
        } else {
            imageProxy.close()
        }
    }
}
