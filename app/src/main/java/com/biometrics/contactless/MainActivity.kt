package com.biometrics.contactless

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.biometrics.contactless.camera.CameraManager
import com.biometrics.contactless.camera.CaptureProcessor
import com.biometrics.contactless.camera.FrameAnalyzer
import com.biometrics.contactless.pipeline.FingerDetector
import com.biometrics.contactless.utils.BenchmarkLogger
import org.opencv.android.OpenCVLoader
import org.opencv.core.Rect
import java.io.File

/**
 * Main UI controller & permission handler.
 *
 * Owns the single shared BenchmarkLogger instance -- this is the piece that
 * makes Section 6's combined 4-stage log summary possible: it's created
 * once here and injected into both FrameAnalyzer (Detection stage) and
 * CaptureProcessor (Segmentation/Rectification/FIR Encoding stages).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var cameraManager: CameraManager

    private val sharedLogger = BenchmarkLogger()
    private val detector = FingerDetector()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required for capture.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV initialization failed.", Toast.LENGTH_LONG).show()
        }

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        // ROI matches the visual target-box overlay in activity_main.xml.
        // In a full implementation this would be computed from the overlay
        // view's actual measured bounds, mapped into frame coordinates.
        val roiProvider: () -> Rect = {
            Rect(240, 400, 600, 900)
        }
        detector.setRoi(roiProvider())

        val captureProcessor = CaptureProcessor(
            logger = sharedLogger,
            roiProvider = roiProvider,
            onResult = { result ->
                runOnUiThread {
                    when (result) {
                        is CaptureProcessor.Result.Success -> {
                            statusText.text = "Capture complete (${result.firBytes.size} bytes). Total: ${sharedLogger.totalMs()} ms"
                            saveFirRecord(result.firBytes)
                        }
                        is CaptureProcessor.Result.Failed -> {
                            statusText.text = "Capture failed at: ${result.stageReached}"
                        }
                    }
                    sharedLogger.reset()
                }
            }
        )

        val analyzer = FrameAnalyzer(
            detector = detector,
            logger = sharedLogger,
            onTriggerCaptured = { imageProxy ->
                runOnUiThread { statusText.text = "Finger detected -- processing..." }
                captureProcessor.process(imageProxy)
            }
        )

        cameraManager = CameraManager(this, this, previewView, analyzer)
        cameraManager.start()
    }

    private fun saveFirRecord(firBytes: ByteArray) {
        try {
            val outFile = File(getExternalFilesDir(null), "capture_${System.currentTimeMillis()}.iso")
            outFile.writeBytes(firBytes)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save FIR record: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraManager.isInitialized) {
            cameraManager.shutdown()
        }
    }
}
