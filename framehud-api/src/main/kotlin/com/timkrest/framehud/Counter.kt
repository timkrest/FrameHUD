package com.timkrest.framehud

import androidx.annotation.AnyThread
import androidx.compose.runtime.Immutable

@AnyThread
public interface FrameHudCounter {

    public fun set(value: Int)

    public fun add(delta: Int)
}

@Immutable
public data class CounterReading(
    val name: String,
    val value: Int,
    val peakSinceReset: Int,
) {
    init {
        require(name.isNotBlank()) { "A counter name must not be blank" }
        require(peakSinceReset >= value) { "A peak of $peakSinceReset is below the $value it covers" }
    }
}
