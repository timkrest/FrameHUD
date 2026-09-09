// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import androidx.annotation.MainThread

@MainThread
@InternalFrameHudApi
public interface FrameHudPanel {

    public fun onConfigChanged()

    public fun onScreenFocused()

    public fun onScreenLost()
}
