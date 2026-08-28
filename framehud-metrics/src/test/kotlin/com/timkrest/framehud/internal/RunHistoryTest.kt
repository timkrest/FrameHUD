package com.timkrest.framehud.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class RunHistoryTest {

    private var stored: Stored<List<StoredRun>> = Stored.Read(emptyList())

    private var failure: Throwable? = null

    private val history = runHistory(Dispatchers.Unconfined)

    @Test
    fun `a second write for the run in progress replaces its record rather than adding one`() {
        history.record(runNumber = 1, recordedAtEpochMs = 100L)
        history.record(runNumber = 1, recordedAtEpochMs = 200L)

        assertEquals(listOf(200L), recordedAt())
    }

    @Test
    fun `a reset keeps the run it ended and records the next one beside it`() {
        history.record(runNumber = 1, recordedAtEpochMs = 100L)
        history.record(runNumber = 2, recordedAtEpochMs = 200L)

        assertEquals(listOf(200L, 100L), recordedAt())
    }

    @Test
    fun `the oldest run goes once the file holds as many as it keeps`() {
        repeat(4) { history.record(runNumber = it, recordedAtEpochMs = it + 1L, keptRuns = 2) }

        assertEquals(listOf(4L, 3L), recordedAt())
    }

    @Test
    fun `a write reads the file as it stands when it runs, not when it was asked for`() {
        val queued = Queued()
        val history = runHistory(queued)

        history.record(runNumber = 1, recordedAtEpochMs = 100L)
        history.record(runNumber = 2, recordedAtEpochMs = 200L)
        queued.runAll()

        assertEquals(listOf(200L, 100L), recordedAt())
    }

    @Test
    fun `a history this run cannot read is left alone and the run it dropped is reported`() {
        val unreadable = Stored.Unreadable("it cannot be opened", IOException("the file is busy"))
        stored = unreadable

        history.record(runNumber = 1, recordedAtEpochMs = 100L)

        assertSame(unreadable, stored)
        assertIs<IOException>(failure)
    }

    @Test
    fun `a history this run cannot read is not answered as no history`() {
        stored = Stored.Unreadable("it cannot be opened", IOException("the file is busy"))

        assertFailsWith<IOException> { runBlocking { history.recorded(FILE, runNumber = 1) } }
    }

    @Test
    fun `the run in progress is not history`() {
        history.record(runNumber = 1, recordedAtEpochMs = 100L)

        assertEquals(emptyList(), runBlocking { history.recorded(FILE, runNumber = 1) })
    }

    @Test
    fun `a run the process before numbered the same is history`() {
        history.record(runNumber = 1, recordedAtEpochMs = 100L)
        val nextProcess = runHistory(Dispatchers.Unconfined)

        val read = runBlocking { nextProcess.recorded(FILE, runNumber = 1) }

        assertEquals(listOf(100L), read.map { it.recordedAtEpochMs })
    }

    private fun runHistory(dispatcher: CoroutineDispatcher) = RunHistory(
        queue = CoroutineScope(dispatcher + CoroutineExceptionHandler { _, error -> failure = error }),
        read = { stored },
        write = { _, runs -> stored = Stored.Read(runs) },
    )

    private fun RunHistory.record(runNumber: Int, recordedAtEpochMs: Long, keptRuns: Int = 5) {
        record(
            keptRuns = keptRuns,
            runNumber = runNumber,
            file = { FILE },
            run = { recordedRun(recordedAtEpochMs = recordedAtEpochMs) },
        )
    }

    private fun recordedAt(): List<Long> = (stored as Stored.Read).value.map { it.run.recordedAtEpochMs }

    private class Queued : CoroutineDispatcher() {

        private val waiting = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            waiting.addLast(block)
        }

        fun runAll() {
            while (waiting.isNotEmpty()) waiting.removeFirst().run()
        }
    }

    private companion object {
        val FILE = File("history.json")
    }
}
