package com.timkrest.framehud.internal

import android.os.Build
import android.view.Display
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.WorkerThread
import com.timkrest.framehud.FramePhase
import java.util.concurrent.atomic.AtomicReference

internal class FrameMetricsCollector(
    private val aggregator: FrameAggregator,
    private val clock: MetricsClock,
    private val display: () -> Display?,
    private val onFirstFrame: (timeToDisplayMs: Float) -> Unit,
) : Window.OnFrameMetricsAvailableListener {

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private val hasApi26FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    private val hasApi31FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val scratchDurationsMs = FloatArray(FramePhase.entries.size)

    private val pendingFirstFrame = AtomicReference<PendingFirstFrame?>()

    @AnyThread
    fun expectFirstFrame(window: Window, creation: ScreenCreation?) {
        pendingFirstFrame.set(creation?.let { PendingFirstFrame(window = window, creation = it) })
    }

    @AnyThread
    fun forgetFirstFrame() {
        pendingFirstFrame.set(null)
    }

    @WorkerThread
    override fun onFrameMetricsAvailable(
        window: Window,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int,
    ) {
        guarded("reading frame metrics") {
            aggregator.addDroppedReports(dropCountSinceLastInvocation)
            val expected = takeExpectedFirstFrame(window)
            if (frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L) {
                expected?.let { reportFirstFrame(it, frameEndTimestampNs(frameMetrics)) }
                return@guarded
            }

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

    private fun takeExpectedFirstFrame(window: Window): PendingFirstFrame? {
        val expected = pendingFirstFrame.get() ?: return null
        if (expected.window !== window) return null
        return if (pendingFirstFrame.compareAndSet(expected, null)) expected else null
    }

    private fun reportFirstFrame(expected: PendingFirstFrame, frameEndNs: Long) {
        expected.creation.timeToDisplayMs(frameEndNs)?.let(onFirstFrame)
    }

    private fun frameDeadlineNs(frameMetrics: FrameMetrics): Long? =
        if (hasApi31FrameMetrics) frameMetrics.getMetric(FrameMetrics.DEADLINE).takeIf { it > 0L } else null

    private fun frameEndTimestampNs(frameMetrics: FrameMetrics): Long = if (hasApi26FrameMetrics) {
        frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) +
            frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
    } else {
        clock.nanoTime()
    }

    private class PendingFirstFrame(val window: Window, val creation: ScreenCreation)
}
