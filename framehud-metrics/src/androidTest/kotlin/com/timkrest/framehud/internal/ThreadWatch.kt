// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import com.timkrest.framehud.shared.awaitUntil

internal fun awaitThreadGone(name: String): Boolean =
    awaitUntil { Thread.getAllStackTraces().keys.none { it.name == name } }

internal fun threadsNamed(name: String): Int = Thread.getAllStackTraces().keys.count { it.name == name }
