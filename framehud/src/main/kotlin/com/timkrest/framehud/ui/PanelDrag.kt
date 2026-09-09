// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.ui

internal fun interface PanelDrag {
    fun grab(): GrabbedPanel
}

internal fun interface GrabbedPanel {
    fun followPointer()
}
