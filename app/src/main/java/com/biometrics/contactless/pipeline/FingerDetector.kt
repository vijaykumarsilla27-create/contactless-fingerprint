package com.biometrics.contactless.pipeline

import androidx.camera.core.ImageProxy
import com.biometrics.contactless.utils.ImageUtils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Step 2: Finger Detection & Auto-Capture Trigger  (target < 500ms)
 *
 * detectAndCheckQuality(ImageProxy) matches the reference Snippet B call
 * signature exactly (detector.detectAndCheckQuality(imageProxy) -> Boolean),
 * so FrameAnalyzer can call it directly on the raw frame without doing its
 * own Mat conversion first. Internally this still does:
 *   1. Fixed-ROI crop (cheaper than full-frame search, matches the on-screen
 *      target box).
 *   2. HSV skin threshold (per spec).
 *   3. Area ratio >= 0.35 check.
 *   4. Laplacian-variance blur check.
 *   5. 3-consecutive-frame stability gate before returning true.
 *
 * roi defaults to a centered box; call setRoi() from the UI layer once the
 * overlay's actual on-screen coordinates are known (they depend on the
 * PreviewView's measured size, which isn't available until layout).
 */
class FingerDetector(
    private val areaRatioThreshold: Double = 0.35,
    private val blurVarianceThreshold: Double = 60.0,
    private val requiredStableFrames: Int = 3
) {
    private var consecutiveStableFrames = 0
    private var roi: Rect? = null

    fun setRoi(newRoi: Rect) {
        roi = newRoi
    }

    /**
     * Matches reference Snippet B's call: detector.detectAndCheckQuality(imageProxy).
     * Returns true only once the stability gate has been satisfied; the caller
     * (FrameAnalyzer) is responsible for closing imageProxy in both cases.
     */
    fun detectAndCheckQuality(imageProxy: ImageProxy): Boolean {
        val frameBgr = ImageUtils.imageProxyToBgrMat(imageProxy)
        val activeRoi = roi ?: centeredDefaultRoi(frameBgr)

        val result = analyze(frameBgr, activeRoi)
        frameBgr.release()
        return result.triggered
    }

    data class DetectionResult(
        val triggered: Boolean,
        val areaRatio: Double,
        val blurVariance: Double,
        val stableFrameCount: Int,
        val passedThisFrame: Boolean
    )

    fun analyze(frameBgr: Mat, roiRect: Rect): DetectionResult {
        val safeRoi = clampRoi(roiRect, frameBgr)
        val roiCrop = Mat(frameBgr, safeRoi)

        val blurVariance = computeLaplacianVariance(roiCrop)
        val areaRatio = computeSkinAreaRatio(roiCrop)

        val passedThisFrame = areaRatio >= areaRatioThreshold && blurVariance >= blurVarianceThreshold

        consecutiveStableFrames = if (passedThisFrame) consecutiveStableFrames + 1 else 0
        val triggered = consecutiveStableFrames >= requiredStableFrames

        if (triggered) {
            consecutiveStableFrames = 0
        }

        roiCrop.release()
        return DetectionResult(triggered, areaRatio, blurVariance, consecutiveStableFrames, passedThisFrame)
    }

    private fun centeredDefaultRoi(frame: Mat): Rect {
        val w = (frame.cols() * 0.6).toInt()
        val h = (frame.rows() * 0.6).toInt()
        val x = (frame.cols() - w) / 2
        val y = (frame.rows() - h) / 2
        return Rect(x, y, w, h)
    }

    private fun clampRoi(roiRect: Rect, frame: Mat): Rect {
        val x = roiRect.x.coerceIn(0, frame.cols() - 1)
        val y = roiRect.y.coerceIn(0, frame.rows() - 1)
        val w = roiRect.width.coerceAtMost(frame.cols() - x)
        val h = roiRect.height.coerceAtMost(frame.rows() - y)
        return Rect(x, y, w, h)
    }

    private fun computeSkinAreaRatio(roiCrop: Mat): Double {
        val hsv = Mat()
        Imgproc.cvtColor(roiCrop, hsv, Imgproc.COLOR_BGR2HSV)

        val mask = Mat()
        Core.inRange(hsv, Scalar(0.0, 30.0, 60.0), Scalar(25.0, 150.0, 255.0), mask)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(5.0, 5.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)

        val skinPixels = Core.countNonZero(mask)
        val totalPixels = roiCrop.rows() * roiCrop.cols()

        hsv.release(); mask.release(); kernel.release()

        return if (totalPixels > 0) skinPixels.toDouble() / totalPixels else 0.0
    }

    private fun computeLaplacianVariance(roiCrop: Mat): Double {
        val gray = Mat()
        Imgproc.cvtColor(roiCrop, gray, Imgproc.COLOR_BGR2GRAY)

        val laplacian = Mat()
        Imgproc.Laplacian(gray, laplacian, org.opencv.core.CvType.CV_64F)

        val mean = org.opencv.core.MatOfDouble()
        val stddev = org.opencv.core.MatOfDouble()
        Core.meanStdDev(laplacian, mean, stddev)
        val variance = stddev.toArray()[0] * stddev.toArray()[0]

        gray.release(); laplacian.release()
        return variance
    }
}
