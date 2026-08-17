package com.timkrest.framehud.internal

import android.app.Application
import android.util.AtomicFile
import android.util.Log
import androidx.annotation.WorkerThread
import com.timkrest.framehud.Baseline
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal const val BASELINE_FILE_NAME = "baseline.json"

private const val MAX_BASELINE_BYTES = 1L shl 20

internal fun baselineFile(application: Application): File = File(exportDirectory(application), BASELINE_FILE_NAME)

@WorkerThread
internal fun readBaseline(file: File): Baseline? {
    if (file.length() > MAX_BASELINE_BYTES) {
        Log.w(LOG_TAG, "Ignoring the baseline at ${file.path}: it is over $MAX_BASELINE_BYTES bytes")
        return null
    }
    val text = try {
        AtomicFile(file).readFully().decodeToString()
    } catch (_: FileNotFoundException) {
        return null
    } catch (e: IOException) {
        Log.w(LOG_TAG, "Cannot read the baseline at ${file.path}", e)
        return null
    }
    return when (val parsed = parseBaseline(text)) {
        is ParsedBaseline.Read -> parsed.baseline
        is ParsedBaseline.Rejected -> {
            Log.w(LOG_TAG, "Ignoring the baseline at ${file.path}: ${parsed.reason}")
            null
        }
    }
}

@WorkerThread
internal fun writeBaseline(file: File, baseline: Baseline) {
    file.parentFile?.mkdirs()
    val store = AtomicFile(file)
    val stream = store.startWrite()
    try {
        stream.write(baseline.toJson().encodeToByteArray())
        store.finishWrite(stream)
    } catch (e: IOException) {
        store.failWrite(stream)
        throw e
    }
}
