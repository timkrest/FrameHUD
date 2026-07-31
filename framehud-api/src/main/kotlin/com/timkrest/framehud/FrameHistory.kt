package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/**
 * Recent frame times in milliseconds, oldest first. Backed by a `FloatArray` so the sparkline
 * can be drawn without boxing on every sample.
 */
@Immutable
public class FrameHistory private constructor(private val samples: FloatArray) {

    public val size: Int get() = samples.size

    public operator fun get(index: Int): Float = samples[index]

    public companion object {
        public val EMPTY: FrameHistory = FrameHistory(FloatArray(0))

        @InternalFrameHudApi
        public fun of(samples: FloatArray): FrameHistory = if (samples.isEmpty()) EMPTY else FrameHistory(samples)
    }
}
