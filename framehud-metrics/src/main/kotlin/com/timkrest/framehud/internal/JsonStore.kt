package com.timkrest.framehud.internal

import android.util.Log
import androidx.annotation.WorkerThread
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal sealed interface Stored<out T> {
    class Read<T>(val value: T) : Stored<T>
    class Unreadable(val reason: String, val cause: IOException? = null) : Stored<Nothing>
}

internal sealed interface Parsed<out T> {
    class Read<T>(val value: T) : Parsed<T>
    class Unreadable(val reason: String) : Parsed<Nothing>
    class Rejected(val reason: String) : Parsed<Nothing>
}

@WorkerThread
internal fun <T> readJson(file: File, maxBytes: Long, empty: T, parse: (String) -> Parsed<T>): Stored<T> {
    try {
        restoreInterruptedWrite(file)
    } catch (e: IOException) {
        return Stored.Unreadable("the copy an interrupted write left cannot be moved back", e)
    }
    if (file.length() > maxBytes) {
        Log.w(LOG_TAG, "Ignoring ${file.path}: it is over $maxBytes bytes")
        return Stored.Read(empty)
    }
    val text = try {
        file.readBytes().decodeToString()
    } catch (e: IOException) {
        if (e is FileNotFoundException && !file.exists()) return Stored.Read(empty)
        return Stored.Unreadable("it cannot be opened", e)
    }
    return when (val parsed = parse(text)) {
        is Parsed.Read -> Stored.Read(parsed.value)
        is Parsed.Unreadable -> Stored.Unreadable(parsed.reason)
        is Parsed.Rejected -> {
            Log.w(LOG_TAG, "Ignoring ${file.path}: ${parsed.reason}")
            Stored.Read(empty)
        }
    }
}

internal fun <T : Any> jsonThatFits(value: T, maxBytes: Long, json: (T) -> String, smaller: (T) -> T?): ByteArray? {
    var kept: T? = value
    while (kept != null) {
        val bytes = json(kept).encodeToByteArray()
        if (bytes.size <= maxBytes) return bytes
        kept = smaller(kept)
    }
    return null
}
