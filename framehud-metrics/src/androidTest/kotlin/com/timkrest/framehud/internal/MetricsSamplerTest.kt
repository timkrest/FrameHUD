package com.timkrest.framehud.internal

import android.os.SystemClock
import android.view.Window
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MetricsSamplerTest {

    private val uniqueThreadName = "framehud-sampler-${NEXT_ID.getAndIncrement()}"

    private val started = mutableListOf<MetricsSampler>()

    @After
    fun retireSamplers() {
        started.forEach(MetricsSampler::quit)
    }

    @Test
    fun postedWorkRunsOnTheNamedMetricsThread() {
        val sampler = startSampler()
        val ranOn = ArrayBlockingQueue<String>(1)

        assertTrue(sampler.post { ranOn.put(Thread.currentThread().name) }, "the task was rejected")

        assertEquals(uniqueThreadName, ranOn.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS))
    }

    @Test
    fun aTaskThatThrowsLeavesTheThreadRunning() {
        val sampler = startSampler()
        sampler.post { error("the metrics thread must survive this") }

        val survived = CountDownLatch(1)
        sampler.post(survived::countDown)

        assertTrue(survived.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "the thread died with the failing task")
    }

    @Test
    fun stoppingRetiresEveryTickThatWasStarted() {
        val ticks = AtomicInteger()
        val sampler = startSampler(onTick = ticks::incrementAndGet)

        sampler.startTicking()
        sampler.startTicking()
        awaitTicks(ticks, atLeast = 2)

        sampler.stopTicking()
        SystemClock.sleep(TICK_INTERVAL_MS * 3)
        val stoppedAt = ticks.get()
        SystemClock.sleep(TICK_INTERVAL_MS * 10)

        assertEquals(stoppedAt, ticks.get(), "a tick outlived stopTicking")
    }

    @Test
    fun aReplacementWaitsForTheThreadItRetires() {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val retiring = startSampler(threadName = "$uniqueThreadName-old")
        retiring.post {
            SystemClock.sleep(HANDOVER_WORK_MS)
            order += "old"
        }
        retiring.quit()

        val replacement = startSampler(threadName = "$uniqueThreadName-new", previousSampler = retiring)
        val ran = CountDownLatch(1)
        replacement.post {
            order += "new"
            ran.countDown()
        }

        assertTrue(ran.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "the replacement never ran")
        assertEquals(listOf("old", "new"), order.toList(), "the two metrics threads overlapped")
    }

    @Test
    fun quittingRunsWhatIsQueuedAndRetiresTheThread() {
        val sampler = startSampler()
        val ran = CountDownLatch(1)
        sampler.post(ran::countDown)

        sampler.quit()

        assertTrue(ran.await(TIMEOUT_MS, TimeUnit.MILLISECONDS), "queued work was dropped")
        assertTrue(awaitThreadGone(uniqueThreadName), "the metrics thread outlived quit()")
    }

    private fun startSampler(
        threadName: String = uniqueThreadName,
        onTick: () -> Unit = {},
        previousSampler: MetricsSampler? = null,
    ): MetricsSampler = MetricsSampler(
        threadName = threadName,
        listener = IDLE_LISTENER,
        tickIntervalMs = { TICK_INTERVAL_MS },
        onTick = onTick,
        previousSampler = previousSampler,
    ).also { started += it }

    private fun awaitTicks(ticks: AtomicInteger, atLeast: Int) {
        awaitUntil { ticks.get() >= atLeast }
        assertTrue(ticks.get() >= atLeast, "the tick never ran")
    }

    private fun awaitThreadGone(name: String): Boolean =
        awaitUntil { Thread.getAllStackTraces().keys.none { it.name == name } }

    private fun awaitUntil(condition: () -> Boolean): Boolean {
        val deadlineMs = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            if (condition()) return true
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        return condition()
    }

    private companion object {
        val NEXT_ID = AtomicInteger()
        val IDLE_LISTENER = Window.OnFrameMetricsAvailableListener { _, _, _ -> }

        const val TICK_INTERVAL_MS = 25L
        const val HANDOVER_WORK_MS = 150L
        const val TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 10L
    }
}
