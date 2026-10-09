// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.AnyThread
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread

/** Leaves the shown state with the system while the process lives, and reads it back once it has died. */
@AnyThread
internal class ProcessExits(post: (task: () -> Unit) -> Boolean) {

    @Volatile
    private var application: Application? = null

    private val summaries = LatestOnly<ByteArray>(post) { summary ->
        val application = application
        if (application != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) leaveSummary(application, summary)
    }

    fun bind(application: Application) {
        this.application = application
    }

    fun leave(summary: ByteArray) = summaries.offer(summary)

    @WorkerThread
    fun read(): List<RunExit> {
        val application = application ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) exitsOf(application) else emptyList()
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun exitsOf(context: Context): List<RunExit> {
    val process = currentProcessName() ?: context.packageName
    return context.activityManager()?.getHistoricalProcessExitReasons(context.packageName, 0, 0).orEmpty()
        .filter { it.processName == process }
        .mapNotNull { RunExit.of(it.reason, it.description, it.timestamp, it.processStateSummary) { it.mainThreadStack() } }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun ApplicationExitInfo.mainThreadStack(): List<String> {
    if (reason != ApplicationExitInfo.REASON_ANR) return emptyList()
    return guarded("reading the ANR trace", otherwise = emptyList()) {
        traceInputStream?.bufferedReader()?.use { mainThreadStackOf(it.lineSequence()) }.orEmpty()
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun leaveSummary(context: Context, summary: ByteArray) {
    guarded("leaving the screen shown for the system to keep") {
        context.activityManager()?.setProcessStateSummary(summary)
    }
}

private fun Context.activityManager(): ActivityManager? = getSystemService(ActivityManager::class.java)
