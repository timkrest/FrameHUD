package com.timkrest.framehud

import androidx.compose.runtime.Immutable
import java.util.Locale

public enum class MeasuredMetric {
    P50,
    P95,
    P99,
    JANK_PERCENT,
    FROZEN_FRAMES,
    MAX_JANK_STREAK,
    UI_THREAD_PHASES,
    RENDER_PHASES,
}

public sealed interface ConfidenceIssue {

    public val affected: Set<MeasuredMetric>

    public val summary: String

    public data class DroppedReports(val count: Int) : ConfidenceIssue {
        override val affected: Set<MeasuredMetric> get() = ALL_METRICS
        override val summary: String get() = formatInvariant("%d dropped FrameMetrics report(s)", count)
    }

    public data class SlowListener(val longestCallMs: Float) : ConfidenceIssue {
        override val affected: Set<MeasuredMetric> get() = ALL_METRICS
        override val summary: String get() = formatInvariant("a listener held the metrics thread for %.1f ms", longestCallMs)
    }

    public data class ThermalThrottling(val worstLevel: ThermalLevel) : ConfidenceIssue {
        override val affected: Set<MeasuredMetric> get() = ALL_METRICS
        override val summary: String
            get() = "thermal throttling reached ${worstLevel.name.lowercase(Locale.US)}"
    }

    public data class LowBattery(val powerSaveMode: Boolean, val levelPercent: Int?) : ConfidenceIssue {
        override val affected: Set<MeasuredMetric> get() = ALL_METRICS
        override val summary: String
            get() = when {
                powerSaveMode && levelPercent != null -> formatInvariant("low battery (power save, %d%%)", levelPercent)
                powerSaveMode -> "low battery (power save)"
                levelPercent != null -> formatInvariant("low battery (%d%%)", levelPercent)
                else -> "low battery"
            }
    }

    public data class RefreshRateChanged(val ratesHz: Set<Int>) : ConfidenceIssue {
        override val affected: Set<MeasuredMetric> get() = FRAME_BUDGET_METRICS
        override val summary: String
            get() = "refresh rate changed across ${ratesHz.sorted().joinToString(", ")} Hz"
    }

    public data object Emulator : ConfidenceIssue {
        override val affected: Set<MeasuredMetric> get() = RENDER_METRICS
        override val summary: String get() = "running on an emulator"
    }

    public data class ShortSample(val frames: Int) : ConfidenceIssue {
        override val affected: Set<MeasuredMetric> = buildSet {
            if (frames < MIN_FRAMES_P99) add(MeasuredMetric.P99)
            if (frames < MIN_FRAMES_P95) {
                add(MeasuredMetric.P95)
                add(MeasuredMetric.JANK_PERCENT)
            }
            if (frames < MIN_FRAMES_P50) add(MeasuredMetric.P50)
        }
        override val summary: String get() = "only $frames frame(s) collected"

        public companion object {
            public const val MIN_FRAMES_P99: Int = 300

            public const val MIN_FRAMES_P95: Int = 60

            public const val MIN_FRAMES_P50: Int = 20
        }
    }
}

@Immutable
public data class MeasurementConfidence(val issues: List<ConfidenceIssue>) {

    public val isSuspect: Boolean get() = issues.isNotEmpty()

    public fun issuesAffecting(metric: MeasuredMetric): List<ConfidenceIssue> = issues.filter { metric in it.affected }

    public companion object {
        public val CLEAN: MeasurementConfidence = MeasurementConfidence(issues = emptyList())
    }
}

private val ALL_METRICS: Set<MeasuredMetric> = MeasuredMetric.entries.toSet()

private val FRAME_BUDGET_METRICS: Set<MeasuredMetric> =
    setOf(MeasuredMetric.JANK_PERCENT, MeasuredMetric.MAX_JANK_STREAK)

private val RENDER_METRICS: Set<MeasuredMetric> = setOf(MeasuredMetric.RENDER_PHASES)
