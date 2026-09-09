// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.ui

import com.timkrest.framehud.IntervalReport

internal fun buildScreenLines(screens: List<IntervalReport>): PanelLines = PanelLines(
    listOf(textRow(SCREEN_COLUMNS_HEADER_LINE, TextHeader)) + when {
        screens.isEmpty() -> listOf(textRow(LABEL_NO_SCREENS, TextHeader))
        else -> listedScreenRows(screens) + hiddenScreensRow(screens)
    },
)

private fun listedScreenRows(screens: List<IntervalReport>): List<PanelLine> =
    screens.take(LISTED_SCREENS).map { screen ->
        textRow(formatScreenLine(screen), jankColor(screen.stats.jankPercent))
    }

private fun hiddenScreensRow(screens: List<IntervalReport>): List<PanelLine> =
    listOfNotNull(
        (screens.size - LISTED_SCREENS).takeIf { it > 0 }?.let { textRow(formatMoreScreens(it), TextHeader) },
    )

private const val LISTED_SCREENS = 8
