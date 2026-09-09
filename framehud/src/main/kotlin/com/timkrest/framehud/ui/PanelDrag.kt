package com.timkrest.framehud.ui

internal fun interface PanelDrag {
    fun grab(): GrabbedPanel
}

internal fun interface GrabbedPanel {
    fun followPointer()
}
