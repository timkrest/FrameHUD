package io.github.timkrest.framehud.sample

import io.github.timkrest.framehud.FrameHud
import io.github.timkrest.framehud.FrameHudConfig
import org.junit.rules.ExternalResource

/**
 * Applies [configure] for one test and puts the original config back afterwards, so a listener or a
 * setting a test installs cannot leak into the next one.
 */
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
