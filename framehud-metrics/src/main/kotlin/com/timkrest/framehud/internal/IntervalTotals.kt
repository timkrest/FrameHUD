package com.timkrest.framehud.internal

import android.util.Log
import androidx.annotation.WorkerThread
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport

@WorkerThread
internal class IntervalTotals(
    private val clock: MetricsClock,
    private val isEmulator: Boolean,
    private val idOf: (String) -> IntervalId,
) {

    private val byName = LinkedHashMap<String, SessionAccumulator>()

    private val collectingByName = LinkedHashMap<String, SessionAccumulator?>()

    private var isPaused = false

    private var hasWarnedAboutNames = false

    fun begin(name: String?): SessionAccumulator? {
        if (name == null) return null
        val accumulator = accumulatorFor(name)
        collectingByName[name] = accumulator
        if (!isPaused) accumulator?.startCollecting()
        return accumulator
    }

    fun end(name: String?) {
        if (name == null) return
        collectingByName.remove(name)?.stopCollecting()
    }

    fun pause() {
        isPaused = true
        forEachCollecting(SessionAccumulator::stopCollecting)
    }

    fun resume() {
        isPaused = false
        forEachCollecting(SessionAccumulator::startCollecting)
    }

    fun collecting(name: String?): SessionAccumulator? = if (name == null) null else collectingByName[name]

    fun forEachCollecting(action: (SessionAccumulator) -> Unit) {
        for (accumulator in collectingByName.values) accumulator?.let(action)
    }

    fun intervals(): List<IntervalReport> =
        byName.map { (name, accumulator) ->
            IntervalReport.of(idOf(name), accumulator.stats(), accumulator.frameBudgetMs())
        }

    fun clear() {
        val names = collectingByName.keys.toList()
        byName.clear()
        collectingByName.clear()
        names.forEach(::begin)
    }

    private fun accumulatorFor(name: String): SessionAccumulator? {
        byName[name]?.let { return it }
        if (byName.size >= MAX_NAMES) {
            if (!hasWarnedAboutNames) {
                hasWarnedAboutNames = true
                Log.w(
                    LOG_TAG,
                    "Reached $MAX_NAMES names for ${idOf(name).label}, dropping the rest from reports. " +
                        "Name screens by route pattern, product/{id} rather than product/12345.",
                )
            }
            return null
        }
        return SessionAccumulator(clock, isEmulator).also { byName[name] = it }
    }

    private companion object {
        const val MAX_NAMES = 32
    }
}
