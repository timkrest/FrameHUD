package com.timkrest.framehud.internal

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.timkrest.framehud.metrics.R

/** Subclassed so the merged manifest never collides with the app's own `FileProvider` entry. */
internal class ExportFileProvider : FileProvider(R.xml.framehud_export_paths)

internal fun exportAuthority(context: Context): String {
    val component = ComponentName(context, ExportFileProvider::class.java)
    val packageManager = context.packageManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getProviderInfo(component, PackageManager.ComponentInfoFlags.of(0L))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getProviderInfo(component, 0)
    }.authority
}
