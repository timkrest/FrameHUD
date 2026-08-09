package com.timkrest.framehud.sample

import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudConfig
import org.junit.rules.ExternalResource

/** Applies [configure] for one test, then restores the previous config. */
class FrameHudConfigRule(private val configure: (FrameHudConfig) -> FrameHudConfig = { it }) : ExternalResource() {

    private lateinit var original: FrameHudConfig

    override fun before() {
        runOnMain {
            original = FrameHud.config
            FrameHud.config = configure(original)
        }
    }

    override fun after() {
        runOnMain { FrameHud.config = original }
    }
}
