// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import android.app.Activity
import com.timkrest.framehud.FrameHudConfig
import org.junit.Test
import kotlin.test.assertEquals

class ScreenNamesTest {

    private var config = FrameHudConfig()

    private var fragment: String? = "CartFragment"

    private val names = ScreenNames(config = { config }, fragmentOf = { fragment })

    private val activity = CheckoutActivity()

    @Test
    fun `a screen is named after the fragment on it`() {
        assertEquals("CartFragment", names.of(activity))
    }

    @Test
    fun `an activity showing no fragment keeps its own name`() {
        fragment = null

        assertEquals("CheckoutActivity", names.of(activity))
    }

    @Test
    fun `turning fragment names off keeps the activity's name`() {
        config = config.copy(nameScreensByFragment = false)

        assertEquals("CheckoutActivity", names.of(activity))
    }

    @Test
    fun `a fragment name a trace could not tell apart falls back to the activity`() {
        fragment = "q".repeat(MAX_TRACE_NAME_LENGTH + 1)

        assertEquals("CheckoutActivity", names.of(activity))
    }

    private class CheckoutActivity : Activity()
}
