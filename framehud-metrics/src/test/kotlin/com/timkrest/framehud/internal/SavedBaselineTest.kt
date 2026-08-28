package com.timkrest.framehud.internal

import com.timkrest.framehud.Baseline
import com.timkrest.framehud.IntervalId
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SavedBaselineTest {

    private var stored: Stored<Baseline?> = Stored.Read(null)

    private var writesFail = false

    private val baseline = SavedBaseline(
        read = { stored },
        write = { _, written ->
            if (writesFail) throw IOException("no room on the device")
            stored = Stored.Read(written)
        },
    )

    @Test
    fun `a baseline this build cannot read is left alone rather than replaced`() {
        val unreadable: Stored<Baseline?> = Stored.Unreadable("it holds a schema this build does not read")
        stored = unreadable

        assertFailsWith<IOException> { baseline.updated(FILE, statsOf(runNumber = 1)) }

        assertSame(unreadable, stored)
    }

    @Test
    fun `a run the write dropped joins the baseline once the file takes it`() {
        baseline.updated(FILE, statsOf(runNumber = 1))
        writesFail = true
        assertFailsWith<IOException> { baseline.updated(FILE, statsOf(runNumber = 2)) }
        writesFail = false

        val saved = baseline.updated(FILE, statsOf(runNumber = 2))

        assertEquals(2, saved.entries.getValue(IntervalId.Session).runs)
    }

    private fun statsOf(runNumber: Int) = MetricsEngine.RunStats(
        runNumber = runNumber,
        session = recordedStats(),
        environment = RECORDED_ENVIRONMENT,
        intervals = listOf(recordedInterval(IntervalId.Session)),
    )

    private companion object {
        val FILE = File("baseline.json")
    }
}
