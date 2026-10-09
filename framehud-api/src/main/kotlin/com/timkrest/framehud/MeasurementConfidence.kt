// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import androidx.compose.runtime.Immutable
import com.timkrest.framehud.internal.formatInvariant
import java.util.Locale

public sealed interface ConfidenceIssue {

    public val affected: Set<IntervalFigure>

    public val summary: String

    @ConsistentCopyVisibility
    public data class DroppedReports private constructor(val count: Int) : ConfidenceIssue {
        override val affected: Set<IntervalFigure> get() = ALL_FIGURES
        override val summary: String get() = formatInvariant("%d dropped FrameMetrics report(s)", count)

        public companion object {
            @InternalFrameHudApi
            public fun of(count: Int): DroppedReports = DroppedReports(count = count)
        }
    }

    @ConsistentCopyVisibility
    public data class SlowListener private constructor(val longestCallMs: Float) : ConfidenceIssue {
        override val affected: Set<IntervalFigure> get() = ALL_FIGURES
        override val summary: String get() = formatInvariant("a listener held the metrics thread for %.1f ms", longestCallMs)

        public companion object {
            @InternalFrameHudApi
            public fun of(longestCallMs: Float): SlowListener = SlowListener(longestCallMs = longestCallMs)
        }
    }

    @ConsistentCopyVisibility
    public data class ThermalThrottling private constructor(val worstLevel: ThermalLevel) : ConfidenceIssue {
        override val affected: Set<IntervalFigure> get() = ALL_FIGURES
        override val summary: String
            get() = "thermal throttling reached ${worstLevel.name.lowercase(Locale.US)}"

        public companion object {
            @InternalFrameHudApi
            public fun of(worstLevel: ThermalLevel): ThermalThrottling = ThermalThrottling(worstLevel = worstLevel)
        }
    }

    @ConsistentCopyVisibility
    public data class LowBattery private constructor(val powerSaveMode: Boolean, val levelPercent: Int?) : ConfidenceIssue {
        override val affected: Set<IntervalFigure> get() = ALL_FIGURES
        override val summary: String
            get() = when {
                powerSaveMode && levelPercent != null -> formatInvariant("low battery (power save, %d%%)", levelPercent)
                powerSaveMode -> "low battery (power save)"
                levelPercent != null -> formatInvariant("low battery (%d%%)", levelPercent)
                else -> "low battery"
            }

        public companion object {
            @InternalFrameHudApi
            public fun of(powerSaveMode: Boolean, levelPercent: Int?): LowBattery =
                LowBattery(powerSaveMode = powerSaveMode, levelPercent = levelPercent)
        }
    }

    @ConsistentCopyVisibility
    public data class RefreshRateChanged private constructor(val ratesHz: Set<Int>) : ConfidenceIssue {
        override val affected: Set<IntervalFigure> get() = FRAME_BUDGET_FIGURES
        override val summary: String
            get() = "refresh rate changed across ${ratesHz.sorted().joinToString(", ")} Hz"

        public companion object {
            @InternalFrameHudApi
            public fun of(ratesHz: Set<Int>): RefreshRateChanged = RefreshRateChanged(ratesHz = ratesHz)
        }
    }

    public data object Emulator : ConfidenceIssue {
        override val affected: Set<IntervalFigure> get() = RENDER_FIGURES
        override val summary: String get() = "running on an emulator"
    }

    @ConsistentCopyVisibility
    public data class ShortSample private constructor(val frames: Int) : ConfidenceIssue {
        override val affected: Set<IntervalFigure> = buildSet {
            if (frames < MIN_FRAMES_P99) add(IntervalFigure.P99)
            if (frames < MIN_FRAMES_P95) {
                add(IntervalFigure.P95)
                add(IntervalFigure.JANK_PERCENT)
            }
            if (frames < MIN_FRAMES_P50) add(IntervalFigure.P50)
        }
        override val summary: String get() = "only $frames frame(s) collected"

        public companion object {
            @InternalFrameHudApi
            public fun of(frames: Int): ShortSample = ShortSample(frames = frames)

            public const val MIN_FRAMES_P99: Int = 300

            public const val MIN_FRAMES_P95: Int = 60

            public const val MIN_FRAMES_P50: Int = 20
        }
    }
}

@Immutable
@ConsistentCopyVisibility
public data class MeasurementConfidence private constructor(val issues: List<ConfidenceIssue>) {

    public val isSuspect: Boolean get() = issues.isNotEmpty()

    public fun issuesAffecting(figure: IntervalFigure): List<ConfidenceIssue> = issues.filter { figure in it.affected }

    public companion object {
        @InternalFrameHudApi
        public fun of(issues: List<ConfidenceIssue>): MeasurementConfidence = MeasurementConfidence(issues = issues)

        public val CLEAN: MeasurementConfidence = MeasurementConfidence(issues = emptyList())
    }
}

private val ALL_FIGURES: Set<IntervalFigure> = IntervalFigure.entries.toSet()

private val FRAME_BUDGET_FIGURES: Set<IntervalFigure> =
    setOf(IntervalFigure.JANK_PERCENT, IntervalFigure.LOST_TIME, IntervalFigure.MAX_JANK_STREAK)

private val RENDER_FIGURES: Set<IntervalFigure> = setOf(IntervalFigure.RENDER_PHASES)
