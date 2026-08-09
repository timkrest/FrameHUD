package com.timkrest.framehud

/** Runtime configuration. Changes take effect when assigned to `FrameHud.config`. */
public data class FrameHudConfig(
    /** While disabled, no window is added and no frames are collected. */
    val enabled: Boolean = true,
    val overlayMode: OverlayMode = OverlayMode.PREFER_SYSTEM,
    val eventListeners: List<FrameHudEventListener> = listOf(LogcatEventListener),
    val metricsSampleWindowFrames: Int = DEFAULT_METRICS_SAMPLE_WINDOW_FRAMES,
    val metricsThrottleIntervalMs: Long = DEFAULT_METRICS_THROTTLE_INTERVAL_MS,
    val fallbackRefreshRateHz: Float = DisplayInfo.DEFAULT_REFRESH_RATE_HZ,
    val metricsThreadName: String = DEFAULT_METRICS_THREAD_NAME,
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
