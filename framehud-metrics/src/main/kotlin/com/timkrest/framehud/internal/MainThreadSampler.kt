package com.timkrest.framehud.internal

import com.timkrest.framehud.MainThreadBlock
import com.timkrest.framehud.SampledCall

internal class MainThreadSampler {

    private val samplesByCall = LinkedHashMap<String, Int>()

    private var stacksTaken = 0

    private var blockedSinceMs = 0L

    fun beginBlock(sinceMs: Long) {
        samplesByCall.clear()
        stacksTaken = 0
        blockedSinceMs = sinceMs
    }

    fun sample(stack: Array<StackTraceElement>) {
        stacksTaken++
        for (index in 0 until minOf(stack.size, CALLS_PER_STACK)) {
            val call = stack[index].toString()
            samplesByCall[call] = (samplesByCall[call] ?: 0) + 1
        }
    }

    fun blockAt(nowMs: Long): MainThreadBlock {
        if (stacksTaken == 0) return MainThreadBlock.NONE
        return MainThreadBlock.of(
            durationMs = maxOf(nowMs - blockedSinceMs, 0L),
            stacksTaken = stacksTaken,
            calls = samplesByCall.entries
                .sortedByDescending { it.value }
                .take(CALLS_KEPT)
                .map { SampledCall.of(name = it.key, samples = it.value) },
        )
    }

    private companion object {
        const val CALLS_PER_STACK = 16
        const val CALLS_KEPT = 8
    }
}
