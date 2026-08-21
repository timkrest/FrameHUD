package com.timkrest.framehud.internal

internal class ScreenStart(private val startedAtNs: Long, val precedesCreation: Boolean = false) {

    fun elapsedMs(frameEndNs: Long): Float? {
        val elapsedNs = frameEndNs - startedAtNs
        return if (elapsedNs < 0L) null else elapsedNs / NS_PER_MS
    }
}
