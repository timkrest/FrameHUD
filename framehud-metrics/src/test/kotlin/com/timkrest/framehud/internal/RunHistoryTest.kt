// Copyright 2026 Timofey Krestyanov
// SPDX-License-Identifier: Apache-2.0
package com.timkrest.framehud.internal

import com.timkrest.framehud.ExitReason
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

    private var ended: () -> List<RunExit> = ::emptyList

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

        assertFailsWith<IOException> { runBlocking { history.recorded(FILE, runId = runId(1)) } }
    }

    @Test
    fun `the run in progress is not history`() {
        history.record(runNumber = 1, recordedAtEpochMs = 100L)

        assertEquals(emptyList(), runBlocking { history.recorded(FILE, runId = runId(1)) })
    }

    @Test
    fun `a run the process before numbered the same is history`() {
        history.record(runNumber = 1, recordedAtEpochMs = 100L)
        val nextProcess = runHistory(Dispatchers.Unconfined)

        val read = runBlocking { nextProcess.recorded(FILE, runId = "4e5f6a7b:1") }

        assertEquals(listOf(100L), read.map { it.recordedAtEpochMs })
    }

    @Test
    fun `reading the history gives an ended run its exit and keeps it in the file`() {
        stored = Stored.Read(listOf(storedRun(runId = "before:1", recordedAtEpochMs = 100L)))
        val exit = processExit(endedAtEpochMs = 300L)
        ended = { listOf(RunExit(runId = "before:1", exit = exit)) }

        val read = runBlocking { history.recorded(FILE, runId = runId(1)) }

        assertEquals(exit, read.single().exit)
        assertEquals(exit, (stored as Stored.Read).value.single().run.exit)
    }

    @Test
    fun `the first write of a process keeps the exits it found beside its own run`() {
        stored = Stored.Read(listOf(storedRun(runId = "before:1", recordedAtEpochMs = 100L)))
        ended = { listOf(RunExit(runId = "before:1", exit = processExit(endedAtEpochMs = 300L))) }

        history.record(runNumber = 1, recordedAtEpochMs = 400L)

        assertEquals(listOf(null, ExitReason.ANR), (stored as Stored.Read).value.map { it.run.exit?.reason })
    }

    @Test
    fun `a system that will not say how processes ended costs no run`() {
        ended = { throw SecurityException("not this package") }

        history.record(runNumber = 1, recordedAtEpochMs = 100L)

        assertEquals(listOf(100L), recordedAt())
    }

    private fun runHistory(dispatcher: CoroutineDispatcher) = RunHistory(
        queue = CoroutineScope(dispatcher + CoroutineExceptionHandler { _, error -> failure = error }),
        read = { stored },
        write = { _, runs -> stored = Stored.Read(runs) },
        exits = { ended() },
    )

    private fun RunHistory.record(runNumber: Int, recordedAtEpochMs: Long, keptRuns: Int = 5) {
        record(
            keptRuns = keptRuns,
            runId = runId(runNumber),
            file = { FILE },
            run = { recordedRun(recordedAtEpochMs = recordedAtEpochMs) },
        )
    }

    private fun runId(runNumber: Int): String = "$PROCESS:$runNumber"

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
        const val PROCESS = "0a1b2c3d"
    }
}
