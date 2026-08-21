package com.timkrest.framehud.internal

import com.timkrest.framehud.MainThreadBlock
import org.junit.Test
import kotlin.test.assertEquals

class MainThreadSamplerTest {

    private val sampler = MainThreadSampler()

    @Test
    fun `the stack names what the samples caught it in most often first`() {
        sampler.beginBlock(sinceMs = 0)
        sampler.sample(stackOf("query", "load", "render"))
        sampler.sample(stackOf("await", "load", "render"))

        val calls = sampler.blockAt(nowMs = 0).calls

        assertEquals(labelsOf("load", "render", "query", "await"), calls.map { it.name })
        assertEquals(listOf(2, 2, 1, 1), calls.map { it.samples })
    }

    @Test
    fun `a block lasts from the last frame the main thread handled`() {
        sampler.beginBlock(sinceMs = 1_000)
        sampler.sample(stackOf("query"))

        val block = sampler.blockAt(nowMs = 1_850)

        assertEquals(850, block.durationMs)
        assertEquals(1, block.stacksTaken)
    }

    @Test
    fun `a stack deeper than a sample reaches is cut at the calls nearest the work`() {
        sampler.beginBlock(sinceMs = 0)
        sampler.sample(stackOf(*Array(40) { "call$it" }))

        val calls = sampler.blockAt(nowMs = 0).calls

        assertEquals(
            labelsOf("call0", "call1", "call2", "call3", "call4", "call5", "call6", "call7"),
            calls.map { it.name },
        )
    }

    @Test
    fun `a watch that sampled nothing reports no block`() {
        sampler.beginBlock(sinceMs = 0)

        assertEquals(MainThreadBlock.NONE, sampler.blockAt(nowMs = 900))
    }

    private fun stackOf(vararg calls: String): Array<StackTraceElement> = Array(calls.size) { element(calls[it]) }

    private fun labelsOf(vararg calls: String): List<String> = calls.map { element(it).toString() }

    private fun element(call: String): StackTraceElement = StackTraceElement("Screen", call, null, -1)
}
