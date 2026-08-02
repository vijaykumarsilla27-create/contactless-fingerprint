package com.biometrics.contactless.utils

import android.util.Log

/**
 * Step 6: Performance Logging & Bottleneck Tracking
 *
 * Matches the reference Snippet A API exactly: startStage(name)/stopStage(name)/
 * printSummary(), tag "PipelineTimer". A single instance of this class is shared
 * across FrameAnalyzer (Detection stage) and CaptureProcessor (Segmentation,
 * Rectification, FIR Encoding stages) so printSummary() produces one combined
 * report across all four stages, matching the expected verification log output
 * in Section 6 of the reference guide.
 */
class BenchmarkLogger {
    private val timestamps = mutableMapOf<String, Long>()
    private val durations = mutableMapOf<String, Long>()

    fun startStage(stageName: String) {
        timestamps[stageName] = System.nanoTime()
    }

    fun stopStage(stageName: String) {
        val startTime = timestamps[stageName] ?: return
        val elapsedTimeMs = (System.nanoTime() - startTime) / 1_000_000
        durations[stageName] = elapsedTimeMs
        Log.d("PipelineTimer", "Stage [$stageName] completed in: $elapsedTimeMs ms")
    }

    fun printSummary() {
        val totalTime = durations.values.sum()
        Log.i("PipelineTimer", "=== PIPELINE PERFORMANCE SUMMARY ===")
        durations.forEach { (stage, time) ->
            Log.i("PipelineTimer", " -> $stage: $time ms")
        }
        Log.i("PipelineTimer", "Total Latency: $totalTime ms")
    }

    /** Extension beyond the reference snippet: budget compliance, used only in report/testing paths. */
    fun isWithinBudget(stageName: String, budgetMs: Long): Boolean {
        val elapsed = durations[stageName] ?: return false
        return elapsed <= budgetMs
    }

    fun totalMs(): Long = durations.values.sum()

    fun reset() {
        timestamps.clear()
        durations.clear()
    }
}
