package com.timkrest.framehud.internal

internal fun buildJsonObject(build: JsonObjectScope.() -> Unit): String {
    val out = StringBuilder()
    JsonObjectScope(out).writeObject(build)
    return out.toString()
}

internal class JsonObjectScope(private val out: StringBuilder) {

    private var isEmpty = true

    fun put(name: String, value: String?) {
        writeName(name)
        writeString(out, value)
    }

    fun put(name: String, value: Boolean) {
        writeName(name)
        out.append(value)
    }

    fun put(name: String, value: Int?) {
        writeName(name)
        if (value == null) out.append("null") else out.append(value)
    }

    fun put(name: String, value: Long) {
        writeName(name)
        out.append(value)
    }

    fun put(name: String, value: Float?) {
        writeName(name)
        writeFloat(out, value)
    }

    fun putNull(name: String) {
        writeName(name)
        out.append("null")
    }

    fun putObject(name: String, build: JsonObjectScope.() -> Unit) {
        writeName(name)
        JsonObjectScope(out).writeObject(build)
    }

    fun putArray(name: String, build: JsonArrayScope.() -> Unit) {
        writeName(name)
        JsonArrayScope(out).writeArray(build)
    }

    fun writeObject(build: JsonObjectScope.() -> Unit) {
        out.append('{')
        build()
        out.append('}')
    }

    private fun writeName(name: String) {
        if (!isEmpty) out.append(',')
        isEmpty = false
        writeString(out, name)
        out.append(':')
    }
}

internal class JsonArrayScope(private val out: StringBuilder) {

    private var isEmpty = true

    fun add(value: String?) {
        writeSeparator()
        writeString(out, value)
    }

    fun add(value: Float?) {
        writeSeparator()
        writeFloat(out, value)
    }

    fun add(value: Int) {
        writeSeparator()
        out.append(value)
    }

    fun addObject(build: JsonObjectScope.() -> Unit) {
        writeSeparator()
        JsonObjectScope(out).writeObject(build)
    }

    fun writeArray(build: JsonArrayScope.() -> Unit) {
        out.append('[')
        build()
        out.append(']')
    }

    private fun writeSeparator() {
        if (!isEmpty) out.append(',')
        isEmpty = false
    }
}

private fun writeFloat(out: StringBuilder, value: Float?) {
    if (value == null || !value.isFinite()) {
        out.append("null")
    } else {
        out.append(value)
    }
}

private fun writeString(out: StringBuilder, value: String?) {
    if (value == null) {
        out.append("null")
        return
    }
    out.append('"')
    for (char in value) {
        when {
            char == '"' -> out.append("\\\"")
            char == '\\' -> out.append("\\\\")
            char == '\n' -> out.append("\\n")
            char == '\r' -> out.append("\\r")
            char == '\t' -> out.append("\\t")
            char < ' ' -> out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> out.append(char)
        }
    }
    out.append('"')
}
