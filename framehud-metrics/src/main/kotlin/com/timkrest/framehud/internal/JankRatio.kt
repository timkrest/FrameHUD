package com.timkrest.framehud.internal

internal class JankRatio(private val capacity: Int) {

    private val flags = BooleanArray(capacity)
    private var writePos = 0
    private var size = 0
    private var jankyCount = 0

    fun add(isJanky: Boolean) {
        if (size == capacity) {
            if (flags[writePos]) jankyCount--
        } else {
            size++
        }
        flags[writePos] = isJanky
        if (isJanky) jankyCount++
        writePos = (writePos + 1) % capacity
    }

    fun percent(): Float = if (size == 0) 0f else jankyCount * PERCENT / size

    fun clear() {
        flags.fill(false)
        writePos = 0
        size = 0
        jankyCount = 0
    }
}
