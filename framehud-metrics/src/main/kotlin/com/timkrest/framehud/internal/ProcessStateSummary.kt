// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import androidx.annotation.AnyThread

@AnyThread
internal class ProcessStateSummary(firstRun: String, private val publish: (ByteArray) -> Unit) : ShownListener {

    private val lock = Any()

    private var shown = ShownState(run = firstRun, screen = null, mark = null)

    private var isPublishing = false

    fun setPublishing(publishing: Boolean) {
        synchronized(lock) {
            if (isPublishing == publishing) return
            isPublishing = publishing
            publish((if (publishing) shown else ShownState.NOTHING).toSummary())
        }
    }

    fun runChanged(run: String) = show { it.copy(run = run) }

    override fun screenChanged(screen: String?) = show { it.copy(screen = screen) }

    override fun markChanged(mark: String?) = show { it.copy(mark = mark) }

    private inline fun show(change: (ShownState) -> ShownState) {
        synchronized(lock) {
            val next = change(shown)
            if (next == shown) return
            shown = next
            if (isPublishing) publish(next.toSummary())
        }
    }
}
