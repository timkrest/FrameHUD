// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import android.app.Application
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.RecordedRun
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

@AnyThread
internal class RunHistory(
    private val queue: CoroutineScope,
    private val read: (File) -> Stored<List<StoredRun>> = ::readHistory,
    private val write: (File, List<StoredRun>) -> Unit = ::writeHistory,
    private val exits: () -> List<RunExit> = ::emptyList,
) {

    private var exitsRead = false

    fun record(keptRuns: Int, runId: String, file: () -> File, run: () -> RecordedRun) {
        queue.launch {
            val target = file()
            val recorded = StoredRun(runId, run())
            write(target, (listOf(recorded) + withExitsOnce(storedIn(target)).withoutTheRun(runId)).take(keptRuns))
        }
    }

    suspend fun recorded(file: File, runId: String): List<RecordedRun> = queue.async {
        val stored = storedIn(file)
        val runs = withExitsOnce(stored)
        if (runs !== stored) guarded("keeping how earlier processes ended") { write(file, runs) }
        runs
    }.await().withoutTheRun(runId).map { it.run }

    private fun withExitsOnce(stored: List<StoredRun>): List<StoredRun> {
        if (exitsRead) return stored
        exitsRead = true
        return stored.withExits(guarded("reading how earlier processes ended", otherwise = emptyList(), exits))
    }

    private fun storedIn(file: File): List<StoredRun> = when (val stored = read(file)) {
        is Stored.Read -> stored.value
        is Stored.Unreadable ->
            throw IOException("Cannot read the run history at ${file.path}: ${stored.reason}", stored.cause)
    }

    private fun List<StoredRun>.withoutTheRun(runId: String): List<StoredRun> = filterNot { it.runId == runId }
}

internal fun oneAtATime(onFailure: (Throwable) -> Unit): CoroutineScope = CoroutineScope(
    SupervisorJob() +
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "framehud-history").apply { isDaemon = true }
        }.asCoroutineDispatcher() +
        CoroutineExceptionHandler { _, error -> onFailure(error) },
)

@WorkerThread
internal fun measuredRun(application: Application, stats: MetricsEngine.RunStats): RecordedRun {
    val version = appVersion(application)
    return RecordedRun.of(
        recordedAtEpochMs = System.currentTimeMillis(),
        environment = stats.environment,
        appVersionName = version.name,
        appVersionCode = version.code,
        intervals = stats.intervals,
    )
}
