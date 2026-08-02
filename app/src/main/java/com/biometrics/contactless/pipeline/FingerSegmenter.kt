package com.biometrics.contactless.pipeline

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Step 3: Segmentation & Background Removal  (target < 1500ms)
 *
 * Matches the reference spec's method directly (previously the Python
 * prototype used GrabCut here, which is heavier and off-spec -- this
 * version replaces it):
 *   1. Convert to YCrCb (spec allows YCrCb or HSV; YCrCb is more
 *      lighting-invariant for skin, which is why it's kept as default).
 *   2. Otsu binarization combined with a skin-color range mask.
 *   3. Largest contour = the fingertip.
 *   4. Binary mask + bitwise AND against the original RGB crop.
 */
class FingerSegmenter {

    data class SegmentationResult(
        val success: Boolean,
        val maskedBgr: Mat?,
        val mask: Mat?,
        val largestContour: MatOfPoint?
    )

    fun segment(roiBgr: Mat): SegmentationResult {
        val ycrcb = Mat()
        Imgproc.cvtColor(roiBgr, ycrcb, Imgproc.COLOR_BGR2YCrCb)

        // Skin color range mask in YCrCb.
        val skinMask = Mat()
        Core.inRange(ycrcb, Scalar(0.0, 133.0, 77.0), Scalar(255.0, 173.0, 127.0), skinMask)

        // Otsu binarization on the Cr channel, combined (AND) with the
        // skin range mask -- Otsu alone can pick up non-skin bright
        // regions, and the skin mask alone can be noisy; combining is
        // more robust than either in isolation.
        val channels = ArrayList<Mat>()
        Core.split(ycrcb, channels)
        val crChannel = channels[1]

        val otsuMask = Mat()
        Imgproc.threshold(crChannel, otsuMask, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        val combinedMask = Mat()
        Core.bitwise_and(skinMask, otsuMask, combinedMask)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(combinedMask, combinedMask, Imgproc.MORPH_CLOSE, kernel)
        Imgproc.morphologyEx(combinedMask, combinedMask, Imgproc.MORPH_OPEN, kernel)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(combinedMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        ycrcb.release(); skinMask.release(); crChannel.release(); otsuMask.release()
        channels.forEach { it.release() }; kernel.release(); hierarchy.release()

        if (contours.isEmpty()) {
            combinedMask.release()
            return SegmentationResult(false, null, null, null)
        }

        val largest = contours.maxByOrNull { Imgproc.contourArea(it) }!!
        contours.filter { it !== largest }.forEach { it.release() }

        val cleanMask = Mat.zeros(combinedMask.size(), combinedMask.type())
        Imgproc.drawContours(cleanMask, listOf(largest), -1, Scalar(255.0), -1)
        combinedMask.release()

        val maskedBgr = Mat()
        Core.bitwise_and(roiBgr, roiBgr, maskedBgr, cleanMask)

        return SegmentationResult(true, maskedBgr, cleanMask, largest)
    }
}
