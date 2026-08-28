package com.timkrest.framehud.internal

import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertIs

class JsonStoreTest {

    private val directory = createTempDirectory().toFile()

    @Test
    fun `a copy that cannot be moved back is not read as no file`() {
        val file = File(directory, "baseline.json").apply {
            mkdirs()
            File(this, "held").writeText("what stops the move")
        }
        File("${file.path}.bak").writeText("the last good copy")

        val stored = readJson(file, maxBytes = 1L shl 20, empty = null) { Parsed.Read(it) }

        assertIs<Stored.Unreadable>(stored)
    }
}
