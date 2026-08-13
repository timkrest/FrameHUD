package com.timkrest.framehud

import java.io.File

/** The same session report in two formats, written by `FrameHud.exportSession`. */
public data class SessionExport(
    /** Machine-readable report for CI. The schema is versioned by its top-level `schema` field. */
    val json: File,
    /** Self-contained human-readable report. Opens without a network connection. */
    val html: File,
)
