package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JsonParserTest {

    @Test
    fun `an object keeps its members, whatever they hold`() {
        val value = parseJson("""{"name":"scroll","runs":3,"ok":true,"gpu":null,"tags":[1,2]}""")

        assertEquals("scroll", value.string("name"))
        assertEquals(3, value.int("runs"))
        assertEquals(JsonValue.Bool(true), value.member("ok"))
        assertEquals(JsonValue.Null, value.member("gpu"))
        assertEquals(2, value.items("tags").size)
    }

    @Test
    fun `numbers keep their fraction and exponent`() {
        val value = parseJson("""{"a":-12.5,"b":1.0E-3}""")

        assertEquals(-12.5f, value.float("a"))
        assertEquals(0.001f, value.float("b"))
    }

    @Test
    fun `escapes come back as the characters they stand for`() {
        val value = parseJson("""{"a":"line\nbreak \"quoted\" é"}""")

        assertEquals("line\nbreak \"quoted\" é", value.string("a"))
    }

    @Test
    fun `whitespace between tokens is skipped`() {
        val value = parseJson(" {\n  \"a\" : [ 1 , 2 ]\n} ")

        assertEquals(2, value.items("a").size)
    }

    @Test
    fun `an empty object and an empty array parse`() {
        assertEquals(JsonValue.Obj(emptyMap()), parseJson("{}"))
        assertEquals(JsonValue.Arr(emptyList()), parseJson("[]"))
    }

    @Test
    fun `a member of the wrong type reads as absent`() {
        val value = parseJson("""{"runs":"three"}""")

        assertNull(value.int("runs"))
    }

    @Test
    fun `a number no integer holds reads as absent rather than rounded or clamped`() {
        val value = parseJson("""{"half":1.5,"huge":1e30,"whole":7.0}""")

        assertNull(value.int("half"))
        assertNull(value.int("huge"))
        assertEquals(7, value.int("whole"))
    }

    @Test
    fun `a number past what a float holds reads as absent`() {
        assertNull(parseJson("""{"a":1e400}""").float("a"))
    }

    @Test
    fun `truncated, trailing and malformed input is refused`() {
        assertNull(parseJson("""{"a":1"""))
        assertNull(parseJson("""{"a":1} extra"""))
        assertNull(parseJson("""{"a":}"""))
        assertNull(parseJson(""))
    }

    @Test
    fun `nesting past the limit is refused rather than overflowing the stack`() {
        val deep = "[".repeat(200) + "]".repeat(200)

        assertNull(parseJson(deep))
    }

    @Test
    fun `a root that is not an object still parses`() {
        assertEquals("scroll", assertIs<JsonValue.Str>(parseJson(""""scroll"""")).value)
    }
}
