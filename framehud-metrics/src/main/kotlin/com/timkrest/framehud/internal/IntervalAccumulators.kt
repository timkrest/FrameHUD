package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import com.timkrest.framehud.FramePhase
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.ThermalLevel

@WorkerThread
internal class IntervalAccumulators(
    private val clock: MetricsClock,
    private val isEmulator: Boolean,
    budgetsMs: Map<IntervalId, Int>,
) {

    private class ActiveMark(val name: String, val accumulator: SessionAccumulator)

    private val session = SessionAccumulator(clock, isEmulator)

    private val screen = SessionAccumulator(clock, isEmulator)

    private var activeMark: ActiveMark? = null

    private val screenTotals = IntervalTotals(clock, isEmulator, IntervalId::Screen)

    private val markTotals = IntervalTotals(clock, isEmulator, IntervalId::Mark)

    private var sessionBudgetMs: Int? = null

    private var screenBudgetsMs = emptyMap<String, Int>()

    private var markBudgetsMs = emptyMap<String, Int>()

    var screenName: String? = null
        private set

    val activeBudgetMs: Int? get() = budgetMsUnder(screenName, activeMark?.name)

    private var latestThermalLevel: ThermalLevel? = null

    private var latestBattery: BatterySample? = null

    init {
        updateBudgets(budgetsMs)
    }

    fun updateBudgets(budgetsMs: Map<IntervalId, Int>) {
        sessionBudgetMs = budgetsMs[IntervalId.Session]
        screenBudgetsMs = budgetsMs.byNameOf<IntervalId.Screen>()
        markBudgetsMs = budgetsMs.byNameOf<IntervalId.Mark>()
    }

    private fun budgetMsUnder(onScreen: String?, onMark: String?): Int? = onMark?.let { markBudgetsMs[it] }
        ?: onScreen?.let { screenBudgetsMs[it] }
        ?: sessionBudgetMs

    private fun forEachActive(action: (SessionAccumulator) -> Unit) {
        action(session)
        action(screen)
        activeMark?.accumulator?.let(action)
        screenTotals.forEachCollecting(action)
        markTotals.forEachCollecting(action)
    }

    private inline fun forEachOn(fromScreen: String?, action: (SessionAccumulator, budgetMs: Int?) -> Unit) {
        action(session, sessionBudgetMs)
        val screenBudgetMs = budgetMsUnder(fromScreen, onMark = null)
        screenTotals.collecting(fromScreen)?.let { action(it, screenBudgetMs) }
        if (fromScreen != screenName) return
        action(screen, screenBudgetMs)
        val mark = activeMark ?: return
        val markBudgetMs = budgetMsUnder(fromScreen, mark.name)
        action(mark.accumulator, markBudgetMs)
        markTotals.collecting(mark.name)?.let { action(it, markBudgetMs) }
    }

    fun addFrame(
        fromScreen: String?,
        durationsMs: FloatArray,
        overrunMs: Float,
        refreshRateHz: Float,
        frameBudgetMs: Float,
    ) {
        val totalMs = durationsMs[FramePhase.TOTAL.ordinal]
        forEachOn(fromScreen) { accumulator, budgetMs ->
            accumulator.addFrame(
                durationsMs = durationsMs,
                overrunMs = overrunAgainst(budgetMs, totalMs = totalMs, displayOverrunMs = overrunMs),
                refreshRateHz = refreshRateHz,
                frameBudgetMs = budgetMs?.toFloat() ?: frameBudgetMs,
            )
        }
    }

    fun addDroppedReports(fromScreen: String?, count: Int) =
        forEachOn(fromScreen) { accumulator, _ -> accumulator.addDroppedReports(count) }

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
        screenTotals.resume()
        activeMark?.let {
            markTotals.begin(it.name)
            it.accumulator.startCollecting()
        }
        beginScreen(label)
    }

    fun stopCollecting() {
        session.stopCollecting()
        screen.stopCollecting()
        screenTotals.pause()
        activeMark?.let {
            markTotals.end(it.name)
            it.accumulator.stopCollecting()
        }
        latestThermalLevel = null
        latestBattery = null
    }

    fun restartScreen(label: String? = null): IntervalStats {
        screen.stopCollecting()
        val ended = screen.stats()
        beginScreen(label)
        return ended
    }

    private fun beginScreen(label: String?) {
        screenTotals.end(screenName)
        screenName = label
        screen.clear()
        screen.startCollecting()
        seedEnvironment(screen)
        screenTotals.begin(label)?.let(::seedEnvironment)
    }

    fun beginWindow(name: String) {
        screenTotals.begin(name)?.let(::seedEnvironment)
    }

    fun endWindow(name: String?) {
        if (name == screenName) return
        screenTotals.end(name)
    }

    fun beginMark(name: String) {
        endMark()
        val accumulator = SessionAccumulator(clock, isEmulator).apply { startCollecting() }.also(::seedEnvironment)
        activeMark = ActiveMark(name, accumulator)
        markTotals.begin(name)?.let(::seedEnvironment)
    }

    fun endMark(): IntervalStats? {
        val ended = activeMark ?: return null
        activeMark = null
        markTotals.end(ended.name)
        ended.accumulator.stopCollecting()
        return ended.accumulator.stats()
    }

    private fun seedEnvironment(accumulator: SessionAccumulator) {
        latestThermalLevel?.let(accumulator::addThermalLevel)
        latestBattery?.let(accumulator::addBattery)
    }

    fun sessionStats(): IntervalStats = session.stats()

    fun screenStats(): IntervalStats = screen.stats()

    fun intervals(): List<IntervalReport> = buildList {
        add(IntervalReport(IntervalId.Session, session.stats(), session.frameBudgetMs()))
        addAll(screenTotals.intervals())
        addAll(markTotals.intervals())
    }

    fun clear() {
        session.clear()
        screen.clear()
        activeMark?.accumulator?.clear()
        screenTotals.clear()
        markTotals.clear()
        forEachActive(::seedEnvironment)
    }
}

private inline fun <reified T : IntervalId> Map<IntervalId, Int>.byNameOf(): Map<String, Int> =
    entries.filter { it.key is T }.associate { (id, budgetMs) -> id.name to budgetMs }
