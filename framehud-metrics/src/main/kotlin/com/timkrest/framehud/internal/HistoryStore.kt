package com.timkrest.framehud.internal

import android.app.Application
import android.util.Log
import androidx.annotation.WorkerThread
import java.io.File

internal const val HISTORY_FILE_NAME = "history.json"

private const val MAX_HISTORY_BYTES = 1L shl 22

internal fun historyFile(application: Application): File = File(exportDirectory(application), HISTORY_FILE_NAME)

@WorkerThread
internal fun readHistory(file: File): Stored<List<StoredRun>> =
    readJson(file, MAX_HISTORY_BYTES, empty = emptyList(), parse = ::parseHistory)

@WorkerThread
internal fun writeHistory(file: File, runs: List<StoredRun>) {
    val json = runs.fittingHistoryJson()
    if (json == null) {
        Log.w(LOG_TAG, "Keeping the history at ${file.path}: one run alone is over $MAX_HISTORY_BYTES bytes")
        return
    }
    writeAtomically(file, json)
}

internal fun List<StoredRun>.fittingHistoryJson(): ByteArray? = jsonThatFits(
    value = this,
    maxBytes = MAX_HISTORY_BYTES,
    json = List<StoredRun>::toHistoryJson,
    smaller = { kept -> kept.dropLast(1).ifEmpty { null } },
)
