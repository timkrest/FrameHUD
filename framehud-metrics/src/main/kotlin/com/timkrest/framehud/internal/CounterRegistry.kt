package com.timkrest.framehud.internal

import android.util.Log
import androidx.annotation.AnyThread
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.FrameHudCounter
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@AnyThread
internal class CounterRegistry {

    private val lock = Any()

    private val byName = LinkedHashMap<String, RegisteredCounter>()

    @Volatile
    private var registered = emptyList<RegisteredCounter>()

    private var hasWarnedAboutNames = false

    private val readings = FreezableReading(emptyList<CounterReading>())

    val counters: StateFlow<List<CounterReading>> = readings.published

    val liveCounters: List<CounterReading> get() = readings.live

    fun counter(name: String): FrameHudCounter {
        requireNameStandsApart("A counter name", name)
        synchronized(lock) {
            byName[name]?.let { return it }
            if (byName.size >= MAX_COUNTERS) {
                if (!hasWarnedAboutNames) {
                    hasWarnedAboutNames = true
                    Log.w(LOG_TAG, "Reached $MAX_COUNTERS counters, dropping $name and any name after it.")
                }
                return DiscardedCounter
            }
            return RegisteredCounter(name).also {
                byName[name] = it
                registered = byName.values.toList()
            }
        }
    }

    fun sample() = readings.update(readCounters())

    fun setFrozen(frozen: Boolean) = readings.setFrozen(frozen)

    fun reset() {
        registered.forEach { it.reset() }
        readings.reset(readCounters())
    }

    private fun readCounters(): List<CounterReading> = registered.map { it.read() }

    private companion object {
        const val MAX_COUNTERS = 16
    }
}

private class RegisteredCounter(private val name: String) : FrameHudCounter {

    private val state = AtomicLong()

    override fun set(value: Int) = write { value }

    override fun add(delta: Int) = write { standing -> standing + delta }

    fun read(): CounterReading {
        val current = CounterState(state.get())
        return CounterReading(name = name, value = current.value, peakSinceReset = current.peak)
    }

    fun reset() = replace { standing -> standing.afterReset() }

    private inline fun write(next: (Int) -> Int) = replace { standing -> standing.written(next(standing.value)) }

    private inline fun replace(next: (CounterState) -> CounterState) {
        while (true) {
            val standing = state.get()
            if (state.compareAndSet(standing, next(CounterState(standing)).packed)) return
        }
    }
}

@JvmInline
private value class CounterState(val packed: Long) {

    val value: Int get() = packed.toInt()

    val peak: Int get() = (packed ushr Int.SIZE_BITS).toInt()

    fun written(next: Int): CounterState = of(next, peak = max(peak, next))

    fun afterReset(): CounterState = of(value, peak = value)

    private fun of(value: Int, peak: Int): CounterState =
        CounterState((peak.toLong() shl Int.SIZE_BITS) or (value.toLong() and LOW_INT))
}

private const val LOW_INT = 0xFFFF_FFFFL

private object DiscardedCounter : FrameHudCounter {
    override fun set(value: Int) = Unit
    override fun add(delta: Int) = Unit
}
