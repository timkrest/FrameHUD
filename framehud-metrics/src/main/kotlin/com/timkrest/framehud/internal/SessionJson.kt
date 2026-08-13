package com.timkrest.framehud.internal

import com.timkrest.framehud.MetricValue
import com.timkrest.framehud.SessionStats

internal const val EXPORT_SCHEMA_VERSION = 1

internal fun SessionSnapshot.toJson(): String = buildJsonObject {
    put("schema", EXPORT_SCHEMA_VERSION)
    put("generatedAt", formatTimestamp(takenAtEpochMs))
    put("generatedAtMs", takenAtEpochMs)
    put("frameHudVersion", frameHudVersion)
    putObject("app") {
        put("packageName", packageName)
        put("versionName", appVersionName)
        put("versionCode", appVersionCode)
    }
    putObject("device") {
        put("manufacturer", manufacturer)
        put("model", model)
        put("apiLevel", apiLevel)
    }
    putObject("display") {
        put("refreshRateHz", display.refreshRateHz)
        put("frameBudgetMs", display.frameBudgetMs)
    }
    putObject("measurement") {
        put("enabled", isEnabled)
        put("frozen", isFrozen)
        put("screen", screenName)
        put("mark", mark)
        putObject("context") {
            for ((key, value) in context) put(key, value)
        }
    }
    putObject("session") { putStats(session) }
    putObject("screen") {
        put("name", screenName)
        putStats(screen)
    }
    putObject("window") {
        put("fps", window.fps)
        put("jankPercent", window.jankPercent)
        put("p95FrameMs", window.p95FrameMs)
        put("worstFrameMs", window.worstFrameMs)
        putArray("frames") {
            val history = window.history
            for (index in 0 until history.size) {
                addObject {
                    put("totalMs", history.totalMsAt(index))
                    put("deadlineMs", history.deadlineMsAt(index))
                }
            }
        }
    }
    putObject("phases") {
        putPhase("unknownDelay", phases.unknownDelay)
        putPhase("input", phases.input)
        putPhase("animation", phases.animation)
        putPhase("layout", phases.layout)
        putPhase("draw", phases.draw)
        putPhase("sync", phases.sync)
        putPhase("commandIssue", phases.commandIssue)
        putPhase("swapBuffers", phases.swapBuffers)
        putPhase("gpu", phases.gpu)
        putPhase("total", phases.total)
        putPhase("overrun", phases.overrun)
        put("isGpuAvailable", phases.isGpuAvailable)
    }
    putArray("worstFrames") {
        for (frame in worstFrames) {
            addObject {
                put("totalMs", frame.totalMs)
                put("at", formatTimestamp(frameEndEpochMs(frame.endNs)))
                put("atMs", frameEndEpochMs(frame.endNs))
            }
        }
    }
    putObject("memory") {
        put("usedHeapMb", memory.usedHeapMb)
        put("maxHeapMb", memory.maxHeapMb)
        put("nativeHeapMb", memory.nativeHeapMb)
        put("peakUsedHeapMb", memory.peakUsedHeapMb)
        put("peakNativeHeapMb", memory.peakNativeHeapMb)
        put("gcCount", memory.gcCount)
        put("gcTimeMs", memory.gcTimeMs)
    }
    putObject("thermal") {
        put("level", thermal.level.name)
        put("headroom", thermal.headroom)
    }
}

private fun JsonObjectScope.putStats(stats: SessionStats) {
    put("frames", stats.frames)
    put("durationMs", stats.durationMs)
    put("p50FrameMs", stats.p50FrameMs)
    put("p95FrameMs", stats.p95FrameMs)
    put("p99FrameMs", stats.p99FrameMs)
    put("jankPercent", stats.jankPercent)
    put("frozenFrames", stats.frozenFrames)
    put("maxJankStreak", stats.maxJankStreak)
    put("droppedReports", stats.droppedReports)
}

private fun JsonObjectScope.putPhase(name: String, value: MetricValue) {
    putObject(name) {
        put("averageMs", value.average)
        put("peakMs", value.peak)
    }
}
