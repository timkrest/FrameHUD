package com.timkrest.framehud.internal

import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AtomicWriteTest {

    private val directory = createTempDirectory().toFile()

    @Test
    fun `a write reaches a file under a directory no one has created yet`() {
        val file = File(directory, "framehud/history.json")

        writeAtomically(file, "written".encodeToByteArray())

        assertEquals("written", file.readText())
    }

    @Test
    fun `a write that cannot start leaves the file as it was`() {
        val file = File(directory, "baseline.json").apply { writeText("kept") }
        File("${file.path}.writing").apply {
            mkdirs()
            File(this, "held").writeText("what stops the write")
        }

        assertFailsWith<IOException> { writeAtomically(file, "next".encodeToByteArray()) }

        assertEquals("kept", file.readText())
    }

    @Test
    fun `a file an older build left half written is replaced by the copy beside it`() {
        val file = File(directory, "baseline.json").apply { writeText("half a base") }
        File("${file.path}.bak").writeText("the last good copy")

        restoreInterruptedWrite(file)

        assertEquals("the last good copy", file.readText())
        assertFalse(File("${file.path}.bak").exists())
    }

    @Test
    fun `a file with no copy beside it is left as it is`() {
        val file = File(directory, "baseline.json").apply { writeText("what was written") }

        restoreInterruptedWrite(file)

        assertEquals("what was written", file.readText())
    }
}
