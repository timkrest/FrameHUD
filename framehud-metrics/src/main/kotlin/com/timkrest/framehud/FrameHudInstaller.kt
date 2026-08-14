package com.timkrest.framehud

import android.app.Application
import com.timkrest.framehud.internal.StartupProvider

/** Runs as a [StartupProvider], so [FrameHud] is installed before the first activity is created. */
internal class FrameHudInstaller : StartupProvider() {

    override fun onStartup(application: Application) {
        FrameHud.install(application)
    }
}
