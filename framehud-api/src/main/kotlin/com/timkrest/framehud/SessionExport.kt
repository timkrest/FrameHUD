// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud

import java.io.File

/** The same session report in two formats, written by `FrameHud.exportSession`. */
@ConsistentCopyVisibility
public data class SessionExport private constructor(
    /** Machine-readable report for CI. The schema is versioned by its top-level `schema` field. */
    val json: File,
    /** Self-contained human-readable report. Opens without a network connection. */
    val html: File,
) {
    public companion object {
        @InternalFrameHudApi
        public fun of(json: File, html: File): SessionExport = SessionExport(json = json, html = html)
    }
}
