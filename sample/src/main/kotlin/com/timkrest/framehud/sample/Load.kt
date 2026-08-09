package com.timkrest.framehud.sample

import androidx.compose.runtime.Immutable

enum class Load(val label: String) {
    BlockMainThread("Block main thread"),
    Overdraw("Overdraw"),
    Allocate("Allocate"),
    HeavyLayout("Heavy layout"),
    GcChurn("GC churn"),
}

@Immutable
data class ActiveLoads(private val values: Set<Load> = emptySet()) {

    operator fun contains(load: Load): Boolean = load in values

    fun toggled(load: Load): ActiveLoads = ActiveLoads(if (load in values) values - load else values + load)
}
