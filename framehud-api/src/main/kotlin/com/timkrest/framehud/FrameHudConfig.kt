package com.timkrest.framehud

/** A copy takes effect only once assigned to `FrameHud.config`. */
public data class FrameHudConfig(
    /** While disabled, no frames are collected and no window is added. */
    val enabled: Boolean = true,
    /** Ignored by builds that collect without the panel. */
    val overlayMode: OverlayMode = OverlayMode.PREFER_SYSTEM,
    val eventListeners: List<FrameHudEventListener> = listOf(LogcatEventListener),
    val metricsSampleWindowFrames: Int = DEFAULT_METRICS_SAMPLE_WINDOW_FRAMES,
    val metricsThrottleIntervalMs: Long = DEFAULT_METRICS_THROTTLE_INTERVAL_MS,
    val fallbackRefreshRateHz: Float = DisplayInfo.DEFAULT_REFRESH_RATE_HZ,
    val metricsThreadName: String = DEFAULT_METRICS_THREAD_NAME,
    /**
     * Milliseconds a frame may take before it counts as janky, in place of the display deadline.
     * A budget covers everything its interval holds: the session covers every screen, a screen
     * covers the marks made on it. An entry deeper in wins.
     */
    val frameBudgetsMs: Map<IntervalId, Int> = emptyMap(),
    /**
     * Name of a Perfetto trigger an incident activates, in a trace already recording into a ring
     * buffer. FrameHUD neither starts nor configures that trace. Null activates none.
     */
    val perfettoTrigger: String? = null,
) {
    init {
        require(metricsSampleWindowFrames > 0) {
            "metricsSampleWindowFrames must be positive, was $metricsSampleWindowFrames"
        }
        require(metricsThrottleIntervalMs >= 0L) {
            "metricsThrottleIntervalMs must not be negative, was $metricsThrottleIntervalMs"
        }
        require(fallbackRefreshRateHz.isFinite() && fallbackRefreshRateHz > 0f) {
            "fallbackRefreshRateHz must be finite and positive, was $fallbackRefreshRateHz"
        }
        require(frameBudgetsMs.values.all { it > 0 }) {
            "A frame budget must be positive, got $frameBudgetsMs"
        }
        require(perfettoTrigger == null || perfettoTrigger.isNotBlank()) {
            "A Perfetto trigger must be named, or left null"
        }
    }

    public companion object {
        public const val DEFAULT_METRICS_THREAD_NAME: String = "framehud-metrics"
        public const val DEFAULT_METRICS_SAMPLE_WINDOW_FRAMES: Int = 120
        public const val DEFAULT_METRICS_THROTTLE_INTERVAL_MS: Long = 400L
    }
}

public enum class OverlayMode {
    /** System overlay when `SYSTEM_ALERT_WINDOW` is granted, a window inside the app otherwise. */
    PREFER_SYSTEM,

    /** A window inside the app. The permission is never used. */
    APP_WINDOW,
}
