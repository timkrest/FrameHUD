package com.timkrest.framehud.internal

internal sealed interface JsonValue {

    data class Obj(val members: Map<String, JsonValue>) : JsonValue

    data class Arr(val items: List<JsonValue>) : JsonValue

    data class Str(val value: String) : JsonValue

    data class Num(val value: Double) : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    data object Null : JsonValue
}

internal fun parseJson(text: String): JsonValue? = try {
    JsonParser(text).parse()
} catch (_: JsonSyntaxException) {
    null
}

internal fun JsonValue?.member(name: String): JsonValue? = (this as? JsonValue.Obj)?.members?.get(name)

internal fun JsonValue?.obj(name: String): JsonValue.Obj? = member(name) as? JsonValue.Obj

internal fun JsonValue?.items(name: String): List<JsonValue> = (member(name) as? JsonValue.Arr)?.items.orEmpty()

internal fun JsonValue?.string(name: String): String? = (member(name) as? JsonValue.Str)?.value

internal fun JsonValue?.float(name: String): Float? =
    (member(name) as? JsonValue.Num)?.value?.toFloat()?.takeIf { it.isFinite() }

internal fun JsonValue?.int(name: String): Int? {
    val number = (member(name) as? JsonValue.Num)?.value ?: return null
    return number.toInt().takeIf { it.toDouble() == number }
}

internal fun JsonValue?.long(name: String): Long? {
    val number = (member(name) as? JsonValue.Num)?.value ?: return null
    return number.toLong().takeIf { it.toDouble() == number }
}

internal fun JsonValue?.bool(name: String): Boolean? = (member(name) as? JsonValue.Bool)?.value

internal inline fun <T : Any> readOrNull(read: () -> T?): T? = try {
    read()
} catch (_: IllegalArgumentException) {
    null
}

internal class JsonSyntaxException(message: String) : Exception(message)

private class JsonParser(private val text: String) {

    private var index = 0

    fun parse(): JsonValue {
        val value = readValue(depth = 0)
        skipWhitespace()
        if (index != text.length) fail("trailing text")
        return value
    }

    private fun readValue(depth: Int): JsonValue {
        if (depth > MAX_DEPTH) fail("nested deeper than $MAX_DEPTH")
        skipWhitespace()
        return when (peek()) {
            '{' -> readObject(depth)
            '[' -> readArray(depth)
            '"' -> JsonValue.Str(readString())
            't' -> readLiteral("true", JsonValue.Bool(true))
            'f' -> readLiteral("false", JsonValue.Bool(false))
            'n' -> readLiteral("null", JsonValue.Null)
            else -> readNumber()
        }
    }

    private fun readObject(depth: Int): JsonValue.Obj {
        expect('{')
        val members = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (peek() == '}') {
            index++
            return JsonValue.Obj(members)
        }
        while (true) {
            skipWhitespace()
            val name = readString()
            skipWhitespace()
            expect(':')
            members[name] = readValue(depth + 1)
            skipWhitespace()
            when (next()) {
                ',' -> Unit
                '}' -> return JsonValue.Obj(members)
                else -> fail("expected , or }")
            }
        }
    }

    private fun readArray(depth: Int): JsonValue.Arr {
        expect('[')
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (peek() == ']') {
            index++
            return JsonValue.Arr(items)
        }
        while (true) {
            items += readValue(depth + 1)
            skipWhitespace()
            when (next()) {
                ',' -> Unit
                ']' -> return JsonValue.Arr(items)
                else -> fail("expected , or ]")
            }
        }
    }

    private fun readString(): String {
        expect('"')
        val out = StringBuilder()
        while (true) {
            when (val char = next()) {
                '"' -> return out.toString()
                '\\' -> out.append(readEscape())
                in CONTROL_CHARS -> fail("unescaped control character")
                else -> out.append(char)
            }
        }
    }

    private fun readEscape(): Char = when (val char = next()) {
        '"', '\\', '/' -> char
        'b' -> '\b'
        'f' -> '\u000C'
        'n' -> '\n'
        'r' -> '\r'
        't' -> '\t'
        'u' -> readUnicodeEscape()
        else -> fail("unknown escape \\$char")
    }

    private fun readUnicodeEscape(): Char {
        if (index + HEX_DIGITS > text.length) fail("truncated \\u escape")
        val digits = text.substring(index, index + HEX_DIGITS)
        if (!digits.all { it.digitToIntOrNull(radix = 16) != null }) fail("malformed \\u escape")
        index += HEX_DIGITS
        return digits.toInt(radix = 16).toChar()
    }

    private fun readNumber(): JsonValue.Num {
        val start = index
        while (index < text.length && text[index] in NUMBER_CHARS) index++
        val value = text.substring(start, index).toDoubleOrNull() ?: fail("malformed number")
        return JsonValue.Num(value)
    }

    private fun <T : JsonValue> readLiteral(literal: String, value: T): T {
        if (!text.startsWith(literal, index)) fail("expected $literal")
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    private fun peek(): Char = if (index < text.length) text[index] else fail("unexpected end")

    private fun next(): Char = peek().also { index++ }

    private fun expect(char: Char) {
        if (next() != char) fail("expected $char")
    }

    private fun fail(reason: String): Nothing = throw JsonSyntaxException("$reason at offset $index")

    private companion object {
        const val MAX_DEPTH = 32
        const val HEX_DIGITS = 4
        val CONTROL_CHARS = '\u0000'..'\u001F'
        val NUMBER_CHARS = "-+.eE0123456789".toSet()
    }
}
