package com.timkrest.framehud.internal

import com.timkrest.framehud.InternalFrameHudApi
import java.util.Locale

/** A number the panel or a report shows keeps its dot on a device set to a comma locale. */
@InternalFrameHudApi
public fun formatInvariant(template: String, vararg args: Any?): String =
    String.format(Locale.US, template, *args)
