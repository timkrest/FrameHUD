package com.timkrest.framehud.internal

import android.os.Build
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
    private val windows: MeasuredWindows,
    private val onFirstFrame: (timeToDisplayMs: Float) -> Unit,
    private val onUsableFrame: (timeToUsableMs: Float) -> Unit,
) : Window.OnFrameMetricsAvailableListener {

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private val hasApi26FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    private val hasApi31FrameMetrics = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val scratchDurationsMs = FloatArray(FramePhase.entries.size)

    private val pendingFirstFrame = AtomicReference<PendingFirstFrame?>()

    private val usableWatch = UsableFrameWatch(clock)

    @AnyThread
    fun expectScreen(window: Window, start: ScreenStart?) {
        val creation = start?.takeIf { it.precedesCreation }
        pendingFirstFrame.set(creation?.let { PendingFirstFrame(window = window, creation = it) })
        usableWatch.expectScreen(window = window, start = start ?: ScreenStart(clock.nanoTime()))
    }

    @AnyThread
    fun restartScreen() {
        usableWatch.restartScreen()
    }

    @AnyThread
    fun forgetScreen() {
        pendingFirstFrame.set(null)
        usableWatch.forgetScreen()
    }

    @AnyThread
    fun reportUsable(start: ScreenStart?) {
        usableWatch.reportUsable(start)
    }

    @WorkerThread
    override fun onFrameMetricsAvailable(
        window: Window,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int,
    ) {
        guarded("reading frame metrics") {
            val measured = windows[window] ?: return@guarded
            val screen = measured.screen
            aggregator.addDroppedReports(screen = screen, count = dropCountSinceLastInvocation)
            val frameEndNs = frameEndTimestampNs(frameMetrics)
            val expected = takeExpectedFirstFrame(window)
            val isFirstDraw = frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L
            if (isFirstDraw) expected?.creation?.elapsedMs(frameEndNs)?.let(onFirstFrame)
            usableWatch.onFrame(window = window, frameEndNs = frameEndNs)?.let(onUsableFrame)
            if (isFirstDraw) return@guarded

            frameMetrics.readPhaseDurationsMs(scratchDurationsMs)
            aggregator.addFrame(
                screen = screen,
                durationsMs = scratchDurationsMs,
                totalDurationNs = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION),
                deadlineNs = frameDeadlineNs(frameMetrics),
                frameEndNs = frameEndNs,
                refreshRateHz = refreshRateHzOf(measured),
            )
        }
    }

    private fun takeExpectedFirstFrame(window: Window): PendingFirstFrame? {
        val expected = pendingFirstFrame.get() ?: return null
        if (expected.window !== window) return null
        return if (pendingFirstFrame.compareAndSet(expected, null)) expected else null
    }

    private fun frameDeadlineNs(frameMetrics: FrameMetrics): Long =
        if (hasApi31FrameMetrics) frameMetrics.getMetric(FrameMetrics.DEADLINE) else NO_DEADLINE_NS

    private fun refreshRateHzOf(measured: MeasuredWindows.Measured): Float {
        val display = measured.display ?: return UNKNOWN_REFRESH_RATE_HZ
        val refreshRateHz = display.refreshRate
        return if (refreshRateHz.isFinite() && refreshRateHz > 0f) refreshRateHz else UNKNOWN_REFRESH_RATE_HZ
    }

    private fun frameEndTimestampNs(frameMetrics: FrameMetrics): Long = if (hasApi26FrameMetrics) {
        frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP) +
            frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
    } else {
        clock.nanoTime()
    }

    private class PendingFirstFrame(val window: Window, val creation: ScreenStart)
}
