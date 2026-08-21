package com.timkrest.framehud.internal

import android.view.Display
import android.view.Window
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import java.lang.ref.WeakReference

@AnyThread
internal class MeasuredWindows {

    class Measured(window: Window, screen: String?, val display: Display?) : WeakReference<Window>(window) {

        @Volatile
        var screen: String? = screen
    }

    private val measured = mutableListOf<Measured>()

    operator fun get(window: Window): Measured? = synchronized(measured) {
        for (index in measured.indices) {
            val entry = measured[index]
            if (entry.get() === window) return entry
        }
        null
    }

    @MainThread
    fun add(window: Window, screen: String?) {
        val added = Measured(window, screen, window.decorView.display)
        synchronized(measured) {
            measured.removeAll { it.get() === window }
            measured += added
        }
    }

    fun remove(window: Window): Measured? = synchronized(measured) {
        val found = get(window) ?: return null
        measured -= found
        found
    }

    fun stillMeasures(screen: String?): Boolean = synchronized(measured) {
        measured.any { it.screen == screen && it.get() != null }
    }

    fun takeEndedScreens(): List<String?> = synchronized(measured) {
        val collected = measured.filter { it.get() == null }
        measured -= collected.toSet()
        collected.map { it.screen }.filterNot(::stillMeasures)
    }

    fun windows(): List<Window> = synchronized(measured) { measured.mapNotNull { it.get() } }

    fun clear(): List<Measured> = synchronized(measured) {
        val removed = measured.toList()
        measured.clear()
        removed
    }
}
