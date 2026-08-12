package com.timkrest.framehud

import android.app.Application
import com.timkrest.framehud.internal.StartupProvider

/**
 * Installs [FrameHud] before the first activity is created. Remove it from the merged manifest to
 * opt out and call [FrameHud.install] yourself.
 */
internal class FrameHudInstaller : StartupProvider() {

    override fun onStartup(application: Application) {
        FrameHud.install(application)
    }
}
