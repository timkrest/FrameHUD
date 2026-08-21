package com.timkrest.framehud.sample.session

import androidx.compose.runtime.Composable
import com.timkrest.framehud.Incident
import com.timkrest.framehud.sample.ui.SampleCard
import com.timkrest.framehud.sample.ui.SampleLine
import com.timkrest.framehud.sample.ui.SampleNote
import com.timkrest.framehud.sample.ui.formatOrMissing
import com.timkrest.framehud.sample.ui.formatPercent
import com.timkrest.framehud.sample.ui.readable

@Composable
fun IncidentCard(incident: Incident) {
    val worst = incident.worst

    SampleCard(title = worst.trigger.summary) {
        SampleLine(label = "occurrences", value = incident.occurrences.toString())
        SampleLine(label = "frames kept", value = worst.frames.size.toString())
        SampleLine(label = "cpu", value = formatOrMissing(worst.process.cpuPercent, ::formatPercent))
        SampleLine(label = "heap", value = "${worst.memory.usedHeapMb} MB")
        SampleLine(label = "thermal", value = worst.thermal.level.readable())
        worst.counters.forEach { counter ->
            SampleLine(label = counter.name, value = counter.value.toString())
        }
        val block = worst.mainThreadBlock
        if (block.stacksTaken > 0) {
            SampleNote(
                text = "main thread blocked ${block.durationMs} ms in " +
                    (block.calls.firstOrNull()?.name ?: "an unnamed call"),
            )
        }
    }
}
