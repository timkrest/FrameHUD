package com.timkrest.framehud

import android.app.Application
import com.timkrest.framehud.internal.StartupProvider

internal class FrameHudInstaller : StartupProvider() {

    override fun onStartup(application: Application) {
        FrameHud.install(application)
    }
}
