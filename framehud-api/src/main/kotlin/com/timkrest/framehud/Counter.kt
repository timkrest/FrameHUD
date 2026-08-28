package com.timkrest.framehud

import androidx.annotation.AnyThread
import androidx.compose.runtime.Immutable

@AnyThread
public interface FrameHudCounter {

    public fun set(value: Int)

    public fun add(delta: Int)
}

@Immutable
@ConsistentCopyVisibility
public data class CounterReading private constructor(
    val name: String,
    val value: Int,
    val peakSinceReset: Int,
) {
    init {
        require(name.isNotBlank()) { "A counter name must not be blank" }
        require(peakSinceReset >= value) { "A peak of $peakSinceReset is below the $value it covers" }
    }

    public companion object {
        @InternalFrameHudApi
        public fun of(name: String, value: Int, peakSinceReset: Int): CounterReading =
            CounterReading(name = name, value = value, peakSinceReset = peakSinceReset)
    }
}
