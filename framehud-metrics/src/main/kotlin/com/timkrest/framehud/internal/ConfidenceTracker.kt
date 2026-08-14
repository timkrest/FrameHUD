package com.timkrest.framehud.internal

import androidx.annotation.WorkerThread
import com.timkrest.framehud.ConfidenceIssue
import com.timkrest.framehud.MeasurementConfidence
import com.timkrest.framehud.ThermalLevel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@WorkerThread
internal class ConfidenceTracker(private val isEmulator: Boolean) {

    private val refreshRatesHz = mutableSetOf<Int>()
    private var worstThrottlingLevel: ThermalLevel? = null
    private var longestListenerCallMs: Float? = null
    private var sawPowerSaveMode = false
    private var lowestBatteryPercent: Int? = null

    fun addRefreshRate(refreshRateHz: Float) {
        refreshRatesHz += refreshRateHz.roundToInt()
    }

    fun addThermalLevel(level: ThermalLevel) {
        if (!level.isThrottling) return
        val current = worstThrottlingLevel
        if (current == null || level > current) worstThrottlingLevel = level
    }

    fun addSlowListener(callMs: Float) {
        longestListenerCallMs = max(longestListenerCallMs ?: 0f, callMs)
    }

    fun addBattery(sample: BatterySample) {
        sawPowerSaveMode = sawPowerSaveMode || sample.powerSaveMode
        val level = sample.levelPercent ?: return
        lowestBatteryPercent = min(level, lowestBatteryPercent ?: level)
    }

    fun confidence(frames: Int, droppedReports: Int): MeasurementConfidence = MeasurementConfidence(
        buildList {
            if (droppedReports > 0) add(ConfidenceIssue.DroppedReports(droppedReports))
            longestListenerCallMs?.let { add(ConfidenceIssue.SlowListener(it)) }
            worstThrottlingLevel?.let { add(ConfidenceIssue.ThermalThrottling(it)) }
            val lowest = lowestBatteryPercent
            if (sawPowerSaveMode || (lowest != null && lowest <= SYSTEM_LOW_BATTERY_WARNING_PERCENT)) {
                add(ConfidenceIssue.LowBattery(powerSaveMode = sawPowerSaveMode, levelPercent = lowest))
            }
            if (refreshRatesHz.size > 1) add(ConfidenceIssue.RefreshRateChanged(refreshRatesHz.toSet()))
            if (isEmulator) add(ConfidenceIssue.Emulator)
            if (frames < ConfidenceIssue.ShortSample.MIN_FRAMES_P99) add(ConfidenceIssue.ShortSample(frames))
        },
    )

    fun clear() {
        refreshRatesHz.clear()
        worstThrottlingLevel = null
        longestListenerCallMs = null
        sawPowerSaveMode = false
        lowestBatteryPercent = null
    }
}

private const val SYSTEM_LOW_BATTERY_WARNING_PERCENT = 15
