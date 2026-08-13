package com.timkrest.framehud.internal

import androidx.core.content.FileProvider
import com.timkrest.framehud.metrics.R

/** Subclassed so the merged manifest never collides with the app's own `FileProvider` entry. */
internal class ExportFileProvider : FileProvider(R.xml.framehud_export_paths)
