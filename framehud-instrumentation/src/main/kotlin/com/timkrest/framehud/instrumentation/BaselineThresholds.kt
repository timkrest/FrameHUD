package com.timkrest.framehud.instrumentation

import com.timkrest.framehud.BaselineMetric

public data class BaselineThresholds(
    val maxRelativeIncreasePercent: Float = 10f,
    val metrics: Set<BaselineMetric> = setOf(
        BaselineMetric.P95_MS,
        BaselineMetric.JANK_PERCENT,
        BaselineMetric.LOST_TIME_MS_PER_FRAME,
    ),
) {
    init {
        require(maxRelativeIncreasePercent >= 0f) {
            "maxRelativeIncreasePercent must be zero or more, got $maxRelativeIncreasePercent"
        }
        require(metrics.isNotEmpty()) { "A baseline check needs a metric to compare" }
    }
}
