package com.timkrest.framehud.internal

import androidx.annotation.AnyThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FreezableReading<T : Any>(initial: T) {

    private val _published = MutableStateFlow(initial)

    @get:AnyThread
    val published: StateFlow<T> = _published.asStateFlow()

    @Volatile
    @get:AnyThread
    var live: T = initial
        private set

    private val lock = Any()

    private var isFrozen = false

    @AnyThread
    fun update(value: T) {
        synchronized(lock) {
            live = value
            if (!isFrozen) _published.value = value
        }
    }

    @AnyThread
    fun updateLive(value: T) {
        synchronized(lock) { live = value }
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        synchronized(lock) {
            isFrozen = frozen
            if (!frozen) _published.value = live
        }
    }

    @AnyThread
    fun reset(value: T) {
        synchronized(lock) {
            live = value
            _published.value = value
        }
    }
}
