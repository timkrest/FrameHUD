package com.timkrest.framehud.internal

import org.junit.Test
import kotlin.test.assertEquals

class JsonTest {

    @Test
    fun `objects nest and separate with commas`() {
        val json = buildJsonObject {
            put("a", 1)
            putObject("b") {
                put("c", "text")
                put("d", true)
            }
            putArray("e") {
                add(1.5f)
                addObject { put("f", 2L) }
            }
        }

        assertEquals("""{"a":1,"b":{"c":"text","d":true},"e":[1.5,{"f":2}]}""", json)
    }

    @Test
    fun `strings escape quotes, backslashes and control characters`() {
        val json = buildJsonObject { put("text", "a\"b\\c\nd\u0001") }

        assertEquals("""{"text":"a\"b\\c\nd\u0001"}""", json)
    }

    @Test
    fun `nulls and non-finite floats export as null`() {
        val json = buildJsonObject {
            put("missing", null as String?)
            put("nan", Float.NaN)
            put("infinity", Float.POSITIVE_INFINITY)
        }

        assertEquals("""{"missing":null,"nan":null,"infinity":null}""", json)
    }

    @Test
    fun `an empty object and an empty array stay valid`() {
        val json = buildJsonObject {
            putObject("o") {}
            putArray("a") {}
        }

        assertEquals("""{"o":{},"a":[]}""", json)
    }
}
