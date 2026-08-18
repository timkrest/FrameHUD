package com.timkrest.framehud.internal

import com.timkrest.framehud.BaselineComparison
import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.IntervalComparison
import com.timkrest.framehud.IntervalId
import com.timkrest.framehud.IntervalStats
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.MetricValue

internal const val EXPORT_SCHEMA_VERSION = 5

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
    putObject("device") { putEnvironment(environment) }
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
    putObject("session") {
        sessionBudgetMs()?.let { put("frameBudgetMs", it) }
        putStats(session)
    }
    putObject("screen") {
        put("name", screenName)
        putStats(screen)
    }
    putArray("intervals") {
        for (interval in intervals) {
            if (interval.id == IntervalId.Session) continue
            addObject {
                put("interval", interval.id.key())
                interval.frameBudgetMs?.let { put("frameBudgetMs", it) }
                putStats(interval.stats)
            }
        }
    }
    putBaseline(baseline)
    putObject("window") {
        put("fps", window.fps)
        put("jankPercent", window.jankPercent)
        put("p95FrameMs", window.p95FrameMs)
        put("worstFrameMs", window.worstFrameMs)
        putObject("phases") {
            put("bottleneckStage", phases.bottleneckStage.name)
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
        }
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

private fun SessionSnapshot.sessionBudgetMs(): Int? =
    intervals.firstOrNull { it.id == IntervalId.Session }?.frameBudgetMs

private fun JsonObjectScope.putBaseline(comparison: BaselineComparison) {
    when (comparison) {
        BaselineComparison.NoBaseline -> putNull("baseline")
        is BaselineComparison.OtherEnvironment -> putObject("baseline") {
            put("comparable", false)
            putObject("recordedEnvironment") { putEnvironment(comparison.recorded) }
            putObject("environment") { putEnvironment(comparison.current) }
        }
        is BaselineComparison.Compared -> putObject("baseline") {
            put("comparable", true)
            putArray("intervals") {
                for (interval in comparison.intervals) addObject { putIntervalComparison(interval) }
            }
        }
    }
}

private fun JsonObjectScope.putIntervalComparison(interval: IntervalComparison) {
    put("interval", interval.id.key())
    put("recordedRuns", interval.recordedRuns)
    put("frames", interval.currentFrames)
    putArray("metrics") {
        for (delta in interval.metrics) {
            addObject {
                put("metric", delta.metric.name)
                put("baseline", delta.baseline)
                put("baselineRuns", delta.baselineRuns)
                put("current", delta.current)
                put("change", delta.change)
                put("changePercent", delta.changePercent)
            }
        }
    }
    putArray("uncompared") {
        for (left in interval.uncompared) {
            addObject {
                put("metric", left.metric.name)
                put("gap", left.gap.name)
            }
        }
    }
    putArray("phases") {
        for (delta in interval.phases) {
            addObject {
                put("phase", delta.phase.name)
                put("baselineMs", delta.baselineMs)
                put("currentMs", delta.currentMs)
                put("changeMs", delta.changeMs)
            }
        }
    }
}

private fun JsonObjectScope.putStats(stats: IntervalStats) {
    put("frames", stats.frames)
    put("durationMs", stats.durationMs)
    put("p50FrameMs", stats.p50FrameMs)
    put("p95FrameMs", stats.p95FrameMs)
    put("p99FrameMs", stats.p99FrameMs)
    put("jankPercent", stats.jankPercent)
    put("lostTimeMs", stats.lostTimeMs)
    put("frozenFrames", stats.frozenFrames)
    put("maxJankStreak", stats.maxJankStreak)
    put("droppedReports", stats.droppedReports)
    putObject("phases") {
        put("bottleneckStage", stats.phases.bottleneckStage.name)
        putPhaseAverages(stats.phases)
    }
    putObject("confidence") { putConfidence(stats.confidence) }
}

private fun JsonObjectScope.putConfidence(confidence: MeasurementConfidence) {
    put("suspect", confidence.isSuspect)
    putArray("issues") {
        for (issue in confidence.issues) addObject { putIssue(issue) }
    }
}

private fun JsonObjectScope.putIssue(issue: ConfidenceIssue) {
    when (issue) {
        is ConfidenceIssue.DroppedReports -> {
            put("type", "droppedReports")
            put("count", issue.count)
        }
        is ConfidenceIssue.SlowListener -> {
            put("type", "slowListener")
            put("longestCallMs", issue.longestCallMs)
        }
        is ConfidenceIssue.ThermalThrottling -> {
            put("type", "thermalThrottling")
            put("worstLevel", issue.worstLevel.name)
        }
        is ConfidenceIssue.LowBattery -> {
            put("type", "lowBattery")
            put("powerSaveMode", issue.powerSaveMode)
            put("levelPercent", issue.levelPercent)
        }
        is ConfidenceIssue.RefreshRateChanged -> {
            put("type", "refreshRateChanged")
            putArray("ratesHz") { for (rate in issue.ratesHz.sorted()) add(rate) }
        }
        is ConfidenceIssue.Emulator -> {
            put("type", "emulator")
        }
        is ConfidenceIssue.ShortSample -> {
            put("type", "shortSample")
            put("frames", issue.frames)
        }
    }
    putArray("affected") { for (metric in issue.affected) add(metric.name) }
}

private fun JsonObjectScope.putPhase(name: String, value: MetricValue?) {
    if (value == null) {
        putNull(name)
        return
    }
    putObject(name) {
        put("averageMs", value.average)
        put("peakSinceResetMs", value.peak)
    }
}
