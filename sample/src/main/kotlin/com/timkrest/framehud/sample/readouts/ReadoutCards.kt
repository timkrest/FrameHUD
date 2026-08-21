package com.timkrest.framehud.sample.readouts

import androidx.compose.runtime.Composable
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FramePhase
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.JankDiagnosis
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalStats
import com.timkrest.framehud.sample.ui.NOT_REPORTED
import com.timkrest.framehud.sample.ui.SampleCard
import com.timkrest.framehud.sample.ui.SampleLine
import com.timkrest.framehud.sample.ui.SampleNote
import com.timkrest.framehud.sample.ui.formatMs
import com.timkrest.framehud.sample.ui.formatOrMissing
import com.timkrest.framehud.sample.ui.formatPercent
import com.timkrest.framehud.sample.ui.formatWithPeak
import com.timkrest.framehud.sample.ui.readable
import java.util.Locale

@Composable
fun WindowCard(window: FrameWindowStats) {
    SampleCard(title = "Rolling window") {
        SampleLine(label = "fps", value = window.fps.toString())
        SampleLine(label = "jank", value = formatPercent(window.jankPercent))
        SampleLine(label = "p95", value = formatMs(window.p95FrameMs))
        SampleLine(label = "worst frame", value = formatMs(window.worstFrameMs))
        SampleLine(label = "frame budget", value = formatMs(window.frameBudgetMs))
    }
}

@Composable
fun PhasesCard(phases: FramePhases) {
    SampleCard(title = "Phases, average over the window") {
        FramePhase.entries.forEach { phase ->
            val phaseMs = phases[phase]
            SampleLine(
                label = phase.readable(),
                value = if (phaseMs == null) NOT_REPORTED else formatMs(phaseMs.average),
            )
        }
        SampleNote(text = "${phases.bottleneckStage.readable()} bound, ${formatMs(phases.bottleneck.average)} per frame")
    }
}

@Composable
fun DiagnosisCard(diagnosis: JankDiagnosis) {
    SampleCard(title = "Diagnosis") {
        SampleLine(label = "severity", value = diagnosis.severity.readable())
        SampleNote(text = diagnosis.summary)
    }
}

@Composable
fun ProcessCard(process: ProcessStats) {
    SampleCard(title = "Process") {
        SampleLine(
            label = "cpu",
            value = formatWithPeak(process.cpuPercent, process.peakCpuPercent, ::formatPercent),
        )
        SampleLine(label = "pss", value = formatWithPeak(process.pssMb, process.peakPssMb) { "$it MB" })
        SampleLine(label = "threads", value = formatWithPeak(process.threads, process.peakThreads))
        SampleLine(label = "open files", value = formatWithPeak(process.openFiles, process.peakOpenFiles))
    }
}

@Composable
fun MemoryCard(memory: MemoryStats) {
    SampleCard(title = "Memory") {
        SampleLine(label = "heap", value = "${memory.usedHeapMb} of ${memory.maxHeapMb} MB")
        SampleLine(label = "peak heap", value = "${memory.peakUsedHeapMb} MB")
        SampleLine(label = "native heap", value = "${memory.nativeHeapMb} MB")
        SampleLine(label = "gc", value = "${memory.gcCount} in ${memory.gcTimeMs} ms")
    }
}

@Composable
fun ThermalCard(thermal: ThermalStats) {
    SampleCard(title = "Thermal") {
        SampleLine(label = "status", value = thermal.level.readable())
        SampleLine(
            label = "headroom",
            value = formatOrMissing(thermal.headroom) { String.format(Locale.US, "%.2f", it) },
        )
    }
}

@Composable
fun CountersCard(counters: List<CounterReading>) {
    SampleCard(title = "Counters") {
        if (counters.isEmpty()) SampleNote(text = "No counter has been written yet.")
        counters.forEach { counter ->
            SampleLine(label = counter.name, value = formatWithPeak(counter.value, counter.peakSinceReset))
        }
    }
}

@Composable
fun CollectionCard(display: DisplayInfo, ticksPerSecond: Int, frozen: Boolean) {
    SampleCard(title = "Collection") {
        SampleLine(label = "choreographer ticks", value = "$ticksPerSecond per second")
        SampleLine(label = "display", value = String.format(Locale.US, "%.0f Hz", display.refreshRateHz))
        SampleLine(label = "display deadline", value = formatMs(display.frameBudgetMs))
        SampleLine(label = "readings", value = if (frozen) "frozen" else "live")
    }
}
