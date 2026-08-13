package com.timkrest.framehud.internal

import com.timkrest.framehud.DisplayInfo
import com.timkrest.framehud.FramePhases
import com.timkrest.framehud.FrameWindowStats
import com.timkrest.framehud.MemoryStats
import com.timkrest.framehud.SessionStats
import com.timkrest.framehud.ThermalStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Everything a session report shows, captured at one moment so both formats agree. */
internal class SessionSnapshot(
    val takenAtEpochMs: Long,
    /** Same moment as [takenAtEpochMs] on the `System.nanoTime` clock that frame ends use. */
    val takenAtNs: Long,
    val timeZone: TimeZone,
    val frameHudVersion: String,
    val packageName: String,
    val appVersionName: String?,
    val appVersionCode: Long,
    val apiLevel: Int,
    val manufacturer: String,
    val model: String,
    val isEnabled: Boolean,
    val isFrozen: Boolean,
    val screenName: String?,
    val mark: String?,
    val context: Map<String, String>,
    val session: SessionStats,
    val screen: SessionStats,
    val phases: FramePhases,
    val window: FrameWindowStats,
    val display: DisplayInfo,
    val memory: MemoryStats,
    val thermal: ThermalStats,
    val worstFrames: List<WorstFrames.Frame>,
) {

    fun frameEndEpochMs(endNs: Long): Long = takenAtEpochMs - (takenAtNs - endNs) / NS_PER_MS_LONG

    fun formatTimestamp(epochMs: Long): String = format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", epochMs)

    fun formatTimeOfDay(epochMs: Long): String = format("HH:mm:ss.SSS", epochMs)

    private fun format(pattern: String, epochMs: Long): String = SimpleDateFormat(pattern, Locale.US)
        .apply { timeZone = this@SessionSnapshot.timeZone }
        .format(Date(epochMs))
}

internal const val NS_PER_MS_LONG: Long = 1_000_000L
