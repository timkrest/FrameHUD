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

    private var currentName: String? = null

    private var isCollecting = false

    private var hasWarnedAboutNames = false

    var current: SessionAccumulator? = null
        private set

    fun begin(name: String?): SessionAccumulator? {
        current?.stopCollecting()
        currentName = name
        isCollecting = name != null
        current = name?.let(::accumulatorFor)?.apply { startCollecting() }
        return current
    }

    fun end() {
        begin(null)
    }

    fun pause() {
        isCollecting = false
        current?.stopCollecting()
    }

    fun intervals(): List<IntervalReport> =
        byName.map { (name, accumulator) ->
            IntervalReport(idOf(name), accumulator.stats(), accumulator.frameBudgetMs())
        }

    fun clear() {
        val name = currentName
        byName.clear()
        current = name?.let(::accumulatorFor)
        if (isCollecting) current?.startCollecting()
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
