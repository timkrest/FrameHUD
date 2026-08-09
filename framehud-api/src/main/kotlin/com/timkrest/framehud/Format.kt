package com.timkrest.framehud

import java.util.Locale

internal fun formatInvariant(template: String, vararg args: Any?): String = String.format(Locale.US, template, *args)
