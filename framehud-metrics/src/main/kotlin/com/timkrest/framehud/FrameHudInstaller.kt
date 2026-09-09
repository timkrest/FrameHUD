// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import android.app.Application
import com.timkrest.framehud.internal.StartupProvider

internal class FrameHudInstaller : StartupProvider() {

    override fun onStartup(application: Application) {
        FrameHud.install(application)
    }
}
