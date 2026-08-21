package com.timkrest.framehud.internal

import android.os.Debug
import android.system.Os
import android.system.OsConstants
import androidx.annotation.WorkerThread
import java.io.File

@WorkerThread
internal interface ProcessProbe {

    fun cpuTimeMs(): Long?

    fun pssMb(): Int?

    fun threads(): Int?

    fun openFiles(): Int?
}

@WorkerThread
internal object SystemProcessProbe : ProcessProbe {

    private val msPerClockTick = MS_PER_SECOND_LONG / Os.sysconf(OsConstants._SC_CLK_TCK).coerceAtLeast(1L)

    override fun cpuTimeMs(): Long? {
        val afterComm = read("/proc/self/stat")?.substringAfterLast(')')?.trim()?.split(' ') ?: return null
        val userTicks = afterComm.getOrNull(UTIME_INDEX_AFTER_COMM)?.toLongOrNull() ?: return null
        val systemTicks = afterComm.getOrNull(STIME_INDEX_AFTER_COMM)?.toLongOrNull() ?: return null
        return (userTicks + systemTicks) * msPerClockTick
    }

    override fun pssMb(): Int? = runCatching { Debug.getPss() }
        .getOrNull()
        ?.takeIf { it > 0L }
        ?.let { (it / KB_PER_MB).toInt() }

    override fun threads(): Int? = count("/proc/self/task")

    override fun openFiles(): Int? = count("/proc/self/fd")

    private fun count(path: String): Int? = runCatching { File(path).list()?.size }.getOrNull()

    private fun read(path: String): String? = runCatching { File(path).readText() }.getOrNull()

    private const val UTIME_INDEX_AFTER_COMM = 11
    private const val STIME_INDEX_AFTER_COMM = 12
    private const val MS_PER_SECOND_LONG = 1_000L
    private const val KB_PER_MB = 1024L
}
