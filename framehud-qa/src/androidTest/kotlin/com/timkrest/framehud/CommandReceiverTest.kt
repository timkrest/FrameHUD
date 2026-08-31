package com.timkrest.framehud

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.timkrest.framehud.internal.baselineFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CommandReceiverTest {

    private val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun resetCollector() {
        FrameHud.reset()
    }

    @After
    fun clearDebugState() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            FrameHud.screen = null
            FrameHud.mark = null
            FrameHud.context = emptyMap()
        }
        FrameHud.baselineOverride = null
        baselineFile(targetContext.applicationContext as Application).delete()
    }

    @Test
    fun theScreenCommandNamesTheScreen() {
        val result = broadcast(
            Intent(FrameHudCommandReceiver.ACTION_SCREEN)
                .putExtra(FrameHudCommandReceiver.EXTRA_NAME, "product/{id}"),
        )

        assertEquals("screen product/{id}", result)
        assertEquals("product/{id}", FrameHud.screen)
    }

    @Test
    fun aNameNoTraceCouldTellApartIsReportedBackAndLeavesTheNameAlone() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { FrameHud.screen = "cart" }

        listOf(" ", "q".repeat(TOO_LONG_FOR_A_TRACE)).forEach { name ->
            val result = assertNotNull(
                broadcast(
                    Intent(FrameHudCommandReceiver.ACTION_SCREEN)
                        .putExtra(FrameHudCommandReceiver.EXTRA_NAME, name),
                ),
                "the command never answered",
            )

            assertTrue(result.startsWith("failed:"), result)
            assertEquals("cart", FrameHud.screen, "a name FrameHud refused cleared the one already set")
        }
    }

    @Test
    fun theContextCommandTakesEveryStringExtra() {
        broadcast(
            Intent(FrameHudCommandReceiver.ACTION_CONTEXT)
                .putExtra("variant", "b")
                .putExtra("scenario", "smoke")
                .putExtra("blank", " ")
                .putExtra("dropped", 7),
        )

        assertEquals(mapOf("variant" to "b", "scenario" to "smoke"), FrameHud.context)

        broadcast(Intent(FrameHudCommandReceiver.ACTION_CONTEXT))
        assertEquals(emptyMap(), FrameHud.context)
    }

    @Test
    fun theExportCommandAnswersWithThePathOfTheReport() {
        ActivityScenario.launch(BlankActivity::class.java).use {
            awaitCollectorStarted()

            val result = assertNotNull(broadcast(Intent(FrameHudCommandReceiver.ACTION_EXPORT)))
            assertTrue(result.endsWith(".json"), "expected a report path, got $result")
            assertTrue(File(result).length() > 0L, "the exported report is empty")
        }
    }

    @Test
    fun theBaselineCommandAnswersWithThePathOfTheBaseline() {
        ActivityScenario.launch(BlankActivity::class.java).use { scenario ->
            awaitCollectorStarted()
            scenario.drawFrames(FRAMES)

            val result = assertNotNull(broadcast(Intent(FrameHudCommandReceiver.ACTION_BASELINE)))
            assertTrue(result.endsWith("baseline.json"), "expected a baseline path, got $result")
            assertTrue(File(result).length() > 0L, "the baseline is empty")
        }
    }

    private fun broadcast(intent: Intent): String? {
        val done = CountDownLatch(1)
        val resultData = AtomicReference<String?>()
        targetContext.sendOrderedBroadcast(
            intent.setPackage(targetContext.packageName),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, received: Intent) {
                    resultData.set(getResultData())
                    done.countDown()
                }
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(done.await(COLD_START_AND_DELIVERY_MS, TimeUnit.MILLISECONDS), "the broadcast was never delivered")
        return resultData.get()
    }

    private companion object {
        const val COLD_START_AND_DELIVERY_MS = 60_000L
        const val FRAMES = 30
        const val TOO_LONG_FOR_A_TRACE = 111
    }
}
