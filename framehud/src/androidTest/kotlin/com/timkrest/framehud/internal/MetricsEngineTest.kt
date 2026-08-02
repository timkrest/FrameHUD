package com.timkrest.framehud.internal

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timkrest.framehud.FrameHudConfig
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MetricsEngineTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Threads are named per test: they outlive the test that started them, and names are compared. */
    private val threadName = "framehud-engine-${NEXT_ID.getAndIncrement()}"

    private var config = FrameHudConfig(metricsThreadName = threadName)

    private val engine = MetricsEngine(config = { config })

    @After
    fun stopEngine() {
        onMainThread { engine.stop() }
    }

    @Test
    fun thereAreNoSessionStatsBeforeTheEngineStarts() {
        assertNull(engine.awaitSessionStats(TIMEOUT_MS), "an engine that never started answered")
    }

    @Test
    fun theAggregatesStayReadableAfterStop() {
        onMainThread { engine.start(context) }
        assertNotNull(engine.awaitSessionStats(TIMEOUT_MS), "nothing was collecting")

        onMainThread { engine.stop() }

        assertNotNull(engine.awaitSessionStats(TIMEOUT_MS), "the aggregates went away with the last activity")
    }

    @Test
    fun renamingTheMetricsThreadRetiresTheOldOne() {
        onMainThread { engine.start(context) }
        assertNotNull(engine.awaitSessionStats(TIMEOUT_MS), "nothing was collecting")

        config = config.copy(metricsThreadName = "$threadName-renamed")
        onMainThread { engine.applyConfig(config) }

        assertNotNull(engine.awaitSessionStats(TIMEOUT_MS), "the renamed thread answers nothing")
        assertTrue(awaitThreadGone(threadName), "the retired metrics thread is still running")
    }

    private fun onMainThread(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }

    private fun awaitThreadGone(name: String): Boolean {
        val deadlineMs = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            if (Thread.getAllStackTraces().keys.none { it.name == name }) return true
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    private companion object {
        val NEXT_ID = AtomicInteger()

        const val TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 10L
    }
}
