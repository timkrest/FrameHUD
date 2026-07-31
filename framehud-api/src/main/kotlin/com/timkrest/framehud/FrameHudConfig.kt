package com.timkrest.framehud

/** Tuning knobs. Assign a copy at any time; changes apply at once. */
public data class FrameHudConfig(
    /** While disabled, no window is added and no frames are collected. */
    val enabled: Boolean = true,
    val overlayMode: OverlayMode = OverlayMode.PREFER_SYSTEM,
    val eventListeners: List<FrameHudEventListener> = listOf(LogcatEventListener),
    /** How many recent frames the rolling window keeps. */
    val metricsSampleWindowSize: Int = DEFAULT_METRICS_SAMPLE_WINDOW_SIZE,
    /** How often the panel is allowed to update. Lower values cost more to render. */
    val metricsThrottleIntervalMs: Long = DEFAULT_METRICS_THROTTLE_INTERVAL_MS,
    /** Refresh rate assumed when the display reports none. */
    val fallbackRefreshRateHz: Float = DisplayInfo.DEFAULT_REFRESH_RATE_HZ,
    val metricsThreadName: String = DEFAULT_METRICS_THREAD_NAME,
) {
    public companion object {
        public const val DEFAULT_METRICS_THREAD_NAME: String = "framehud-metrics"
        public const val DEFAULT_METRICS_SAMPLE_WINDOW_SIZE: Int = 120
        public const val DEFAULT_METRICS_THROTTLE_INTERVAL_MS: Long = 400L
    }
}

public enum class OverlayMode {
    /** System overlay when `SYSTEM_ALERT_WINDOW` is granted, a window inside the app otherwise. */
    PREFER_SYSTEM,

    /** A window inside the app. The permission is never used. */
    APP_WINDOW,
}
