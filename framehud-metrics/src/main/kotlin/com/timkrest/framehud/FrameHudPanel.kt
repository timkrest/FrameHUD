package com.timkrest.framehud

import androidx.annotation.MainThread

@MainThread
@InternalFrameHudApi
public interface FrameHudPanel {

    public fun onConfigChanged()

    public fun onScreenFocused()

    public fun onScreenLost()
}
