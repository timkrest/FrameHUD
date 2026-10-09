// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.compose

import androidx.annotation.MainThread

internal interface Marks {
    var current: String?
}

@MainThread
internal class ScrollMark(private val name: String, private val marks: Marks) {

    fun scrolling(isScrolling: Boolean) {
        if (isScrolling) marks.current = name else release()
    }

    fun release() {
        if (marks.current == name) marks.current = null
    }
}
