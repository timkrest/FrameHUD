package com.timkrest.framehud.internal

import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.timkrest.framehud.Baseline
import java.io.File
import java.io.IOException

@AnyThread
internal class SavedBaseline(
    private val read: (File) -> Stored<Baseline?> = ::readBaseline,
    private val write: (File, Baseline) -> Unit = ::writeBaseline,
) {

    private val lock = Any()

    private var savedRunNumber: Int? = null

    @WorkerThread
    fun updated(file: File, stats: MetricsEngine.RunStats): Baseline = synchronized(lock) {
        val kept = baselineIn(file)
        if (stats.runNumber == savedRunNumber && kept != null) {
            Log.w(LOG_TAG, "This session is already in the baseline; reset before measuring the next run")
            return@synchronized kept
        }
        val updated = (kept ?: Baseline(stats.environment, emptyMap()))
            .updatedWith(stats.environment, stats.intervals)
        write(file, updated)
        savedRunNumber = stats.runNumber
        updated
    }

    @WorkerThread
    fun stored(file: File): Baseline? = synchronized(lock) {
        when (val stored = read(file)) {
            is Stored.Read -> stored.value
            is Stored.Unreadable -> {
                Log.w(LOG_TAG, "Ignoring the baseline at ${file.path}: ${stored.reason}", stored.cause)
                null
            }
        }
    }

    private fun baselineIn(file: File): Baseline? = when (val stored = read(file)) {
        is Stored.Read -> stored.value
        is Stored.Unreadable ->
            throw IOException("Not saving a baseline over the one at ${file.path}: ${stored.reason}", stored.cause)
    }
}
