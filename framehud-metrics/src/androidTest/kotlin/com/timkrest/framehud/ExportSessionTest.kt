package com.timkrest.framehud

import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timkrest.framehud.internal.exportAuthority
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ExportSessionTest {

    @Before
    fun resetCollector() {
        FrameHud.reset()
    }

    @Test
    fun theFileProviderServesAnExport() {
        ActivityScenario.launch(BlankActivity::class.java).use {
            val export = assertNotNull(FrameHud.exportSession(TIMEOUT_MS), "nothing was collecting")

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val uri = FileProvider.getUriForFile(context, exportAuthority(context), export.html)
            val served = assertNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
            assertTrue(served.isNotEmpty(), "the provider served an empty report")
        }
    }

    @Test
    fun anExportWritesBothReportsUnderTheAppFiles() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            FrameHud.screen = "checkout"
            FrameHud.context = mapOf("scenario" to "smoke")
        }
        try {
            ActivityScenario.launch(BlankActivity::class.java).use {
                val export = assertNotNull(FrameHud.exportSession(TIMEOUT_MS), "nothing was collecting")

                assertTrue(export.json.length() > 0L, "the JSON report is empty")
                assertTrue(export.html.length() > 0L, "the HTML report is empty")
                val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
                assertContains(export.json.readText(), "\"packageName\":\"$packageName\"")
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                FrameHud.screen = null
                FrameHud.context = emptyMap()
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
