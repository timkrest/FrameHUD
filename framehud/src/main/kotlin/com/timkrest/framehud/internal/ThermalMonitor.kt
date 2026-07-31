package com.timkrest.framehud.internal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.ChecksSdkIntAtLeast
import com.timkrest.framehud.ThermalLevel
import com.timkrest.framehud.ThermalStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ThermalMonitor {

    private val _stats = MutableStateFlow(ThermalStats.EMPTY)
    val stats: StateFlow<ThermalStats> = _stats.asStateFlow()

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    private val hasThermalStatus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @field:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    private val hasThermalHeadroom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @Volatile
    private var isFrozen = false

    @Volatile
    private var powerManager: PowerManager? = null

    @Volatile
    private var lastSampleMs = 0L

    fun bind(context: Context) {
        if (!hasThermalStatus) return
        powerManager = context.applicationContext.getSystemService(PowerManager::class.java)
    }

    fun unbind() {
        powerManager = null
        lastSampleMs = 0L
        _stats.value = ThermalStats.EMPTY
    }

    fun setFrozen(frozen: Boolean) {
        isFrozen = frozen
    }

    fun sample() {
        if (!hasThermalStatus) return
        val manager = powerManager ?: return
        if (isFrozen) return
        val now = SystemClock.elapsedRealtime()
        if (lastSampleMs != 0L && now - lastSampleMs < SAMPLE_INTERVAL_MS) return
        lastSampleMs = now

        _stats.value = ThermalStats(
            level = thermalLevelOf(manager.currentThermalStatus),
            headroom = readHeadroom(manager),
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

    companion object {
        private const val SAMPLE_INTERVAL_MS = 2000L
        private const val HEADROOM_FORECAST_SECONDS = 0
    }
}
