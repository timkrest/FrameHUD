package com.timkrest.framehud.internal

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

internal fun isMainProcess(context: Context): Boolean {
    val processName = currentProcessName() ?: return true
    return processName == context.packageName
}

private fun currentProcessName(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    Application.getProcessName()
} else {
    readCmdline()
}

private fun readCmdline(): String? = try {
    File(CMDLINE_PATH).readText().substringBefore(Char(0)).trim().takeIf { it.isNotEmpty() }
} catch (e: Exception) {
    null
}

private const val CMDLINE_PATH = "/proc/self/cmdline"
