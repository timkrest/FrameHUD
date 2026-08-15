package com.timkrest.framehud.internal

import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FrameHistory
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalStats
import java.util.TimeZone

internal fun sessionSnapshotFixture(
    screenName: String? = null,
    mark: String? = null,
    context: Map<String, String> = emptyMap(),
    session: SessionStats = SessionStats.EMPTY,
    screen: SessionStats = SessionStats.EMPTY,
    phases: FramePhases = FramePhases.EMPTY,
    window: FrameWindowStats = FrameWindowStats.EMPTY,
    worstFrames: List<WorstFrames.Frame> = emptyList(),
) = SessionSnapshot(
    takenAtEpochMs = TAKEN_AT_EPOCH_MS,
    takenAtNs = TAKEN_AT_NS,
    timeZone = TimeZone.getTimeZone("UTC"),
    frameHudVersion = "1.2.3",
    packageName = "com.example.app",
    appVersionName = "9.9",
    appVersionCode = 42L,
    apiLevel = 34,
    manufacturer = "Google",
    model = "Pixel 8",
    isEnabled = true,
    isFrozen = false,
    screenName = screenName,
    mark = mark,
    context = context,
    session = session,
    screen = screen,
    phases = phases,
    window = window,
    display = DisplayInfo(refreshRateHz = 60f, frameBudgetMs = 16.6f),
    memory = MemoryStats.EMPTY,
    thermal = ThermalStats.EMPTY,
    worstFrames = worstFrames,
)

internal fun windowOf(totalsMs: FloatArray, deadlinesMs: FloatArray) = FrameWindowStats(
    fps = totalsMs.size,
    jankPercent = 50f,
    p95FrameMs = totalsMs.max(),
    worstFrameMs = totalsMs.max(),
    history = FrameHistory.of(totalsMs = totalsMs, deadlinesMs = deadlinesMs),
)

internal const val TAKEN_AT_EPOCH_MS = 1_700_000_000_000L
internal const val TAKEN_AT_NS = 1_000_000_000_000L
