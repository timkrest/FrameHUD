package com.timkrest.framehud

import android.app.Application
import com.timkrest.framehud.internal.PanelHost
import com.timkrest.framehud.internal.StartupProvider

internal class PanelInstaller : StartupProvider() {

    override fun onStartup(application: Application) {
        FrameHud.attachPanel(PanelHost(application))
    }
}
