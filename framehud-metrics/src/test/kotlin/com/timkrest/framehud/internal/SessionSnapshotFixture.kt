package com.timkrest.framehud.internal

import com.timkrest.framehud.Baseline
import com.timkrest.framehud.BaselineComparison
import com.timkrest.framehud.BaselineEntry
import com.timkrest.framehud.CounterReading
import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FrameHudEvent
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.Incident
import com.timkrest.framehud.IncidentWindow
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalReport
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MainThreadBlock
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.PhaseAverages
import com.timkrest.framehud.ProcessStats
import com.timkrest.framehud.ThermalStats
import java.util.TimeZone

internal fun sessionSnapshotFixture(
    screenName: String? = null,
    mark: String? = null,
    context: Map<String, String> = emptyMap(),
    session: IntervalStats = IntervalStats.EMPTY,
    screen: IntervalStats = IntervalStats.EMPTY,
    intervals: List<IntervalReport> = emptyList(),
    baseline: BaselineComparison = BaselineComparison.NoBaseline,
    phases: FramePhases = FramePhases.EMPTY,
    window: FrameWindowStats = FrameWindowStats.of(frameBudgetMs = 16.6f),
    worstFrames: List<WorstFrames.Frame> = emptyList(),
    incidents: List<Incident> = emptyList(),
    process: ProcessStats = ProcessStats.EMPTY,
    counters: List<CounterReading> = emptyList(),
    flightRecording: FlightRecording? = null,
) = SessionSnapshot(
    takenAtEpochMs = TAKEN_AT_EPOCH_MS,
    takenAtNs = TAKEN_AT_NS,
    timeZone = TimeZone.getTimeZone("UTC"),
    frameHudVersion = "1.2.3",
    packageName = "com.example.app",
    appVersionName = "9.9",
    appVersionCode = 42L,
    environment = RECORDED_ENVIRONMENT,
    isEnabled = true,
    isFrozen = false,
    screenName = screenName,
    mark = mark,
    context = context,
    session = session,
    screen = screen,
    intervals = intervals,
    baseline = baseline,
    phases = phases,
    window = window,
    display = DisplayInfo.of(refreshRateHz = 60f, frameBudgetMs = 16.6f),
    memory = MemoryStats.EMPTY,
    thermal = ThermalStats.EMPTY,
    process = process,
    counters = counters,
    worstFrames = worstFrames,
    incidents = incidents,
    flightRecording = flightRecording,
)

internal fun incidentFixture(
    trigger: FrameHudEvent.IncidentTrigger = FrameHudEvent.FrozenFrames(count = 1, screen = "cart", mark = null),
    stats: IntervalStats = IntervalStats.EMPTY,
    frames: FrameHistory = FrameHistory.of(floatArrayOf(10f, 40f), floatArrayOf(16f, 16f)),
    framesBeforeTrigger: Int = 1,
    process: ProcessStats = ProcessStats.EMPTY,
    counters: List<CounterReading> = emptyList(),
    mainThreadBlock: MainThreadBlock = MainThreadBlock.NONE,
    occurrences: Int = 1,
    lastAtEpochMs: Long = TAKEN_AT_EPOCH_MS,
) = Incident.of(
    occurrences = occurrences,
    firstAtEpochMs = TAKEN_AT_EPOCH_MS,
    lastAtEpochMs = lastAtEpochMs,
    worst = IncidentWindow.of(
        trigger = trigger,
        triggeredAtEpochMs = TAKEN_AT_EPOCH_MS,
        stats = stats,
        frames = frames,
        framesBeforeTrigger = framesBeforeTrigger,
        memory = MemoryStats.EMPTY,
        thermal = ThermalStats.EMPTY,
        process = process,
        counters = counters,
        mainThreadBlock = mainThreadBlock,
    ),
)

internal fun windowOf(totalsMs: FloatArray, deadlinesMs: FloatArray) = FrameWindowStats.of(
    fps = totalsMs.size,
    jankPercent = 50f,
    p95FrameMs = totalsMs.max(),
    worstFrameMs = totalsMs.max(),
    frameBudgetMs = deadlinesMs.last(),
    history = FrameHistory.of(totalsMs = totalsMs, deadlinesMs = deadlinesMs),
)

internal const val TAKEN_AT_EPOCH_MS = 1_700_000_000_000L
internal const val TAKEN_AT_NS = 1_000_000_000_000L

internal fun comparisonFixture(): BaselineComparison = Baseline(
    environment = RECORDED_ENVIRONMENT,
    entries = mapOf(
        IntervalId.Session to BaselineEntry.of(runStats(p95FrameMs = 10f, layoutMs = 4f), runs = 3),
    ),
).compare(
    environment = RECORDED_ENVIRONMENT,
    intervals = listOf(IntervalReport.of(IntervalId.Session, runStats(p95FrameMs = 12f, layoutMs = 7f))),
)

private fun runStats(p95FrameMs: Float, layoutMs: Float) = IntervalStats.EMPTY.copy(
    frames = 300,
    p95FrameMs = p95FrameMs,
    phases = PhaseAverages.of(layout = layoutMs),
)
