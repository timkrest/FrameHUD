package com.timkrest.framehud.sample.load

import androidx.compose.runtime.Immutable

enum class Load(val label: String) {
    BlockMainThread("Block main thread"),
    Overdraw("Overdraw"),
    Allocate("Allocate"),
    HeavyLayout("Heavy layout"),
    GcChurn("GC churn"),
    BackgroundDecode("Background decode"),
}

@Immutable
data class ActiveLoads(private val values: Set<Load> = emptySet()) {

    operator fun contains(load: Load): Boolean = load in values

    fun toggled(load: Load): ActiveLoads = ActiveLoads(if (load in values) values - load else values + load)

    fun asContext(): Map<String, String> {
        val running = Load.entries.filter { it in values }
        if (running.isEmpty()) return emptyMap()
        return mapOf(CONTEXT_KEY to running.joinToString { it.label })
    }
}

private const val CONTEXT_KEY = "loads"
