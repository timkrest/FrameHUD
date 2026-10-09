// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import androidx.annotation.MainThread

@MainThread
internal interface ShownListener {
    fun screenChanged(screen: String?)

    fun markChanged(mark: String?)
}
