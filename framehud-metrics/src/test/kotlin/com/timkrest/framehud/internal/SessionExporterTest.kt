package com.timkrest.framehud.internal

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertContains
import kotlin.test.assertNotEquals

class SessionExporterTest {

    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `two exports taken in the same millisecond both keep their reports`() {
        val first = sessionSnapshotFixture(screenName = "cart").writeTo(directory.root)
        val second = sessionSnapshotFixture(screenName = "checkout").writeTo(directory.root)

        assertNotEquals(first.json, second.json)
        assertNotEquals(first.html, second.html)
        assertContains(first.json.readText(), "\"screen\":\"cart\"")
        assertContains(second.json.readText(), "\"screen\":\"checkout\"")
    }
}
