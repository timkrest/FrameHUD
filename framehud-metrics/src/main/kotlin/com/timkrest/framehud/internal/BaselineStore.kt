package com.timkrest.framehud.internal

import android.app.Application
import android.util.Log
import androidx.annotation.WorkerThread
import com.timkrest.framehud.Baseline
import com.timkrest.framehud.BaselineEntry
import com.timkrest.framehud.InternalFrameHudApi
import com.timkrest.framehud.IntervalId
import java.io.File

internal const val BASELINE_FILE_NAME = "baseline.json"

private const val MAX_BASELINE_BYTES = 1L shl 20

@InternalFrameHudApi
public fun baselineFile(application: Application): File = File(exportDirectory(application), BASELINE_FILE_NAME)

@WorkerThread
internal fun readBaseline(file: File): Stored<Baseline?> =
    readJson(file, MAX_BASELINE_BYTES, empty = null, parse = ::parseBaseline)

@WorkerThread
internal fun writeBaseline(file: File, baseline: Baseline) {
    val json = baseline.fittingJson()
    if (json == null) {
        Log.w(LOG_TAG, "Keeping the baseline at ${file.path}: one interval alone is over $MAX_BASELINE_BYTES bytes")
        return
    }
    writeAtomically(file, json)
}

internal fun Baseline.fittingJson(): ByteArray? = jsonThatFits(
    value = entries,
    maxBytes = MAX_BASELINE_BYTES,
    json = { kept -> Baseline(environment, kept).toJson() },
    smaller = { kept -> kept.withoutTheLeastMeasured() },
)

private fun Map<IntervalId, BaselineEntry>.withoutTheLeastMeasured(): Map<IntervalId, BaselineEntry>? =
    if (size <= 1) null else this - keys.minWith(compareBy({ getValue(it).runs }, { it.key() }))
