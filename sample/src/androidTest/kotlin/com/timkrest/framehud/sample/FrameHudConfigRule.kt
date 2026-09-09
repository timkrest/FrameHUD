// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.sample

import com.timkrest.framehud.FrameHud
import com.timkrest.framehud.FrameHudConfig
import com.timkrest.framehud.shared.runOnMain
import org.junit.rules.ExternalResource

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
