package com.timkrest.framehud.internal

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.JankCause
import com.timkrest.framehud.JankDiagnosis
import com.timkrest.framehud.JankSeverity
import com.timkrest.framehud.MainThreadBlock
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PipelineStage
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IncidentRecorderTest {

    private val clock = TestMetricsClock()

    private val recorder = IncidentRecorder(
        clock = clock,
        isEmulator = false,
        framesBeforeTrigger = 4,
        framesAfterTrigger = 2,
        keptIncidents = 3,
    )

    @Test
    fun `an incident keeps the frames around the trigger and where the trigger fell`() {
        clock.epoch = 1_700_000_000_000L
        addFrames(10f, 12f)
        arm()
        addFrames(800f, 14f)

        val worst = recorder.incidents().single().worst
        assertEquals(1_700_000_000_000L, worst.triggeredAtEpochMs)
        assertEquals(2, worst.framesBeforeTrigger)
        assertContentEquals(listOf(10f, 12f, 800f, 14f), worst.frames.totals())
    }

    @Test
    fun `only the frames the window holds survive`() {
        addFrames(1f, 2f, 3f, 4f, 5f, 6f)
        arm()
        addFrames(7f, 8f)

        val worst = recorder.incidents().single().worst
        assertContentEquals(listOf(3f, 4f, 5f, 6f, 7f, 8f), worst.frames.totals())
        assertEquals(4, worst.framesBeforeTrigger)
    }

    @Test
    fun `a trigger while a window is open does not start a second incident`() {
        addFrames(10f)
        arm(screen = "Home")
        arm(screen = "Cart")
        addFrames(10f, 10f)

        assertEquals("Home", recorder.incidents().single().worst.trigger.screen)
    }

    @Test
    fun `a window closes once its trailing frames arrive`() {
        arm(screen = "Home")
        addFrames(10f, 10f)
        arm(screen = "Cart")
        addFrames(10f, 10f)

        assertEquals(setOf("Home", "Cart"), recorder.incidents().map { it.worst.trigger.screen }.toSet())
    }

    @Test
    fun `a window whose app stopped drawing closes on a tick`() {
        addFrames(900f)
        arm(screen = "Home")

        clock.elapsedMs += 1_000L
        recorder.onTick()
        arm(screen = "Cart")

        assertEquals(2, recorder.incidents().size)
    }

    @Test
    fun `a window still filling closes with the frames it has`() {
        addFrames(10f)
        arm()

        assertEquals(1, recorder.incidents().single().worst.frames.size)
    }

    @Test
    fun `frames from the screen that ended stay out of the next incident`() {
        addFrames(10f, 12f)
        recorder.endScreen()
        addFrames(14f)
        arm()

        assertContentEquals(listOf(14f), recorder.incidents().single().worst.frames.totals())
    }

    @Test
    fun `the stats measure the window alone`() {
        addFrames(10f)
        arm()
        addFrame(totalMs = 800f)
        recorder.addDroppedReports(3)
        addFrame(totalMs = 10f)

        val stats = recorder.incidents().single().worst.stats
        assertEquals(3, stats.frames)
        assertEquals(1, stats.frozenFrames)
        assertEquals(3, stats.droppedReports)
        assertEquals(100f / 3f, stats.jankPercent)
        assertTrue(stats.durationMs > 0L)
    }

    @Test
    fun `the same case on the same screen is counted once and spans its occurrences`() {
        clock.epoch = 1_000L
        record(screen = "Home", worstFrameMs = 40f)
        clock.epoch = 5_000L
        record(screen = "Home", worstFrameMs = 30f)

        val incident = recorder.incidents().single()
        assertEquals(2, incident.occurrences)
        assertEquals(1_000L, incident.firstAtEpochMs)
        assertEquals(5_000L, incident.lastAtEpochMs)
    }

    @Test
    fun `the occurrence that lost the most time is the one kept`() {
        record(screen = "Home", worstFrameMs = 40f)
        record(screen = "Home", worstFrameMs = 300f)
        record(screen = "Home", worstFrameMs = 60f)

        val worst = recorder.incidents().single().worst
        assertEquals(300f - FRAME_BUDGET_MS, worst.stats.lostTimeMs)
    }

    @Test
    fun `the same screen blamed for something else is a case of its own`() {
        armBurst(JankCause.Stage(PipelineStage.CPU, averageMs = 12f))
        addFrames(10f, 10f)
        armBurst(JankCause.Thermal(ThermalLevel.SEVERE))
        addFrames(10f, 10f)

        assertEquals(2, recorder.incidents().size)
    }

    @Test
    fun `readings apart, the same diagnosis is the same case`() {
        armBurst(JankCause.Stage(PipelineStage.CPU, averageMs = 12f))
        addFrames(10f, 10f)
        armBurst(JankCause.Stage(PipelineStage.CPU, averageMs = 31f))
        addFrames(10f, 10f)

        assertEquals(2, recorder.incidents().single().occurrences)
    }

    @Test
    fun `a new case has to be worse than the mildest one kept`() {
        record(screen = "A", worstFrameMs = 100f)
        record(screen = "B", worstFrameMs = 200f)
        record(screen = "C", worstFrameMs = 300f)

        record(screen = "D", worstFrameMs = 50f)
        assertEquals(listOf("C", "B", "A"), recorder.incidents().map { it.worst.trigger.screen })

        record(screen = "E", worstFrameMs = 400f)
        assertEquals(listOf("E", "C", "B"), recorder.incidents().map { it.worst.trigger.screen })
    }

    @Test
    fun `clearing forgets the incidents and the frames behind them`() {
        addFrames(10f)
        arm()
        addFrames(10f, 10f)

        recorder.clear()

        assertEquals(emptyList(), recorder.incidents())
    }

    private fun record(screen: String, worstFrameMs: Float) {
        recorder.endScreen()
        addFrame(totalMs = worstFrameMs)
        arm(screen = screen)
        addFrames(10f, 10f)
    }

    private fun addFrames(vararg totalsMs: Float) = totalsMs.forEach { addFrame(totalMs = it) }

    private fun addFrame(totalMs: Float) {
        clock.nanos += FRAME_BUDGET_MS.toLong() * NS_PER_MS_LONG
        clock.elapsedMs += FRAME_BUDGET_MS.toLong()
        recorder.addFrame(
            durationsMs = phaseDurationsMs(totalMs = totalMs),
            overrunMs = totalMs - FRAME_BUDGET_MS,
            refreshRateHz = 60f,
            frameBudgetMs = FRAME_BUDGET_MS,
            endNs = clock.nanos,
        )
    }

    private fun arm(screen: String = "Home", battery: BatterySample = BatterySample.UNKNOWN) =
        arm(FrameHudEvent.FrozenFrames(count = 1, screen = screen, mark = null), battery)

    @Test
    fun `a screen change keeps the window an incident was still filling`() {
        addFrames(10f)
        arm()
        addFrames(800f)
        recorder.endScreen()

        val worst = recorder.incidents().single().worst
        assertContentEquals(listOf(10f, 800f), worst.frames.totals())
        assertEquals(1, worst.framesBeforeTrigger)
    }

    @Test
    fun `an incident drawn in power save says so`() {
        addFrames(10f)
        arm(battery = BatterySample(powerSaveMode = true, levelPercent = 80))
        addFrames(10f, 10f)

        val issues = recorder.incidents().single().worst.stats.confidence.issues
        assertEquals(
            listOf(ConfidenceIssue.LowBattery(powerSaveMode = true, levelPercent = 80)),
            issues.filterIsInstance<ConfidenceIssue.LowBattery>(),
        )
    }

    private fun armBurst(cause: JankCause) = arm(
        FrameHudEvent.JankBurst(
            diagnosis = JankDiagnosis.of(
                cause = cause,
                severity = JankSeverity.WARNING,
                jankPercent = 10f,
                worstFrameMs = 30f,
                frameBudgetMs = FRAME_BUDGET_MS,
            ),
            screen = "Home",
            mark = null,
        ),
    )

    private fun arm(
        trigger: FrameHudEvent.IncidentTrigger,
        battery: BatterySample = BatterySample.UNKNOWN,
        counters: List<CounterReading> = emptyList(),
        mainThreadBlock: MainThreadBlock = MainThreadBlock.NONE,
    ) = recorder.arm(
        trigger = trigger,
        readings = IncidentReadings(
            memory = MemoryStats.EMPTY,
            thermal = ThermalStats.EMPTY,
            process = ProcessStats.EMPTY,
            battery = battery,
            counters = counters,
            mainThreadBlock = mainThreadBlock,
        ),
    )

    private companion object {
        const val FRAME_BUDGET_MS = 16f
    }
}

private fun FrameHistory.totals(): List<Float> = List(size) { totalMsAt(it) }
