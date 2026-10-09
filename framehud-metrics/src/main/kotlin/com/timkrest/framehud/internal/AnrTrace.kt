// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

/** The main thread's section of an ANR trace: its frames and the locks it waits on, top first. */
internal fun mainThreadStackOf(trace: Sequence<String>): List<String> {
    val stack = mutableListOf<String>()
    var inMain = false
    for (line in trace) {
        if (!inMain) {
            inMain = line.startsWith(MAIN_THREAD_HEADER)
            continue
        }
        if (line.isBlank() || stack.size == MAX_FRAMES) break
        val text = line.trim()
        when {
            text.startsWith(FRAME_PREFIX) -> stack += text.removePrefix(FRAME_PREFIX)
            text.startsWith(LOCK_PREFIX) -> stack += text
        }
    }
    return stack
}

private const val MAIN_THREAD_HEADER = "\"main\" "
private const val FRAME_PREFIX = "at "
private const val LOCK_PREFIX = "- "
private const val MAX_FRAMES = 64
