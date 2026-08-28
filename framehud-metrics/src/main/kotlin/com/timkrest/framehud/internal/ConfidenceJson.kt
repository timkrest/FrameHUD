package com.timkrest.framehud.internal

import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.ThermalLevel

internal fun JsonObjectScope.putConfidence(confidence: MeasurementConfidence) {
    put(SUSPECT, confidence.isSuspect)
    putArray(ISSUES) {
        for (issue in confidence.issues) addObject { putIssue(issue) }
    }
}

internal fun JsonValue.confidence(): MeasurementConfidence? {
    val listed = member(ISSUES) as? JsonValue.Arr ?: return null
    return MeasurementConfidence(listed.items.map { it.issue() ?: return null })
}

private fun JsonObjectScope.putIssue(issue: ConfidenceIssue) {
    when (issue) {
        is ConfidenceIssue.DroppedReports -> {
            put(TYPE, DROPPED_REPORTS)
            put(COUNT, issue.count)
        }
        is ConfidenceIssue.SlowListener -> {
            put(TYPE, SLOW_LISTENER)
            put(LONGEST_CALL_MS, issue.longestCallMs)
        }
        is ConfidenceIssue.ThermalThrottling -> {
            put(TYPE, THERMAL_THROTTLING)
            put(WORST_LEVEL, issue.worstLevel.name)
        }
        is ConfidenceIssue.LowBattery -> {
            put(TYPE, LOW_BATTERY)
            put(POWER_SAVE_MODE, issue.powerSaveMode)
            put(LEVEL_PERCENT, issue.levelPercent)
        }
        is ConfidenceIssue.RefreshRateChanged -> {
            put(TYPE, REFRESH_RATE_CHANGED)
            putArray(RATES_HZ) { for (rate in issue.ratesHz.sorted()) add(rate) }
        }
        is ConfidenceIssue.Emulator -> {
            put(TYPE, EMULATOR)
        }
        is ConfidenceIssue.ShortSample -> {
            put(TYPE, SHORT_SAMPLE)
            put(FRAMES, issue.frames)
        }
    }
    putArray(AFFECTED) { for (metric in issue.affected) add(metric.name) }
}

private fun JsonValue.issue(): ConfidenceIssue? = readOrNull {
    when (string(TYPE)) {
        DROPPED_REPORTS -> int(COUNT)?.let(ConfidenceIssue::DroppedReports)
        SLOW_LISTENER -> float(LONGEST_CALL_MS)?.let(ConfidenceIssue::SlowListener)
        THERMAL_THROTTLING -> thermalLevel(string(WORST_LEVEL))?.let(ConfidenceIssue::ThermalThrottling)
        LOW_BATTERY -> bool(POWER_SAVE_MODE)?.let { powerSaveMode ->
            ConfidenceIssue.LowBattery(powerSaveMode = powerSaveMode, levelPercent = int(LEVEL_PERCENT))
        }
        REFRESH_RATE_CHANGED -> ratesHz()?.let(ConfidenceIssue::RefreshRateChanged)
        EMULATOR -> ConfidenceIssue.Emulator
        SHORT_SAMPLE -> int(FRAMES)?.let(ConfidenceIssue::ShortSample)
        else -> null
    }
}

private fun JsonValue.ratesHz(): Set<Int>? {
    val listed = member(RATES_HZ) as? JsonValue.Arr ?: return null
    return listed.items.mapTo(LinkedHashSet(listed.items.size)) { item ->
        val number = (item as? JsonValue.Num)?.value ?: return null
        number.toInt().takeIf { it.toDouble() == number } ?: return null
    }
}

private fun thermalLevel(name: String?): ThermalLevel? = ThermalLevel.entries.firstOrNull { it.name == name }

private const val SUSPECT = "suspect"
private const val ISSUES = "issues"
private const val AFFECTED = "affected"
private const val TYPE = "type"
private const val COUNT = "count"
private const val LONGEST_CALL_MS = "longestCallMs"
private const val WORST_LEVEL = "worstLevel"
private const val POWER_SAVE_MODE = "powerSaveMode"
private const val LEVEL_PERCENT = "levelPercent"
private const val RATES_HZ = "ratesHz"
private const val FRAMES = "frames"
private const val DROPPED_REPORTS = "droppedReports"
private const val SLOW_LISTENER = "slowListener"
private const val THERMAL_THROTTLING = "thermalThrottling"
private const val LOW_BATTERY = "lowBattery"
private const val REFRESH_RATE_CHANGED = "refreshRateChanged"
private const val EMULATOR = "emulator"
private const val SHORT_SAMPLE = "shortSample"
