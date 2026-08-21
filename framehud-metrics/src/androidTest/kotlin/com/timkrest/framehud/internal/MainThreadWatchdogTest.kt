package com.timkrest.framehud.internal

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timkrest.framehud.MainThreadBlock
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MainThreadWatchdogTest {

    private val lastTickMs = AtomicLong(SystemClock.uptimeMillis())

    private val watchdog = MainThreadWatchdog(
        mainThread = Thread.currentThread(),
        lastTickMs = lastTickMs::get,
    )

    @After
    fun stopWatchdog() {
        watchdog.stop()
    }

    @Test
    fun aThreadThatStoppedDrawingIsSampledWhereItStands() {
        lastTickMs.set(SystemClock.uptimeMillis() - QUIET_FOR_MS)
        watchdog.startWatching()

        val block = awaitStackTakenHere()

        assertTrue(block.durationMs >= QUIET_FOR_MS, "the block lasted ${block.durationMs} ms")
    }

    @Test
    fun aThreadStillDrawingIsNeverReportedBlocked() {
        watchdog.startWatching()

        repeat(TICKS_TO_KEEP_DRAWING) {
            lastTickMs.set(SystemClock.uptimeMillis())
            SystemClock.sleep(POLL_INTERVAL_MS)
        }

        assertEquals(MainThreadBlock.NONE, watchdog.latestBlock)
    }

    @Test
    fun aStackTakenBeforeTheWatchStoppedNoLongerExplainsJank() {
        lastTickMs.set(SystemClock.uptimeMillis() - QUIET_FOR_MS)
        watchdog.startWatching()
        val block = awaitStackTakenHere()

        watchdog.stopWatching()
        SystemClock.sleep(POLL_INTERVAL_MS * 4)
        assertEquals(
            block.stacksTaken,
            watchdog.latestBlock.stacksTaken,
            "the watch kept sampling after it stopped",
        )

        SystemClock.sleep(BLOCK_GOES_STALE_AFTER_MS)
        assertEquals(MainThreadBlock.NONE, watchdog.latestBlock)
    }

    @Test
    fun aThreadThatDrewAgainStartsTheNextBlockOver() {
        lastTickMs.set(SystemClock.uptimeMillis() - QUIET_FOR_MS)
        watchdog.startWatching()
        awaitStackTakenHere()

        repeat(TICKS_TO_DRAW_AGAIN) {
            lastTickMs.set(SystemClock.uptimeMillis())
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        val ended = watchdog.latestBlock
        lastTickMs.set(SystemClock.uptimeMillis() - SHORT_STALL_MS)

        val next = awaitBlockOtherThan(ended)

        assertTrue(
            next.durationMs < ended.durationMs,
            "the next block carried on from the ${ended.durationMs} ms one that ended: $next",
        )
    }

    @Test
    fun aWatchThatStartsAgainCarriesNothingOfTheStallItLeft() {
        lastTickMs.set(SystemClock.uptimeMillis() - QUIET_FOR_MS)
        watchdog.startWatching()
        val stalled = awaitStackTakenHere()

        watchdog.stopWatching()
        lastTickMs.set(SystemClock.uptimeMillis() - SHORT_STALL_MS)
        watchdog.startWatching()

        val next = awaitBlockOtherThan(stalled)

        assertTrue(
            next.durationMs < stalled.durationMs,
            "the watch resumed the ${stalled.durationMs} ms stall it was stopped in: $next",
        )
    }

    @Test
    fun aWatchThatStartsAgainAnswersWithNoBlockOfTheOneItLeft() {
        lastTickMs.set(SystemClock.uptimeMillis() - QUIET_FOR_MS)
        watchdog.startWatching()
        awaitStackTakenHere()

        watchdog.stopWatching()
        lastTickMs.set(SystemClock.uptimeMillis())
        watchdog.startWatching()

        val deadlineMs = SystemClock.uptimeMillis() + SOONER_THAN_A_BLOCK_GOES_STALE_MS
        while (SystemClock.uptimeMillis() < deadlineMs) {
            if (watchdog.latestBlock == MainThreadBlock.NONE) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        error("the watch still explains jank with the block it took before it stopped")
    }

    @Test
    fun aWatchdogThatGaveUpItsThreadTakesStacksAgainOnceItStartsOver() {
        lastTickMs.set(SystemClock.uptimeMillis() - QUIET_FOR_MS)
        watchdog.startWatching()
        awaitStackTakenHere()

        watchdog.stop()
        SystemClock.sleep(BLOCK_GOES_STALE_AFTER_MS)
        lastTickMs.set(SystemClock.uptimeMillis() - QUIET_FOR_MS)
        watchdog.startWatching()

        awaitStackTakenHere()
    }

    private fun awaitBlockOtherThan(block: MainThreadBlock): MainThreadBlock {
        val deadlineMs = SystemClock.uptimeMillis() + AWAIT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadlineMs) {
            val latest = watchdog.latestBlock
            if (latest != block) return latest
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        error("the watch took no stack of the thread it left quiet for $SHORT_STALL_MS ms")
    }

    private fun awaitStackTakenHere(): MainThreadBlock {
        val deadlineMs = SystemClock.uptimeMillis() + AWAIT_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadlineMs) {
            val block = watchdog.latestBlock
            if (block.calls.any { it.name.contains(TAKEN_HERE) }) return block
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        error("no stack the watch took names $TAKEN_HERE, where this thread stood")
    }

    private companion object {
        const val TAKEN_HERE = "awaitStackTakenHere"
        const val QUIET_FOR_MS = 2_000L
        const val SHORT_STALL_MS = 400L
        const val POLL_INTERVAL_MS = 50L
        const val TICKS_TO_KEEP_DRAWING = 20
        const val TICKS_TO_DRAW_AGAIN = 8
        const val BLOCK_GOES_STALE_AFTER_MS = 2_100L
        const val SOONER_THAN_A_BLOCK_GOES_STALE_MS = 1_000L
        const val AWAIT_TIMEOUT_MS = 5_000L
    }
}
