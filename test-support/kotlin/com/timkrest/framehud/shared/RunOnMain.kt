package com.timkrest.framehud.shared

import androidx.test.platform.app.InstrumentationRegistry

internal fun runOnMain(block: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
