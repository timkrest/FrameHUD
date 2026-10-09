// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.shared.pollFor
import kotlin.test.fail

internal inline fun <reified T : FrameHudEvent> List<FrameHudEvent>.awaitEvents(count: Int): List<T> =
    pollFor { filterIsInstance<T>().takeIf { it.size >= count }?.take(count) }
        ?: fail("Expected $count ${T::class.java.simpleName} events, saw ${map { it.summary }}")
