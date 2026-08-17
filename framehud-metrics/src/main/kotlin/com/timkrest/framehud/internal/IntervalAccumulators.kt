package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalLevel

@WorkerThread
internal class IntervalAccumulators(
    private val clock: MetricsClock,
    private val isEmulator: Boolean,
) {

    private val session = SessionAccumulator(clock, isEmulator)

    private val screen = SessionAccumulator(clock, isEmulator)

    private var mark: SessionAccumulator? = null

    private val screenTotals = IntervalTotals(clock, isEmulator, IntervalId::Screen)

    private val markTotals = IntervalTotals(clock, isEmulator, IntervalId::Mark)

    var screenName: String? = null
        private set

    private var latestThermalLevel: ThermalLevel? = null

    private var latestBattery: BatterySample? = null

    private inline fun forEachActive(action: (SessionAccumulator) -> Unit) {
        action(session)
        action(screen)
        mark?.let(action)
        screenTotals.current?.let(action)
        markTotals.current?.let(action)
    }

    fun addFrame(durationsMs: FloatArray, overrunMs: Float, refreshRateHz: Float, frameBudgetMs: Float) =
        forEachActive {
            it.addFrame(
                durationsMs = durationsMs,
                overrunMs = overrunMs,
                refreshRateHz = refreshRateHz,
                frameBudgetMs = frameBudgetMs,
            )
        }

    fun addDroppedReports(count: Int) = forEachActive { it.addDroppedReports(count) }

    fun addThermalLevel(level: ThermalLevel) {
        latestThermalLevel = level
        forEachActive { it.addThermalLevel(level) }
    }

    fun addBattery(sample: BatterySample) {
        latestBattery = sample
        forEachActive { it.addBattery(sample) }
    }

    fun addSlowListener(callMs: Float) = forEachActive { it.addSlowListener(callMs) }

    fun startCollecting(label: String? = null) {
        session.startCollecting()
        beginScreen(label)
    }

    fun stopCollecting() {
        session.stopCollecting()
        screen.stopCollecting()
        screenTotals.pause()
        mark?.stopCollecting()
        markTotals.pause()
        latestThermalLevel = null
        latestBattery = null
    }

    fun restartScreen(label: String? = null): SessionStats {
        screen.stopCollecting()
        val ended = screen.stats()
        beginScreen(label)
        return ended
    }

    private fun beginScreen(label: String?) {
        screenName = label
        screen.clear()
        screen.startCollecting()
        seedEnvironment(screen)
        screenTotals.begin(label)?.let(::seedEnvironment)
    }

    fun beginMark(name: String) {
        mark = SessionAccumulator(clock, isEmulator).apply { startCollecting() }.also(::seedEnvironment)
        markTotals.begin(name)?.let(::seedEnvironment)
    }

    fun endMark(): SessionStats? {
        markTotals.end()
        val ended = mark ?: return null
        mark = null
        ended.stopCollecting()
        return ended.stats()
    }

    private fun seedEnvironment(accumulator: SessionAccumulator) {
        latestThermalLevel?.let(accumulator::addThermalLevel)
        latestBattery?.let(accumulator::addBattery)
    }

    fun sessionStats(): SessionStats = session.stats()

    fun screenStats(): SessionStats = screen.stats()

    fun intervals(): List<IntervalStats> = buildList {
        add(IntervalStats(IntervalId.Session, session.stats(), session.frameBudgetMs()))
        addAll(screenTotals.intervals())
        addAll(markTotals.intervals())
    }

    fun clear() {
        session.clear()
        screen.clear()
        mark?.clear()
        screenTotals.clear()
        markTotals.clear()
        forEachActive(::seedEnvironment)
    }
}
