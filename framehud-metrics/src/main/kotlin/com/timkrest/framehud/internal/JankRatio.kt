package com.timkrest.framehud.internal

internal class JankRatio(capacity: Int) {

    private var flags = BooleanArray(capacity)
    private var writePos = 0
    private var size = 0
    private var jankyCount = 0

    private val capacity: Int get() = flags.size

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

    fun resizeTo(capacity: Int) {
        if (capacity == flags.size) return
        flags = BooleanArray(capacity)
        clear()
    }

    fun clear() {
        flags.fill(false)
        writePos = 0
        size = 0
        jankyCount = 0
    }
}
