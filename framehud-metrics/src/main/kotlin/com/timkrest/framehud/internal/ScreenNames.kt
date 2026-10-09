// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import android.app.Activity
import androidx.annotation.MainThread
import com.timkrest.framehud.FrameHudConfig

@MainThread
internal class ScreenNames(
    private val config: () -> FrameHudConfig,
    private val fragmentOf: (Activity) -> String?,
) {

    fun of(activity: Activity): String {
        val fragment = if (config().nameScreensByFragment) fragmentOf(activity) else null
        return fragment?.takeIf(::nameStandsApart) ?: activity.javaClass.simpleName
    }
}
