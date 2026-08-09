package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/**
 * Recent frames, oldest first. Backed by `FloatArray`s so the sparkline can be drawn without
 * boxing on every sample.
 */
@Immutable
public class FrameHistory private constructor(
    private val totalsMs: FloatArray,
    private val deadlinesMs: FloatArray,
) {

    public val size: Int get() = totalsMs.size

    public fun totalMsAt(index: Int): Float = totalsMs[index]

    public fun deadlineMsAt(index: Int): Float = deadlinesMs[index]

    public companion object {
        public val EMPTY: FrameHistory = FrameHistory(FloatArray(0), FloatArray(0))

        @InternalFrameHudApi
        public fun of(totalsMs: FloatArray, deadlinesMs: FloatArray): FrameHistory {
            require(totalsMs.size == deadlinesMs.size) {
                "totalsMs and deadlinesMs must be the same length, were ${totalsMs.size} and ${deadlinesMs.size}"
            }
            return if (totalsMs.isEmpty()) EMPTY else FrameHistory(totalsMs, deadlinesMs)
        }
    }
}
