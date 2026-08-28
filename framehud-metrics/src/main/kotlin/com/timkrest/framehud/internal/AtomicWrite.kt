package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@WorkerThread
internal fun writeAtomically(file: File, bytes: ByteArray) {
    file.parentFile?.mkdirs()
    val writing = File("${file.path}.writing")
    var moved = false
    try {
        FileOutputStream(writing).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        moved = writing.renameTo(file)
        if (!moved) throw IOException("Cannot move ${writing.path} onto ${file.path}")
    } finally {
        if (!moved) writing.delete()
    }
}

// Builds through 0.14 wrote with AtomicFile: an interrupted write left the last copy in <name>.bak.
@WorkerThread
internal fun restoreInterruptedWrite(file: File) {
    val copy = File("${file.path}.bak")
    if (copy.exists() && !copy.renameTo(file)) {
        throw IOException("Cannot move ${copy.path} onto ${file.path}")
    }
}
