package com.biometrics.contactless.utils

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/**
 * Converts CameraX's YUV_420_888 ImageProxy frames into OpenCV Mats.
 *
 * CameraX's ImageAnalysis delivers frames in YUV_420_888 by default (not RGB),
 * so every frame handed to the pipeline needs this conversion before any
 * OpenCV call (color threshold, contour, etc.) will work correctly.
 */
object ImageUtils {

    /**
     * Convert a YUV_420_888 ImageProxy to a BGR Mat (OpenCV's native channel order).
     * Uses the standard NV21 intermediate step since OpenCV's YUV420sp->BGR
     * conversion expects that plane layout.
     */
    fun imageProxyToBgrMat(image: ImageProxy): Mat {
        require(image.format == ImageFormat.YUV_420_888) {
            "Expected YUV_420_888, got format ${image.format}"
        }

        val nv21 = yuv420888ToNv21(image)
        val yuvMat = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)

        val bgrMat = Mat()
        Imgproc.cvtColor(yuvMat, bgrMat, Imgproc.COLOR_YUV2BGR_NV21)
        yuvMat.release()
        return bgrMat
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = yPlane.buffer.remaining()
        val uSize = uPlane.buffer.remaining()
        val vSize = vPlane.buffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yPlane.buffer.get(nv21, 0, ySize)

        val vBuffer: ByteBuffer = vPlane.buffer
        val uBuffer: ByteBuffer = uPlane.buffer
        // NV21 expects VU interleaved order after the Y plane.
        var offset = ySize
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride

        // Fallback general-purpose interleave (handles non-contiguous strides
        // safely instead of assuming pixelStride == 1, which some devices break).
        val chromaHeight = image.height / 2
        val chromaWidth = image.width / 2
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride
                nv21[offset++] = vBuffer.get(vIndex)
                nv21[offset++] = uBuffer.get(uIndex)
            }
        }

        return nv21
    }
}
