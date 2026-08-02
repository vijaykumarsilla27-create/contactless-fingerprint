package com.biometrics.contactless.camera

import androidx.camera.core.ImageProxy
import com.biometrics.contactless.pipeline.FingerSegmenter
import com.biometrics.contactless.pipeline.FirEncoder
import com.biometrics.contactless.pipeline.ImageRectifier
import com.biometrics.contactless.utils.BenchmarkLogger
import com.biometrics.contactless.utils.ImageUtils
import org.opencv.core.MatOfByte
import org.opencv.core.Rect
import org.opencv.imgcodecs.Imgcodecs

/**
 * Runs Segmentation -> Rectification -> FIR Encoding on the frame
 * FrameAnalyzer hands off after its detection trigger fires. Takes the same
 * BenchmarkLogger instance FrameAnalyzer used for the Detection stage, so
 * logger.printSummary() at the end produces one combined report across all
 * four stages -- matching the exact expected log output in Section 6 of
 * the reference guide (Detection, Segmentation, Rectification, FIR Encoding,
 * then the summary block).
 *
 * IMPORTANT: this class is responsible for closing the ImageProxy handed to
 * it, since FrameAnalyzer deliberately does NOT close it on the trigger path
 * (see FrameAnalyzer's doc comment).
 */
class CaptureProcessor(
    private val logger: BenchmarkLogger,
    private val roiProvider: () -> Rect,
    private val onResult: (Result) -> Unit
) {
    private val segmenter = FingerSegmenter()
    private val rectifier = ImageRectifier()
    private val firEncoder = FirEncoder()

    sealed class Result {
        data class Success(val firBytes: ByteArray) : Result()
        data class Failed(val stageReached: String) : Result()
    }

    fun process(imageProxy: ImageProxy) {
        try {
            val frameBgr = ImageUtils.imageProxyToBgrMat(imageProxy)
            val roi = clampRoi(roiProvider(), frameBgr.cols(), frameBgr.rows())
            val roiCrop = org.opencv.core.Mat(frameBgr, roi)
            frameBgr.release()

            logger.startStage("Segmentation")
            val segResult = segmenter.segment(roiCrop)
            logger.stopStage("Segmentation")

            if (!segResult.success || segResult.maskedBgr == null || segResult.largestContour == null) {
                roiCrop.release()
                logger.printSummary()
                onResult(Result.Failed("Segmentation"))
                return
            }

            logger.startStage("Rectification")
            val aligned = rectifier.alignFinger(segResult.maskedBgr, segResult.largestContour)
            val enhanced = rectifier.processAndEnhance(aligned)
            aligned.release()
            logger.stopStage("Rectification")

            roiCrop.release(); segResult.maskedBgr.release(); segResult.mask?.release()

            logger.startStage("FIR Encoding")
            val jp2Bytes = encodeToJp2(enhanced)
            enhanced.release()

            if (jp2Bytes == null) {
                logger.stopStage("FIR Encoding")
                logger.printSummary()
                onResult(Result.Failed("FIR Encoding"))
                return
            }

            val firBytes = firEncoder.createFirRecord(jp2Bytes, enhanced.cols(), enhanced.rows())
            logger.stopStage("FIR Encoding")

            logger.printSummary()
            onResult(Result.Success(firBytes))
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Attempts JPEG2000 encoding; note this requires OpenCV built with
     * OpenJPEG support, which is not part of a stock OpenCV-Android AAR --
     * see FirEncoder's class doc. Falls back to PNG bytes (still passed
     * into createFirRecord as "jp2Data" for structural purposes) if JP2
     * encoding isn't available in the linked OpenCV build.
     */
    private fun encodeToJp2(image: org.opencv.core.Mat): ByteArray? {
        val buf = MatOfByte()
        val ok = try {
            Imgcodecs.imencode(".jp2", image, buf)
        } catch (e: Exception) {
            false
        }
        if (ok && buf.total() > 0) {
            val bytes = buf.toArray()
            buf.release()
            return bytes
        }
        buf.release()

        val pngBuf = MatOfByte()
        val pngOk = Imgcodecs.imencode(".png", image, pngBuf)
        if (!pngOk) {
            pngBuf.release()
            return null
        }
        val bytes = pngBuf.toArray()
        pngBuf.release()
        return bytes
    }

    private fun clampRoi(roi: Rect, frameWidth: Int, frameHeight: Int): Rect {
        val x = roi.x.coerceIn(0, frameWidth - 1)
        val y = roi.y.coerceIn(0, frameHeight - 1)
        val w = roi.width.coerceAtMost(frameWidth - x)
        val h = roi.height.coerceAtMost(frameHeight - y)
        return Rect(x, y, w, h)
    }
}
