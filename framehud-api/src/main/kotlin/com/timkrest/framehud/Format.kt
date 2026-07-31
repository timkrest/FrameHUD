package com.timkrest.framehud

import java.util.Locale

/** Summaries end up in logs and test failures, so they are formatted in a fixed locale. */
internal fun format(template: String, vararg args: Any?): String = String.format(Locale.US, template, *args)
