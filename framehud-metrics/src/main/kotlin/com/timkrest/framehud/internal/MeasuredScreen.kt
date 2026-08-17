package com.timkrest.framehud.internal

import androidx.annotation.AnyThread
import androidx.annotation.MainThread

@MainThread
internal class MeasuredScreen {

    sealed interface Rename {
        data object None : Rename
        data object WhileUnbound : Rename
        data class Renamed(val previous: String?, val current: String?) : Rename
    }

    private class BoundScreen(val name: String?)

    @Volatile
    private var bound: BoundScreen? = null

    @Volatile
    @get:AnyThread
    var screenOverride: String? = null
        private set

    @Volatile
    @get:AnyThread
    var active: String? = null
        private set

    fun bind(screen: String?): String? {
        bound = BoundScreen(screen)
        active = screenOverride ?: screen
        return active
    }

    fun unbind(): String? {
        val ended = active
        bound = null
        active = null
        return ended
    }

    fun rename(name: String?): Rename {
        if (screenOverride == name) return Rename.None
        val previous = active
        screenOverride = name
        val boundScreen = bound
        val current = name ?: boundScreen?.name
        if (current == previous) return Rename.None
        if (boundScreen == null) return Rename.WhileUnbound
        active = current
        return Rename.Renamed(previous = previous, current = current)
    }
}
