package com.biometrics.contactless.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc

/**
 * Instrumented tests -- these need real OpenCV native libraries loaded,
 * which only works on an actual device or emulator (JVM unit tests under
 * src/test cannot load .so files). Run with:
 *   ./gradlew connectedDebugAndroidTest
 * or the green gutter arrow in Android Studio with a device/emulator
 * selected as the run target. See TESTING.md for full setup.
 *
 * Uses synthetic skin-tone-colored ellipses (same approach as the Python
 * prototype's test data) rather than real finger photos, since no camera
 * captures were available -- this validates pipeline mechanics (does
 * segmentation find a contour, does rectification produce the right
 * output size), not ridge-level image quality. See DEVIATIONS.md.
 */
@RunWith(AndroidJUnit4::class)
class PipelineInstrumentedTest {

    @Before
    fun loadOpenCv() {
        assertTrue("OpenCV native library failed to load", OpenCVLoader.initLocal())
    }

    private fun makeSyntheticFingerMat(): Mat {
        val mat = Mat.zeros(480, 640, CvType.CV_8UC3)
        // BGR skin-tone approximation, same values used in the Python prototype's test harness.
        Imgproc.ellipse(
            mat, Point(320.0, 240.0), org.opencv.core.Size(60.0, 140.0),
            0.0, 0.0, 360.0, org.opencv.core.Scalar(90.0, 140.0, 210.0), -1
        )
        return mat
    }

    @Test
    fun segmenter_finds_a_contour_on_a_synthetic_finger_shape() {
        val segmenter = FingerSegmenter()
        val input = makeSyntheticFingerMat()

        val result = segmenter.segment(input)

        assertTrue("Expected segmentation to succeed on a clear synthetic shape", result.success)
        assertNotNull(result.maskedBgr)
        assertNotNull(result.largestContour)
        assertTrue("Expected nonzero mask coverage", Core.countNonZero(result.mask) > 0)
    }

    @Test
    fun segmenter_fails_gracefully_on_an_empty_dark_frame() {
        val segmenter = FingerSegmenter()
        val emptyFrame = Mat.zeros(480, 640, CvType.CV_8UC3) // no skin tone present

        val result = segmenter.segment(emptyFrame)

        assertFalse(result.success)
    }

    @Test
    fun rectifier_alignFinger_produces_same_size_output_as_input() {
        val segmenter = FingerSegmenter()
        val rectifier = ImageRectifier()
        val input = makeSyntheticFingerMat()

        val segResult = segmenter.segment(input)
        assertTrue(segResult.success)

        val aligned = rectifier.alignFinger(segResult.maskedBgr!!, segResult.largestContour!!)
        assertEquals(input.size(), aligned.size())
    }

    @Test
    fun rectifier_processAndEnhance_produces_single_channel_grayscale() {
        val segmenter = FingerSegmenter()
        val rectifier = ImageRectifier()
        val input = makeSyntheticFingerMat()

        val segResult = segmenter.segment(input)
        assertTrue(segResult.success)

        val aligned = rectifier.alignFinger(segResult.maskedBgr!!, segResult.largestContour!!)
        val enhanced = rectifier.processAndEnhance(aligned)

        assertEquals(1, enhanced.channels())
    }

    @Test
    fun full_stage_chain_runs_within_the_5000ms_total_budget() {
        val segmenter = FingerSegmenter()
        val rectifier = ImageRectifier()
        val firEncoder = FirEncoder()
        val input = makeSyntheticFingerMat()

        val start = System.nanoTime()

        val segResult = segmenter.segment(input)
        assertTrue(segResult.success)
        val aligned = rectifier.alignFinger(segResult.maskedBgr!!, segResult.largestContour!!)
        val enhanced = rectifier.processAndEnhance(aligned)
        val firBytes = firEncoder.createFirRecord(byteArrayOf(0, 1, 2, 3), enhanced.cols(), enhanced.rows())

        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertNotNull(firBytes)
        assertTrue("Pipeline took ${elapsedMs}ms, over the 5000ms budget", elapsedMs < 5000)
    }
}
