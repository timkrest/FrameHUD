package com.timkrest.framehud.internal

import android.os.Build
import android.view.Display
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.WorkerThread

/**
 * Reads `FrameMetrics` and hands the numbers to [FrameAggregator]. Everything version-dependent
 * lives here, so the aggregation stays platform-free and testable.
 */
@WorkerThread
internal class FrameMetricsCollector(
    private val aggregator: FrameAggregator,
    private val clock: MetricsClock,
    private val display: () -> Display?,
) : Window.OnFrameMetricsAvailableListener {

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private val hasApi26FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    private val hasApi31FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val scratchDurationsMs = FloatArray(FramePhase.entries.size)

    override fun onFrameMetricsAvailable(
        window: Window,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int,
    ) {
        guarded("reading frame metrics") {
            aggregator.addDroppedReports(dropCountSinceLastInvocation)
            if (frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L) return@guarded

            frameMetrics.readPhaseDurationsMs(scratchDurationsMs)
            aggregator.addFrame(
                durationsMs = scratchDurationsMs,
                totalDurationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION),
                deadlineNs = frameDeadlineNs(frameMetrics),
                frameEndNs = frameEndTimestampNs(frameMetrics),
                refreshRateHz = display()?.refreshRate?.takeIf { it.isFinite() && it > 0f },
            )
        }
    }

    private fun frameDeadlineNs(frameMetrics: FrameMetrics): Long? =
        if (hasApi31FrameMetrics) frameMetrics.getMetric(FrameMetrics.DEADLINE).takeIf { it > 0L } else null

    private fun frameEndTimestampNs(frameMetrics: FrameMetrics): Long = if (hasApi26FrameMetrics) {
        frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) +
            frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
    } else {
        clock.nanoTime()
    }
}
