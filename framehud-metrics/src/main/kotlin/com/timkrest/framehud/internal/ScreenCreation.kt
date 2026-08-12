package com.timkrest.framehud.internal

internal class ScreenCreation(private val startedAtNs: Long) {

    fun timeToDisplayMs(frameEndNs: Long): Float? {
        val elapsedNs = frameEndNs - startedAtNs
        return if (elapsedNs < 0L) null else elapsedNs / NS_PER_MS
    }
}
