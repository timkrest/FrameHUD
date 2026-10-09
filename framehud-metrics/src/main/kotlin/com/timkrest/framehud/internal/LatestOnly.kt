// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import androidx.annotation.AnyThread
import java.util.concurrent.atomic.AtomicReference

/** Delivers only the newest of the values offered while a delivery waits its turn. */
@AnyThread
internal class LatestOnly<T : Any>(
    private val post: (task: () -> Unit) -> Boolean,
    private val deliver: (T) -> Unit,
) {

    private val pending = AtomicReference<T?>()

    fun offer(value: T) {
        if (pending.getAndSet(value) != null) return
        if (!post { pending.getAndSet(null)?.let(deliver) }) pending.set(null)
    }
}
