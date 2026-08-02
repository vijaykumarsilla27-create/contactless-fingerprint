package com.biometrics.contactless.pipeline

import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Step 4: Rectification & Image Normalization  (target < 1000ms)
 *
 * alignFinger() and processAndEnhance() match reference Snippet C exactly,
 * including its RGB2GRAY color conversion and CLAHE clip limit of 2.0.
 *
 * FLAG FOR THE WALKTHROUGH CALL -- do not silently resolve this either way:
 * Step 4's written spec says to "perform a perspective warp to square off
 * non-orthogonal planar angles," but Snippet C's actual code only rotates
 * (warpAffine) -- there is no warpPerspective call in the reference example.
 * That is a real conflict between the spec's prose and its own example code,
 * not something safe to silently pick a side on. This file matches the
 * example code as the default path (alignFinger does rotation only, exactly
 * as shown), and adds applyPerspectiveCorrection() as a SEPARATE, opt-in
 * method that is NOT called anywhere by default -- so the default behavior
 * matches what was actually demonstrated, while the spec-required capability
 * still exists and can be wired in with one line if the answer on the call
 * is "yes, we do want perspective correction."
 *
 * Also worth flagging: Snippet C uses COLOR_RGB2GRAY. Camera frames converted
 * via ImageUtils.imageProxyToBgrMat() are BGR (OpenCV's default channel
 * order, and the conventional target of a YUV->BGR conversion). If the Mat
 * reaching this class is genuinely BGR, RGB2GRAY here would swap the
 * R/B weighting slightly in the luminance formula -- worth confirming which
 * conversion is actually upstream before assuming this line is correct as
 * written. Left as RGB2GRAY to match the reference exactly rather than
 * silently changing it.
 */
class ImageRectifier {

    /**
     * Matches reference Snippet C exactly: grayscale -> CLAHE -> min-max normalize.
     */
    fun processAndEnhance(rawMat: Mat): Mat {
        val grayMat = Mat()
        Imgproc.cvtColor(rawMat, grayMat, Imgproc.COLOR_RGB2GRAY)

        // Step 1: Contrast Enhancement via CLAHE
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val enhancedMat = Mat()
        clahe.apply(grayMat, enhancedMat)
        grayMat.release()

        // Step 2: Basic Normalization
        Core.normalize(enhancedMat, enhancedMat, 0.0, 255.0, Core.NORM_MINMAX)
        return enhancedMat
    }

    /**
     * Matches reference Snippet C exactly: minAreaRect -> angle correction -> warpAffine.
     * Rotation only -- no perspective correction (see class doc above).
     */
    fun alignFinger(srcMat: Mat, contour: MatOfPoint): Mat {
        val points = MatOfPoint2f(*contour.toArray())
        val rotatedRect = Imgproc.minAreaRect(points)
        points.release()

        var angle = rotatedRect.angle
        if (rotatedRect.size.width < rotatedRect.size.height) {
            angle += 90.0
        }

        val center = rotatedRect.center
        val rotationMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0)
        val alignedMat = Mat()
        Imgproc.warpAffine(srcMat, alignedMat, rotationMatrix, srcMat.size())
        rotationMatrix.release()

        return alignedMat
    }

    /**
     * OPT-IN ONLY -- not called by alignFinger() or anywhere else by default.
     * Implements the spec prose's "perspective warp to square off
     * non-orthogonal planar angles" requirement, in case the answer on the
     * walkthrough call is that this should actually run. Squares the
     * rotated finger's bounding quadrilateral onto a fronto-parallel
     * canonical rectangle via a 4-point homography.
     */
    fun applyPerspectiveCorrection(
        alignedMat: Mat,
        contour: MatOfPoint,
        canonicalWidth: Int = 256,
        canonicalHeight: Int = 384
    ): Mat {
        val points = MatOfPoint2f(*contour.toArray())
        val rotatedRect = Imgproc.minAreaRect(points)
        points.release()

        val boxPoints = Mat()
        Imgproc.boxPoints(rotatedRect, boxPoints)
        val srcCorners = orderCorners(boxPoints)
        boxPoints.release()

        val dstCorners = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((canonicalWidth - 1).toDouble(), 0.0),
            Point((canonicalWidth - 1).toDouble(), (canonicalHeight - 1).toDouble()),
            Point(0.0, (canonicalHeight - 1).toDouble())
        )

        val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcCorners, dstCorners)
        val warped = Mat()
        Imgproc.warpPerspective(
            alignedMat, warped, perspectiveMatrix,
            Size(canonicalWidth.toDouble(), canonicalHeight.toDouble())
        )

        srcCorners.release(); dstCorners.release(); perspectiveMatrix.release()
        return warped
    }

    private fun orderCorners(boxPoints: Mat): MatOfPoint2f {
        val pts = Array(4) { i -> Point(boxPoints.get(i, 0)[0], boxPoints.get(i, 1)[0]) }
        val sortedBySum = pts.sortedBy { it.x + it.y }
        val topLeft = sortedBySum.first()
        val bottomRight = sortedBySum.last()
        val remaining = pts.filter { it != topLeft && it != bottomRight }
        val topRight = remaining.maxByOrNull { it.x - it.y } ?: remaining[0]
        val bottomLeft = remaining.first { it != topRight }
        return MatOfPoint2f(topLeft, topRight, bottomRight, bottomLeft)
    }
}
