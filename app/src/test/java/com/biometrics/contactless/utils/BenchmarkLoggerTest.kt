package com.biometrics.contactless.utils

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric lets these run against android.util.Log on the plain JVM --
 * a stock unit test crashes on any Log.d/Log.i call with "Method d in
 * android.util.Log not mocked" since the real Android framework isn't
 * present outside a device/emulator. Run with:
 *   ./gradlew testDebugUnitTest --tests "*.BenchmarkLoggerTest"
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BenchmarkLoggerTest {

    @Test
    fun `stopStage records nonzero elapsed time for a real interval`() {
        val logger = BenchmarkLogger()
        logger.startStage("Detection")
        Thread.sleep(5)
        logger.stopStage("Detection")

        assertTrue(logger.totalMs() > 0)
    }

    @Test
    fun `stopStage without a matching startStage is a no-op, not a crash`() {
        val logger = BenchmarkLogger()
        logger.stopStage("NeverStarted") // should not throw
        assertEquals(0L, logger.totalMs())
    }

    @Test
    fun `totalMs sums across all recorded stages`() {
        val logger = BenchmarkLogger()
        for (stage in listOf("Detection", "Segmentation", "Rectification", "FIR Encoding")) {
            logger.startStage(stage)
            Thread.sleep(2)
            logger.stopStage(stage)
        }
        // Each stage takes >=2ms, so total across 4 stages must be >=8ms.
        assertTrue(logger.totalMs() >= 8)
    }

    @Test
    fun `reset clears prior stage timings`() {
        val logger = BenchmarkLogger()
        logger.startStage("Detection")
        logger.stopStage("Detection")
        assertTrue(logger.totalMs() >= 0)

        logger.reset()
        assertEquals(0L, logger.totalMs())
    }

    @Test
    fun `isWithinBudget correctly flags a stage that exceeds its budget`() {
        val logger = BenchmarkLogger()
        logger.startStage("Segmentation")
        Thread.sleep(10)
        logger.stopStage("Segmentation")

        assertFalse(logger.isWithinBudget("Segmentation", budgetMs = 1))
        assertTrue(logger.isWithinBudget("Segmentation", budgetMs = 5000))
    }

    @Test
    fun `printSummary does not throw when no stages were recorded`() {
        val logger = BenchmarkLogger()
        logger.printSummary() // should not throw
    }
}
