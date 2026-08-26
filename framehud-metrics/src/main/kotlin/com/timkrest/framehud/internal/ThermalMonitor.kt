package com.timkrest.framehud.internal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.annotation.AnyThread
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.WorkerThread
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.StateFlow

@WorkerThread
internal class ThermalMonitor(clock: MetricsClock) {

    private val readings = FreezableReading(ThermalStats.EMPTY)

    @get:AnyThread
    val stats: StateFlow<ThermalStats> = readings.published

    val liveStats: ThermalStats get() = readings.live

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private val hasThermalStatus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    private val hasThermalHeadroom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private var powerManager: PowerManager? = null

    private val sampleLimit = RateLimit(clock, MIN_SAMPLE_INTERVAL_MS)

    private val headroomLimit = RateLimit(clock, MIN_HEADROOM_INTERVAL_MS)

    fun bind(context: Context) {
        if (!hasThermalStatus) return
        powerManager = context.applicationContext.getSystemService(PowerManager::class.java)
    }

    fun unbind() {
        powerManager = null
        sampleLimit.clear()
        headroomLimit.clear()
        readings.reset(ThermalStats.EMPTY)
    }

    @AnyThread
    fun setFrozen(frozen: Boolean) {
        readings.setFrozen(frozen)
    }

    fun sample() {
        if (!hasThermalStatus) return
        val manager = powerManager ?: return
        if (!sampleLimit.tryTake()) return
        val headroom = if (headroomLimit.tryTake()) readHeadroom(manager) else readings.live.headroom

        readings.update(
            ThermalStats(
                level = thermalLevelOf(manager.currentThermalStatus),
                headroom = headroom,
            ),
        )
    }

    private fun readHeadroom(manager: PowerManager): Float? {
        if (!hasThermalHeadroom) return null
        return manager.getThermalHeadroom(HEADROOM_FORECAST_SECONDS).takeIf { !it.isNaN() }
    }

    private fun thermalLevelOf(status: Int): ThermalLevel = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SHUTDOWN
        else -> ThermalLevel.UNKNOWN
    }

    private companion object {
        const val MIN_SAMPLE_INTERVAL_MS = 1_000L

        const val PLATFORM_HEADROOM_RATE_LIMIT_MS = 1_000L

        const val MIN_HEADROOM_INTERVAL_MS = 2 * PLATFORM_HEADROOM_RATE_LIMIT_MS

        const val HEADROOM_FORECAST_SECONDS = 0
    }
}
