// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import com.timkrest.framehud.InternalFrameHudApi
import java.util.Locale

@InternalFrameHudApi
public fun formatInvariant(template: String, vararg args: Any?): String =
    String.format(Locale.US, template, *args)
