package io.github.timkrest.framehud.internal

import android.os.Build
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Reads `FrameMetrics` and hands the numbers to [FrameAggregator]. Everything version-dependent
 * lives here, so the aggregation stays platform-free and testable.
 *
 * Confined to the thread the callbacks are delivered on.
 */
internal class FrameMetricsCollector(
    private val aggregator: FrameAggregator,
    private val clock: MetricsClock,
) : Window.OnFrameMetricsAvailableListener {

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private val hasApi26FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    private val hasApi31FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val scratchDurationsMs = FloatArray(FramePhase.entries.size)

    /** The platform calls this, so it is the outermost frame of the metrics thread. */
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
                refreshRateHz = window.currentRefreshRate(),
            )
        }
    }

    private fun frameDeadlineNs(frameMetrics: FrameMetrics): Long = if (hasApi31FrameMetrics) {
        frameMetrics.getMetric(FrameMetrics.DEADLINE)
    } else {
        FrameAggregator.NO_DEADLINE
    }

    private fun frameEndTimestampNs(frameMetrics: FrameMetrics): Long = if (hasApi26FrameMetrics) {
        frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) +
            frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
    } else {
        clock.nanoTime()
    }

    private fun Window.currentRefreshRate(): Float =
        decorView.display?.refreshRate ?: FrameAggregator.NO_REFRESH_RATE
}
