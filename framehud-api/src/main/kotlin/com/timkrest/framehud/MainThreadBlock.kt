package com.timkrest.framehud

import androidx.compose.runtime.Immutable

/** [calls] come most sampled first. */
@Immutable
public data class MainThreadBlock(
    val durationMs: Long,
    val stacksTaken: Int,
    val calls: List<SampledCall>,
) {
    init {
        require(durationMs >= 0) { "A block lasts a duration, got $durationMs ms" }
        require(stacksTaken >= 0) { "A block takes no negative number of stacks, got $stacksTaken" }
        require(stacksTaken > 0 || calls.isEmpty()) { "A block that took no stack cannot name $calls" }
    }

    public companion object {
        public val NONE: MainThreadBlock =
            MainThreadBlock(durationMs = 0, stacksTaken = 0, calls = emptyList())
    }
}

@Immutable
public data class SampledCall(
    val name: String,
    val samples: Int,
) {
    init {
        require(name.isNotBlank()) { "A sampled call must name where the thread stood" }
        require(samples > 0) { "A call reaches the block once sampled, got $samples" }
    }
}
